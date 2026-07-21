import { expect, test } from '@playwright/test';

/**
 * Full student journey against the seeded e2e stack:
 * register → login → redeem (seeded code) → create session → answer 3 → submit → stats.
 */
const SEED_CODE = process.env.E2E_SEED_CODE ?? 'E2E1-2345-6789-ABCD';
const PASSWORD = 'e2e-student-password';

test('student journey: register → login → redeem → session → answer 3 → submit → stats', async ({
  page,
}) => {
  const unique = Date.now().toString(36) + Math.floor(Math.random() * 1000);
  const username = `e2e${unique}`;
  const email = `e2e-${unique}@example.com`;

  // --- Register ---------------------------------------------------------
  await page.goto('/register');
  await page.getByLabel('Nom', { exact: true }).fill('Test');
  await page.getByLabel('Prénom', { exact: true }).fill('Étudiant');
  await page.getByLabel("Nom d'utilisateur").fill(username);
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('École').selectOption({ label: 'ENSV Alger' });
  await page.getByLabel("Année d'étude").selectOption({ label: 'Année 3' });
  await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD);
  await page.getByLabel('Confirmer le mot de passe').fill(PASSWORD);
  await page.getByRole('button', { name: "S'inscrire" }).click();

  // Redirected to login after successful registration.
  await expect(page).toHaveURL(/\/login$/);

  // --- Login ------------------------------------------------------------
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/app$/);

  // --- Redeem the seeded activation code --------------------------------
  await page.goto('/app/abonnement');
  await page.getByRole('textbox', { name: 'Activer un code' }).fill(SEED_CODE);
  await page.getByRole('button', { name: 'VALIDER' }).click();
  await expect(page.getByText(/Abonnement activé/).first()).toBeVisible();

  // --- Create a 3-question training session ------------------------------
  await page.goto('/app/sessions/entrainement');
  await page.getByRole('button', { name: /Créer une nouvelle session/ }).first().click();
  await page.getByLabel('Module').selectOption({ label: 'Anatomie' });
  const countField = page.getByLabel('Nombre de questions');
  await countField.fill('3');
  await page.getByRole('button', { name: 'Créer la session' }).click();

  // Landed in the fullscreen player.
  await expect(page).toHaveURL(/\/app\/session\//);

  // --- Answer 3 questions ------------------------------------------------
  for (let i = 0; i < 3; i++) {
    // Match the proposition row by its letter badge, not by its text. Matching
    // /Proposition A/ only worked because the seeded propositions happen to be *named*
    // "Proposition A…"; the moment any other test authored and published a question,
    // this session could draw it and the selector found nothing. The accessible name of
    // a row is "<letter> <text>", so anchoring on the letter is text-independent.
    await page.getByRole('button', { name: /^A\s/ }).first().click();
    await page.getByRole('button', { name: 'VALIDER' }).click();
    // Correction shown (VRAI/FAUX badge appears).
    await expect(page.getByText(/VRAI|FAUX/).first()).toBeVisible();

    if (i < 2) {
      await page.getByRole('button', { name: 'Suivant' }).click();
    } else {
      await page.getByRole('button', { name: 'Terminer la session' }).click();
    }
  }

  // --- Stats visible -----------------------------------------------------
  await page.goto('/app/statistiques');
  await expect(page.getByRole('heading', { name: 'Statistiques' })).toBeVisible();
  // The session we just completed is listed with its metrics (a data row in the stats table).
  await expect(page.locator('table tbody tr').first()).toBeVisible();
});
