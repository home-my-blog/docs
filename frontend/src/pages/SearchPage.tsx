import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { search } from '../api/endpoints';
import { qk, useConfig } from '../api/queries';
import type { SearchResponse, SearchType } from '../api/types';
import { BlogCard, PostCard } from '../components/Cards';
import { Pagination } from '../components/Pagination';
import { Empty, ErrorBox, Loading } from '../components/Status';
import { TrendingList } from '../components/TrendingList';
import { validateQuery } from '../lib/validation';
import { EXPLORE } from '../messages';

const TYPES: { value: SearchType; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'blog', label: '블로그' },
  { value: 'post', label: '글' },
];

function toType(v: string | null): SearchType {
  return v === 'blog' || v === 'post' ? v : 'all';
}

/** 통합 검색 결과: "검색 결과 N건", 블로그·글 구역 (3.8, CF-11; 커뮤니티 제외) */
export function SearchPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { limits } = useConfig();
  const rawQ = params.get('q') ?? '';
  const q = rawQ.trim();
  const type = toType(params.get('type'));
  const page = Math.max(1, Number(params.get('page')) || 1);
  const invalid = validateQuery(rawQ, limits.searchMin, limits.searchMax);

  const result = useQuery({
    queryKey: qk.search(q, type, page),
    queryFn: () => search({ q, type, page }),
    enabled: !invalid,
    placeholderData: (prev) => prev,
  });

  const setTypePage = (t: SearchType, p = 1) => {
    const next = new URLSearchParams({ q, type: t });
    if (p > 1) next.set('page', String(p));
    setParams(next);
  };

  return (
    <div className="with-aside">
      <div className="search-results">
        {invalid ? (
          <Empty>{invalid}</Empty>
        ) : (
          <>
            <header className="page-head page-head--compact">
              <h1 className="page-head__title">
                ‘{q}’ 검색 결과
              </h1>
              {result.data && <p className="page-head__meta">{EXPLORE.searchTotal(result.data.total)}</p>}
            </header>
            <div className="tabs" role="tablist" aria-label="검색 대상">
              {TYPES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  role="tab"
                  aria-selected={type === t.value}
                  className={`tabs__tab${type === t.value ? ' is-active' : ''}`}
                  onClick={() => setTypePage(t.value)}
                >
                  {t.label}
                  {result.data && t.value !== 'all' && (
                    <span className="tabs__count">
                      {t.value === 'blog' ? result.data.blogs.totalItems : result.data.posts.totalItems}
                    </span>
                  )}
                </button>
              ))}
            </div>
            {result.isPending ? (
              <Loading />
            ) : result.error ? (
              <ErrorBox error={result.error} onRetry={() => void result.refetch()} />
            ) : (
              <Results data={result.data} type={type} onTypePage={setTypePage} q={q} />
            )}
          </>
        )}
      </div>
      <aside className="aside">
        <TrendingList className="card" onSelect={(k) => navigate(`/search?q=${encodeURIComponent(k)}&type=all`)} />
      </aside>
    </div>
  );
}

function Results({
  data,
  type,
  q,
  onTypePage,
}: {
  data: SearchResponse;
  type: SearchType;
  q: string;
  onTypePage: (t: SearchType, p?: number) => void;
}) {
  if (data.total === 0) return <Empty>{EXPLORE.searchNoResult}</Empty>;
  const showBlogs = type === 'all' || type === 'blog';
  const showPosts = type === 'all' || type === 'post';
  return (
    <>
      {showBlogs && (type === 'blog' || data.blogs.totalItems > 0) && (
        <section className="section">
          <div className="section__head">
            <h2 className="section__title">
              블로그 <span className="muted">{data.blogs.totalItems}</span>
            </h2>
            {type === 'all' && data.blogs.totalPages > 1 && (
              <Link to={`/search?q=${encodeURIComponent(q)}&type=blog`} className="section__more">
                더 보기
              </Link>
            )}
          </div>
          {data.blogs.items.length === 0 ? (
            <Empty>{EXPLORE.searchNoResult}</Empty>
          ) : (
            <div className="card-grid card-grid--2">
              {data.blogs.items.map((b) => (
                <BlogCard key={b.id} blog={b} />
              ))}
            </div>
          )}
          {type === 'blog' && (
            <Pagination page={data.blogs.page} totalPages={data.blogs.totalPages} onChange={(p) => onTypePage('blog', p)} />
          )}
        </section>
      )}
      {showPosts && (type === 'post' || data.posts.totalItems > 0) && (
        <section className="section">
          <div className="section__head">
            <h2 className="section__title">
              글 <span className="muted">{data.posts.totalItems}</span>
            </h2>
            {type === 'all' && data.posts.totalPages > 1 && (
              <Link to={`/search?q=${encodeURIComponent(q)}&type=post`} className="section__more">
                더 보기
              </Link>
            )}
          </div>
          {data.posts.items.length === 0 ? (
            <Empty>{EXPLORE.searchNoResult}</Empty>
          ) : (
            <div className="post-rows">
              {data.posts.items.map((p) => (
                <PostCard key={p.id} post={p} />
              ))}
            </div>
          )}
          {type === 'post' && (
            <Pagination page={data.posts.page} totalPages={data.posts.totalPages} onChange={(p) => onTypePage('post', p)} />
          )}
        </section>
      )}
    </>
  );
}
