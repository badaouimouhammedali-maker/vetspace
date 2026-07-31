import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type DragEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { useToast } from '../../components/ToastProvider';
import { EmptyState, Skeleton } from '../../components/ui';
import {
  deleteResource,
  fetchAdminModules,
  fetchAdminResources,
  fetchResourceSummary,
  publishResource,
  reorderResources,
  uploadResource,
} from '../../lib/adminEndpoints';
import { apiErrorMessage } from '../../lib/api';
import { formatFileSize } from '../../lib/formatFileSize';
import { AdminHeader, Card, GhostButton, PrimaryButton, StatusBadge } from './adminUi';

/** Mirrors the server limit; keeping it here only saves a doomed round trip. */
const MAX_MB = 25;

export function RessourcesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const [moduleId, setModuleId] = useState('');

  const modules = useQuery({ queryKey: ['admin', 'modules'], queryFn: fetchAdminModules });
  const summary = useQuery({ queryKey: ['admin', 'resource-summary'], queryFn: fetchResourceSummary });
  const resources = useQuery({
    queryKey: ['admin', 'resources', moduleId],
    queryFn: () => fetchAdminResources(moduleId),
    enabled: moduleId !== '',
  });

  const refresh = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['admin', 'resources'] }),
      qc.invalidateQueries({ queryKey: ['admin', 'resource-summary'] }),
    ]);

  const publish = useMutation({
    mutationFn: ({ id, published }: { id: string; published: boolean }) => publishResource(id, published),
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: deleteResource, onSuccess: refresh });
  const reorder = useMutation({ mutationFn: reorderResources, onSuccess: refresh });

  // Upload form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [published, setPublished] = useState(true);
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const moduleSummary = summary.data?.find((s) => s.moduleId === moduleId);

  function pick(selected: File | null) {
    if (!selected) return;
    if (selected.size > MAX_MB * 1024 * 1024) {
      toast('error', `Fichier trop volumineux (max ${MAX_MB} Mo).`);
      return;
    }
    setFile(selected);
    // A title is required; the filename is the obvious first guess, minus its extension.
    if (!title) setTitle(selected.name.replace(/\.[^.]+$/, ''));
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    pick(e.dataTransfer.files[0] ?? null);
  }

  async function submit() {
    if (!moduleId || !file || !title.trim()) {
      toast('error', 'Module, fichier et titre sont requis.');
      return;
    }
    setProgress(0);
    try {
      const trimmed = description.trim();
      await uploadResource(
        file,
        // The key is omitted rather than set to undefined: exactOptionalPropertyTypes
        // draws a distinction between "absent" and "present but undefined".
        { moduleId, title: title.trim(), published, ...(trimmed ? { description: trimmed } : {}) },
        setProgress,
      );
      toast('success', 'Ressource ajoutée.');
      setFile(null);
      setTitle('');
      setDescription('');
      if (inputRef.current) inputRef.current.value = '';
      await refresh();
    } catch (err) {
      toast('error', apiErrorMessage(err) ?? "L'envoi a échoué.");
    } finally {
      setProgress(null);
    }
  }

  function move(index: number, delta: number) {
    const list = resources.data ?? [];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    const ids = list.map((r) => r.id);
    const [moved] = ids.splice(index, 1);
    ids.splice(target, 0, moved as string);
    reorder.mutate(ids);
  }

  return (
    <div>
      <AdminHeader
        title="Ressources"
        subtitle="Supports de cours en accès libre : PDF et images, visibles par tout étudiant connecté sans abonnement."
      />

      {/* Storage totals first: R2 usage should be visible where the uploading happens,
          not discovered on a bill. */}
      <Card className="mb-4 p-0">
        <div className="border-b border-subtle px-4 py-3 text-sm font-bold text-brand-navy">
          Stockage par module
        </div>
        {summary.isLoading ? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-5" />
            <Skeleton className="h-5" />
          </div>
        ) : (summary.data ?? []).filter((s) => s.resourceCount > 0).length === 0 ? (
          <p className="px-4 py-5 text-sm text-brand-gray">Aucune ressource pour le moment.</p>
        ) : (
          <ul className="divide-y divide-subtle">
            {(summary.data ?? [])
              .filter((s) => s.resourceCount > 0)
              .map((s) => (
                <li key={s.moduleId} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                  <span className="w-16 shrink-0 text-xs font-semibold text-brand-gray">
                    Année {s.studyYear}
                  </span>
                  <span className="min-w-0 flex-1 truncate font-semibold text-brand-navy">
                    {s.moduleName}
                  </span>
                  <span className="shrink-0 text-brand-gray">{s.resourceCount} fichier(s)</span>
                  <span className="w-20 shrink-0 text-right font-semibold text-brand-navy">
                    {formatFileSize(s.totalBytes)}
                  </span>
                </li>
              ))}
            <li className="flex items-center gap-3 bg-gray-50 px-4 py-2.5 text-sm">
              <span className="min-w-0 flex-1 font-bold text-brand-navy">Total</span>
              <span className="w-20 shrink-0 text-right font-bold text-brand-navy">
                {formatFileSize((summary.data ?? []).reduce((sum, s) => sum + s.totalBytes, 0))}
              </span>
            </li>
          </ul>
        )}
      </Card>

      <div className="mb-4 max-w-sm">
        <Field label="Module" htmlFor="res-module">
          <Select id="res-module" value={moduleId} onChange={(e) => setModuleId(e.target.value)}>
            <option value="">— Choisir un module —</option>
            {(modules.data ?? [])
              .slice()
              .sort((a, b) => a.studyYear - b.studyYear || a.name.localeCompare(b.name, 'fr'))
              .map((m) => (
                <option key={m.id} value={m.id}>
                  Année {m.studyYear} — {m.name}
                </option>
              ))}
          </Select>
        </Field>
      </div>

      {!moduleId ? (
        <Card>
          <p className="text-sm text-brand-gray">
            Sélectionnez un module pour voir et ajouter ses ressources.
          </p>
        </Card>
      ) : (
        <>
          <Card className="mb-4">
            <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-brand-gray">
              Ajouter une ressource
            </h2>

            <div
              onDragOver={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              className={`mb-3 rounded-lg border-2 border-dashed p-6 text-center transition ${
                dragging ? 'border-brand-green bg-brand-green/5' : 'border-gray-200'
              }`}
            >
              <p className="text-sm text-brand-gray">
                Glissez un fichier ici, ou{' '}
                <button
                  type="button"
                  onClick={() => inputRef.current?.click()}
                  className="font-semibold text-brand-green hover:underline"
                >
                  parcourez
                </button>
              </p>
              <p className="mt-1 text-xs text-brand-gray">
                PDF, JPEG, PNG ou WebP — {MAX_MB} Mo maximum
              </p>
              <input
                ref={inputRef}
                type="file"
                accept="application/pdf,image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={(e) => pick(e.target.files?.[0] ?? null)}
              />
              {file ? (
                <p className="mt-3 text-sm font-semibold text-brand-navy">
                  {file.name} · {formatFileSize(file.size)}
                </p>
              ) : null}
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Titre" htmlFor="res-title">
                <TextInput id="res-title" value={title} onChange={(e) => setTitle(e.target.value)} />
              </Field>
              <Field label="Description (facultative)" htmlFor="res-desc">
                <TextInput
                  id="res-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </Field>
            </div>

            <label className="mt-3 flex items-center gap-2 text-sm text-brand-navy">
              <input
                type="checkbox"
                checked={published}
                onChange={(e) => setPublished(e.target.checked)}
              />
              Publier immédiatement
            </label>

            {progress !== null ? (
              <div className="mt-3">
                <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                  <div
                    className="h-full rounded-full bg-brand-green transition-all"
                    style={{ width: `${progress}%` }}
                  />
                </div>
                <p className="mt-1 text-xs text-brand-gray">Envoi… {progress}%</p>
              </div>
            ) : null}

            <div className="mt-4">
              <PrimaryButton onClick={submit} disabled={progress !== null}>
                {progress !== null ? 'Envoi…' : 'Ajouter'}
              </PrimaryButton>
            </div>
          </Card>

          <Card className="p-0">
            <div className="flex items-center justify-between border-b border-subtle px-4 py-3">
              <h2 className="text-sm font-bold uppercase tracking-wide text-brand-gray">Fichiers</h2>
              {moduleSummary ? (
                <span className="text-xs text-brand-gray">
                  {moduleSummary.resourceCount} fichier(s) · {formatFileSize(moduleSummary.totalBytes)}
                </span>
              ) : null}
            </div>

            {resources.isLoading ? (
              <div className="space-y-2 p-4">
                <Skeleton className="h-12" />
                <Skeleton className="h-12" />
              </div>
            ) : (resources.data ?? []).length === 0 ? (
              <div className="p-5">
                <EmptyState icon="📚" title="Aucune ressource dans ce module" compact />
              </div>
            ) : (
              <ul className="divide-y divide-subtle">
                {(resources.data ?? []).map((r, i) => (
                  <li key={r.id} className="flex items-center gap-3 px-4 py-3">
                    <div className="flex shrink-0 flex-col">
                      <button
                        type="button"
                        onClick={() => move(i, -1)}
                        disabled={i === 0}
                        aria-label="Monter"
                        className="px-1 text-xs text-brand-gray disabled:opacity-30"
                      >
                        ▲
                      </button>
                      <button
                        type="button"
                        onClick={() => move(i, 1)}
                        disabled={i === (resources.data ?? []).length - 1}
                        aria-label="Descendre"
                        className="px-1 text-xs text-brand-gray disabled:opacity-30"
                      >
                        ▼
                      </button>
                    </div>
                    <span className="text-lg" aria-hidden>
                      {r.fileType === 'PDF' ? '📄' : '🖼️'}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-semibold text-brand-navy">{r.title}</p>
                      <p className="text-xs text-brand-gray">
                        {r.fileType} · {formatFileSize(r.fileSizeBytes)}
                      </p>
                    </div>
                    <StatusBadge tone={r.published ? 'green' : 'gray'}>
                      {r.published ? 'Publié' : 'Brouillon'}
                    </StatusBadge>
                    <div className="flex shrink-0 gap-2">
                      <a
                        href={r.fileUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="rounded-md px-2 py-1 text-sm font-semibold text-brand-green hover:bg-brand-green/10"
                      >
                        Voir
                      </a>
                      <GhostButton
                        onClick={() => publish.mutate({ id: r.id, published: !r.published })}
                      >
                        {r.published ? 'Dépublier' : 'Publier'}
                      </GhostButton>
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm(`Supprimer « ${r.title} » ? Le fichier sera effacé du stockage.`)) {
                            remove.mutate(r.id);
                          }
                        }}
                      >
                        Suppr.
                      </GhostButton>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
