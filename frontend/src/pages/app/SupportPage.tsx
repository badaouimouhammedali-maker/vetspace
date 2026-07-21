import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { t } from '../../i18n/fr';
import { apiErrorStatus } from '../../lib/api';
import { submitSupport } from '../../lib/endpoints';

export function SupportPage() {
  const { user } = useAuth();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = useMutation({
    mutationFn: () => submitSupport(subject, body),
    onSuccess: () => {
      setSent(true);
      setSubject('');
      setBody('');
    },
    onError: (err) =>
      setError(apiErrorStatus(err) === 429 ? t('support.rateLimited') : t('common.error')),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    send.mutate();
  }

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <h1 className="text-h1 text-brand-navy dark:text-white">
        {t('support.title')}
      </h1>

      <div className="rounded-lg bg-surface p-6 shadow-card">
        {sent ? (
          <p className="rounded-lg bg-brand-green/10 p-4 text-sm font-medium text-brand-green">
            {t('support.sent')}
          </p>
        ) : (
          <form onSubmit={onSubmit} className="space-y-4">
            <Field label={t('support.from')} htmlFor="support-from">
              <TextInput id="support-from" value={user?.email ?? ''} readOnly disabled />
            </Field>
            <Field label={t('support.subject')} htmlFor="support-subject">
              <TextInput
                id="support-subject"
                required
                maxLength={255}
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
              />
            </Field>
            <Field label={t('support.body')} htmlFor="support-body">
              <textarea
                id="support-body"
                required
                maxLength={5000}
                rows={6}
                value={body}
                onChange={(e) => setBody(e.target.value)}
                className="w-full rounded-lg border border-gray-300 px-3.5 py-2.5 text-sm outline-none focus:border-brand-green focus:ring-2 focus:ring-brand-green/20"
              />
            </Field>
            {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}
            <SubmitButton loading={send.isPending}>{t('support.submit')}</SubmitButton>
          </form>
        )}
      </div>

      <div className="rounded-lg bg-brand-navy/5 p-5">
        <p className="text-sm font-bold text-brand-navy">{t('support.contactTitle')}</p>
        <p className="mt-1 text-sm text-brand-gray">{t('support.contactInfo')}</p>
      </div>
    </div>
  );
}
