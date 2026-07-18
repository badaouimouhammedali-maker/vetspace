import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { CoursePicker } from '../../components/CoursePicker';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { RichTextEditor } from '../../components/RichTextEditor';
import { EmptyState, Modal, Skeleton } from '../../components/ui';
import { t } from '../../i18n/fr';
import { SafeHtml } from '../../lib/sanitize';
import { createNote, deleteNote, fetchNotes, updateNote, type NoteInput } from '../../lib/endpoints';
import type { Note } from '../../lib/schemas';

export function NotesPage() {
  const queryClient = useQueryClient();
  const notes = useQuery({ queryKey: ['notes'], queryFn: fetchNotes });

  const [editing, setEditing] = useState<{ mode: 'create' | 'edit'; note?: Note } | null>(null);
  const [title, setTitle] = useState('');
  const [contentHtml, setContentHtml] = useState('');
  const [courseId, setCourseId] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<Note | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notes'] });

  function openCreate() {
    setTitle('');
    setContentHtml('');
    setCourseId('');
    setEditing({ mode: 'create' });
  }
  function openEdit(note: Note) {
    setTitle(note.title);
    setContentHtml(note.contentHtml);
    setCourseId(note.courseId ?? '');
    setEditing({ mode: 'edit', note });
  }

  const save = useMutation({
    mutationFn: () => {
      const input: NoteInput = {
        title,
        contentHtml,
        ...(courseId && courseId !== '__none__' ? { courseId } : {}),
      };
      return editing?.mode === 'edit' ? updateNote(editing.note!.id, input) : createNote(input);
    },
    onSuccess: async () => {
      await invalidate();
      setEditing(null);
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteNote(deleteTarget!.id),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
    },
  });

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
          {t('notes.title')}
        </h1>
        <button
          onClick={openCreate}
          className="rounded-lg bg-brand-green px-4 py-2.5 text-sm font-bold text-white transition hover:bg-brand-green-hover"
        >
          {t('notes.create')}
        </button>
      </div>

      {notes.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">
          <Skeleton className="h-40" />
          <Skeleton className="h-40" />
        </div>
      ) : (notes.data?.length ?? 0) === 0 ? (
        <EmptyState
          message={t('notes.empty')}
          action={
            <button
              onClick={openCreate}
              className="rounded-lg bg-brand-green px-4 py-2 text-sm font-bold text-white hover:bg-brand-green-hover"
            >
              {t('notes.create')}
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {notes.data?.map((note) => (
            <div key={note.id} className="flex flex-col rounded-2xl bg-white p-5 shadow-sm ring-1 ring-gray-100">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-bold text-brand-navy">{note.title}</h2>
                <div className="flex shrink-0 gap-1.5">
                  <button
                    onClick={() => openEdit(note)}
                    aria-label={t('notes.edit')}
                    className="rounded-lg border border-gray-200 p-1.5 text-sm text-brand-gray hover:border-brand-green hover:text-brand-green"
                  >
                    ✎
                  </button>
                  <button
                    onClick={() => setDeleteTarget(note)}
                    aria-label={t('sessions.delete')}
                    className="rounded-lg border border-gray-200 p-1.5 text-sm text-brand-gray hover:border-red-500 hover:text-red-500"
                  >
                    🗑️
                  </button>
                </div>
              </div>
              <SafeHtml
                html={note.contentHtml}
                className="mt-2 flex-1 text-sm text-gray-700 [&_ol]:list-decimal [&_ol]:pl-5 [&_ul]:list-disc [&_ul]:pl-5"
              />
              <p className="mt-3 text-xs text-brand-gray">
                {new Date(note.updatedAt).toLocaleDateString('fr-FR')}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Éditeur */}
      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing?.mode === 'edit' ? t('notes.edit') : t('notes.create')}
        wide
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
          className="space-y-4"
        >
          <Field label={t('notes.noteTitle')} htmlFor="note-title">
            <TextInput
              id="note-title"
              required
              maxLength={255}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </Field>
          <div>
            <span className="mb-1.5 block text-sm font-semibold text-brand-navy">
              {t('notes.content')}
            </span>
            <RichTextEditor value={contentHtml} onChange={setContentHtml} />
          </div>
          <div>
            <span className="mb-1.5 block text-sm font-semibold text-brand-navy">
              {t('builder.courses')}
            </span>
            <CoursePicker courseId={courseId} onCourse={setCourseId} />
          </div>
          <SubmitButton loading={save.isPending}>{t('common.save')}</SubmitButton>
        </form>
      </Modal>

      {/* Confirmation suppression */}
      <Modal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title={t('sessions.delete')}
      >
        <p className="text-sm text-brand-gray">{t('notes.deleteConfirm')}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={() => setDeleteTarget(null)}
            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-brand-gray"
          >
            {t('common.cancel')}
          </button>
          <button
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700"
          >
            {t('common.confirm')}
          </button>
        </div>
      </Modal>
    </div>
  );
}
