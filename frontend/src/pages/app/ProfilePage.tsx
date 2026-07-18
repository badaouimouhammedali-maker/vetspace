import { useMutation, useQuery } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { Field, PasswordInput, Select, SubmitButton, TextInput } from '../../components/forms';
import { Modal, Skeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { t } from '../../i18n/fr';
import { apiErrorStatus } from '../../lib/api';
import {
  changePassword,
  deleteAccount,
  fetchSchools,
  updateProfile,
  uploadPhoto,
} from '../../lib/endpoints';

const DEFAULT_THEME = { primary: '#0f766e', secondary: '#12355b', tertiary: '#6b7280' };
const STUDY_YEARS = [1, 2, 3, 4, 5, 6];

function applyThemeVars(theme: { primary: string; secondary: string; tertiary: string }) {
  const root = document.documentElement;
  root.style.setProperty('--color-primary', theme.primary);
  root.style.setProperty('--color-secondary', theme.secondary);
  root.style.setProperty('--color-tertiary', theme.tertiary);
}

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const schools = useQuery({ queryKey: ['schools'], queryFn: fetchSchools });

  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [username, setUsername] = useState(user?.username ?? '');
  const [schoolId, setSchoolId] = useState(user?.schoolId ?? '');
  const [studyYear, setStudyYear] = useState(String(user?.studyYear ?? ''));
  const [infoError, setInfoError] = useState<string | null>(null);

  const [theme, setTheme] = useState({
    primary: user?.theme.primary ?? DEFAULT_THEME.primary,
    secondary: user?.theme.secondary ?? DEFAULT_THEME.secondary,
    tertiary: user?.theme.tertiary ?? DEFAULT_THEME.tertiary,
  });

  const [pwOpen, setPwOpen] = useState(false);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [pwError, setPwError] = useState<string | null>(null);

  const [delStep, setDelStep] = useState<0 | 1 | 2>(0);
  const [delPassword, setDelPassword] = useState('');
  const [delError, setDelError] = useState<string | null>(null);

  const photo = useMutation({
    mutationFn: (file: File) => uploadPhoto(file),
    onSuccess: async () => {
      await refreshUser();
      toast('success', t('profile.saved'));
    },
    onError: (err) =>
      toast('error', apiErrorStatus(err) === 413 ? t('common.error') : t('common.error')),
  });

  const saveInfo = useMutation({
    mutationFn: () =>
      updateProfile({
        lastName,
        firstName,
        username,
        ...(schoolId ? { schoolId } : {}),
        ...(studyYear ? { studyYear: Number(studyYear) } : {}),
      }),
    onSuccess: async () => {
      await refreshUser();
      toast('success', t('profile.saved'));
    },
    onError: (err) =>
      setInfoError(apiErrorStatus(err) === 409 ? t('profile.usernameTaken') : t('common.error')),
  });

  const saveTheme = useMutation({
    mutationFn: (next: typeof theme) =>
      updateProfile({
        themePrimary: next.primary,
        themeSecondary: next.secondary,
        themeTertiary: next.tertiary,
      }),
    onSuccess: async () => {
      await refreshUser();
      toast('success', t('profile.themeSaved'));
    },
  });

  const savePassword = useMutation({
    mutationFn: () => changePassword(currentPassword, newPassword),
    onSuccess: () => {
      setPwOpen(false);
      setCurrentPassword('');
      setNewPassword('');
      toast('success', t('profile.passwordChanged'));
    },
    onError: (err) =>
      setPwError(apiErrorStatus(err) === 400 ? t('profile.passwordError') : t('common.error')),
  });

  const removeAccount = useMutation({
    mutationFn: () => deleteAccount(delPassword),
    onSuccess: () => {
      toast('success', t('profile.deleted'));
      navigate('/login');
    },
    onError: (err) =>
      setDelError(apiErrorStatus(err) === 400 ? t('profile.deleteError') : t('common.error')),
  });

  function updateThemeColor(key: keyof typeof theme, value: string) {
    const next = { ...theme, [key]: value };
    setTheme(next);
    applyThemeVars(next); // aperçu en direct
  }

  function resetTheme() {
    setTheme(DEFAULT_THEME);
    applyThemeVars(DEFAULT_THEME);
    saveTheme.mutate(DEFAULT_THEME);
  }

  function onPhotoChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (file) {
      photo.mutate(file);
    }
  }

  const initials = `${user?.firstName?.[0] ?? ''}${user?.lastName?.[0] ?? ''}`.toUpperCase();

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
        {t('profile.title')}
      </h1>

      {/* Photo */}
      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
        <h2 className="mb-4 text-base font-extrabold text-brand-navy">{t('profile.photo')}</h2>
        <div className="flex items-center gap-4">
          <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-brand-green text-2xl font-bold text-white">
            {user?.photoUrl ? (
              <img src={user.photoUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              initials
            )}
          </div>
          <input
            ref={fileRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={onPhotoChange}
            className="hidden"
          />
          <button
            onClick={() => fileRef.current?.click()}
            disabled={photo.isPending}
            className="rounded-lg border-2 border-brand-green px-4 py-2 text-sm font-bold text-brand-green transition hover:bg-brand-green hover:text-white disabled:opacity-60"
          >
            {t('profile.changePhoto')}
          </button>
        </div>
      </section>

      {/* Infos personnelles */}
      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
        <h2 className="mb-4 text-base font-extrabold text-brand-navy">{t('profile.info')}</h2>
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            setInfoError(null);
            saveInfo.mutate();
          }}
          className="space-y-4"
        >
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('profile.lastName')} htmlFor="p-last">
              <TextInput id="p-last" value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </Field>
            <Field label={t('profile.firstName')} htmlFor="p-first">
              <TextInput id="p-first" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </Field>
          </div>
          <Field label={t('profile.username')} htmlFor="p-username" error={infoError ?? undefined}>
            <TextInput
              id="p-username"
              value={username}
              minLength={3}
              onChange={(e) => setUsername(e.target.value)}
            />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('profile.school')} htmlFor="p-school">
              {schools.isLoading ? (
                <Skeleton className="h-11" />
              ) : (
                <Select id="p-school" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}>
                  {(schools.data ?? []).map((school) => (
                    <option key={school.id} value={school.id}>
                      {school.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label={t('profile.studyYear')} htmlFor="p-year">
              <Select id="p-year" value={studyYear} onChange={(e) => setStudyYear(e.target.value)}>
                {STUDY_YEARS.map((year) => (
                  <option key={year} value={year}>
                    {t('abo.year')} {year}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <SubmitButton loading={saveInfo.isPending}>{t('profile.saveInfo')}</SubmitButton>
        </form>
      </section>

      {/* Thème */}
      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
        <h2 className="mb-4 text-base font-extrabold text-brand-navy">{t('profile.theme')}</h2>
        <div className="flex flex-wrap items-end gap-5">
          {(
            [
              ['primary', t('profile.themePrimary')],
              ['secondary', t('profile.themeSecondary')],
              ['tertiary', t('profile.themeTertiary')],
            ] as const
          ).map(([key, label]) => (
            <label key={key} className="flex flex-col items-center gap-1.5">
              <span className="text-xs font-semibold text-brand-navy">{label}</span>
              <input
                type="color"
                value={theme[key]}
                onChange={(e) => updateThemeColor(key, e.target.value)}
                className="h-11 w-16 cursor-pointer rounded-lg border border-gray-300"
              />
            </label>
          ))}
          <button
            onClick={() => saveTheme.mutate(theme)}
            disabled={saveTheme.isPending}
            className="rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white hover:bg-brand-green-hover"
          >
            {t('common.save')}
          </button>
          <button
            onClick={resetTheme}
            className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-semibold text-brand-gray hover:border-brand-navy"
          >
            {t('profile.themeReset')}
          </button>
        </div>
      </section>

      {/* Mot de passe */}
      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100">
        <h2 className="mb-4 text-base font-extrabold text-brand-navy">{t('profile.password')}</h2>
        <button
          onClick={() => {
            setPwError(null);
            setPwOpen(true);
          }}
          className="rounded-lg border-2 border-brand-navy px-4 py-2 text-sm font-bold text-brand-navy transition hover:bg-brand-navy hover:text-white"
        >
          {t('profile.changePassword')}
        </button>
      </section>

      {/* Zone de danger */}
      <section className="rounded-2xl border-2 border-red-200 bg-red-50 p-6">
        <h2 className="mb-2 text-base font-extrabold text-red-700">{t('profile.dangerZone')}</h2>
        <p className="mb-4 text-sm text-red-700/80">{t('profile.deleteWarning')}</p>
        <button
          onClick={() => {
            setDelError(null);
            setDelPassword('');
            setDelStep(1);
          }}
          className="rounded-lg bg-red-600 px-4 py-2.5 text-sm font-extrabold text-white hover:bg-red-700"
        >
          {t('profile.deleteAccount')}
        </button>
      </section>

      {/* Dialogue mot de passe */}
      <Modal open={pwOpen} onClose={() => setPwOpen(false)} title={t('profile.changePassword')}>
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            setPwError(null);
            savePassword.mutate();
          }}
          className="space-y-4"
        >
          <Field label={t('profile.currentPassword')} htmlFor="cur-pw">
            <PasswordInput
              id="cur-pw"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
          </Field>
          <Field label={t('profile.newPassword')} htmlFor="new-pw" error={pwError ?? undefined}>
            <PasswordInput
              id="new-pw"
              required
              minLength={10}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </Field>
          <SubmitButton loading={savePassword.isPending}>{t('common.save')}</SubmitButton>
        </form>
      </Modal>

      {/* Suppression — première confirmation */}
      <Modal open={delStep === 1} onClose={() => setDelStep(0)} title={t('profile.deleteAccount')}>
        <p className="text-sm text-brand-gray">{t('profile.deleteConfirm1')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={() => setDelStep(0)}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => setDelStep(2)}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700"
          >
            {t('common.confirm')}
          </button>
        </div>
      </Modal>

      {/* Suppression — confirmation finale avec mot de passe */}
      <Modal open={delStep === 2} onClose={() => setDelStep(0)} title={t('profile.deleteAccount')}>
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            setDelError(null);
            removeAccount.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-brand-gray">{t('profile.deleteConfirm2')}</p>
          <Field label={t('profile.deletePassword')} htmlFor="del-pw" error={delError ?? undefined}>
            <PasswordInput
              id="del-pw"
              required
              value={delPassword}
              onChange={(e) => setDelPassword(e.target.value)}
            />
          </Field>
          <button
            type="submit"
            disabled={removeAccount.isPending}
            className="w-full rounded-lg bg-red-600 px-4 py-2.5 text-sm font-extrabold text-white hover:bg-red-700 disabled:opacity-60"
          >
            {t('profile.deleteFinal')}
          </button>
        </form>
      </Modal>
    </div>
  );
}
