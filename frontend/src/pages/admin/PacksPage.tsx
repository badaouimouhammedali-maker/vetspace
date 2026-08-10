import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { EmptyState, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  createPack,
  deletePack,
  fetchAdminPacks,
  fetchCodes,
  generateCodes,
  revokeCode,
  updatePack,
  type PackInput,
} from '../../lib/adminEndpoints';
import type { AdminPack, CodeStatus, GenerateCodesResponse } from '../../lib/schemas';
import { AdminHeader, Card, GhostButton, Pager, PrimaryButton, StatusBadge } from './adminUi';

const YEAR_OPTS = [null, 1, 2, 3, 4, 5, 6];

export function PacksPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const packs = useQuery({ queryKey: ['admin', 'packs'], queryFn: fetchAdminPacks });
  const [packModal, setPackModal] = useState<AdminPack | 'new' | null>(null);
  const [genFor, setGenFor] = useState<AdminPack | null>(null);


  const del = useMutation({
    mutationFn: deletePack,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'packs'] });
      toast('success', 'Pack supprimé.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <div>
      <AdminHeader title="Packs & Codes" subtitle="Offres d'abonnement et codes d'activation.">
        <PrimaryButton onClick={() => setPackModal('new')}>+ Pack</PrimaryButton>
      </AdminHeader>

      <Card className="mb-8 overflow-x-auto p-0">
        {packs.isLoading ? (
          <div className="p-5">
            <TableSkeleton rows={5} />
          </div>
        ) : (packs.data ?? []).length === 0 ? (
          <div className="p-5">
            <EmptyState icon="📦" title="Aucun pack" compact />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-subtle text-left text-xs uppercase text-ink-muted">
                <th className="px-4 py-3 font-semibold">Nom</th>
                <th className="px-4 py-3 font-semibold">Année</th>
                <th className="px-4 py-3 font-semibold">Prix</th>
                <th className="px-4 py-3 font-semibold">Expire</th>
                <th className="px-4 py-3 font-semibold">État</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {packs.data!.map((p) => (
                <tr key={p.id} className="border-b border-subtle last:border-0">
                  <td className="px-4 py-3 font-semibold text-ink">
                    {p.name}
                    <span className="ml-1 text-xs font-normal text-ink-muted">
                      {p.academicYear}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-ink-muted">
                    {p.studyYear == null ? 'Toutes' : `A${p.studyYear}`}
                  </td>
                  <td className="px-4 py-3 text-ink-muted">{p.priceDa} DA</td>
                  <td className="px-4 py-3 text-ink-muted">
                    {new Date(p.expiresAt).toLocaleDateString('fr-FR')}
                  </td>
                  <td className="px-4 py-3">
                    {p.active ? (
                      <StatusBadge tone="green">Actif</StatusBadge>
                    ) : (
                      <StatusBadge tone="gray">Inactif</StatusBadge>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <GhostButton onClick={() => setGenFor(p)}>Générer codes</GhostButton>
                      <GhostButton onClick={() => setPackModal(p)}>Modifier</GhostButton>
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm(`Supprimer le pack « ${p.name} » ?`)) {
                            del.mutate(p.id);
                          }
                        }}
                      >
                        Suppr.
                      </GhostButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <CodesSection packs={packs.data ?? []} />

      {packModal ? (
        <PackModal
          pack={packModal === 'new' ? null : packModal}
          onClose={() => setPackModal(null)}
          onSaved={() => {
            setPackModal(null);
            qc.invalidateQueries({ queryKey: ['admin', 'packs'] });
          }}
        />
      ) : null}

      {genFor ? <GenerateModal pack={genFor} onClose={() => setGenFor(null)} /> : null}
    </div>
  );
}

function CodesSection({ packs }: { packs: AdminPack[] }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [packId, setPackId] = useState('');
  const [status, setStatus] = useState<CodeStatus | ''>('');
  const [page, setPage] = useState(0);
  const codes = useQuery({
    queryKey: ['admin', 'codes', packId, page],
    queryFn: () => fetchCodes(packId || null, page),
  });
  const revoke = useMutation({
    mutationFn: revokeCode,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'codes'] });
      toast('success', 'Code révoqué.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  const rows = (codes.data?.content ?? []).filter((c) => !status || c.status === status);
  const statusTone: Record<CodeStatus, 'green' | 'gray' | 'red' | 'amber'> = {
    ACTIVE: 'green',
    EXHAUSTED: 'gray',
    REVOKED: 'red',
    EXPIRED: 'amber',
  };

  return (
    <div>
      <h2 className="mb-3 text-lg font-bold text-ink">Codes d'activation</h2>
      <div className="mb-3 flex flex-wrap gap-3">
        <Select
          value={packId}
          onChange={(e) => {
            setPackId(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Tous les packs</option>
          {packs.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </Select>
        <Select value={status} onChange={(e) => setStatus(e.target.value as CodeStatus | '')}>
          <option value="">Tous les états</option>
          <option value="ACTIVE">Actif</option>
          <option value="EXHAUSTED">Épuisé</option>
          <option value="REVOKED">Révoqué</option>
          <option value="EXPIRED">Expiré</option>
        </Select>
      </div>
      <Card className="overflow-x-auto p-0">
        {codes.isLoading ? (
          <TableSkeleton rows={5} />
        ) : rows.length === 0 ? (
          <div className="p-5">
            <EmptyState icon="🔑" title="Aucun code" compact />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-subtle text-left text-xs uppercase text-ink-muted">
                <th className="px-4 py-3 font-semibold">Pack</th>
                <th className="px-4 py-3 font-semibold">Utilisations</th>
                <th className="px-4 py-3 font-semibold">État</th>
                <th className="px-4 py-3 font-semibold">Créé le</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id} className="border-b border-subtle last:border-0">
                  <td className="px-4 py-3 font-semibold text-ink">{c.packName}</td>
                  <td className="px-4 py-3 text-ink-muted">
                    {c.usedCount} / {c.maxUses}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge tone={statusTone[c.status]}>{c.status}</StatusBadge>
                  </td>
                  <td className="px-4 py-3 text-ink-muted">
                    {new Date(c.createdAt).toLocaleDateString('fr-FR')}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {!c.revoked && c.status !== 'REVOKED' ? (
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm('Révoquer ce code ? Cette action est définitive.')) {
                            revoke.mutate(c.id);
                          }
                        }}
                      >
                        Révoquer
                      </GhostButton>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <Pager page={page} totalPages={codes.data?.totalPages ?? 1} onPage={setPage} />
    </div>
  );
}

function PackModal({
  pack,
  onClose,
  onSaved,
}: {
  pack: AdminPack | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const [studyYear, setStudyYear] = useState<number | null>(pack?.studyYear ?? null);
  const [name, setName] = useState(pack?.name ?? '');
  const [academicYear, setAcademicYear] = useState(pack?.academicYear ?? '');
  const [priceDa, setPriceDa] = useState(pack?.priceDa ?? 0);
  const [active, setActive] = useState(pack?.active ?? true);
  const [expiresAt, setExpiresAt] = useState(
    pack
      ? pack.expiresAt.slice(0, 10)
      : new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10),
  );

  const save = useMutation({
    mutationFn: () => {
      const body: PackInput = {
        studyYear,
        name,
        academicYear,
        priceDa,
        active,
        expiresAt: new Date(expiresAt + 'T23:59:59').toISOString(),
      };
      return pack ? updatePack(pack.id, body) : createPack(body);
    },
    onSuccess: () => {
      toast('success', 'Pack enregistré.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <Modal open onClose={onClose} title={pack ? 'Modifier le pack' : 'Nouveau pack'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Année d'étude" htmlFor="pk-year">
          <Select
            id="pk-year"
            value={studyYear == null ? '' : String(studyYear)}
            onChange={(e) => setStudyYear(e.target.value === '' ? null : Number(e.target.value))}
          >
            {YEAR_OPTS.map((y) => (
              <option key={String(y)} value={y == null ? '' : y}>
                {y == null ? 'Toutes les années' : `Année ${y}`}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Nom" htmlFor="pk-name">
          <TextInput id="pk-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Année universitaire" htmlFor="pk-ay">
          <TextInput
            id="pk-ay"
            required
            placeholder="2026-2027"
            value={academicYear}
            onChange={(e) => setAcademicYear(e.target.value)}
          />
        </Field>
        <Field label="Prix (DA)" htmlFor="pk-price">
          <TextInput
            id="pk-price"
            type="number"
            required
            value={priceDa}
            onChange={(e) => setPriceDa(Number(e.target.value))}
          />
        </Field>
        <Field label="Expire le" htmlFor="pk-exp">
          <TextInput
            id="pk-exp"
            type="date"
            required
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
          />
        </Field>
        <label className="flex items-center gap-2 text-sm font-semibold text-ink-muted">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          Actif
        </label>
        <div className="flex justify-end gap-2">
          <GhostButton onClick={onClose}>Annuler</GhostButton>
          <PrimaryButton type="submit" disabled={save.isPending}>
            Enregistrer
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  );
}

function GenerateModal({ pack, onClose }: { pack: AdminPack; onClose: () => void }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [count, setCount] = useState(10);
  const [maxUses, setMaxUses] = useState(1);
  const [result, setResult] = useState<GenerateCodesResponse | null>(null);

  const gen = useMutation({
    mutationFn: () => generateCodes(pack.id, count, maxUses),
    onSuccess: (r) => {
      setResult(r);
      qc.invalidateQueries({ queryKey: ['admin', 'codes'] });
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  function downloadCsv() {
    if (!result) {
      return;
    }
    const csv = 'code\n' + result.codes.join('\n') + '\n';
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'activation-codes.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Modal open onClose={onClose} title={`Générer des codes — ${pack.name}`}>
      {result ? (
        <div className="space-y-4">
          <div className="rounded-lg border-2 border-danger bg-danger/10 p-3 text-sm text-danger">
            ⚠️ Ces codes ne s'affichent <strong>qu'une seule fois</strong>. Téléchargez-les
            maintenant — ils ne pourront plus être récupérés en clair.
          </div>
          <div className="max-h-48 overflow-y-auto rounded-lg bg-brand-navy-deep p-3 font-mono text-xs text-accent-on-dark">
            {result.codes.map((c) => (
              <div key={c}>{c}</div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <PrimaryButton onClick={downloadCsv}>Télécharger CSV</PrimaryButton>
            <GhostButton onClick={onClose}>Fermer</GhostButton>
          </div>
        </div>
      ) : (
        <form
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            gen.mutate();
          }}
          className="space-y-4"
        >
          <Field label="Nombre de codes" htmlFor="g-count">
            <TextInput
              id="g-count"
              type="number"
              min={1}
              max={500}
              required
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
            />
          </Field>
          <Field label="Utilisations par code" htmlFor="g-max">
            <TextInput
              id="g-max"
              type="number"
              min={1}
              required
              value={maxUses}
              onChange={(e) => setMaxUses(Number(e.target.value))}
            />
          </Field>
          <div className="flex justify-end gap-2">
            <GhostButton onClick={onClose}>Annuler</GhostButton>
            <PrimaryButton type="submit" disabled={gen.isPending}>
              Générer
            </PrimaryButton>
          </div>
        </form>
      )}
    </Modal>
  );
}
