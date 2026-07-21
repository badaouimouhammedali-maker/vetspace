import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type FormEvent } from 'react';
import { Field, Select, TextInput } from '../../components/forms';
import { Button, Modal, TableSkeleton } from '../../components/ui';
import { useToast } from '../../components/ToastProvider';
import { apiErrorMessage } from '../../lib/api';
import {
  createCourse,
  createModule,
  createSchool,
  deleteCourse,
  deleteModule,
  deleteSchool,
  fetchAdminCourses,
  fetchAdminModules,
  fetchAdminSchools,
  publishCourse,
  publishModule,
  reorderCourses,
  reorderModules,
  updateCourse,
  updateModule,
  updateSchool,
} from '../../lib/adminEndpoints';
import type { AdminCourse, AdminModule, AdminSchool } from '../../lib/schemas';
import { AdminHeader, Card, GhostButton, PrimaryButton, StatusBadge } from './adminUi';

const YEARS = [1, 2, 3, 4, 5, 6];

export function EcolesPage() {
  const qc = useQueryClient();
  const { toast } = useToast();
  const schools = useQuery({ queryKey: ['admin', 'schools'], queryFn: fetchAdminSchools });
  const modules = useQuery({ queryKey: ['admin', 'modules'], queryFn: fetchAdminModules });
  const courses = useQuery({ queryKey: ['admin', 'courses'], queryFn: fetchAdminCourses });

  const [schoolId, setSchoolId] = useState('');
  const [year, setYear] = useState(1);
  const [schoolModal, setSchoolModal] = useState<AdminSchool | 'new' | null>(null);
  const [moduleModal, setModuleModal] = useState<AdminModule | 'new' | null>(null);
  const [courseModal, setCourseModal] = useState<{ course: AdminCourse | 'new'; moduleId: string } | null>(
    null,
  );
  const [expanded, setExpanded] = useState<string | null>(null);

  const invalidate = (key: string) => qc.invalidateQueries({ queryKey: ['admin', key] });
  const onError = (e: unknown) => toast('error', apiErrorMessage(e) ?? 'Erreur');

  const visibleModules = useMemo(
    () =>
      (modules.data ?? [])
        .filter((m) => m.schoolId === schoolId && m.studyYear === year)
        .sort((a, b) => a.position - b.position),
    [modules.data, schoolId, year],
  );

  const coursesByModule = useMemo(() => {
    const map = new Map<string, AdminCourse[]>();
    (courses.data ?? []).forEach((c) => {
      const list = map.get(c.moduleId) ?? [];
      list.push(c);
      map.set(c.moduleId, list);
    });
    map.forEach((list) => list.sort((a, b) => a.position - b.position));
    return map;
  }, [courses.data]);

  // Schools -----------------------------------------------------------
  const delSchool = useMutation({
    mutationFn: deleteSchool,
    onSuccess: () => {
      invalidate('schools');
      toast('success', 'École supprimée.');
    },
    onError,
  });

  // Modules -----------------------------------------------------------
  const modPublish = useMutation({
    mutationFn: ({ id, published }: { id: string; published: boolean }) => publishModule(id, published),
    onSuccess: () => invalidate('modules'),
    onError,
  });
  const modReorder = useMutation({
    mutationFn: reorderModules,
    onSuccess: () => invalidate('modules'),
    onError,
  });
  const delModule = useMutation({
    mutationFn: deleteModule,
    onSuccess: () => {
      invalidate('modules');
      toast('success', 'Module supprimé.');
    },
    onError,
  });

  // Courses -----------------------------------------------------------
  const crsPublish = useMutation({
    mutationFn: ({ id, published }: { id: string; published: boolean }) => publishCourse(id, published),
    onSuccess: () => invalidate('courses'),
    onError,
  });
  const crsFreePreview = useMutation({
    mutationFn: (c: AdminCourse) =>
      updateCourse(c.id, {
        moduleId: c.moduleId,
        name: c.name,
        published: c.published,
        freePreview: !c.freePreview,
      }),
    onSuccess: () => invalidate('courses'),
    onError,
  });
  const crsReorder = useMutation({
    mutationFn: reorderCourses,
    onSuccess: () => invalidate('courses'),
    onError,
  });
  const delCourse = useMutation({
    mutationFn: deleteCourse,
    onSuccess: () => {
      invalidate('courses');
      toast('success', 'Cours supprimé.');
    },
    onError,
  });

  function move<T extends { id: string }>(list: T[], index: number, dir: -1 | 1): string[] {
    const next = [...list];
    const target = index + dir;
    if (target < 0 || target >= next.length) {
      return list.map((x) => x.id);
    }
    [next[index], next[target]] = [next[target]!, next[index]!];
    return next.map((x) => x.id);
  }

  return (
    <div>
      <AdminHeader title="Écoles & Modules" subtitle="Gérez le catalogue par école et année d'étude.">
        <PrimaryButton onClick={() => setSchoolModal('new')}>+ École</PrimaryButton>
      </AdminHeader>

      {/* Schools list */}
      <Card className="mb-6">
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-brand-gray">Écoles</h2>
        {schools.isLoading ? (
          <TableSkeleton rows={5} />
        ) : (schools.data ?? []).length === 0 ? (
          <p className="text-sm text-brand-gray">Aucune école. Créez-en une pour commencer.</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {schools.data!.map((s) => (
              <li key={s.id} className="flex items-center justify-between py-2">
                <span className="text-sm font-semibold text-brand-navy">
                  {s.name} <span className="font-normal text-brand-gray">/{s.slug}</span>
                </span>
                <div className="flex gap-2">
                  <GhostButton onClick={() => setSchoolModal(s)}>Renommer</GhostButton>
                  <GhostButton
                    tone="red"
                    onClick={() => {
                      if (confirm(`Supprimer l'école « ${s.name} » ?`)) {
                        delSchool.mutate(s.id);
                      }
                    }}
                  >
                    Supprimer
                  </GhostButton>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* Context selector */}
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <Field label="École" htmlFor="school-ctx">
          <Select id="school-ctx" value={schoolId} onChange={(e) => setSchoolId(e.target.value)}>
            <option value="">— Choisir une école —</option>
            {(schools.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Année d'étude" htmlFor="year-ctx">
          <Select id="year-ctx" value={year} onChange={(e) => setYear(Number(e.target.value))}>
            {YEARS.map((y) => (
              <option key={y} value={y}>
                Année {y}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {!schoolId ? (
        <Card>
          <p className="text-sm text-brand-gray">
            Sélectionnez une école et une année pour gérer ses modules et cours.
          </p>
        </Card>
      ) : (
        <Card>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-bold uppercase tracking-wide text-brand-gray">Modules</h2>
            <PrimaryButton onClick={() => setModuleModal('new')}>+ Module</PrimaryButton>
          </div>

          {modules.isLoading || courses.isLoading ? (
            <TableSkeleton rows={5} />
          ) : visibleModules.length === 0 ? (
            <p className="text-sm text-brand-gray">Aucun module pour cette année.</p>
          ) : (
            <ul className="space-y-2">
              {visibleModules.map((m, mi) => {
                const mCourses = coursesByModule.get(m.id) ?? [];
                const isOpen = expanded === m.id;
                return (
                  <li key={m.id} className="rounded-xl border border-gray-100">
                    <div className="flex items-center gap-2 p-3">
                      <div className="flex flex-col">
                        <Button variant="ghost" size="sm"
                          aria-label="Monter"
                          disabled={mi === 0}
                          onClick={() => modReorder.mutate(move(visibleModules, mi, -1))}
                          
                        >
                          ▲
                        </Button>
                        <Button variant="ghost" size="sm"
                          aria-label="Descendre"
                          disabled={mi === visibleModules.length - 1}
                          onClick={() => modReorder.mutate(move(visibleModules, mi, 1))}
                          
                        >
                          ▼
                        </Button>
                      </div>
                      <button
                        onClick={() => setExpanded(isOpen ? null : m.id)}
                        className="flex-1 text-left text-sm font-bold text-brand-navy"
                      >
                        {isOpen ? '▾' : '▸'} {m.name}{' '}
                        <span className="ml-1 font-normal text-brand-gray">
                          ({mCourses.length} cours)
                        </span>
                      </button>
                      {m.published ? (
                        <StatusBadge tone="green">Publié</StatusBadge>
                      ) : (
                        <StatusBadge tone="gray">Brouillon</StatusBadge>
                      )}
                      <GhostButton onClick={() => modPublish.mutate({ id: m.id, published: !m.published })}>
                        {m.published ? 'Dépublier' : 'Publier'}
                      </GhostButton>
                      <GhostButton onClick={() => setModuleModal(m)}>Renommer</GhostButton>
                      <GhostButton
                        tone="red"
                        onClick={() => {
                          if (confirm(`Supprimer le module « ${m.name} » ?`)) {
                            delModule.mutate(m.id);
                          }
                        }}
                      >
                        Suppr.
                      </GhostButton>
                    </div>

                    {isOpen ? (
                      <div className="border-t border-gray-100 bg-canvas/50 p-3">
                        <div className="mb-2 flex justify-end">
                          <PrimaryButton
                            onClick={() => setCourseModal({ course: 'new', moduleId: m.id })}
                          >
                            + Cours
                          </PrimaryButton>
                        </div>
                        {mCourses.length === 0 ? (
                          <p className="text-sm text-brand-gray">Aucun cours.</p>
                        ) : (
                          <ul className="space-y-1">
                            {mCourses.map((c, ci) => (
                              <li
                                key={c.id}
                                className="flex flex-wrap items-center gap-2 rounded-lg bg-surface px-3 py-2 ring-1 ring-subtle"
                              >
                                <div className="flex flex-col">
                                  <Button variant="ghost" size="sm"
                                    aria-label="Monter"
                                    disabled={ci === 0}
                                    onClick={() => crsReorder.mutate(move(mCourses, ci, -1))}
                                    
                                  >
                                    ▲
                                  </Button>
                                  <Button variant="ghost" size="sm"
                                    aria-label="Descendre"
                                    disabled={ci === mCourses.length - 1}
                                    onClick={() => crsReorder.mutate(move(mCourses, ci, 1))}
                                    
                                  >
                                    ▼
                                  </Button>
                                </div>
                                <span className="flex-1 text-sm font-semibold text-brand-navy">
                                  {c.name}
                                </span>
                                {c.published ? (
                                  <StatusBadge tone="green">Publié</StatusBadge>
                                ) : (
                                  <StatusBadge tone="gray">Brouillon</StatusBadge>
                                )}
                                {c.freePreview ? (
                                  <StatusBadge tone="amber">Aperçu gratuit</StatusBadge>
                                ) : null}
                                <label className="flex items-center gap-1 text-xs font-semibold text-brand-gray">
                                  <input
                                    type="checkbox"
                                    checked={c.freePreview}
                                    onChange={() => crsFreePreview.mutate(c)}
                                    className="h-4 w-4 accent-brand-green"
                                  />
                                  Aperçu
                                </label>
                                <GhostButton
                                  onClick={() =>
                                    crsPublish.mutate({ id: c.id, published: !c.published })
                                  }
                                >
                                  {c.published ? 'Dépublier' : 'Publier'}
                                </GhostButton>
                                <GhostButton
                                  onClick={() => setCourseModal({ course: c, moduleId: m.id })}
                                >
                                  Renommer
                                </GhostButton>
                                <GhostButton
                                  tone="red"
                                  onClick={() => {
                                    if (confirm(`Supprimer le cours « ${c.name} » ?`)) {
                                      delCourse.mutate(c.id);
                                    }
                                  }}
                                >
                                  Suppr.
                                </GhostButton>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      )}

      {schoolModal ? (
        <SchoolModal
          school={schoolModal === 'new' ? null : schoolModal}
          onClose={() => setSchoolModal(null)}
          onSaved={() => {
            setSchoolModal(null);
            invalidate('schools');
          }}
        />
      ) : null}

      {moduleModal ? (
        <ModuleModal
          module={moduleModal === 'new' ? null : moduleModal}
          schoolId={schoolId}
          year={year}
          onClose={() => setModuleModal(null)}
          onSaved={() => {
            setModuleModal(null);
            invalidate('modules');
          }}
        />
      ) : null}

      {courseModal ? (
        <CourseModal
          course={courseModal.course === 'new' ? null : courseModal.course}
          moduleId={courseModal.moduleId}
          onClose={() => setCourseModal(null)}
          onSaved={() => {
            setCourseModal(null);
            invalidate('courses');
          }}
        />
      ) : null}
    </div>
  );
}

function SchoolModal({
  school,
  onClose,
  onSaved,
}: {
  school: AdminSchool | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const [name, setName] = useState(school?.name ?? '');
  const [slug, setSlug] = useState(school?.slug ?? '');
  const save = useMutation({
    mutationFn: () => (school ? updateSchool(school.id, { name, slug }) : createSchool({ name, slug })),
    onSuccess: () => {
      toast('success', 'École enregistrée.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });
  return (
    <Modal open onClose={onClose} title={school ? "Modifier l'école" : 'Nouvelle école'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Nom" htmlFor="s-name">
          <TextInput id="s-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Slug" htmlFor="s-slug">
          <TextInput id="s-slug" required value={slug} onChange={(e) => setSlug(e.target.value)} />
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

function ModuleModal({
  module,
  schoolId,
  year,
  onClose,
  onSaved,
}: {
  module: AdminModule | null;
  schoolId: string;
  year: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const [name, setName] = useState(module?.name ?? '');
  const [published, setPublished] = useState(module?.published ?? false);
  const save = useMutation({
    mutationFn: () =>
      module
        ? updateModule(module.id, { schoolId: module.schoolId, studyYear: module.studyYear, name, published })
        : createModule({ schoolId, studyYear: year, name, published }),
    onSuccess: () => {
      toast('success', 'Module enregistré.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });
  return (
    <Modal open onClose={onClose} title={module ? 'Modifier le module' : 'Nouveau module'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Nom" htmlFor="m-name">
          <TextInput id="m-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm font-semibold text-brand-gray">
          <input
            type="checkbox"
            checked={published}
            onChange={(e) => setPublished(e.target.checked)}
            className="h-4 w-4 accent-brand-green"
          />
          Publié
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

function CourseModal({
  course,
  moduleId,
  onClose,
  onSaved,
}: {
  course: AdminCourse | null;
  moduleId: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { toast } = useToast();
  const [name, setName] = useState(course?.name ?? '');
  const [published, setPublished] = useState(course?.published ?? false);
  const [freePreview, setFreePreview] = useState(course?.freePreview ?? false);
  const save = useMutation({
    mutationFn: () =>
      course
        ? updateCourse(course.id, { moduleId: course.moduleId, name, published, freePreview })
        : createCourse({ moduleId, name, published, freePreview }),
    onSuccess: () => {
      toast('success', 'Cours enregistré.');
      onSaved();
    },
    onError: (e) => toast('error', apiErrorMessage(e) ?? 'Erreur'),
  });
  return (
    <Modal open onClose={onClose} title={course ? 'Modifier le cours' : 'Nouveau cours'}>
      <form
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          save.mutate();
        }}
        className="space-y-4"
      >
        <Field label="Nom" htmlFor="c-name">
          <TextInput id="c-name" required value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm font-semibold text-brand-gray">
          <input
            type="checkbox"
            checked={published}
            onChange={(e) => setPublished(e.target.checked)}
            className="h-4 w-4 accent-brand-green"
          />
          Publié
        </label>
        <label className="flex items-center gap-2 text-sm font-semibold text-brand-gray">
          <input
            type="checkbox"
            checked={freePreview}
            onChange={(e) => setFreePreview(e.target.checked)}
            className="h-4 w-4 accent-brand-green"
          />
          Aperçu gratuit (démo)
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
