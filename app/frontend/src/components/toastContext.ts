import { createContext, useContext } from 'react';

export interface ToastContextValue {
  show: (message: string, kind?: 'info' | 'error') => void;
}

export const ToastContext = createContext<ToastContextValue>({ show: () => undefined });

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}
