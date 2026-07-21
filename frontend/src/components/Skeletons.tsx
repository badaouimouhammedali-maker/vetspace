import { Card } from './Card';

/**
 * Loading placeholders shaped like the content they replace.
 *
 * <p>A centred spinner tells the user "something is happening" and nothing else; the
 * page then jumps when content arrives. Blocks in the real layout's shape hold the
 * space, so the load reads as filling in rather than reflowing.
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-gray-200 ${className}`} />;
}

/** Stat row + two panels — the dashboard's shape. */
export function DashboardSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Chargement">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {[0, 1, 2].map((i) => (
          <Card key={i}>
            <div className="flex items-center gap-4">
              <Skeleton className="h-11 w-11 rounded-md" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-3 w-20" />
                <Skeleton className="h-5 w-14" />
              </div>
            </div>
          </Card>
        ))}
      </div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {[0, 1].map((i) => (
          <Card key={i}>
            <Skeleton className="mb-4 h-4 w-32" />
            <Skeleton className="h-40 w-full" />
          </Card>
        ))}
      </div>
    </div>
  );
}

/** Header row plus `rows` body rows. */
export function TableSkeleton({ rows = 6, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <Card padding="none" className="overflow-hidden" >
      <div aria-busy="true" aria-label="Chargement">
        <div className="flex gap-4 border-b border-subtle px-6 py-3">
          {Array.from({ length: columns }, (_, i) => (
            <Skeleton key={i} className="h-3 flex-1" />
          ))}
        </div>
        {Array.from({ length: rows }, (_, r) => (
          <div key={r} className="flex gap-4 border-b border-subtle px-6 py-4 last:border-0">
            {Array.from({ length: columns }, (_, c) => (
              <Skeleton key={c} className="h-4 flex-1" />
            ))}
          </div>
        ))}
      </div>
    </Card>
  );
}

/** Grid of cards — session lists, mindmaps, packs. */
export function CardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div
      className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      aria-busy="true"
      aria-label="Chargement"
    >
      {Array.from({ length: count }, (_, i) => (
        <Card key={i}>
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-1/2" />
            </div>
            <Skeleton className="h-14 w-14 rounded-full" />
          </div>
          <Skeleton className="mt-4 h-2 w-full" />
        </Card>
      ))}
    </div>
  );
}

/** Stacked rows for list-shaped pages: notes, notifications, signals. */
export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Chargement">
      {Array.from({ length: rows }, (_, i) => (
        <Card key={i} padding="sm">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="mt-2 h-3 w-2/3" />
        </Card>
      ))}
    </div>
  );
}
