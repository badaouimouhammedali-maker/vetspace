import { beforeEach, describe, expect, it, vi } from 'vitest';

// The api module builds `axios.create(...)` + interceptors at import time and
// calls `axios.post` for the refresh. Mock the whole module so we control it.
vi.mock('axios', () => {
  const instance = {
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  };
  return {
    default: {
      create: () => instance,
      post: vi.fn(),
      isAxiosError: () => false,
    },
  };
});

import axios from 'axios';
import { getAccessToken, setAccessToken, singleFlightRefresh } from './api';

const post = axios.post as unknown as ReturnType<typeof vi.fn>;

describe('singleFlightRefresh', () => {
  beforeEach(() => {
    setAccessToken(null);
    post.mockReset();
  });

  it('collapses concurrent refreshes into a single POST and shares the token', async () => {
    let resolvePost!: (v: { data: { accessToken: string } }) => void;
    post.mockReturnValue(new Promise((res) => (resolvePost = res)));

    const p1 = singleFlightRefresh();
    const p2 = singleFlightRefresh();
    const p3 = singleFlightRefresh();

    // Three concurrent callers, exactly one network refresh in flight.
    expect(post).toHaveBeenCalledTimes(1);

    resolvePost({ data: { accessToken: 'tok-123' } });
    const results = await Promise.all([p1, p2, p3]);

    expect(results).toEqual(['tok-123', 'tok-123', 'tok-123']);
    expect(getAccessToken()).toBe('tok-123');
  });

  it('issues a fresh POST once the previous refresh settled', async () => {
    post.mockResolvedValueOnce({ data: { accessToken: 'first' } });
    await singleFlightRefresh();
    post.mockResolvedValueOnce({ data: { accessToken: 'second' } });
    const token = await singleFlightRefresh();

    expect(token).toBe('second');
    expect(post).toHaveBeenCalledTimes(2);
  });

  it('clears the token and returns null when the refresh fails', async () => {
    setAccessToken('stale');
    post.mockRejectedValueOnce(new Error('401'));
    const token = await singleFlightRefresh();

    expect(token).toBeNull();
    expect(getAccessToken()).toBeNull();
  });
});
