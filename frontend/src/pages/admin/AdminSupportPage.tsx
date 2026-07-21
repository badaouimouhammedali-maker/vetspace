import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { TableSkeleton } from '../../components/ui';
import { fetchSupportInbox } from '../../lib/adminEndpoints';
import { AdminHeader, Card, Pager } from './adminUi';

export function AdminSupportPage() {
  const [page, setPage] = useState(0);
  const inbox = useQuery({
    queryKey: ['admin', 'support', page],
    queryFn: () => fetchSupportInbox(page),
  });

  return (
    <div>
      <AdminHeader
        title="Support"
        subtitle="Messages reçus. Les réponses se font par e-mail (Répondre-à = l'étudiant)."
      />

      {inbox.isLoading ? (
        <TableSkeleton rows={5} />
      ) : (inbox.data?.content ?? []).length === 0 ? (
        <Card>
          <p className="text-sm text-brand-gray">Aucun message.</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {inbox.data!.content.map((m) => (
            <Card key={m.id}>
              <div className="mb-1 flex items-center justify-between gap-2">
                <span className="text-sm font-bold text-brand-navy">{m.subject}</span>
                <span className="text-xs text-brand-gray">
                  {new Date(m.createdAt).toLocaleString('fr-FR')}
                </span>
              </div>
              <p className="mb-2 whitespace-pre-wrap text-sm text-brand-gray">{m.body}</p>
              <p className="text-xs text-brand-gray">
                De <strong>{m.fullName}</strong> ·{' '}
                <a href={`mailto:${m.userEmail}`} className="text-brand-green hover:underline">
                  {m.userEmail}
                </a>
              </p>
            </Card>
          ))}
        </div>
      )}
      <Pager page={page} totalPages={inbox.data?.totalPages ?? 1} onPage={setPage} />
    </div>
  );
}
