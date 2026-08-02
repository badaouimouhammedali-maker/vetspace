import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { TextInput } from '../../components/forms';
import { EmptyState, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  fetchSubscriptionAudit,
  searchAdminUsers,
  revokeUserSessions,
  updateUserStatus,
} from '../../lib/adminEndpoints';
import type { AdminUser } from '../../lib/schemas';
import {
  AdminHeader,
  AdminToolbar,
  Card,
  GhostButton,
  Pager,
  PrimaryButton,
  StatusBadge,
} from './adminUi';

export function AbonnesPage() {
  const qc = useQueryClient();
  const { user } = useAuth();
  const { toast } = useToast();
  const isAdmin = user?.role === 'ADMIN';
  const [input, setInput] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [auditFor, setAuditFor] = useState<AdminUser | null>(null);

  const users = useQuery({
    queryKey: ['admin', 'users', query, page],
    queryFn: () => searchAdminUsers(query, page),
  });

  const revoke = useMutation({
    mutationFn: (id: string) => revokeUserSessions(id),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] });
      toast(
        'success',
        r.revokedSessions === 0
          ? 'Aucune session active à fermer.'
          : `${r.revokedSessions} session(s) fermée(s).`,
      );
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  const toggle = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'ACTIVE' | 'DISABLED' }) =>
      updateUserStatus(id, status),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] });
      toast('success', u.status === 'DISABLED' ? 'Compte désactivé.' : 'Compte réactivé.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <div>
      <AdminHeader
        title="Abonnés"
        subtitle="Rechercher, consulter et gérer les comptes étudiants."
      />

      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          setPage(0);
          setQuery(input.trim());
        }}
      >
        <AdminToolbar
          search={
            <TextInput
              placeholder="Nom, e-mail ou nom d'utilisateur…"
              value={input}
              onChange={(e) => setInput(e.target.value)}
            />
          }
          action={<PrimaryButton type="submit">Rechercher</PrimaryButton>}
        />
      </form>

      <Card className="overflow-x-auto p-0">
        {users.isLoading ? (
          <TableSkeleton rows={5} />
        ) : (users.data?.content ?? []).length === 0 ? (
          <div className="p-5">
            <EmptyState
              icon="🔍"
              title="Aucun étudiant trouvé"
              caption="Essayez un autre terme de recherche."
              compact
            />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs uppercase text-brand-gray">
                <th className="px-4 py-3 font-semibold">Étudiant</th>
                <th className="px-4 py-3 font-semibold">E-mail</th>
                <th className="px-4 py-3 font-semibold">École / Année</th>
                <th className="px-4 py-3 font-semibold">Abo. actifs</th>
                {/* The sharing signal. Two numbers side by side because neither means
                    much alone: 20 logins from one address is a diligent student on one
                    laptop; 6 logins from 6 addresses is an account doing the rounds. */}
                <th className="px-4 py-3 font-semibold" title="Connexions sur les 7 derniers jours">
                  Connexions 7j
                </th>
                <th className="px-4 py-3 font-semibold" title="Adresses IP distinctes sur les 7 derniers jours">
                  IP distinctes
                </th>
                <th className="px-4 py-3 font-semibold">Appareil actuel</th>
                <th className="px-4 py-3 font-semibold">État</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {users.data!.content.map((u) => (
                <tr key={u.id} className="border-b border-gray-50 last:border-0">
                  <td className="px-4 py-3">
                    <div className="font-semibold text-brand-navy">{u.fullName}</div>
                    <div className="text-xs text-brand-gray">@{u.username}</div>
                  </td>
                  <td className="px-4 py-3 text-brand-gray">{u.email}</td>
                  <td className="px-4 py-3 text-brand-gray">
                    {u.schoolName ?? '—'} {u.studyYear ? `· A${u.studyYear}` : ''}
                  </td>
                  <td className="px-4 py-3 text-brand-gray">{u.activeSubscriptions}</td>
                  <td className="px-4 py-3 tabular-nums text-brand-gray">{u.logins7d}</td>
                  <td className="px-4 py-3 tabular-nums">
                    {/* Highlighted past 2, which is roughly "phone + laptop, one network".
                        A hint to look, never an accusation and never an automatic action. */}
                    <span className={u.distinctIps7d > 2 ? 'font-bold text-warning' : 'text-brand-gray'}>
                      {u.distinctIps7d}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-brand-gray">
                    {u.activeSessions === 0 ? (
                      'Hors ligne'
                    ) : (
                      <>
                        <div>{u.lastDeviceLabel ?? 'Appareil inconnu'}</div>
                        {u.lastSeenAt ? (
                          <div className="text-[11px]">
                            {new Date(u.lastSeenAt).toLocaleString('fr-FR')}
                          </div>
                        ) : null}
                      </>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {u.status === 'ACTIVE' ? (
                      <StatusBadge tone="green">Actif</StatusBadge>
                    ) : (
                      <StatusBadge tone="red">Désactivé</StatusBadge>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <GhostButton onClick={() => setAuditFor(u)}>Abonnements</GhostButton>
                      {isAdmin && u.activeSessions > 0 ? (
                        <GhostButton
                          onClick={() => {
                            if (
                              confirm(
                                `Fermer la session active de ${u.fullName} ? Le compte reste actif : l'étudiant pourra se reconnecter immédiatement.`,
                              )
                            ) {
                              revoke.mutate(u.id);
                            }
                          }}
                        >
                          Révoquer la session active
                        </GhostButton>
                      ) : null}
                      {isAdmin ? (
                        <GhostButton
                          tone={u.status === 'ACTIVE' ? 'red' : 'gray'}
                          onClick={() => {
                            const next = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
                            if (
                              confirm(
                                next === 'DISABLED'
                                  ? `Désactiver le compte de ${u.fullName} ? Il sera déconnecté et ne pourra plus se connecter.`
                                  : `Réactiver le compte de ${u.fullName} ?`,
                              )
                            ) {
                              toggle.mutate({ id: u.id, status: next });
                            }
                          }}
                        >
                          {u.status === 'ACTIVE' ? 'Désactiver' : 'Réactiver'}
                        </GhostButton>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <Pager page={page} totalPages={users.data?.totalPages ?? 1} onPage={setPage} />

      {auditFor ? <AuditModal user={auditFor} onClose={() => setAuditFor(null)} /> : null}
    </div>
  );
}

function AuditModal({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const audit = useQuery({
    queryKey: ['admin', 'subscriptions', user.email],
    queryFn: () => fetchSubscriptionAudit(user.email),
  });
  return (
    <Modal open onClose={onClose} title={`Abonnements — ${user.fullName}`}>
      {audit.isLoading ? (
        <TableSkeleton rows={5} />
      ) : (audit.data ?? []).length === 0 ? (
        <EmptyState icon="🎫" title="Aucun abonnement pour ce compte" compact />
      ) : (
        <ul className="space-y-2">
          {audit.data!.map((s) => (
            <li key={s.id} className="rounded-lg bg-canvas p-3 text-sm">
              <div className="font-semibold text-brand-navy">{s.packName}</div>
              <div className="text-xs text-brand-gray">
                Du {new Date(s.startsAt).toLocaleDateString('fr-FR')} au{' '}
                {new Date(s.endsAt).toLocaleDateString('fr-FR')}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}
