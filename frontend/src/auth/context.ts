import { createContext, useContext } from 'react';

export type AuthTab = 'login' | 'signup';
export type PendingAction = () => unknown;

export interface OpenLoginOptions {
  tab?: AuthTab;
  /** 로그인한 뒤 이어서 실행할 동작 (CF-16-1) */
  action?: PendingAction;
  notice?: string;
  email?: string;
}

export interface AuthModalState {
  open: boolean;
  tab: AuthTab;
  notice: string | null;
  email: string;
}

export interface AuthContextValue {
  modal: AuthModalState;
  openLogin: (opts?: OpenLoginOptions) => void;
  setTab: (tab: AuthTab) => void;
  closeModal: () => void;
  /** 로그인 성공 처리: 세션 정보 새로고침, 창 닫기, 미뤄 둔 동작 실행 */
  completeLogin: () => Promise<void>;
  /** 가입 완료 후 로그인 탭으로 */
  completeSignup: (email: string) => void;
  doLogout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
