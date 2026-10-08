import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { getBlogPosts } from '../api/endpoints';
import { qk } from '../api/queries';
import type { BlogPostSort, Category } from '../api/types';
import { BlogShell, DiaryCover, LockIcon } from '../components/BlogShell';
import { DiarySettingsModal } from '../components/DiarySettingsModal';
import { Pagination } from '../components/Pagination';
import { Empty, ErrorBox, Loading } from '../components/Status';
import { categoryColor, formatDate } from '../lib/format';
import { CATEGORY, EXPLORE, POST } from '../messages';

function toPage(v: string | null): number {
  const n = Number(v);
  return Number.isInteger(n) && n > 0 ? n : 1;
}

const SORTS: { key: BlogPostSort; label: string; hint?: string }[] = [
  { key: 'latest', label: '최신순' },
  { key: 'popular', label: '인기순', hint: '조회 ×1 + 좋아요 ×5 + 댓글 단 사람 ×3' },
  { key: 'oldest', label: '오래된 순' },
];

/** 블로그 글 목록 — 다이어리·태그·정렬·페이지를 주소에 유지 (CF-10, 데모 블로그 화면) */
export function BlogPage() {
  const { blogId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const categoryId = params.get('category');
  const tag = params.get('tag') ?? '';
  const sortParam = params.get('sort');
  const sort: BlogPostSort = sortParam === 'popular' || sortParam === 'oldest' ? sortParam : 'latest';
  const page = toPage(params.get('page'));

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  };

  return (
    <BlogShell blogId={blogId} active={{ type: 'list', categoryId, tag }}>
      {({ blog, categories }) => {
        const diary = categories.find((c) => String(c.id) === categoryId);
        return (
          <>
            {diary && <DiaryHead diary={diary} isOwner={blog.isOwner} />}
            <BlogPostList
              blogId={blogId}
              isOwner={blog.isOwner}
              categoryId={categoryId}
              title={diary ? '글' : '전체 글'}
              tag={tag}
              sort={sort}
              page={page}
              onSort={(s) => setParam('sort', s === 'latest' ? null : s)}
              onClearTag={() => setParam('tag', null)}
              onPage={(p) => {
                setParam('page', p > 1 ? String(p) : null);
                window.scrollTo({ top: 0 });
              }}
            />
          </>
        );
      }}
    </BlogShell>
  );
}

/** 다이어리를 고르면 위에 보이는 머리: 표지 · 이름 · 소개 · 글 수, 주인에게는 쓰기 · 설정 */
function DiaryHead({ diary, isOwner }: { diary: Category; isOwner: boolean }) {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const locked = diary.visibility === 'PRIVATE';
  return (
    <section className="diary-head" style={{ ['--c' as string]: categoryColor(diary.colorIndex) }}>
      <DiaryCover colorIndex={diary.colorIndex} size="lg" locked={locked} />
      <div className="diary-head__info">
        <h2>
          {diary.name}
          {locked && <LockIcon />}
        </h2>
        {diary.description && <p>{diary.description}</p>}
        <span className="muted">
          글 {diary.postCount}개{locked ? ` · ${CATEGORY.privateNote}` : ''}
        </span>
      </div>
      {isOwner && (
        <div className="diary-head__actions">
          <Link to={`/write?category=${diary.id}`} className="btn btn--sm btn--primary">
            {CATEGORY.writeHere}
          </Link>
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => setSettingsOpen(true)}>
            다이어리 설정
          </button>
        </div>
      )}
      {settingsOpen && <DiarySettingsModal key={diary.id} diary={diary} onClose={() => setSettingsOpen(false)} />}
    </section>
  );
}

interface ListProps {
  blogId: string;
  isOwner: boolean;
  categoryId: string | null;
  title: string;
  tag: string;
  sort: BlogPostSort;
  page: number;
  onSort: (s: BlogPostSort) => void;
  onClearTag: () => void;
  onPage: (p: number) => void;
}

function BlogPostList({ blogId, isOwner, categoryId, title, tag, sort, page, onSort, onClearTag, onPage }: ListProps) {
  const { data, isPending, error, refetch } = useQuery({
    queryKey: qk.blogPosts(blogId, categoryId, page, tag, sort),
    queryFn: () => getBlogPosts(blogId, { categoryId, tag, sort, page }),
    placeholderData: (prev) => prev,
  });

  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={() => void refetch()} />;

  return (
    <section>
      <div className="list-head">
        <h2 className="list-head__title">{title}</h2>
        <span className="list-head__count">{EXPLORE.postCount(data.totalItems)}</span>
        <nav className="seg-tabs" aria-label="정렬">
          {SORTS.map((s) => (
            <button
              key={s.key}
              type="button"
              className={sort === s.key ? 'is-on' : ''}
              aria-pressed={sort === s.key}
              title={s.hint}
              onClick={() => onSort(s.key)}
            >
              {s.label}
            </button>
          ))}
        </nav>
      </div>
      {tag && (
        <div className="tag-filter">
          <b>#{tag}</b>
          <span className="muted">{EXPLORE.postCount(data.totalItems)}</span>
          <button type="button" className="link-btn" onClick={onClearTag}>
            {CATEGORY.tagFilterClear}
          </button>
        </div>
      )}
      {data.items.length === 0 ? (
        <Empty>
          <p>{tag ? CATEGORY.emptyTag : EXPLORE.emptyList}</p>
          {isOwner && !tag && (
            <>
              <p>{EXPLORE.firstPost}</p>
              <Link to={categoryId ? `/write?category=${categoryId}` : '/write'} className="btn btn--primary">
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
