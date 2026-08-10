import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { Modal } from './ui';
import { useToast } from './ToastProvider';
import { t } from '../i18n/fr';
import { apiErrorMessage, apiErrorStatus } from '../lib/api';
import { createSignal } from '../lib/endpoints';

/** Dialogue de signalement réutilisé depuis le lecteur (icône drapeau sur une question). */
export function SignalDialog({
  open,
  questionId,
  onClose,
}: {
  open: boolean;
  questionId: string | null;
  onClose: () => void;
}) {
  const { toast } = useToast();
  const [message, setMessage] = useState('');
  const [error, setError] = useState<string | null>(null);

  const send = useMutation({
    mutationFn: () => createSignal(questionId!, message),
    onSuccess: () => {
      toast('success', t('signals.sent'));
      setMessage('');
      onClose();
    },
    onError: (err) =>
      setError(
        apiErrorStatus(err) === 429 ? t('signals.rateLimited') : (apiErrorMessage(err) ?? t('common.error')),
      ),
  });

  return (
    <Modal open={open} onClose={onClose} title={t('signals.reportTitle')}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError(null);
          send.mutate();
        }}
        className="space-y-4"
      >
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          required
          maxLength={2000}
          rows={4}
          placeholder={t('signals.message')}
          aria-label={t('signals.message')}
          className="w-full rounded-lg border border-subtle px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
        />
        {error ? <p className="text-sm font-medium text-danger">{error}</p> : null}
        <button
          type="submit"
          disabled={send.isPending}
          className="w-full rounded-lg bg-accent px-4 py-2.5 text-sm font-bold text-white transition hover:bg-accent-hover disabled:opacity-60"
        >
          {t('signals.submit')}
        </button>
      </form>
    </Modal>
  );
}
