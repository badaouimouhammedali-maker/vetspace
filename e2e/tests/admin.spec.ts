import { expect, test, type Page } from '@playwright/test';

/**
 * Full admin journey against the seeded e2e stack:
 *
 *   login as admin
 *     → author a question (2 propositions, rich HTML explanation)
 *     → publish it
 *     → generate 5 activation codes
 *     → download the CSV and parse it
 *     → disable a student, prove that student can no longer log in
 *     → re-enable
 *
 * The disable/re-enable leg is the point of the test. Every other step has a unit or
 * integration test behind it; "the account toggle actually locks somebody out" is a
 * claim that spans the admin UI, the users API, the auth filter and the login page, and
 * only an end-to-end run can make it.
 */
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@vetspace.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'e2e-admin-password';
const STUDENT_PASSWORD = 'e2e-student-password';

/** Unique per run, so repeated local runs never collide on a username or email. */
function unique(): string {
  return Date.now().toString(36) + Math.floor(Math.random() * 1000);
}

/**
 * Logs in and waits for the session to actually be established.
 *
 * <p>The wait is not optional: navigating straight after the click races the auth
 * round-trip, and the route guard then bounces you back to /login — which reads as a
 * broken app rather than as a test that moved too early.
 */
async function login(page: Page, email: string, password: string, expectSuccess = true) {
  await page.goto('/login');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  if (expectSuccess) {
    await expect(page).toHaveURL(/\/app$/);
  }
}

async function logout(page: Page) {
  // Clearing storage is not enough on its own: the refresh token lives in an httpOnly
  // cookie, so a stale session would survive into the next login and mask a failure.
  await page.context().clearCookies();
  await page.goto('/login');
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
}

/** Registers a student through the UI and returns the credentials. */
async function registerStudent(page: Page) {
  const id = unique();
  const email = `e2e-admin-${id}@example.com`;
  await page.goto('/register');
  await page.getByLabel('Nom', { exact: true }).fill('Cible');
  await page.getByLabel('Prénom', { exact: true }).fill('Étudiant');
  await page.getByLabel("Nom d'utilisateur").fill(`cible${id}`);
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('École').selectOption({ label: 'ENSV Alger' });
  await page.getByLabel("Année d'étude").selectOption({ label: 'Année 3' });
  await page.getByLabel('Mot de passe', { exact: true }).fill(STUDENT_PASSWORD);
  await page.getByLabel('Confirmer le mot de passe').fill(STUDENT_PASSWORD);
  await page.getByRole('button', { name: "S'inscrire" }).click();
  await expect(page).toHaveURL(/\/login$/);
  return { email, fullName: 'Étudiant Cible' };
}

