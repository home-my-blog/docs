import { useCallback, useEffect, useRef } from 'react';
import { useBlocker } from 'react-router';
import { POST } from '../messages';

/**
 * 저장하지 않은 내용이 있으면 화면을 떠날 때 묻는다 (CF-05-10, CF-15-7).
 * 저장 직후 이동할 때는 allowNavigation() 을 먼저 부른다.
 */
export function useUnsavedChangesPrompt(dirty: boolean) {
  const allowRef = useRef(false);

  const shouldBlock = useCallback(
    ({ currentLocation, nextLocation }: { currentLocation: { pathname: string; search: string }; nextLocation: { pathname: string; search: string } }) =>
      dirty &&
      !allowRef.current &&
      (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search),
    [dirty],
  );
  const blocker = useBlocker(shouldBlock);

  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    if (window.confirm(POST.leaveConfirm)) blocker.proceed();
    else blocker.reset();
  }, [blocker]);

  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (allowRef.current) return;
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [dirty]);

  return useCallback(() => {
    allowRef.current = true;
  }, []);
}
