import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockFetch, renderWithProviders } from '../test/utils';
import { SignupForm } from './SignupForm';

function rule(name: string) {
  return document.querySelector(`[data-rule="${name}"]`);
}

describe('SignupForm (CF-01)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('CF-01-6: 비밀번호를 입력하는 동안 규칙 충족 여부를 바로 보여 준다', async () => {
    mockFetch([]);
    renderWithProviders(<SignupForm onDone={() => undefined} />);
    const pw = await screen.findByLabelText('비밀번호');
    fireEvent.change(pw, { target: { value: 'abc' } });
    expect(rule('letter')).toHaveAttribute('data-ok', 'true');
    expect(rule('digit')).toHaveAttribute('data-ok', 'false');
    expect(rule('special')).toHaveAttribute('data-ok', 'false');
    expect(rule('length')).toHaveAttribute('data-ok', 'false');

    fireEvent.change(pw, { target: { value: 'abcd123!' } });
    for (const r of ['length', 'letter', 'digit', 'special']) expect(rule(r)).toHaveAttribute('data-ok', 'true');

    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'abcd123?' } });
    expect(screen.getByText('비밀번호가 일치하지 않습니다')).toBeInTheDocument();
  });

  it('CF-01-11: 닉네임·이메일이 형식에 맞을 때만 인증번호 받기가 눌리고, 형식 오류는 칸 아래에 보인다', async () => {
    mockFetch([]);
    renderWithProviders(<SignupForm onDone={() => undefined} />);
    const send = await screen.findByRole('button', { name: '인증번호 받기' });
    expect(send).toBeDisabled();

    const email = screen.getByLabelText('이메일');
    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: '수연' } });
    fireEvent.change(email, { target: { value: 'not-an-email' } });
    fireEvent.blur(email);
    expect(send).toBeDisabled();
    expect(screen.getByText('이메일 형식이 올바르지 않습니다')).toBeInTheDocument();

    fireEvent.change(email, { target: { value: 'a@b.com' } });
    expect(send).toBeEnabled();
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
  });

  it('CF-01-13, 19: 인증을 마치면 이메일 칸이 잠기고 이메일 변경으로 인증이 취소된다', async () => {
    const fetchMock = mockFetch([
      { method: 'POST', path: '/api/auth/verifications', status: 202, body: { expiresAt: '2026-10-08T10:10:00+09:00' } },
      { method: 'POST', path: '/api/auth/verifications/confirm', body: { verifiedUntil: '2026-10-08T10:40:00+09:00' } },
    ]);
    document.cookie = 'XSRF-TOKEN=t; path=/';
    renderWithProviders(<SignupForm onDone={() => undefined} />);
    fireEvent.change(await screen.findByLabelText('닉네임'), { target: { value: '수연' } });
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: ' A@B.com ' } });
    fireEvent.click(screen.getByRole('button', { name: '인증번호 받기' }));

    expect(await screen.findByText('인증번호를 보냈습니다. 10분 안에 입력해 주세요')).toBeInTheDocument();
    const sendCall = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/verifications');
    expect(JSON.parse((sendCall?.[1] as RequestInit).body as string)).toEqual({ purpose: 'signup', email: 'a@b.com', nickname: '수연' });

    fireEvent.change(screen.getByLabelText('인증번호'), { target: { value: 'abc234' } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByText('이메일 인증이 완료되었습니다')).toBeInTheDocument();
    const confirmCall = fetchMock.mock.calls.find((c) => c[0] === '/api/auth/verifications/confirm');
    expect(JSON.parse((confirmCall?.[1] as RequestInit).body as string).code).toBe('ABC234');
    expect(screen.getByLabelText('이메일')).toHaveAttribute('readonly');

    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'abcd123!' } });
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'abcd123!' } });
    expect(screen.getByRole('button', { name: '가입하기' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: '이메일 변경' }));
    await waitFor(() => expect(screen.getByLabelText('이메일')).not.toHaveAttribute('readonly'));
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '인증번호 받기' })).toBeEnabled();
  });

  it('CF-14-1: 이미 가입된 이메일이면 이메일 칸 아래에 알려 준다', async () => {
    mockFetch([
      {
        method: 'POST',
        path: '/api/auth/verifications',
        status: 409,
        body: { error: { code: 'EMAIL_TAKEN', message: '이미 가입된 이메일입니다' } },
      },
    ]);
    document.cookie = 'XSRF-TOKEN=t; path=/';
    renderWithProviders(<SignupForm onDone={() => undefined} />);
    fireEvent.change(await screen.findByLabelText('닉네임'), { target: { value: '수연' } });
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'a@b.com' } });
    fireEvent.click(screen.getByRole('button', { name: '인증번호 받기' }));
    expect(await screen.findByText('이미 가입된 이메일입니다')).toBeInTheDocument();
    expect(screen.queryByLabelText('인증번호')).toBeNull();
  });
});