test('admin journey: author + publish a question, issue codes, disable and restore a student', async ({
  page,
}) => {
  const statement = `E2E question ${unique()}`;

  // A student to disable later. Registered first, while we are logged out anyway.
  const student = await registerStudent(page);

  // --- Login as admin ----------------------------------------------------
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  // Admins land in the app shell; the console is reachable from there.
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: /Vue d'ensemble|Console/ })).toBeVisible();

  // --- Author a question -------------------------------------------------
  await page.goto('/admin/questions');
  await page.getByRole('button', { name: '+ Question' }).click();
  await expect(page.getByRole('heading', { name: 'Nouvelle question' })).toBeVisible();

  // Index 1 skips the "— Module —" placeholder. Selecting by index rather than by
  // label keeps the test working when a seeded module is renamed.
  const moduleSelect = page.getByLabel('Module');
  const courseSelect = page.getByLabel('Cours');
  await moduleSelect.selectOption({ index: 1 });

  // The course select is disabled until a module is picked and only then populated, so
  // wait for both before choosing — and assert the choice actually took. Without this
  // last check an empty courseId makes the form refuse to submit later, with the real
  // cause ("Veuillez choisir un cours") twenty lines away from the failure.
  await expect(courseSelect).toBeEnabled();
  await expect(courseSelect.locator('option')).not.toHaveCount(1);
  await courseSelect.selectOption({ index: 1 });
  await expect(courseSelect).not.toHaveValue('');

  await page.getByLabel(/Énoncé/).fill(statement);

  const propositions = page.locator('form').getByText(/^Proposition [A-E]$/);
  await expect(propositions).toHaveCount(2); // the editor opens with A and B

  // Proposition A — true, with a rich explanation.
  const propTexts = page.getByPlaceholder('Texte de la proposition');
  await propTexts.nth(0).fill('La scapula est un os plat.');
  await page
    .getByRole('button', { name: 'VRAI' })
    .nth(0)
    .click();
  const explanations = page.getByRole('textbox', { name: '', exact: true }).filter({
    has: page.locator('xpath=self::*[@contenteditable="true"]'),
  });
  await explanations.nth(0).click();
  await page.keyboard.type('Os plat de la ceinture scapulaire.');
  // Bold a fragment so the saved HTML really is rich, not just a <p>.
  await page.keyboard.down('Shift');
  for (let i = 0; i < 10; i++) {
    await page.keyboard.press('ArrowLeft');
  }
  await page.keyboard.up('Shift');
  await page.getByRole('button', { name: /gras|bold/i }).first().click();

  // Proposition B — false.
  await propTexts.nth(1).fill('La scapula est un os long.');
  await page
    .getByRole('button', { name: 'FAUX' })
    .nth(1)
    .click();

  await page.getByRole('button', { name: 'Enregistrer' }).click();

  // Back on the list, saved as a draft.
  await expect(page.getByRole('heading', { name: 'Questions' })).toBeVisible();
  const row = page.locator('tr', { hasText: statement });
  await expect(row).toBeVisible();
  await expect(row.getByText('Brouillon')).toBeVisible();

  // --- Publish it ---------------------------------------------------------
  await row.getByRole('button', { name: 'Publier' }).click();
  await expect(row.getByText('Publiée')).toBeVisible();

  // ...and put it back to draft. A published question joins the pool every student
  // session draws from, so leaving it there makes this test change the outcome of the
  // others — which is exactly how it broke the smoke run. Publish is still proven; the
  // catalogue is just handed back the way it was found.
  await row.getByRole('button', { name: 'Dépublier' }).click();
  await expect(row.getByText('Brouillon')).toBeVisible();

  // --- Generate 5 activation codes ---------------------------------------
  await page.goto('/admin/packs');
  await page.getByRole('button', { name: 'Générer codes' }).first().click();
  await page.getByLabel('Nombre de codes').fill('5');
  await page.getByRole('button', { name: 'Générer', exact: true }).click();

  // The codes are shown exactly once — the UI says so, and this asserts it issued 5.
  const codeList = page.locator('.font-mono > div');
  await expect(codeList).toHaveCount(5);

  // --- Download the CSV and parse it -------------------------------------
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Télécharger CSV' }).click(),
  ]);
  expect(download.suggestedFilename()).toBe('activation-codes.csv');

  const stream = await download.createReadStream();
  const csv = await new Promise<string>((resolve, reject) => {
    let out = '';
    stream.on('data', (chunk) => (out += chunk));
    stream.on('end', () => resolve(out));
    stream.on('error', reject);
  });

  const lines = csv.trim().split('\n');
  expect(lines[0]).toBe('code');
  const codes = lines.slice(1);
  expect(codes).toHaveLength(5);
  // Every row is a real code, not a header repeat or a blank line.
  for (const code of codes) {
    expect(code.trim()).toMatch(/^[A-Z0-9-]{8,}$/);
  }
  // And they are distinct — a generator that repeats itself would sell the same code twice.
  expect(new Set(codes).size).toBe(5);

  // Escape rather than clicking "Fermer": the modal's ✕ carries the same accessible
  // name as its footer button, and dismissing with the keyboard exercises the trap.
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();

  // --- Disable the student ------------------------------------------------
  await page.goto('/admin/abonnes');
  await page.getByPlaceholder(/Nom, e-mail/).fill(student.email);
  const studentRow = page.locator('tr', { hasText: student.email });
  await expect(studentRow).toBeVisible();
  await expect(studentRow.getByText('Actif')).toBeVisible();

  page.once('dialog', (d) => d.accept()); // "Désactiver le compte de … ?"
  await studentRow.getByRole('button', { name: 'Désactiver' }).click();
  await expect(studentRow.getByText('Désactivé')).toBeVisible();

  // --- The student can no longer log in ------------------------------------
  await logout(page);
  await login(page, student.email, STUDENT_PASSWORD, false);
  // Refused: still on /login, with an error and no app shell.
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByText(/identifiants|désactivé|incorrect/i).first()).toBeVisible();

  // --- Re-enable, and the student is welcome back ---------------------------
  await logout(page);
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.goto('/admin/abonnes');
  await page.getByPlaceholder(/Nom, e-mail/).fill(student.email);
  const restored = page.locator('tr', { hasText: student.email });

  page.once('dialog', (d) => d.accept()); // "Réactiver le compte de … ?"
  await restored.getByRole('button', { name: 'Réactiver' }).click();
  await expect(restored.getByText('Actif')).toBeVisible();

  await logout(page);
  await login(page, student.email, STUDENT_PASSWORD);
  await expect(page).toHaveURL(/\/app$/);
});
