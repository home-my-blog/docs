import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { isNotFound } from '../api/client';
import { getBlog, getCategories } from '../api/endpoints';
import { qk } from '../api/queries';
import type { BlogDetail, Category } from '../api/types';
import { categoryColor } from '../lib/format';
import { NotFound } from './NotFound';
import { ErrorBox, Loading } from './Status';
import { useHighlightTopic } from './topicHighlight';

interface BlogShellProps {
  blogId: string;
  /** 'about' 이면 소개 화면 */
  active: { type: 'list'; categoryId: string | null } | { type: 'about' };
  children: (ctx: { blog: BlogDetail; categories: Category[] }) => ReactNode;
}

/** 블로그 화면 공통 틀: 이름·소개 + 본문 + 오른쪽 프로필·분류 목록 (3.4, 3.6) */
export function BlogShell({ blogId, active, children }: BlogShellProps) {
  const blogQ = useQuery({ queryKey: qk.blog(blogId), queryFn: () => getBlog(blogId) });
  const catQ = useQuery({ queryKey: qk.categories(blogId), queryFn: () => getCategories(blogId), enabled: blogQ.isSuccess });
  useHighlightTopic(blogQ.data?.topic?.code);

  if (blogQ.isPending) return <Loading />;
  if (isNotFound(blogQ.error)) return <NotFound what="블로그" />;
  if (blogQ.error) return <ErrorBox error={blogQ.error} onRetry={() => void blogQ.refetch()} />;

  const blog = blogQ.data;
  const categories = catQ.data ?? [];
  const total = categories.reduce((sum, c) => sum + c.postCount, 0);
  const currentCat = active.type === 'list' ? active.categoryId : undefined;

  return (
    <div className="blog">
      <header className="blog__head">
        <h1 className="blog__name">
          <Link to={`/blogs/${blog.id}`}>{blog.name}</Link>
        </h1>
        {blog.description && <p className="blog__desc">{blog.description}</p>}
        {blog.isOwner && (
          <div className="blog__owner-actions">
            <Link to="/write" className="btn btn--sm btn--primary">
              글쓰기
            </Link>
            <Link to="/manage" className="btn btn--sm btn--ghost">
              블로그 관리
            </Link>
          </div>
        )}
      </header>
      <div className="with-aside">
        <div className="blog__main">{children({ blog, categories })}</div>
        <aside className="aside blog__aside">
          <section className="card profile">
            <span className="avatar avatar--lg" aria-hidden="true">
              {blog.owner.nickname.slice(0, 1)}
            </span>
            <div>
              <p className="profile__name">{blog.owner.nickname}</p>
              {blog.owner.bio && <p className="profile__bio">{blog.owner.bio}</p>}
              {blog.topic && <span className="chip">{blog.topic.name}</span>}
            </div>
            <Link
              to={`/blogs/${blog.id}/about`}
              className={`profile__about${active.type === 'about' ? ' is-current' : ''}`}
            >
              소개 보기
            </Link>
          </section>
          <nav className="card catlist" aria-label="분류">
            <h2 className="catlist__title">분류</h2>
            <ul>
              <li>
                <Link
                  to={`/blogs/${blog.id}`}
                  className={currentCat === null ? 'is-current' : ''}
                  aria-current={currentCat === null ? 'page' : undefined}
                >
                  전체 <span className="catlist__count">{total}</span>
                </Link>
              </li>
              {categories.map((c) => {
                const isCur = currentCat === String(c.id);
                return (
                  <li key={c.id}>
                    <Link
                      to={`/blogs/${blog.id}?category=${c.id}`}
                      className={isCur ? 'is-current' : ''}
                      aria-current={isCur ? 'page' : undefined}
                    >
                      <span className="dot" style={{ background: categoryColor(c.colorIndex) }} aria-hidden="true" />
                      {c.name} <span className="catlist__count">{c.postCount}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
        </aside>
      </div>
    </div>
  );
}
