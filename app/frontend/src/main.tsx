import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { refreshCsrf } from './api/client';
import { App } from './App';
import { createQueryClient } from './queryClient';
import { routes } from './routes';
import './styles/index.css';

// 앱 시작 시 CSRF 쿠키를 받는다. /api/config 와 /api/auth/me 는 화면이 쿼리로 부른다.
void refreshCsrf();

const queryClient = createQueryClient();
const router = createBrowserRouter(routes);

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <App queryClient={queryClient} router={router} />
  </StrictMode>,
);
