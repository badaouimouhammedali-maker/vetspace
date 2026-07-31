import { useState } from 'react';
import { Button } from './ui';
import { t } from '../i18n/fr';

/**
 * Full-screen image viewer with zoom. Shared by the mindmap gallery and the free
 * study library — an image resource and a mindmap are the same thing to a reader,
 * so they get the same viewer rather than two that drift apart.
 */
export function Lightbox({ url, title, onClose }: { url: string; title: string; onClose: () => void }) {
  const [zoom, setZoom] = useState(1);
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-brand-navy/85 p-4"
      onClick={onClose}
    >
      <div className="mb-3 flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
        <Button variant="secondary" size="sm"
          onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))}
          aria-label={t('mindmaps.zoomOut')}
          
        >
          −
        </Button>
        <span className="min-w-14 text-center text-sm font-semibold text-white">
          {Math.round(zoom * 100)}%
        </span>
        <Button variant="secondary" size="sm"
          onClick={() => setZoom((z) => Math.min(4, z + 0.25))}
          aria-label={t('mindmaps.zoomIn')}
          
        >
          +
        </Button>
        <Button variant="secondary" size="sm"
          onClick={() => setZoom(1)}
          
        >
          {t('mindmaps.resetZoom')}
        </Button>
        <Button variant="secondary" size="sm"
          onClick={onClose}
          aria-label={t('play.close')}
          
        >
          ×
        </Button>
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
