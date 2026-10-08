import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockFetch, renderWithProviders } from '../test/utils';
import { BlogPage } from './BlogPage';

const blog = {
  id: 1,
  name: '생각 서랍',
  description: '떠오르는 대로 넣어 두는 서랍',
  topic: { code: 'hobby', name: '취미' },
  owner: { nickname: '끄적이', bio: '' },
  postCount: 3,
  lastPostAt: null,
  isOwner: true,
};
const categories = [
  { id: 1, name: '일상', description: '하루하루', visibility: 'PUBLIC', postCount: 2, isDefault: true, colorIndex: 2 },
  { id: 4, name: '비밀 일기', description: '나만 보는 다이어리', visibility: 'PRIVATE', postCount: 1, isDefault: false, colorIndex: 5 },
];
const page = (items: unknown[]) => ({ items, page: 1, size: 10, totalItems: items.length, totalPages: 1 });

function renderAt(path: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/blogs/:blogId" element={<BlogPage />} />
    </Routes>,
    { path },
  );
}

describe('블로그 화면 (데모 다이어리)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('다이어리 책장과 왼쪽 목록·태그를 보여 주고, 비공개 다이어리는 자물쇠로 표시한다', async () => {
    mockFetch([
      { path: '/api/auth/me', status: 204 },
      { path: '/api/blogs/1', body: blog },
      { path: '/api/blogs/1/categories', body: categories },
      { path: '/api/blogs/1/tags', body: [{ name: '여행', count: 2 }] },
      { path: '/api/blogs/1/posts', body: page([]) },
    ]);
    renderAt('/blogs/1');
    expect(await screen.findByRole('navigation', { name: '다이어리 책장' })).toBeInTheDocument();
    expect(await screen.findAllByText('비밀 일기')).toHaveLength(2); // 책장 + 왼쪽 목록
    expect(screen.getAllByRole('img', { name: '비공개' }).length).toBeGreaterThan(0);
    expect(screen.getByText('#여행')).toBeInTheDocument();
    expect(screen.getByText(/다이어리 2권/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '인기순' })).toBeInTheDocument();
  });

  it('다이어리를 고르면 다이어리 머리(소개, 주인에게는 쓰기·설정)를, 태그로 거르면 해제 버튼을 보여 준다', async () => {
    const fetchMock = mockFetch([
      { path: '/api/auth/me', status: 204 },
      { path: '/api/blogs/1', body: blog },
      { path: '/api/blogs/1/categories', body: categories },
      { path: '/api/blogs/1/tags', body: [] },
      { path: '/api/blogs/1/posts', body: page([]) },
    ]);
    renderAt('/blogs/1?category=4&tag=여행&sort=popular');
    expect(await screen.findByText('나만 보는 다이어리')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '이 다이어리에 쓰기' })).toHaveAttribute('href', '/write?category=4');
    expect(screen.getByRole('button', { name: '다이어리 설정' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '✕ 태그 거르기 해제' })).toBeInTheDocument();
    const listCall = fetchMock.mock.calls.map(([u]) => String(u)).find((u) => u.startsWith('/api/blogs/1/posts'));
    expect(listCall).toContain('categoryId=4');
    expect(listCall).toContain('sort=popular');
    expect(decodeURIComponent(listCall ?? '')).toContain('tag=여행');
  });
});
