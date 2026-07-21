import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { ToastProvider, useToast } from '../components/ToastProvider';
import { apiErrorMessage, apiErrorReference } from './api';

/**
 * The correlation id only earns its keep if it survives all the way to something a
 * student can read out. These cover both ends of that: pulling it off the response, and
 * putting it on screen.
 */
function axiosErrorWith({
  header,
  body,
}: {
  header?: string;
  body?: unknown;
}): AxiosError {
  const error = new AxiosError('boom');
  const headers = new AxiosHeaders();
  if (header) {
    headers.set('x-request-id', header);
  }
  error.response = {
    status: 500,
    statusText: 'Internal Server Error',
    data: body,
    headers,
    config: { headers: new AxiosHeaders() },
  } as NonNullable<AxiosError['response']>;
  return error;
}

describe('apiErrorReference', () => {
  it('prefers the response header', () => {
    const err = axiosErrorWith({ header: 'abc123', body: { requestId: 'from-body' } });
    expect(apiErrorReference(err)).toBe('abc123');
  });

  it('falls back to the body when there is no header', () => {
    const err = axiosErrorWith({
      body: { error: 'Bad Request', message: 'nope', timestamp: 'x', requestId: 'from-body' },
    });
    expect(apiErrorReference(err)).toBe('from-body');
  });

  it('reads the header even when the body is not our JSON at all', () => {
    // A proxy 502 or an nginx error page: the header is set by a filter ahead of the
    // whole chain, so it is there even when the body is HTML.
    const err = axiosErrorWith({ header: 'proxy-1', body: '<html>502</html>' });
    expect(apiErrorReference(err)).toBe('proxy-1');
  });

  it('returns null when there is nothing to correlate', () => {
    expect(apiErrorReference(axiosErrorWith({ body: {} }))).toBeNull();
    expect(apiErrorReference(new Error('offline'))).toBeNull();
  });

  it('still parses an error body from a backend that predates requestId', () => {
    // requestId is optional in the schema on purpose — a stricter schema would make the
    // message unreadable too, turning a useful error into a generic one.
    const err = axiosErrorWith({
      body: { error: 'Bad Request', message: 'Champ requis', timestamp: 'x' },
    });
    expect(apiErrorMessage(err)).toBe('Champ requis');
    expect(apiErrorReference(err)).toBeNull();
  });
});

function Harness({ reference }: { reference?: string | null }) {
  const { toast } = useToast();
  return (
    <button type="button" onClick={() => toast('error', 'Échec', reference)}>
      go
    </button>
  );
}

describe('error toast reference', () => {
  it('shows the reference so a student can quote it', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <Harness reference="9f2c17ab" />
      </ToastProvider>,
    );
    await user.click(screen.getByRole('button', { name: 'go' }));

    expect(screen.getByRole('status')).toHaveTextContent('Échec');
    expect(screen.getByText(/Référence\s*:\s*9f2c17ab/)).toBeInTheDocument();
  });

  it('shows no reference line for a client-side error that never reached the API', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <Harness />
      </ToastProvider>,
    );
    await user.click(screen.getByRole('button', { name: 'go' }));

    expect(screen.getByRole('status')).toHaveTextContent('Échec');
    // Offering a reference for a validation message would send someone to support with
    // an id that matches no request.
    expect(screen.queryByText(/Référence/)).not.toBeInTheDocument();
  });
});
