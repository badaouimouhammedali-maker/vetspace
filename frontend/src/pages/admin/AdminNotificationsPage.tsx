import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { EmptyState, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  broadcastNotification,
  fetchAdminSchools,
  fetchNotificationHistory,
  type BroadcastInput,
} from '../../lib/adminEndpoints';
import type { NotificationKind } from '../../lib/schemas';
import { AdminHeader, Card, PrimaryButton, StatusBadge } from './adminUi';

const KINDS: { value: NotificationKind; label: string }[] = [
  { value: 'INFO', label: 'Info' },
  { value: 'UPDATE', label: 'Mise à jour' },
  { value: 'QUESTIONS', label: 'Questions' },
];
const YEARS = [1, 2, 3, 4, 5, 6];

export function AdminNotificationsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const schools = useQuery({ queryKey: ['admin', 'schools'], queryFn: fetchAdminSchools });
  const history = useQuery({
    queryKey: ['admin', 'notif-history'],
    queryFn: fetchNotificationHistory,
  });

  const [kind, setKind] = useState<NotificationKind>('INFO');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [schoolId, setSchoolId] = useState('');
  const [studyYear, setStudyYear] = useState('');

  const send = useMutation({
    mutationFn: () => {
      const payload: BroadcastInput = { kind, title, body };
      if (schoolId) payload.schoolId = schoolId;
      if (studyYear) payload.studyYear = Number(studyYear);
      return broadcastNotification(payload);
    },
    onSuccess: () => {
      toast('success', 'Notification envoyée.');
      setTitle('');
      setBody('');
      qc.invalidateQueries({ queryKey: ['admin', 'notif-history'] });
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <div>
      <AdminHeader
        title="Notifications"
        subtitle="Diffuser une annonce et consulter l'historique."
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <h2 className="mb-4 text-sm font-bold uppercase tracking-wide text-brand-gray">
            Nouvelle diffusion
          </h2>
          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              send.mutate();
            }}
            className="space-y-4"
          >
            <Field label="Type" htmlFor="n-kind">
              <Select
                id="n-kind"
                value={kind}
                onChange={(e) => setKind(e.target.value as NotificationKind)}
              >
                {KINDS.map((k) => (
                  <option key={k.value} value={k.value}>
                    {k.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Titre" htmlFor="n-title">
              <TextInput
                id="n-title"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </Field>
            <label className="block text-sm font-semibold text-brand-gray">
              Message
              <textarea
                required
                value={body}
                onChange={(e) => setBody(e.target.value)}
                rows={4}
                className="mt-1 w-full rounded-lg border border-gray-300 p-2 text-sm text-brand-navy focus:border-brand-green focus:outline-none"
              />
            </label>
            <div className="grid grid-cols-2 gap-3">
              <Field label="École (facultatif)" htmlFor="n-school">
                <Select
                  id="n-school"
                  value={schoolId}
                  onChange={(e) => setSchoolId(e.target.value)}
                >
                  <option value="">Toutes</option>
                  {(schools.data ?? []).map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Année (facultatif)" htmlFor="n-year">
                <Select
                  id="n-year"
                  value={studyYear}
                  onChange={(e) => setStudyYear(e.target.value)}
                >
                  <option value="">Toutes</option>
                  {YEARS.map((y) => (
                    <option key={y} value={y}>
                      Année {y}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <p className="text-xs text-brand-gray">
              Laisser « Toutes » cible l'ensemble des étudiants.
            </p>
            <PrimaryButton type="submit" disabled={send.isPending}>
              Diffuser
            </PrimaryButton>
          </form>
        </Card>

        <div>
          <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-brand-gray">
            Historique
          </h2>
          {history.isLoading ? (
            <TableSkeleton rows={5} />
          ) : (history.data ?? []).length === 0 ? (
            <Card>
              <EmptyState icon="🔔" title="Aucune notification envoyée" compact />
            </Card>
          ) : (
            <div className="space-y-3">
              {history.data!.map((n) => (
                <Card key={n.id}>
                  <div className="mb-1 flex items-center justify-between gap-2">
                    <span className="text-sm font-bold text-brand-navy">{n.title}</span>
                    <StatusBadge tone="navy">{n.kind}</StatusBadge>
                  </div>
                  <p className="mb-2 text-sm text-brand-gray">{n.body}</p>
                  <p className="text-xs text-brand-gray">
                    Cible : {n.schoolName ?? 'Toutes écoles'}
                    {n.studyYear ? ` · Année ${n.studyYear}` : ' · Toutes années'} ·{' '}
                    {new Date(n.createdAt).toLocaleDateString('fr-FR')}
                  </p>
                </Card>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
