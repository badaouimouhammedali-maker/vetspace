import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { CoursePicker } from '../../components/CoursePicker';
import { EmptyState, Skeleton } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchMindmaps } from '../../lib/endpoints';

function Lightbox({ url, title, onClose }: { url: string; title: string; onClose: () => void }) {
  const [zoom, setZoom] = useState(1);
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-brand-navy/85 p-4"
      onClick={onClose}
    >
      <div className="mb-3 flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
        <button
          onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))}
          aria-label={t('mindmaps.zoomOut')}
          className="rounded-lg bg-white/90 px-3 py-1.5 text-sm font-bold text-brand-navy"
        >
          −
        </button>
        <span className="min-w-14 text-center text-sm font-semibold text-white">
          {Math.round(zoom * 100)}%
        </span>
        <button
          onClick={() => setZoom((z) => Math.min(4, z + 0.25))}
          aria-label={t('mindmaps.zoomIn')}
          className="rounded-lg bg-white/90 px-3 py-1.5 text-sm font-bold text-brand-navy"
        >
          +
        </button>
        <button
          onClick={() => setZoom(1)}
          className="rounded-lg bg-white/90 px-3 py-1.5 text-sm font-semibold text-brand-navy"
        >
          {t('mindmaps.resetZoom')}
        </button>
        <button
          onClick={onClose}
          aria-label={t('play.close')}
          className="rounded-lg bg-white/90 px-3 py-1.5 text-sm font-bold text-brand-navy"
        >
          ×
        </button>
      </div>
      <div className="max-h-[80vh] max-w-full overflow-auto" onClick={(e) => e.stopPropagation()}>
        <img
          src={url}
          alt={title}
          style={{ transform: `scale(${zoom})`, transformOrigin: 'top left' }}
          className="rounded-lg transition-transform"
        />
      </div>
    </div>
  );
}

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
      <h1 className="text-2xl font-extrabold text-brand-navy dark:text-white">
        {t('mindmaps.title')}
      </h1>

      <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-gray-100">
        <CoursePicker courseId={courseId} onCourse={setCourseId} />
      </div>

      {!courseId ? (
        <EmptyState message={t('mindmaps.selectCourse')} />
      ) : mindmaps.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      ) : (mindmaps.data?.length ?? 0) === 0 ? (
        <EmptyState message={t('mindmaps.empty')} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {mindmaps.data?.map((mindmap) => (
            <button
              key={mindmap.id}
              onClick={() => setViewing({ url: mindmap.imageUrl, title: mindmap.title })}
              className="group overflow-hidden rounded-2xl bg-white text-left shadow-sm ring-1 ring-gray-100 transition hover:ring-brand-green"
            >
              <div className="aspect-video overflow-hidden bg-gray-100">
                <img
                  src={mindmap.imageUrl}
                  alt={mindmap.title}
                  className="h-full w-full object-cover transition group-hover:scale-105"
                />
              </div>
              <p className="p-3 text-sm font-bold text-brand-navy">{mindmap.title}</p>
            </button>
          ))}
        </div>
      )}

      {viewing ? (
        <Lightbox url={viewing.url} title={viewing.title} onClose={() => setViewing(null)} />
      ) : null}
    </div>
  );
}
