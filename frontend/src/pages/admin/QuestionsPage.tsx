import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type FormEvent } from 'react';
import { RichTextEditor } from '../../components/RichTextEditor';
import { Field, Select, TextInput } from '../../components/forms';
import { Button, Modal, SegmentToggle, TableSkeleton } from '../../components/ui';
import { SafeHtml } from '../../lib/sanitize';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage } from '../../lib/api';
import {
  createQuestion,
  deleteQuestion,
  fetchAdminCourses,
  fetchAdminModules,
  fetchAdminQuestions,
  fetchAdminSourceExams,
  importQuestions,
  publishQuestion,
  updateQuestion,
  uploadContentImage,
  type QuestionFilters,
  type QuestionInput,
} from '../../lib/adminEndpoints';
import {
  importRowErrorSchema,
  type AdminCourse,
  type AdminSourceExam,
  type Difficulty,
  type QuestionAdmin,
} from '../../lib/schemas';
import { z } from 'zod';
import {
  AdminHeader,
  AdminToolbar,
  Card,
  GhostButton,
  Pager,
  PrimaryButton,
  StatusBadge,
} from './adminUi';

const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: 'EASY', label: 'Facile' },
  { value: 'MEDIUM', label: 'Moyen' },
  { value: 'HARD', label: 'Difficile' },
];
const LETTERS = ['A', 'B', 'C', 'D', 'E'];

export function QuestionsPage() {
  const [editing, setEditing] = useState<QuestionAdmin | 'new' | null>(null);
  const [bulkOpen, setBulkOpen] = useState(false);

  const modules = useQuery({ queryKey: ['admin', 'modules'], queryFn: fetchAdminModules });
  const courses = useQuery({ queryKey: ['admin', 'courses'], queryFn: fetchAdminCourses });
  const sources = useQuery({ queryKey: ['admin', 'source-exams'], queryFn: fetchAdminSourceExams });

  if (editing) {
    return (
      <QuestionEditor
        question={editing === 'new' ? null : editing}
        courses={courses.data ?? []}
        sources={sources.data ?? []}
        modules={(modules.data ?? []).map((m) => ({ id: m.id, name: m.name, studyYear: m.studyYear }))}
        onDone={() => setEditing(null)}
      />
    );
  }

  return (
    <div>
      <AdminHeader title="Questions" subtitle="Banque de questions et propositions.">
        <GhostButton onClick={() => setBulkOpen(true)}>Import JSON</GhostButton>
        <PrimaryButton onClick={() => setEditing('new')}>+ Question</PrimaryButton>
      </AdminHeader>

      <QuestionsTable
        modules={(modules.data ?? []).map((m) => ({ id: m.id, name: m.name, studyYear: m.studyYear }))}
        courses={courses.data ?? []}
        sources={sources.data ?? []}
        onEdit={setEditing}
      />

      {bulkOpen ? <BulkImportModal onClose={() => setBulkOpen(false)} /> : null}
    </div>
  );
}

