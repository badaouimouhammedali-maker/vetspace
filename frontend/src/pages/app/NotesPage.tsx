import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { CoursePicker } from '../../components/CoursePicker';
import { Field, SubmitButton, TextInput } from '../../components/forms';
import { RichTextEditor } from '../../components/RichTextEditor';
import { Button, EmptyState, ListSkeleton, Modal } from '../../components/ui';
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
        <h1 className="text-h1 text-brand-navy dark:text-white">
          {t('notes.title')}
        </h1>
        <Button variant="primary" size="md"
     onClick={openCreate}>
          {t('notes.create')}
        </Button>
      </div>

      {notes.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">
          <ListSkeleton rows={4} />
        </div>
      ) : (notes.data?.length ?? 0) === 0 ? (
        <EmptyState
          title={t('notes.empty')}
          action={
            <Button variant="primary" size="md"
       onClick={openCreate}>
              {t('notes.create')}
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {notes.data?.map((note) => (
            <div key={note.id} className="flex flex-col rounded-lg bg-surface p-5 shadow-card">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-bold text-brand-navy">{note.title}</h2>
                <div className="flex shrink-0 gap-1.5">
                  <Button variant="secondary" size="md"
                    onClick={() => openEdit(note)}
                    aria-label={t('notes.edit')}
                  >
                    ✎
                  </Button>
                  <Button variant="ghost" size="sm" className="text-gray-500 hover:bg-danger/10 hover:text-danger"
                    onClick={() => setDeleteTarget(note)}
                    aria-label={t('sessions.delete')}
                    
                  >
                    🗑️
                  </Button>
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
          <Button variant="secondary" size="md"
            onClick={() => setDeleteTarget(null)}
          >
            {t('common.cancel')}
          </Button>
          <Button variant="danger" size="md"
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
          >
            {t('common.confirm')}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
