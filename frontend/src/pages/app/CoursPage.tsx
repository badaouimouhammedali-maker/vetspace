import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { Lightbox } from '../../components/Lightbox';
import { Field, Select, TextInput } from '../../components/forms';
import { Button, EmptyState, ListSkeleton } from '../../components/ui';
import { t } from '../../i18n/fr';
import { fetchResourceYears, fetchResources } from '../../lib/endpoints';
import { formatFileSize } from '../../lib/formatFileSize';
import type { ModuleResources, Resource } from '../../lib/schemas';

/**
 * PDF preview. An <iframe> of the stored URL, because the browser's own viewer is
 * better than anything worth shipping here — and a download link underneath for the
 * browsers (and mobile Safari versions) whose viewer refuses to render inline.
 */
function PdfModal({ url, title, onClose }: { url: string; title: string; onClose: () => void }) {
  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-brand-navy/85 p-3 sm:p-6"
      onClick={onClose}
    >
      <div
        className="mx-auto flex h-full w-full max-w-5xl flex-col overflow-hidden rounded-lg bg-surface"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 border-b border-subtle px-4 py-3">
          <span className="min-w-0 flex-1 truncate text-sm font-bold text-brand-navy">{title}</span>
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="shrink-0 rounded-md px-3 py-1.5 text-sm font-semibold text-brand-green hover:bg-brand-green/10"
          >
            {t('cours.download')}
          </a>
          <Button variant="secondary" size="sm" onClick={onClose} aria-label={t('play.close')}>
            ×
          </Button>
        </div>
        <iframe src={url} title={title} className="min-h-0 flex-1 bg-gray-100" />
      </div>
    </div>
  );
}

function TypeIcon({ resource }: { resource: Resource }) {
  return (
    <span className="text-xl" aria-hidden>
      {resource.fileType === 'PDF' ? '📄' : '🖼️'}
    </span>
  );
}

export function CoursPage() {
  const { user } = useAuth();
  const years = useQuery({ queryKey: ['resource-years'], queryFn: fetchResourceYears });

  // The student's own year is the answer they want nine times out of ten; the selector
  // exists for the tenth. Falls back to the first year that has anything.
  const [yearOverride, setYearOverride] = useState<number | null>(null);
  const availableYears = years.data ?? [];
  const year =
    yearOverride ??
    (user?.studyYear && availableYears.includes(user.studyYear)
      ? user.studyYear
      : (availableYears[0] ?? user?.studyYear ?? 1));

  const [search, setSearch] = useState('');
  const [pdf, setPdf] = useState<{ url: string; title: string } | null>(null);
  const [image, setImage] = useState<{ url: string; title: string } | null>(null);
  const [openModules, setOpenModules] = useState<Record<string, boolean>>({});

  const resources = useQuery({
    queryKey: ['resources', year],
    queryFn: () => fetchResources(year),
    enabled: availableYears.length > 0,
  });

  /** Title search, applied within the selected year; modules left with nothing drop out. */
  const filtered: ModuleResources[] = useMemo(() => {
    const groups = resources.data ?? [];
    const needle = search.trim().toLowerCase();
    if (!needle) return groups;
    return groups
      .map((g) => ({ ...g, resources: g.resources.filter((r) => r.title.toLowerCase().includes(needle)) }))
      .filter((g) => g.resources.length > 0);
  }, [resources.data, search]);

  const isOpen = (moduleId: string) => openModules[moduleId] ?? true;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-h1 text-brand-navy dark:text-white">{t('cours.title')}</h1>
        <p className="mt-1 text-body text-gray-500">{t('cours.subtitle')}</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label={t('cours.year')} htmlFor="cours-year">
          <Select
            id="cours-year"
            value={String(year)}
            onChange={(e) => setYearOverride(Number(e.target.value))}
          >
            {(availableYears.length > 0 ? availableYears : [year]).map((y) => (
              <option key={y} value={y}>
                {t('register.year')} {y}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t('cours.search')} htmlFor="cours-search">
          <TextInput
            id="cours-search"
            type="search"
            placeholder={t('cours.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </Field>
      </div>

      {years.isLoading || resources.isLoading ? (
        <ListSkeleton rows={4} />
      ) : availableYears.length === 0 ? (
        <EmptyState icon="📚" title={t('cours.emptyAll')} />
      ) : filtered.length === 0 ? (
        <EmptyState icon={search ? '🔍' : '📚'} title={search ? t('cours.emptySearch') : t('cours.emptyYear')} />
      ) : (
        <div className="space-y-3">
          {filtered.map((group) => (
            <div key={group.moduleId} className="overflow-hidden rounded-lg bg-surface shadow-subtle ring-1 ring-subtle">
              <button
                type="button"
                onClick={() =>
                  setOpenModules((o) => ({ ...o, [group.moduleId]: !isOpen(group.moduleId) }))
                }
                aria-expanded={isOpen(group.moduleId)}
                className="flex w-full items-center gap-3 px-4 py-3 text-left transition hover:bg-gray-50"
              >
                <span className="text-brand-gray" aria-hidden>
                  {isOpen(group.moduleId) ? '▾' : '▸'}
                </span>
                <span className="flex-1 font-bold text-brand-navy">{group.moduleName}</span>
                <span className="text-xs font-semibold text-brand-gray">
                  {group.resources.length} {group.resources.length > 1 ? t('cours.files') : t('cours.file')}
                </span>
              </button>

              {isOpen(group.moduleId) ? (
                <ul className="divide-y divide-subtle border-t border-subtle">
                  {group.resources.map((r) => (
                    <li key={r.id} className="flex items-center gap-3 px-4 py-3">
                      <TypeIcon resource={r} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-semibold text-brand-navy">{r.title}</p>
                        {r.description ? (
                          <p className="truncate text-xs text-brand-gray">{r.description}</p>
                        ) : null}
                        <p className="mt-0.5 text-xs text-brand-gray">
                          {r.fileType === 'PDF' ? 'PDF' : 'Image'} · {formatFileSize(r.fileSizeBytes)}
                        </p>
                      </div>
                      <div className="flex shrink-0 gap-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() =>
                            r.fileType === 'PDF'
                              ? setPdf({ url: r.fileUrl, title: r.title })
                              : setImage({ url: r.fileUrl, title: r.title })
                          }
                        >
                          {t('cours.preview')}
                        </Button>
                        {/* Opens the R2 URL directly: the file is public, and routing a
                            25MB download through the API would pay for bandwidth twice. */}
                        <a
                          href={r.fileUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="rounded-md bg-brand-green px-3 py-1.5 text-sm font-bold text-white transition hover:bg-brand-green-hover"
                        >
                          {t('cours.download')}
                        </a>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ))}
        </div>
      )}

      {pdf ? <PdfModal url={pdf.url} title={pdf.title} onClose={() => setPdf(null)} /> : null}
      {image ? (
        <Lightbox url={image.url} title={image.title} onClose={() => setImage(null)} />
      ) : null}
    </div>
  );
}
