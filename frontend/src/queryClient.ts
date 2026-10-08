import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './api/client';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 4xx 는 다시 시도해도 같다
        retry: (count, error) => !(isApiError(error) && error.status >= 400 && error.status < 500) && count < 2,
        refetchOnWindowFocus: false,
        staleTime: 15_000,
      },
      mutations: { retry: false },
    },
  });
}
