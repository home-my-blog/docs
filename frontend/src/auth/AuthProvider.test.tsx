import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useRequireLogin } from '../api/useRequireLogin';
import { AuthModal } from '../components/AuthModal';
import { mockFetch, renderWithProviders } from '../test/utils';

function Protected({ onRun }: { onRun: () => void }) {
  const requireLogin = useRequireLogin();
  return (
    <>
      <button type="button" onClick={() => requireLogin(onRun)}>
        좋아요
      </button>
      <AuthModal />
    </>
  );
}

describe('로그인 유도 (CF-16-1)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('비로그인으로 회원 전용 동작을 누르면 로그인 창을 띄우고, 로그인하면 그 동작을 이어서 한다', async () => {
    let loggedIn = false;
    const me = { member: { id: 1, nickname: '서연', email: 'a@b.com' }, blog: { id: 1, name: 'b' }, newCommentCount: 0 };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === '/api/auth/me') return loggedIn ? new Response(JSON.stringify(me), { status: 200 }) : new Response(null, { status: 204 });
      if (url === '/api/auth/login' && init?.method === 'POST') {
        loggedIn = true;
        return new Response(JSON.stringify({ member: me.member }), { status: 200 });
      }
      if (url === '/api/csrf') return new Response('{}', { status: 200 });
      return new Response(null, { status: 404 });
    });
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=t; path=/';

    const onRun = vi.fn();
    renderWithProviders(<Protected onRun={onRun} />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.anything()));

    fireEvent.click(screen.getByRole('button', { name: '좋아요' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(onRun).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'a@b.com' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'abcd123!' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => expect(onRun).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('CF-02-3: 로그인 실패는 같은 문구, 잠금이면 남은 시간을 알려 준다', async () => {
    mockFetch([
      { path: '/api/auth/me', status: 204 },
      { method: 'POST', path: '/api/auth/login', status: 401, body: { error: { code: 'LOGIN_FAILED', message: '이메일 또는 비밀번호가 올바르지 않습니다' } } },
    ]);
    document.cookie = 'XSRF-TOKEN=t; path=/';
    renderWithProviders(<Protected onRun={() => undefined} />);
    fireEvent.click(await screen.findByRole('button', { name: '좋아요' }));
    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'a@b.com' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByText('이메일 또는 비밀번호가 올바르지 않습니다')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toHaveValue('');

    vi.unstubAllGlobals();
    const unlockAt = new Date(Date.now() + 9.5 * 60_000).toISOString();
    mockFetch([
      { method: 'POST', path: '/api/auth/login', status: 423, body: { error: { code: 'ACCOUNT_LOCKED', message: 'locked', unlockAt } } },
    ]);
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'abcd123!' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByText('로그인 시도가 5회 실패해 잠겼습니다. 10분 뒤에 다시 시도해 주세요')).toBeInTheDocument();
  });
});
