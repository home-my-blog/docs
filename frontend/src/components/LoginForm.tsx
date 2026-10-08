import { useId, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { errorMessage, isApiError } from '../api/client';
import { login } from '../api/endpoints';
import { minutesUntil } from '../lib/format';
import { isValidEmail, normalizeEmail } from '../lib/validation';
import { AUTH } from '../messages';
import { FieldError, FormMessage } from './Status';

interface LoginFormProps {
  initialEmail?: string;
  notice?: string | null;
  onSuccess: () => Promise<void> | void;
  onForgot: () => void;
}

/** 로그인 (CF-02): 실패 문구는 하나, 잠금이면 남은 시간 안내, "비밀번호를 잊으셨나요?" */
export function LoginForm({ initialEmail = '', notice, onSuccess, onForgot }: LoginFormProps) {
  const emailId = useId();
  const pwId = useId();
  const pwRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const busy = useRef(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const next: { email?: string; password?: string } = {};
    if (!email.trim()) next.email = AUTH.required;
    else if (!isValidEmail(email)) next.email = AUTH.emailInvalid;
    if (!password) next.password = AUTH.required;
    setErrors(next);
    if (next.email) return emailRef.current?.focus();
    if (next.password) return pwRef.current?.focus();
    if (busy.current) return;
    busy.current = true;
    setSubmitting(true);
    setFormError(null);
    try {
      await login({ email: normalizeEmail(email), password });
      await onSuccess();
    } catch (err) {
      if (isApiError(err) && err.code === 'ACCOUNT_LOCKED') {
        const unlockAt = typeof err.details.unlockAt === 'string' ? err.details.unlockAt : null;
        setFormError(unlockAt ? AUTH.accountLocked(minutesUntil(unlockAt, Date.now())) : errorMessage(err));
      } else if (isApiError(err) && (err.code === 'LOGIN_FAILED' || err.status === 401)) {
        setFormError(AUTH.loginFailed);
      } else {
        setFormError(errorMessage(err));
      }
      setPassword('');
      pwRef.current?.focus();
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  };

  return (
    <form className="form" onSubmit={onSubmit} noValidate aria-label="로그인">
      <FormMessage kind="ok" message={notice} />
      <div className="field">
        <label htmlFor={emailId} className="field__label">
          이메일
        </label>
        <input
          id={emailId}
          ref={emailRef}
          className="input"
          type="email"
          autoComplete="email"
          value={email}
          aria-invalid={!!errors.email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <FieldError message={errors.email} />
      </div>
      <div className="field">
        <label htmlFor={pwId} className="field__label">
          비밀번호
        </label>
        <input
          id={pwId}
          ref={pwRef}
          className="input"
          type="password"
          autoComplete="current-password"
          value={password}
          aria-invalid={!!errors.password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <FieldError message={errors.password} />
      </div>
      <FormMessage kind="error" message={formError} />
      <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
        {submitting ? '로그인 중…' : '로그인'}
      </button>
      <p className="form__foot">
        <Link to="/password-reset" onClick={onForgot}>
          {AUTH.forgotPassword}
        </Link>
      </p>
    </form>
  );
}
