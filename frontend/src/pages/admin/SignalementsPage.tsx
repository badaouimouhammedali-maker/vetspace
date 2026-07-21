import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Select } from '../../components/forms';
import { EmptyState, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage } from '../../lib/api';
import { fetchAdminSignals, rejectSignal, resolveSignal } from '../../lib/adminEndpoints';
import type { SignalAdmin, SignalStatus } from '../../lib/schemas';
import { AdminHeader, Card, GhostButton, Pager, PrimaryButton, StatusBadge } from './adminUi';

const STATUS_TONE: Record<SignalStatus, 'amber' | 'green' | 'red'> = {
  OPEN: 'amber',
  RESOLVED: 'green',
  REJECTED: 'red',
};
const STATUS_LABEL: Record<SignalStatus, string> = {
  OPEN: 'Ouvert',
  RESOLVED: 'Résolu',
  REJECTED: 'Rejeté',
};

export function SignalementsPage() {
  const [status, setStatus] = useState<SignalStatus | ''>('OPEN');
  const [page, setPage] = useState(0);
  const [replyTo, setReplyTo] = useState<{
    signal: SignalAdmin;
    action: 'resolve' | 'reject';
  } | null>(null);

  const signals = useQuery({
    queryKey: ['admin', 'signals', status, page],
    queryFn: () => fetchAdminSignals(status || null, page),
  });

  return (
    <div>
      <AdminHeader
        title="Signalements"
        subtitle="File d'attente des erreurs signalées par les étudiants."
      />

      <div className="mb-3">
        <Select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as SignalStatus | '');
            setPage(0);
          }}
        >
          <option value="">Tous</option>
          <option value="OPEN">Ouverts</option>
          <option value="RESOLVED">Résolus</option>
          <option value="REJECTED">Rejetés</option>
        </Select>
      </div>

      {signals.isLoading ? (
        <TableSkeleton rows={5} />
      ) : (signals.data?.content ?? []).length === 0 ? (
        <Card>
          <EmptyState
            icon="🚩"
            title="Aucun signalement"
            caption="Rien à traiter pour le moment."
            compact
          />
        </Card>
      ) : (
        <div className="space-y-3">
          {signals.data!.content.map((s) => (
            <Card key={s.id}>
              <div className="mb-2 flex items-start justify-between gap-3">
                <StatusBadge tone={STATUS_TONE[s.status]}>{STATUS_LABEL[s.status]}</StatusBadge>
                <span className="text-xs text-brand-gray">
                  {new Date(s.createdAt).toLocaleString('fr-FR')}
                </span>
              </div>
              <p className="mb-1 text-xs font-semibold uppercase text-brand-gray">Question</p>
              <p className="mb-3 rounded-lg bg-canvas p-3 text-sm text-brand-navy">
                {s.questionStatement}
              </p>
              <p className="mb-1 text-xs font-semibold uppercase text-brand-gray">
                Message de {s.userEmail}
              </p>
              <p className="mb-3 text-sm text-brand-gray">{s.message}</p>
              {s.adminReply ? (
                <p className="mb-3 rounded-lg border-l-4 border-brand-green bg-success/10 p-3 text-sm text-brand-navy">
                  <strong>Réponse :</strong> {s.adminReply}
                </p>
              ) : null}
              {s.status === 'OPEN' ? (
                <div className="flex gap-2">
                  <PrimaryButton onClick={() => setReplyTo({ signal: s, action: 'resolve' })}>
                    Résoudre
                  </PrimaryButton>
                  <GhostButton
                    tone="red"
                    onClick={() => setReplyTo({ signal: s, action: 'reject' })}
                  >
                    Rejeter
                  </GhostButton>
                </div>
              ) : null}
            </Card>
          ))}
        </div>
      )}
      <Pager page={page} totalPages={signals.data?.totalPages ?? 1} onPage={setPage} />

      {replyTo ? (
        <ReplyModal
          signal={replyTo.signal}
          action={replyTo.action}
          onClose={() => setReplyTo(null)}
        />
      ) : null}
    </div>
  );
}

function ReplyModal({
  signal,
  action,
  onClose,
}: {
  signal: SignalAdmin;
  action: 'resolve' | 'reject';
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [reply, setReply] = useState('');
  const mut = useMutation({
    mutationFn: () =>
      action === 'resolve' ? resolveSignal(signal.id, reply) : rejectSignal(signal.id, reply),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'signals'] });
      toast('success', action === 'resolve' ? 'Signalement résolu.' : 'Signalement rejeté.');
      onClose();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });
  return (
    <Modal
      open
      onClose={onClose}
      title={action === 'resolve' ? 'Résoudre le signalement' : 'Rejeter le signalement'}
    >
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          mut.mutate();
        }}
        className="space-y-4"
      >
        <label className="block text-sm font-semibold text-brand-gray">
          Réponse à l'étudiant (facultatif)
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            rows={4}
            className="mt-1 w-full rounded-lg border border-gray-300 p-2 text-sm text-brand-navy focus:border-brand-green focus:outline-none"
          />
        </label>
        <div className="flex justify-end gap-2">
          <GhostButton onClick={onClose}>Annuler</GhostButton>
          <PrimaryButton type="submit" disabled={mut.isPending}>
            Confirmer
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  );
}
