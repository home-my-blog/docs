import { Link } from 'react-router';
import type { BlogSummary, PostSummary } from '../api/types';
import { formatDate } from '../lib/format';

/** 글 카드: 블로그 이름 · 작성일 · 제목 · 요약 */
export function PostCard({ post, showBlog = true }: { post: PostSummary; showBlog?: boolean }) {
  return (
    <article className="post-card">
      <Link to={`/posts/${post.id}`} className="post-card__link">
        <div className="post-card__meta">
          {showBlog && <span className="post-card__blog">{post.blog.name}</span>}
          {post.category && <span className="post-card__cat">{post.category.name}</span>}
          <time dateTime={post.createdAt}>{formatDate(post.createdAt)}</time>
        </div>
        <h3 className="post-card__title">{post.title}</h3>
        {post.excerpt && <p className="post-card__excerpt">{post.excerpt}</p>}
      </Link>
    </article>
  );
}

/** 블로그 카드: 주인 첫 글자 · 주제 · 이름 · 주인 · 소개 · 글 개수 · 최근 글 날짜 (3.2) */
export function BlogCard({ blog }: { blog: BlogSummary }) {
  return (
    <article className="blog-card">
      <Link to={`/blogs/${blog.id}`} className="blog-card__link">
        <div className="blog-card__top">
          <span className="avatar avatar--lg" aria-hidden="true">
            {blog.ownerNickname.slice(0, 1)}
          </span>
          {blog.topic && <span className="chip">{blog.topic.name}</span>}
        </div>
        <h3 className="blog-card__name">{blog.name}</h3>
        <p className="blog-card__owner">{blog.ownerNickname}</p>
        {blog.description && <p className="blog-card__desc">{blog.description}</p>}
        <p className="blog-card__stats">
          글 {blog.postCount.toLocaleString()}개
          {blog.lastPostAt && <> · 최근 {formatDate(blog.lastPostAt)}</>}
        </p>
      </Link>
    </article>
  );
}

/** 오늘의 이슈 카드 — 사진이 있으면 사진 위에 글자, 없으면 글자 중심 (3.2) */
export function FeaturedCard({ post, size }: { post: PostSummary; size: 'lg' | 'sm' }) {
  const hasImage = !!post.coverImageUrl;
  return (
    <article className={`featured featured--${size}${hasImage ? ' featured--image' : ''}`}>
      <Link to={`/posts/${post.id}`} className="featured__link">
        {hasImage && <img className="featured__img" src={post.coverImageUrl ?? ''} alt="" loading="lazy" />}
        <div className="featured__body">
          <span className="featured__blog">{post.blog.name}</span>
          <h3 className="featured__title">{post.title}</h3>
          {size === 'lg' && post.excerpt && <p className="featured__excerpt">{post.excerpt}</p>}
          {size === 'lg' && <span className="featured__more">자세히 보기 →</span>}
        </div>
      </Link>
    </article>
  );
}
