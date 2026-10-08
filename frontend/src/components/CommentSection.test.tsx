import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PostDetail } from '../api/types';
import { mockFetch, renderWithProviders } from '../test/utils';
import { CommentSection } from './CommentSection';

const post: PostDetail = {
  id: 7,
  blog: { id: 1, name: '떠나는 고양이' },
  topic: { code: 'travel', name: '여행' },
  category: { id: 2, name: '국내여행' },
  title: '단풍 명소',
  body: '본문',
  createdAt: '2026-10-01T10:00:00+09:00',
  contentUpdatedAt: null,
  visibility: 'PUBLIC',
  coverImageUrl: null,
  tags: [],
  likeCount: 0,
  likedByMe: false,
  commentCount: 2,
  prev: null,
  next: null,
  otherPosts: [],
  isAuthor: false,
};

describe('CommentSection (CF-18)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('비회원에게는 입력칸 대신 안내와 로그인 버튼, 탈퇴한 작성자는 "탈퇴한 사용자"', async () => {
    mockFetch([
      { path: '/api/auth/me', status: 204 },
      {
        path: '/api/posts/7/comments',
        body: [
          { id: 1, author: { nickname: '준호' }, body: '좋아요', createdAt: '2026-10-01T11:00:00+09:00', canDelete: false },
          { id: 2, author: null, body: '옛 댓글', createdAt: '2026-10-01T12:00:00+09:00', canDelete: false },
        ],
      },
    ]);
    renderWithProviders(<CommentSection post={post} />);
    expect(await screen.findByText('준호')).toBeInTheDocument();
    expect(screen.getByText('탈퇴한 사용자')).toBeInTheDocument();
    expect(await screen.findByText('로그인한 회원만 댓글을 쓸 수 있습니다')).toBeInTheDocument();
    expect(screen.queryByLabelText('댓글 입력')).toBeNull();
    expect(screen.queryByRole('button', { name: '삭제' })).toBeNull();
  });

  it('로그인한 회원에게는 입력칸과, 지울 수 있는 댓글에만 삭제 버튼을 보여 준다', async () => {
    mockFetch([
      { path: '/api/auth/me', body: { member: { id: 3, nickname: '서연', email: 's@b.com' }, blog: { id: 5, name: '서연의 블로그' }, newCommentCount: 0 } },
      {
        path: '/api/posts/7/comments',
        body: [
          { id: 1, author: { nickname: '준호' }, body: '남의 댓글', createdAt: '2026-10-01T11:00:00+09:00', canDelete: false },
          { id: 2, author: { nickname: '서연' }, body: '내 댓글', createdAt: '2026-10-01T12:00:00+09:00', canDelete: true },
        ],
      },
    ]);
    renderWithProviders(<CommentSection post={post} />);
    expect(await screen.findByLabelText('댓글 입력')).toBeInTheDocument();
    expect(await screen.findAllByRole('button', { name: '삭제' })).toHaveLength(1);
  });
});
