import { createContext, useContext, useEffect } from 'react';

export interface TopicHighlightValue {
  code: string | null;
  setCode: (code: string | null) => void;
}

export const TopicHighlightContext = createContext<TopicHighlightValue>({ code: null, setCode: () => undefined });

/** 블로그·글 화면에서 그 블로그의 주제를 주제 메뉴에 강조한다 (3.1). */
export function useHighlightTopic(code: string | null | undefined) {
  const { setCode } = useContext(TopicHighlightContext);
  useEffect(() => {
    setCode(code ?? null);
    return () => setCode(null);
  }, [code, setCode]);
}

export function useHighlightedTopic(): string | null {
  return useContext(TopicHighlightContext).code;
}
