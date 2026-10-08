import { Link, useLocation } from 'react-router';
import { useTopics } from '../api/queries';
import { useHighlightedTopic } from './topicHighlight';

/** 주제 메뉴: 전체 · 여행 · 음식 · 취미 · 운동 · 개발 — 현재 메뉴는 진하게 (3.1) */
export function TopicNav() {
  const topics = useTopics();
  const { pathname } = useLocation();
  const highlighted = useHighlightedTopic();
  const topicMatch = /^\/topics\/([^/]+)/.exec(pathname);
  const current = topicMatch ? decodeURIComponent(topicMatch[1]) : highlighted;
  const isHome = pathname === '/';

  return (
    <nav className="topic-nav" aria-label="주제">
      <ul className="topic-nav__list">
        <li>
          <Link to="/" className={`topic-nav__item${isHome ? ' is-current' : ''}`} aria-current={isHome ? 'page' : undefined}>
            전체
          </Link>
        </li>
        {topics.map((t) => {
          const active = current === t.code;
          return (
            <li key={t.code}>
              <Link
                to={`/topics/${encodeURIComponent(t.code)}`}
                className={`topic-nav__item${active ? ' is-current' : ''}`}
                aria-current={active ? 'page' : undefined}
              >
                {t.name}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