function QuestionsTable({
  modules,
  courses,
  sources,
  onEdit,
}: {
  modules: { id: string; name: string; studyYear: number }[];
  courses: AdminCourse[];
  sources: AdminSourceExam[];
  onEdit: (q: QuestionAdmin) => void;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [moduleId, setModuleId] = useState('');
  const [courseId, setCourseId] = useState('');
  const [sourceExamId, setSourceExamId] = useState('');
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [published, setPublished] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  const filters: QuestionFilters = {};
  if (courseId) filters.courseId = courseId;
  else if (moduleId) filters.moduleId = moduleId;
  if (sourceExamId) filters.sourceExamId = sourceExamId;
  if (difficulty) filters.difficulty = difficulty;
  if (published) filters.published = published === 'true';
  if (q) filters.q = q;

  const list = useQuery({
    queryKey: ['admin', 'questions', filters, page],
    queryFn: () => fetchAdminQuestions(filters, page),
  });

  const moduleCourses = useMemo(
    () => courses.filter((c) => c.moduleId === moduleId),
    [courses, moduleId],
  );
  const courseName = (id: string) => courses.find((c) => c.id === id)?.name ?? '—';

  const del = useMutation({
    mutationFn: deleteQuestion,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'questions'] });
      toast('success', 'Question supprimée.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });
  const pub = useMutation({
    mutationFn: ({ id, p }: { id: string; p: boolean }) => publishQuestion(id, p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'questions'] }),
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });

  const resetPage = () => setPage(0);

  return (
    <>
      <AdminToolbar
        search={
          <TextInput
          placeholder="Rechercher dans l'énoncé…"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            resetPage();
          }}
        />
        }
        filters={
          <>
            <Select
          value={moduleId}
          onChange={(e) => {
            setModuleId(e.target.value);
            setCourseId('');
            resetPage();
          }}
        >
          <option value="">Tous les modules</option>
          {modules.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name} (A{m.studyYear})
            </option>
          ))}
        </Select>
        <Select
          value={courseId}
          onChange={(e) => {
            setCourseId(e.target.value);
            resetPage();
          }}
          disabled={!moduleId}
        >
          <option value="">Tous les cours</option>
          {moduleCourses.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
        <Select
          value={sourceExamId}
          onChange={(e) => {
            setSourceExamId(e.target.value);
            resetPage();
          }}
        >
          <option value="">Toutes les sources</option>
          {sources.map((s) => (
            <option key={s.id} value={s.id}>
              {s.label} ({s.year})
            </option>
          ))}
        </Select>
        <Select
          value={difficulty}
          onChange={(e) => {
            setDifficulty(e.target.value as Difficulty | '');
            resetPage();
          }}
        >
          <option value="">Toutes difficultés</option>
          {DIFFICULTIES.map((d) => (
            <option key={d.value} value={d.value}>
              {d.label}
            </option>
          ))}
        </Select>
        <Select
          value={published}
          onChange={(e) => {
            setPublished(e.target.value);
            resetPage();
          }}
        >
          <option value="">Tous les états</option>
          <option value="true">Publiées</option>
          <option value="false">Brouillons</option>
        </Select>
          </>
        }
      />

      <Card className="overflow-x-auto p-0">
        {list.isLoading ? (
          <TableSkeleton rows={5} />
        ) : (list.data?.content ?? []).length === 0 ? (
          <p className="p-5 text-sm text-brand-gray">Aucune question.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left text-xs uppercase text-brand-gray">
                <th className="px-4 py-3 font-semibold">Énoncé</th>
                <th className="px-4 py-3 font-semibold">Cours</th>
                <th className="px-4 py-3 font-semibold">Prop.</th>
                <th className="px-4 py-3 font-semibold">Diff.</th>
                <th className="px-4 py-3 font-semibold">État</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {list.data!.content.map((question) => (
                <tr key={question.id} className="border-b border-gray-50 last:border-0">
                  <td className="max-w-md px-4 py-3">
                    <span className="line-clamp-2 font-medium text-brand-navy">
                      {question.statement}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-brand-gray">{courseName(question.courseId)}</td>
                  <td className="px-4 py-3 text-brand-gray">{question.propositions.length}</td>
                  <td className="px-4 py-3 text-brand-gray">{question.difficulty ?? '—'}</td>
                  <td className="px-4 py-3">
                    {question.published ? (
                      <StatusBadge tone="green">Publiée</StatusBadge>
                    ) : (
                      <StatusBadge tone="gray">Brouillon</StatusBadge>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <GhostButton onClick={() => pub.mutate({ id: question.id, p: !question.published })}>
                        {question.published ? 'Dépublier' : 'Publier'}
                      </GhostButton>
                      <GhostButton onClick={() => onEdit(question)}>Éditer</GhostButton>
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm('Supprimer cette question ?')) {
                            del.mutate(question.id);
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
      <Pager page={page} totalPages={list.data?.totalPages ?? 1} onPage={setPage} />
    </>
  );
}

interface PropState {
  letter: string;
  text: string;
  isTrue: boolean;
  explanationHtml: string;
  explanationImages: string[];
}

function QuestionEditor({
  question,
  courses,
  sources,
  modules,
  onDone,
}: {
  question: QuestionAdmin | null;
  courses: AdminCourse[];
  sources: AdminSourceExam[];
  modules: { id: string; name: string; studyYear: number }[];
  onDone: () => void;
}) {
  const qc = useQueryClient();
  const { toast } = useToast();

  const initialModuleId = question
    ? (courses.find((c) => c.id === question.courseId)?.moduleId ?? '')
    : '';
  const [moduleId, setModuleId] = useState(initialModuleId);
  const [courseId, setCourseId] = useState(question?.courseId ?? '');
  const [statement, setStatement] = useState(question?.statement ?? '');
  const [statementImages, setStatementImages] = useState<string[]>(question?.statementImages ?? []);
  const [sourceExamId, setSourceExamId] = useState(question?.sourceExamId ?? '');
  const [difficulty, setDifficulty] = useState<Difficulty | ''>(question?.difficulty ?? '');
  const [published, setPublished] = useState(question?.published ?? false);
  const [props, setProps] = useState<PropState[]>(
    question
      ? question.propositions.map((p) => ({
          letter: p.letter,
          text: p.text,
          isTrue: p.isTrue,
          explanationHtml: p.explanationHtml ?? '',
          explanationImages: p.explanationImages ?? [],
        }))
      : [
          { letter: 'A', text: '', isTrue: false, explanationHtml: '', explanationImages: [] },
          { letter: 'B', text: '', isTrue: false, explanationHtml: '', explanationImages: [] },
        ],
  );

  const moduleCourses = useMemo(
    () => courses.filter((c) => c.moduleId === moduleId),
    [courses, moduleId],
  );

  function relabel(list: PropState[]): PropState[] {
    return list.map((p, i) => ({ ...p, letter: LETTERS[i] ?? p.letter }));
  }
  function setProp(i: number, patch: Partial<PropState>) {
    setProps((prev) => prev.map((p, idx) => (idx === i ? { ...p, ...patch } : p)));
  }
  function addProp() {
    if (props.length >= 5) return;
    setProps((prev) =>
      relabel([...prev, { letter: 'X', text: '', isTrue: false, explanationHtml: '', explanationImages: [] }]),
    );
  }
  function removeProp(i: number) {
    if (props.length <= 2) return;
    setProps((prev) => relabel(prev.filter((_, idx) => idx !== i)));
  }

  const save = useMutation({
    mutationFn: () => {
      const body: QuestionInput = {
        courseId,
        statement,
        published,
        propositions: props.map((p) => {
          const prop: QuestionInput['propositions'][number] = {
            letter: p.letter,
            text: p.text,
            isTrue: p.isTrue,
          };
          if (p.explanationHtml.trim()) prop.explanationHtml = p.explanationHtml;
          if (p.explanationImages.length) prop.explanationImages = p.explanationImages;
          return prop;
        }),
      };
      if (statementImages.length) body.statementImages = statementImages;
      if (sourceExamId) body.sourceExamId = sourceExamId;
      if (difficulty) body.difficulty = difficulty;
      return question ? updateQuestion(question.id, body) : createQuestion(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'questions'] });
      toast('success', 'Question enregistrée.');
      onDone();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!courseId) {
      toast('error', 'Veuillez choisir un cours.');
      return;
    }
    if (props.some((p) => !p.text.trim())) {
      toast('error', 'Chaque proposition doit avoir un texte.');
      return;
    }
    save.mutate();
  }

  return (
    <div>
      <AdminHeader title={question ? 'Modifier la question' : 'Nouvelle question'}>
        <GhostButton onClick={onDone}>← Retour</GhostButton>
      </AdminHeader>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Form */}
        <form onSubmit={submit} className="space-y-4">
          <Card className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Module" htmlFor="q-module">
                <Select
                  id="q-module"
                  value={moduleId}
                  onChange={(e) => {
                    setModuleId(e.target.value);
                    setCourseId('');
                  }}
                >
                  <option value="">— Module —</option>
                  {modules.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} (A{m.studyYear})
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Cours" htmlFor="q-course">
                <Select
                  id="q-course"
                  value={courseId}
                  onChange={(e) => setCourseId(e.target.value)}
                  disabled={!moduleId}
                >
                  <option value="">— Cours —</option>
                  {moduleCourses.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>

            <label className="block text-sm font-semibold text-brand-gray">
              Énoncé
              <textarea
                required
                value={statement}
                onChange={(e) => setStatement(e.target.value)}
                rows={3}
                className="mt-1 w-full rounded-lg border border-gray-300 p-2 text-sm text-brand-navy focus:border-brand-green focus:outline-none"
              />
            </label>

            <ImageAttacher
              label="Images de l'énoncé"
              images={statementImages}
              onChange={setStatementImages}
            />

            <div className="grid grid-cols-2 gap-3">
              <Field label="Source d'examen" htmlFor="q-source">
                <Select id="q-source" value={sourceExamId} onChange={(e) => setSourceExamId(e.target.value)}>
                  <option value="">— Aucune —</option>
                  {sources.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.label} ({s.year})
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Difficulté" htmlFor="q-diff">
                <Select
                  id="q-diff"
                  value={difficulty}
                  onChange={(e) => setDifficulty(e.target.value as Difficulty | '')}
                >
                  <option value="">— Non définie —</option>
                  {DIFFICULTIES.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          </Card>

          {/* Propositions */}
          <div className="space-y-3">
            {props.map((p, i) => (
              <Card key={i} className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-bold text-brand-navy">Proposition {p.letter}</span>
                  <div className="flex items-center gap-2">
                    <SegmentToggle
                      tone="success"
                      selected={p.isTrue}
                      onClick={() => setProp(i, { isTrue: true })}
                    >
                      VRAI
                    </SegmentToggle>
                    <SegmentToggle
                      tone="danger"
                      selected={!p.isTrue}
                      onClick={() => setProp(i, { isTrue: false })}
                    >
                      FAUX
                    </SegmentToggle>
                    {props.length > 2 ? (
                      <button
                        type="button"
                        onClick={() => removeProp(i)}
                        aria-label="Supprimer la proposition"
                        className="text-danger hover:text-danger"
                      >
                        ✕
                      </button>
                    ) : null}
                  </div>
                </div>
                <TextInput
                  placeholder="Texte de la proposition"
                  value={p.text}
                  onChange={(e) => setProp(i, { text: e.target.value })}
                />
                <div>
                  <p className="mb-1 text-xs font-semibold text-brand-gray">Explication</p>
                  <RichTextEditor
                    value={p.explanationHtml}
                    onChange={(html) => setProp(i, { explanationHtml: html })}
                  />
                </div>
                <ImageAttacher
                  label="Images de l'explication"
                  images={p.explanationImages}
                  onChange={(imgs) => setProp(i, { explanationImages: imgs })}
                />
              </Card>
            ))}
            {props.length < 5 ? (
              <GhostButton onClick={addProp}>+ Proposition</GhostButton>
            ) : null}
          </div>

          <Card className="flex items-center justify-between">
            <label className="flex items-center gap-2 text-sm font-semibold text-brand-gray">
              <input
                type="checkbox"
                checked={published}
                onChange={(e) => setPublished(e.target.checked)}
                className="h-4 w-4 accent-brand-green"
              />
              Publier la question
            </label>
            <div className="flex gap-2">
              <GhostButton onClick={onDone}>Annuler</GhostButton>
              <PrimaryButton type="submit" disabled={save.isPending}>
                Enregistrer
              </PrimaryButton>
            </div>
          </Card>
        </form>

        {/* Live student-view preview */}
        <div className="lg:sticky lg:top-20 lg:self-start">
          <p className="mb-2 text-xs font-bold uppercase tracking-wide text-brand-gray">
            Aperçu (vue étudiant)
          </p>
          <Card>
            {difficulty ? (
              <StatusBadge tone="navy">{DIFFICULTIES.find((d) => d.value === difficulty)?.label}</StatusBadge>
            ) : null}
            <p className="mt-2 whitespace-pre-wrap text-base font-semibold text-brand-navy">
              {statement || 'Énoncé…'}
            </p>
            <div className="mt-3 space-y-2">
              {statementImages.map((src) => (
                <img key={src} src={src} alt="" className="max-h-48 rounded-lg ring-1 ring-subtle" />
              ))}
            </div>
            <ul className="mt-4 space-y-2">
              {props.map((p) => (
                <li
                  key={p.letter}
                  className="flex items-start gap-3 rounded-lg border border-gray-200 p-3"
                >
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gray-100 text-xs font-bold text-brand-navy">
                    {p.letter}
                  </span>
                  <span className="text-sm text-brand-navy">{p.text || '…'}</span>
                </li>
              ))}
            </ul>
          </Card>

          {/* Correction preview (admin-only, shows truth + explanation) */}
          <p className="mb-2 mt-6 text-xs font-bold uppercase tracking-wide text-brand-gray">
            Aperçu correction (interne)
          </p>
          <Card className="space-y-2">
            {props.map((p) => (
              <div key={p.letter} className="rounded-lg bg-canvas p-3">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-brand-navy">{p.letter}.</span>
                  <StatusBadge tone={p.isTrue ? 'green' : 'red'}>
                    {p.isTrue ? 'VRAI' : 'FAUX'}
                  </StatusBadge>
                  <span className="text-sm text-brand-gray">{p.text || '…'}</span>
                </div>
                {p.explanationHtml ? (
                  <SafeHtml html={p.explanationHtml} className="mt-2 text-sm text-brand-gray" />
                ) : null}
              </div>
            ))}
          </Card>
        </div>
      </div>
    </div>
  );
}

function ImageAttacher({
  label,
  images,
  onChange,
}: {
  label: string;
  images: string[];
  onChange: (imgs: string[]) => void;
}) {
  const { toast } = useToast();
  const [busy, setBusy] = useState(false);
  const upload = useMutation({
    mutationFn: uploadContentImage,
    onMutate: () => setBusy(true),
    onSettled: () => setBusy(false),
    onSuccess: (r) => onChange([...images, r.url]),
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Échec du téléversement'),
  });
  return (
    <div>
      <p className="mb-1 text-xs font-semibold text-brand-gray">{label}</p>
      <div className="flex flex-wrap items-center gap-2">
        {images.map((src) => (
          <div key={src} className="relative">
            <img src={src} alt="" className="h-16 w-16 rounded-lg object-cover ring-1 ring-subtle" />
            <Button variant="danger" size="sm" className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center"
              type="button"
              onClick={() => onChange(images.filter((i) => i !== src))}
              aria-label="Retirer l'image"
            >
              ✕
            </Button>
          </div>
        ))}
        <label className="flex h-16 w-16 cursor-pointer items-center justify-center rounded-lg border-2 border-dashed border-gray-300 text-2xl text-brand-gray hover:border-brand-green">
          {busy ? '…' : '+'}
          <input
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) upload.mutate(file);
              e.target.value = '';
            }}
          />
        </label>
      </div>
    </div>
  );
}

function BulkImportModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [text, setText] = useState('');
  const [rowErrors, setRowErrors] = useState<{ row: number; field: string; message: string }[]>([]);
  const [parseError, setParseError] = useState<string | null>(null);

  const run = useMutation({
    mutationFn: (rows: unknown[]) => importQuestions(rows),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['admin', 'questions'] });
      toast('success', `${r.imported} question(s) importée(s).`);
      onClose();
    },
    onError: (e) => {
      const body = (e as { response?: { data?: unknown } })?.response?.data;
      const parsed = z.array(importRowErrorSchema).safeParse(body);
      if (parsed.success) {
        setRowErrors(parsed.data);
      } else {
        toast('error', apiErrorMessage(e) ?? "Échec de l'import");
      }
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setParseError(null);
    setRowErrors([]);
    let rows: unknown;
    try {
      rows = JSON.parse(text);
    } catch {
      setParseError('JSON invalide.');
      return;
    }
    if (!Array.isArray(rows)) {
      setParseError('Le JSON doit être un tableau de questions.');
      return;
    }
    run.mutate(rows);
  }

  return (
    <Modal open onClose={onClose} title="Import JSON de questions">
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-brand-gray">
          Collez un tableau JSON d'objets question. Toute erreur annule l'import entier et liste les
          lignes fautives.
        </p>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={10}
          placeholder='[{"courseId":"…","statement":"…","propositions":[…]}]'
          className="w-full rounded-lg border border-gray-300 p-2 font-mono text-xs text-brand-navy focus:border-brand-green focus:outline-none"
        />
        {parseError ? <p className="text-sm font-semibold text-danger">{parseError}</p> : null}
        {rowErrors.length > 0 ? (
          <div className="max-h-40 overflow-y-auto rounded-lg border border-danger bg-danger/10 p-3 text-sm">
            <p className="mb-1 font-bold text-danger">Erreurs par ligne :</p>
            <ul className="space-y-1 text-danger">
              {rowErrors.map((err, idx) => (
                <li key={idx}>
                  Ligne {err.row + 1} — <span className="font-mono">{err.field}</span> : {err.message}
                </li>
              ))}
            </ul>
          </div>
        ) : null}
        <div className="flex justify-end gap-2">
          <GhostButton onClick={onClose}>Annuler</GhostButton>
          <PrimaryButton type="submit" disabled={run.isPending}>
            Importer
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  );
}
