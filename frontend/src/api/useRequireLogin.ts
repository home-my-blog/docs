import { useCallback } from 'react';
import { useAuth } from '../auth/context';
import { isApiError } from './client';
import { useMe } from './queries';

/**
 * 회원 전용 동작을 감싼다 (CF-16-1).
 * 비로그인이면 로그인 창을 띄우고, 로그인하면 그 동작을 이어서 실행한다.
 * 동작 중 401 이 나도(세션 만료) 같은 방식으로 다시 시도한다.
 */
export function useRequireLogin() {
  const { data: me } = useMe();
  const { openLogin } = useAuth();

  return useCallback(
    (action: () => unknown) => {
      const run = async () => {
        try {
          await action();
        } catch (e) {
          if (isApiError(e) && e.status === 401) openLogin({ action: run });
          // 그 밖의 오류는 호출한 쪽(뮤테이션 상태 등)이 보여 준다.
        }
      };
      if (!me) {
        openLogin({ action: run });
        return;
      }
      void run();
    },
    [me, openLogin],
  );
}
