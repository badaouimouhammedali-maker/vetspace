import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type FormEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { EmptyState, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from '../../lib/api';
import {
  createMindmap,
  deleteMindmap,
  fetchAdminCourses,
  fetchAdminMindmaps,
  fetchAdminModules,
  publishMindmap,
  updateMindmap,
  uploadContentImage,
} from '../../lib/adminEndpoints';
import type { AdminCourse, AdminMindmap, AdminModule } from '../../lib/schemas';
import { AdminHeader, Card, GhostButton, PrimaryButton, StatusBadge } from './adminUi';

export function AdminMindmapsPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const mindmaps = useQuery({ queryKey: ['admin', 'mindmaps'], queryFn: fetchAdminMindmaps });
  const modules = useQuery({ queryKey: ['admin', 'modules'], queryFn: fetchAdminModules });
  const courses = useQuery({ queryKey: ['admin', 'courses'], queryFn: fetchAdminCourses });
  const [modal, setModal] = useState<AdminMindmap | 'new' | null>(null);

  const courseName = (id: string) => courses.data?.find((c) => c.id === id)?.name ?? '—';

  const del = useMutation({
    mutationFn: deleteMindmap,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'mindmaps'] });
      toast('success', 'MindMap supprimée.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });
  const pub = useMutation({
    mutationFn: ({ id, published }: { id: string; published: boolean }) =>
      publishMindmap(id, published),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'mindmaps'] }),
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <div>
      <AdminHeader title="MindMaps" subtitle="Cartes mentales par cours.">
        <PrimaryButton onClick={() => setModal('new')}>+ MindMap</PrimaryButton>
      </AdminHeader>

      {mindmaps.isLoading ? (
        <TableSkeleton rows={5} />
      ) : (mindmaps.data ?? []).length === 0 ? (
        <Card>
          <EmptyState
            icon="🧠"
            title="Aucune MindMap"
            caption="Ajoutez-en une pour la rendre disponible aux étudiants."
            compact
          />
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {mindmaps.data!.map((m) => (
            <Card key={m.id} className="flex flex-col gap-3">
              <img
                src={m.imageUrl}
                alt={m.title}
                className="h-40 w-full rounded-lg object-cover ring-1 ring-subtle"
              />
              <div className="flex items-start justify-between gap-2">
                <span className="text-sm font-bold text-ink">{m.title}</span>
                {m.published ? (
                  <StatusBadge tone="green">Publié</StatusBadge>
                ) : (
                  <StatusBadge tone="gray">Brouillon</StatusBadge>
                )}
              </div>
              <span className="text-xs text-ink-muted">{courseName(m.courseId)}</span>
              <div className="flex flex-wrap gap-2">
                <GhostButton onClick={() => pub.mutate({ id: m.id, published: !m.published })}>
                  {m.published ? 'Dépublier' : 'Publier'}
                </GhostButton>
                <GhostButton onClick={() => setModal(m)}>Modifier</GhostButton>
                <GhostButton
                  tone="red"
                  onClick={() => {
                    if (confirm(`Supprimer « ${m.title} » ?`)) {
                      del.mutate(m.id);
                    }
                  }}
                >
                  Suppr.
                </GhostButton>
              </div>
            </Card>
          ))}
        </div>
      )}

      {modal ? (
        <MindmapModal
          mindmap={modal === 'new' ? null : modal}
          modules={modules.data ?? []}
          courses={courses.data ?? []}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            qc.invalidateQueries({ queryKey: ['admin', 'mindmaps'] });
          }}
        />
      ) : null}
    </div>
  );
}

function MindmapModal({
  mindmap,
  modules,
  courses,
  onClose,
  onSaved,
}: {
  mindmap: AdminMindmap | null;
  modules: AdminModule[];
  courses: AdminCourse[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const initialModuleId = mindmap
    ? (courses.find((c) => c.id === mindmap.courseId)?.moduleId ?? '')
    : '';
  const [moduleId, setModuleId] = useState(initialModuleId);
  const [courseId, setCourseId] = useState(mindmap?.courseId ?? '');
  const [title, setTitle] = useState(mindmap?.title ?? '');
  const [imageUrl, setImageUrl] = useState(mindmap?.imageUrl ?? '');
  const [published, setPublished] = useState(mindmap?.published ?? false);
  const [uploading, setUploading] = useState(false);

  const moduleCourses = useMemo(
    () => courses.filter((c) => c.moduleId === moduleId).sort((a, b) => a.position - b.position),
    [courses, moduleId],
  );

  const upload = useMutation({
    mutationFn: uploadContentImage,
    onMutate: () => setUploading(true),
    onSettled: () => setUploading(false),
    onSuccess: (r) => {
      setImageUrl(r.url);
      toast('success', 'Image téléversée.');
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Échec du téléversement', apiErrorReference(e)),
  });

  const save = useMutation({
    mutationFn: () => {
      const body = { courseId, title, imageUrl, published };
      return mindmap ? updateMindmap(mindmap.id, body) : createMindmap(body);
    },
    onSuccess: () => {
      toast('success', 'MindMap enregistrée.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur', apiErrorReference(e)),
  });

  return (
    <Modal open onClose={onClose} title={mindmap ? 'Modifier la MindMap' : 'Nouvelle MindMap'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          if (!imageUrl) {
            toast('error', 'Veuillez téléverser une image.');
            return;
          }
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Module" htmlFor="mm-module">
          <Select
            id="mm-module"
            value={moduleId}
            onChange={(e) => {
              setModuleId(e.target.value);
              setCourseId('');
            }}
          >
            <option value="">— Choisir un module —</option>
            {modules.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} (A{m.studyYear})
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Cours" htmlFor="mm-course">
          <Select
            id="mm-course"
            value={courseId}
            onChange={(e) => setCourseId(e.target.value)}
            disabled={!moduleId}
          >
            <option value="">— Choisir un cours —</option>
            {moduleCourses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Titre" htmlFor="mm-title">
          <TextInput
            id="mm-title"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </Field>
        <Field label="Image" htmlFor="mm-file">
          <input
            id="mm-file"
            type="file"
            accept="image/*"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                upload.mutate(file);
              }
            }}
            className="block w-full text-sm text-ink-muted file:mr-3 file:rounded-lg file:border-0 file:bg-accent file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-white"
          />
        </Field>
        {uploading ? <p className="text-xs text-ink-muted">Téléversement…</p> : null}
        {imageUrl ? (
          <img src={imageUrl} alt="" className="h-32 rounded-lg object-cover ring-1 ring-subtle" />
        ) : null}
        <label className="flex items-center gap-2 text-sm font-semibold text-ink-muted">
          <input
            type="checkbox"
            checked={published}
            onChange={(e) => setPublished(e.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          Publié
        </label>
        <div className="flex justify-end gap-2">
          <GhostButton onClick={onClose}>Annuler</GhostButton>
          <PrimaryButton type="submit" disabled={save.isPending || !courseId}>
            Enregistrer
          </PrimaryButton>
        </div>
      </form>
    </Modal>
  );
}
