import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { getBlogPosts } from '../api/endpoints';
import { qk } from '../api/queries';
import { BlogShell } from '../components/BlogShell';
import { Pagination } from '../components/Pagination';
import { Empty, ErrorBox, Loading } from '../components/Status';
import { categoryColor, formatDate } from '../lib/format';
import { EXPLORE, POST } from '../messages';

function toPage(v: string | null): number {
  const n = Number(v);
  return Number.isInteger(n) && n > 0 ? n : 1;
}

/** 블로그 글 목록 — ?category&page 를 주소에 유지 (CF-10) */
export function BlogPage() {
  const { blogId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const categoryId = params.get('category');
  const page = toPage(params.get('page'));

  return (
    <BlogShell blogId={blogId} active={{ type: 'list', categoryId }}>
      {({ blog, categories }) => (
        <BlogPostList
          blogId={blogId}
          isOwner={blog.isOwner}
          categoryId={categoryId}
          categoryName={categories.find((c) => String(c.id) === categoryId)?.name}
          page={page}
          onPage={(p) => {
            const next = new URLSearchParams(params);
            if (p > 1) next.set('page', String(p));
            else next.delete('page');
            setParams(next);
            window.scrollTo({ top: 0 });
          }}
        />
      )}
    </BlogShell>
  );
}

interface ListProps {
  blogId: string;
  isOwner: boolean;
  categoryId: string | null;
  categoryName?: string;
  page: number;
  onPage: (p: number) => void;
}

function BlogPostList({ blogId, isOwner, categoryId, categoryName, page, onPage }: ListProps) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: qk.blogPosts(blogId, categoryId, page),
    queryFn: () => getBlogPosts(blogId, { categoryId, page }),
    placeholderData: (prev) => prev,
  });

  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={() => void refetch()} />;

  return (
    <section>
      <div className="list-head">
        <h2 className="list-head__title">{categoryName ?? '전체 글'}</h2>
        <span className="list-head__count">{EXPLORE.postCount(data.totalItems)}</span>
      </div>
      {data.items.length === 0 ? (
        <Empty>
          <p>{EXPLORE.emptyList}</p>
          {isOwner && !categoryId && (
            <>
              <p>{EXPLORE.firstPost}</p>
              <Link to="/write" className="btn btn--primary">
                글쓰기
              </Link>
            </>
          )}
        </Empty>
      ) : (
        <ul className="post-list">
          {data.items.map((p) => (
            <li key={p.id} className="post-list__item">
              <Link to={`/posts/${p.id}`} className="post-list__link">
                <div className="post-list__meta">
                  <span className="post-list__cat">
                    <span className="dot" style={{ background: categoryColor(p.category.colorIndex) }} aria-hidden="true" />
                    {p.category.name}
                  </span>
                  <time dateTime={p.createdAt}>{formatDate(p.createdAt)}</time>
                  {p.visibility === 'PRIVATE' && <span className="badge badge--muted">{POST.privateBadge}</span>}
                </div>
                <h3 className="post-list__title">{p.title}</h3>
                {p.excerpt && <p className="post-list__excerpt">{p.excerpt}</p>}
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={data.page} totalPages={data.totalPages} onChange={onPage} />
    </section>
  );
}
