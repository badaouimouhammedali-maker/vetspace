import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { CoursePicker } from '../../components/CoursePicker';
import { Lightbox } from '../../components/Lightbox';
import { CardGridSkeleton, EmptyState, PlainButton } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchMindmaps } from '../../lib/endpoints';

export function MindmapsPage() {
  const [courseId, setCourseId] = useState('');
  const [viewing, setViewing] = useState<{ url: string; title: string } | null>(null);

  const mindmaps = useQuery({
    queryKey: ['mindmaps', courseId],
    queryFn: () => fetchMindmaps(courseId),
    enabled: courseId !== '' && courseId !== '__none__',
  });

  return (
    <div className="space-y-5">
      <h1 className="text-h1 text-brand-navy dark:text-white">
        {t('mindmaps.title')}
      </h1>

      <div className="rounded-lg bg-surface p-4 shadow-card">
        <CoursePicker courseId={courseId} onCourse={setCourseId} />
      </div>

      {!courseId ? (
        <EmptyState title={t('mindmaps.selectCourse')} />
      ) : mindmaps.isLoading ? (
        <CardGridSkeleton count={3} />
      ) : (mindmaps.data?.length ?? 0) === 0 ? (
        <EmptyState title={t('mindmaps.empty')} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {mindmaps.data?.map((mindmap) => (
            <PlainButton
              key={mindmap.id}
              onClick={() => setViewing({ url: mindmap.imageUrl, title: mindmap.title })}
              className="group overflow-hidden rounded-lg bg-surface text-left shadow-card transition hover:ring-1 hover:ring-brand-green"
            >
              <div className="aspect-video overflow-hidden bg-gray-100">
                <img
                  src={mindmap.imageUrl}
                  alt={mindmap.title}
                  className="h-full w-full object-cover transition group-hover:scale-105"
                />
              </div>
              <p className="p-3 text-sm font-bold text-brand-navy">{mindmap.title}</p>
            </PlainButton>
          ))}
        </div>
      )}

      {viewing ? (
        <Lightbox url={viewing.url} title={viewing.title} onClose={() => setViewing(null)} />
      ) : null}
    </div>
  );
}
