import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { getBlogAbout } from '../api/endpoints';
import { qk } from '../api/queries';
import { BlogShell } from '../components/BlogShell';
import { ErrorBox, Loading } from '../components/Status';

/** 블로그 소개: 주인 이름 · 한 줄 소개 · 소개 글 (3.6) */
export function BlogAboutPage() {
  const { blogId = '' } = useParams();
  return (
    <BlogShell blogId={blogId} active={{ type: 'about' }}>
      {() => <About blogId={blogId} />}
    </BlogShell>
  );
}

function About({ blogId }: { blogId: string }) {
  const { data, isPending, error, refetch } = useQuery({ queryKey: qk.blogAbout(blogId), queryFn: () => getBlogAbout(blogId) });
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={() => void refetch()} />;
  return (
    <section className="about card">
      <h2 className="about__name">{data.ownerNickname}</h2>
      {data.ownerBio && <p className="about__bio">{data.ownerBio}</p>}
      <div className="about__body">{data.about ? data.about : <p className="muted">아직 소개 글이 없습니다</p>}</div>
    </section>
  );
}
