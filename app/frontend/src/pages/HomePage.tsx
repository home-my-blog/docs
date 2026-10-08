import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { getHome } from '../api/endpoints';
import { qk } from '../api/queries';
import { BlogCard, FeaturedCard, PostCard } from '../components/Cards';
import { Empty, ErrorBox, Loading } from '../components/Status';
import { TrendingList } from '../components/TrendingList';
import { COMMON } from '../messages';

/** 홈: 오늘의 이슈 · 새로 올라온 글 6 · 인기 검색어 · 블로그 둘러보기 (3.2, 커뮤니티 제외) */
export function HomePage() {
  const navigate = useNavigate();
  const { data, isPending, error, refetch } = useQuery({ queryKey: qk.home, queryFn: getHome });

  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={() => void refetch()} />;

  const [big, ...smalls] = data.featured;

  return (
    <div className="home">
      {big && (
        <section className="home__featured" aria-label="오늘의 이슈">
          <h2 className="sr-only">오늘의 이슈</h2>
          <div className="featured-grid">
            <FeaturedCard post={big} size="lg" />
            {smalls.length > 0 && (
              <div className="featured-grid__side">
                {smalls.slice(0, 2).map((p) => (
                  <FeaturedCard key={p.id} post={p} size="sm" />
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      <div className="with-aside">
        <section className="section">
          <h2 className="section__title">새로 올라온 글</h2>
          {data.latestPosts.length === 0 ? (
            <Empty>{COMMON.noPosts}</Empty>
          ) : (
            <div className="post-rows">
              {data.latestPosts.slice(0, 6).map((p) => (
                <PostCard key={p.id} post={p} />
              ))}
            </div>
          )}
        </section>
        <aside className="aside">
          <TrendingList onSelect={(k) => navigate(`/search?q=${encodeURIComponent(k)}&type=all`)} className="card" />
        </aside>
      </div>

      <section className="section" id="blogs">
        <h2 className="section__title">블로그 둘러보기</h2>
        {data.blogs.length === 0 ? (
          <Empty>{COMMON.noBlogs}</Empty>
        ) : (
          <div className="card-grid card-grid--3">
            {data.blogs.map((b) => (
              <BlogCard key={b.id} blog={b} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
