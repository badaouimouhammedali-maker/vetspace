import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { EmptyState, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  createSourceExam,
  deleteSourceExam,
  fetchAdminSourceExams,
  updateSourceExam,
} from '../../lib/adminEndpoints';
import type { AdminSourceExam, ExamType } from '../../lib/schemas';
import { AdminHeader, Card, GhostButton, PrimaryButton, StatusBadge } from './adminUi';

const EXAM_TYPES: { value: ExamType; label: string }[] = [
  { value: 'ENTRAINEMENT', label: 'Entraînement' },
  { value: 'EXAMEN', label: 'Examen' },
];

export function SourcesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const sources = useQuery({ queryKey: ['admin', 'source-exams'], queryFn: fetchAdminSourceExams });
  const [modal, setModal] = useState<AdminSourceExam | 'new' | null>(null);


  const del = useMutation({
    mutationFn: deleteSourceExam,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'source-exams'] });
      toast('success', "Source d'examen supprimée.");
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <div>
      <AdminHeader title="Sources d'examen" subtitle="Références des sujets (année, type, école).">
        <PrimaryButton onClick={() => setModal('new')}>+ Source</PrimaryButton>
      </AdminHeader>

      <Card className="overflow-x-auto p-0">
        {sources.isLoading ? (
          <TableSkeleton rows={5} />
        ) : (sources.data ?? []).length === 0 ? (
          <div className="p-5">
            <EmptyState icon="📝" title="Aucune source d'examen" compact />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-subtle text-left text-xs uppercase text-ink-muted">
                <th className="px-4 py-3 font-semibold">Libellé</th>
                <th className="px-4 py-3 font-semibold">Année</th>
                <th className="px-4 py-3 font-semibold">Type</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {sources.data!.map((s) => (
                <tr key={s.id} className="border-b border-subtle last:border-0">
                  <td className="px-4 py-3 font-semibold text-ink">{s.label}</td>
                  <td className="px-4 py-3 text-ink-muted">{s.year}</td>
                  <td className="px-4 py-3">
                    <StatusBadge tone={s.examType === 'EXAMEN' ? 'navy' : 'gray'}>
                      {s.examType === 'EXAMEN' ? 'Examen' : 'Entraînement'}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <GhostButton onClick={() => setModal(s)}>Modifier</GhostButton>
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm(`Supprimer « ${s.label} » ?`)) {
                            del.mutate(s.id);
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

      {modal ? (
        <SourceModal
          source={modal === 'new' ? null : modal}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            qc.invalidateQueries({ queryKey: ['admin', 'source-exams'] });
          }}
        />
      ) : null}
    </div>
  );
}

function SourceModal({
  source,
  onClose,
  onSaved,
}: {
  source: AdminSourceExam | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const [label, setLabel] = useState(source?.label ?? '');
  const [year, setYear] = useState(source?.year ?? new Date().getFullYear());
  const [examType, setExamType] = useState<ExamType>(source?.examType ?? 'EXAMEN');
  const save = useMutation({
    mutationFn: () => {
      const body = { label, year, examType };
      return source ? updateSourceExam(source.id, body) : createSourceExam(body);
    },
    onSuccess: () => {
      toast('success', 'Source enregistrée.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });
  return (
    <Modal open onClose={onClose} title={source ? 'Modifier la source' : 'Nouvelle source'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Libellé" htmlFor="src-label">
          <TextInput
            id="src-label"
            required
            value={label}
            onChange={(e) => setLabel(e.target.value)}
          />
        </Field>
        <Field label="Année" htmlFor="src-year">
          <TextInput
            id="src-year"
            type="number"
            required
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
          />
        </Field>
        <Field label="Type" htmlFor="src-type">
          <Select
            id="src-type"
            value={examType}
            onChange={(e) => setExamType(e.target.value as ExamType)}
          >
            {EXAM_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </Select>
        </Field>
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
