import { useAuth } from '../auth/context';
import { LoginForm } from './LoginForm';
import { Modal } from './Modal';
import { SignupForm } from './SignupForm';

/** `로그인 | 회원가입` 탭 전환 창 (요구사항.md 3.9) */
export function AuthModal() {
  const { modal, setTab, closeModal, completeLogin, completeSignup } = useAuth();
  if (!modal.open) return null;
  const isLogin = modal.tab === 'login';

  return (
    <Modal title={isLogin ? '로그인' : '회원가입'} onClose={closeModal} hideTitle>
      <div className="tabs" role="tablist" aria-label="로그인 또는 회원가입">
        <button
          type="button"
          role="tab"
          aria-selected={isLogin}
          className={`tabs__tab${isLogin ? ' is-active' : ''}`}
          onClick={() => setTab('login')}
        >
          로그인
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={!isLogin}
          className={`tabs__tab${!isLogin ? ' is-active' : ''}`}
          onClick={() => setTab('signup')}
        >
          회원가입
        </button>
      </div>
      <div role="tabpanel">
        {isLogin ? (
          <LoginForm
            key={`login-${modal.email}-${modal.notice ?? ''}`}
            initialEmail={modal.email}
            notice={modal.notice}
            onSuccess={completeLogin}
            onForgot={closeModal}
          />
        ) : (
          <SignupForm onDone={completeSignup} />
        )}
      </div>
    </Modal>
  );
}
