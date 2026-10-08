import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, CSRF_HEADER, UNAUTHENTICATED_EVENT, api } from './client';
import { mockFetch } from '../test/utils';

function headersOf(call: unknown[]): Record<string, string> {
  return ((call[1] as RequestInit).headers ?? {}) as Record<string, string>;
}

describe('api client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=tok-123; path=/';
  });
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    vi.unstubAllGlobals();
  });

  it('NF-11: GET 이 아닌 요청에 X-XSRF-TOKEN 헤더로 쿠키 값을 싣고 쿠키를 포함한다', async () => {
    const fetchMock = mockFetch([{ method: 'POST', path: '/api/posts/1/comments', status: 201, body: { id: 9 } }]);
    const res = await api<{ id: number }>('/api/posts/1/comments', { method: 'POST', body: { body: 'hi' } });
    expect(res).toEqual({ id: 9 });
    const call = fetchMock.mock.calls[0];
    expect(headersOf(call)[CSRF_HEADER]).toBe('tok-123');
    expect(headersOf(call)['Content-Type']).toBe('application/json');
    expect((call[1] as RequestInit).credentials).toBe('include');
    expect((call[1] as RequestInit).body).toBe(JSON.stringify({ body: 'hi' }));
  });

  it('GET 요청에는 CSRF 헤더를 싣지 않고 쿼리를 붙인다', async () => {
    const fetchMock = mockFetch([{ path: '/api/blogs/3/posts', body: { items: [], page: 1, size: 10, totalItems: 0, totalPages: 0 } }]);
    await api('/api/blogs/3/posts', { query: { categoryId: 5, page: 2, empty: '' } });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/blogs/3/posts?categoryId=5&page=2');
    expect((init.headers as Record<string, string>)[CSRF_HEADER]).toBeUndefined();
  });

  it('오류 본문 { error: { code, message, fields } } 를 ApiError 로 바꾼다', async () => {
    mockFetch([
      {
        method: 'POST',
        path: '/api/blogs/1/posts',
        status: 400,
        body: { error: { code: 'VALIDATION_FAILED', message: '제목을 입력해 주세요', fields: { title: '제목을 입력해 주세요' } } },
      },
    ]);
    const err = await api('/api/blogs/1/posts', { method: 'POST', body: {} }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    const e = err as ApiError;
    expect(e.status).toBe(400);
    expect(e.code).toBe('VALIDATION_FAILED');
    expect(e.message).toBe('제목을 입력해 주세요');
    expect(e.fields).toEqual({ title: '제목을 입력해 주세요' });
  });

  it('error 객체의 나머지 값(unlockAt 등)을 details 로 보관한다', async () => {
    mockFetch([
      {
        method: 'POST',
        path: '/api/auth/login',
        status: 423,
        body: { error: { code: 'ACCOUNT_LOCKED', message: '잠김', unlockAt: '2026-10-08T10:00:00+09:00' } },
      },
    ]);
    const e = (await api('/api/auth/login', { method: 'POST', body: {}, skipAuthEvent: true }).catch((x: unknown) => x)) as ApiError;
    expect(e.code).toBe('ACCOUNT_LOCKED');
    expect(e.details.unlockAt).toBe('2026-10-08T10:00:00+09:00');
  });

  it('CF-16-1: 401 이면 로그인 창 이벤트를 보낸다', async () => {
    mockFetch([{ method: 'PUT', path: '/api/posts/1/like', status: 401, body: { error: { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다' } } }]);
    const listener = vi.fn();
    window.addEventListener(UNAUTHENTICATED_EVENT, listener);
    const e = (await api('/api/posts/1/like', { method: 'PUT' }).catch((x: unknown) => x)) as ApiError;
    window.removeEventListener(UNAUTHENTICATED_EVENT, listener);
    expect(e.status).toBe(401);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('204 와 JSON 이 아닌 오류 본문을 처리한다', async () => {
    mockFetch([
      { method: 'DELETE', path: '/api/comments/1', status: 204 },
      { path: '/api/broken', status: 500 },
    ]);
    await expect(api('/api/comments/1', { method: 'DELETE' })).resolves.toBeUndefined();
    const e = (await api('/api/broken').catch((x: unknown) => x)) as ApiError;
    expect(e).toBeInstanceOf(ApiError);
    expect(e.code).toBe('HTTP_500');
  });
});
