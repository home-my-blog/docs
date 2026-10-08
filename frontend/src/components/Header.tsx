import { Link, useLocation, useSearchParams } from 'react-router';
import { useMe } from '../api/queries';
import { useAuth } from '../auth/context';
import { SearchBox } from './SearchBox';
import { TopicNav } from './TopicNav';
import { UserMenu } from './UserMenu';

/** 맨 위 메뉴: 로그인/회원가입 또는 사용자 메뉴 · 로고 · 검색창 · 주제 메뉴 (3.1) */
export function Header() {
  const { data: me, isPending } = useMe();
  const { openLogin } = useAuth();
  const { pathname } = useLocation();
  const [params] = useSearchParams();
  const onSearch = pathname === '/search';
  const q = onSearch ? (params.get('q') ?? '') : '';

  return (
    <header className="site-header">
      <div className="site-header__bar container">
        <div className="site-header__auth">
          {isPending ? null : me ? (
            <UserMenu me={me} />
          ) : (
            <>
              <button type="button" className="btn btn--sm btn--ghost" onClick={() => openLogin({ tab: 'login' })}>
                로그인
              </button>
              <button type="button" className="btn btn--sm btn--primary" onClick={() => openLogin({ tab: 'signup' })}>
                회원가입
              </button>
            </>
          )}
        </div>
        <Link to="/" className="logo" aria-label="MyBlog 홈">
          My<span>Blog</span>
        </Link>
        <div className="site-header__search">
          <SearchBox key={onSearch ? `q:${q}` : 'idle'} initialQuery={q} />
          <Link to="/" className="site-header__home">
            홈
          </Link>
        </div>
      </div>
      <div className="container">
        <TopicNav />
      </div>
    </header>
  );
}
