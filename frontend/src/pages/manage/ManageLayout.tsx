import { Link, NavLink, Outlet } from 'react-router';
import { NotFound } from '../../components/NotFound';
import { RequireLogin } from '../../components/RequireLogin';
import type { ManageContext } from './context';

const MENU = [
  { to: '/manage', label: '대시보드', end: true },
  { to: '/manage/posts', label: '글 관리' },
  { to: '/manage/categories', label: '다이어리 관리' },
  { to: '/manage/comments', label: '댓글 관리', comments: true },
  { to: '/manage/stats', label: '통계' },
  { to: '/manage/settings', label: '설정' },
];

/** 블로그 관리: 왼쪽 메뉴 + 오른쪽 내용 (BM-01) */
export function ManageLayout() {
  return (
    <RequireLogin>
      {(me) => {
        if (!me.blog) return <NotFound what="블로그" />;
        const ctx: ManageContext = { me, blogId: me.blog.id, blogName: me.blog.name };
        const count = me.newCommentCount ?? 0;
        return (
          <div className="manage">
            <aside className="manage__side">
              <div className="manage__blog">
                <p className="manage__blog-name">{me.blog.name}</p>
                <div className="manage__blog-actions">
                  <Link to={`/blogs/${me.blog.id}`} className="btn btn--sm btn--ghost">
                    내 블로그 보기
                  </Link>
                  <Link to="/write" className="btn btn--sm btn--primary">
                    글쓰기
                  </Link>
                </div>
              </div>
              <nav aria-label="블로그 관리 메뉴">
                <ul className="manage__menu">
                  {MENU.map((m) => (
                    <li key={m.to}>
                      <NavLink to={m.to} end={m.end} className={({ isActive }) => (isActive ? 'is-current' : '')}>
                        {m.label}
                        {m.comments && count > 0 && (
                          <span className="badge" aria-label={`새 댓글 ${count}개`}>
                            {count}
                          </span>
                        )}
                      </NavLink>
                    </li>
                  ))}
                </ul>
              </nav>
            </aside>
            <section className="manage__content">
              <Outlet context={ctx} />
            </section>
          </div>
        );
      }}
    </RequireLogin>
  );
}
