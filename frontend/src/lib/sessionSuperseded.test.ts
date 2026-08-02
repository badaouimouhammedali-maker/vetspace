import axios from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_EVENTS, singleFlightRefresh, wasLastRefreshSuperseded } from './api';

/**
 * A supersession must be a DISTINCT signal from an ordinary expiry.
 *
 * <p>The refresh call is the only place the server can say why — every other request just
 * sees a 401 — so if the code is not read out of that response it is gone, and the
 * student gets the generic "session expirée" for something quite specific.
 */
describe('session superseded', () => {
  const events: string[] = [];

  function record(event: Event) {
    events.push(event.type);
  }

  beforeEach(() => {
    events.length = 0;
    window.addEventListener(API_EVENTS.sessionSuperseded, record);
    window.addEventListener(API_EVENTS.sessionExpired, record);
  });

  afterEach(() => {
    window.removeEventListener(API_EVENTS.sessionSuperseded, record);
    window.removeEventListener(API_EVENTS.sessionExpired, record);
    vi.restoreAllMocks();
  });

  function rejectRefreshWith(body: unknown) {
    vi.spyOn(axios, 'post').mockRejectedValue(
      Object.assign(new Error('Request failed with status code 401'), {
        isAxiosError: true,
        response: { status: 401, data: body },
      }),
    );
  }

  it('announces a supersession when the API says SESSION_SUPERSEDED', async () => {
    rejectRefreshWith({
      error: 'SESSION_SUPERSEDED',
      message: 'Session closed by a newer sign-in on another device',
      timestamp: new Date().toISOString(),
    });

    const token = await singleFlightRefresh();

    expect(token).toBeNull();
    expect(events).toEqual([API_EVENTS.sessionSuperseded]);
    expect(wasLastRefreshSuperseded()).toBe(true);
  });

  it('does not announce one for an ordinary expired session', async () => {
    rejectRefreshWith({
      error: 'Unauthorized',
      message: 'Invalid refresh token',
      timestamp: new Date().toISOString(),
    });

    await singleFlightRefresh();

    // The generic path is the interceptor's job, not this function's — what matters here
    // is that nothing claimed another device took over.
    expect(events).not.toContain(API_EVENTS.sessionSuperseded);
    expect(wasLastRefreshSuperseded()).toBe(false);
  });

  it('clears the flag once a refresh succeeds again', async () => {
    rejectRefreshWith({
      error: 'SESSION_SUPERSEDED',
      message: 'x',
      timestamp: new Date().toISOString(),
    });
    await singleFlightRefresh();
    expect(wasLastRefreshSuperseded()).toBe(true);

    // Signing back in must not leave the login screen permanently accusing another
    // device — the flag is about the LAST refresh, not the session's history.
    vi.spyOn(axios, 'post').mockResolvedValue({ data: { accessToken: 'fresh' } });
    await singleFlightRefresh();

    expect(wasLastRefreshSuperseded()).toBe(false);
  });
});
