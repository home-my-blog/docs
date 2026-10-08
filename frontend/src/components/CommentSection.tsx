import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useState, type FormEvent } from 'react';
import { errorMessage, isApiError } from '../api/client';
import { createComment, deleteComment, getComments } from '../api/endpoints';
import { qk, useConfig, useMe } from '../api/queries';
import type { PostDetail } from '../api/types';
import { useRequireLogin } from '../api/useRequireLogin';
import { useAuth } from '../auth/context';
import { formatDateTime } from '../lib/format';
import { SOCIAL } from '../messages';
import { ErrorBox, FieldError, Loading } from './Status';
import { useToast } from './toastContext';

/** 댓글: 오래된 순, 비회원 안내, 등록, 삭제 확인, 탈퇴한 사용자 (CF-18) */
export function CommentSection({ post }: { post: PostDetail }) {
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const { openLogin } = useAuth();
  const requireLogin = useRequireLogin();
  const toast = useToast();
  const { limits } = useConfig();
  const inputId = useId();
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);

  const q = useQuery({ queryKey: qk.comments(post.id), queryFn: () => getComments(post.id) });

  // 댓글 관리에서 "#comment-{id}" 로 들어오면 그 위치로 (BM-05-2)
  useEffect(() => {
    if (!q.data) return;
    const hash = window.location.hash;
    if (hash.startsWith('#comment')) {
      document.getElementById(hash.slice(1))?.scrollIntoView({ block: 'center' });
    }
  }, [q.data]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: qk.comments(post.id) });
  };

  const add = useMutation({
    mutationFn: (text: string) => createComment(post.id, text),
    onSuccess: () => {
      setBody('');
      setError(null);
      queryClient.setQueryData<PostDetail>(qk.post(post.id), (old) => (old ? { ...old, commentCount: old.commentCount + 1 } : old));
      refresh();
    },
    onError: (e) => {
      if (isApiError(e) && e.status === 401) return;
      setError(isApiError(e) && e.fields.body ? e.fields.body : errorMessage(e));
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteComment(id),
    onSuccess: () => {
      queryClient.setQueryData<PostDetail>(qk.post(post.id), (old) =>
        old ? { ...old, commentCount: Math.max(0, old.commentCount - 1) } : old,
      );
      refresh();
    },
    onError: (e) => toast.show(errorMessage(e), 'error'),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const text = body.trim();
    if (!text) return setError(SOCIAL.commentRequired);
    if (body.length > limits.commentMax) return setError(SOCIAL.commentTooLong(limits.commentMax));
    if (add.isPending) return;
    requireLogin(() => add.mutateAsync(body));
  };

  const count = q.data?.length ?? post.commentCount;

  return (
    <section className="comments" id="comments" aria-label="댓글">
      <h2 className="comments__title">
        댓글 <span className="comments__count">{count}</span>
      </h2>

      {q.isPending ? (
        <Loading />
      ) : q.error ? (
        <ErrorBox error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <p className="muted">아직 댓글이 없습니다</p>
      ) : (
        <ul className="comment-list">
          {q.data.map((c) => (
            <li key={c.id} id={`comment-${c.id}`} className="comment">
              <div className="comment__head">
                <span className={`comment__author${c.author ? '' : ' is-withdrawn'}`}>
                  {c.author ? c.author.nickname : SOCIAL.withdrawnUser}
                </span>
                <time className="comment__time" dateTime={c.createdAt}>
                  {formatDateTime(c.createdAt)}
                </time>
                {c.canDelete && (
                  <button
                    type="button"
                    className="link-btn comment__delete"
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm(SOCIAL.commentDeleteConfirm)) remove.mutate(c.id);
                    }}
                  >
                    삭제
                  </button>
                )}
              </div>
              <p className="comment__body">{c.body}</p>
            </li>
          ))}
        </ul>
      )}

      {me ? (
        <form className="comment-form" onSubmit={onSubmit}>
          <label htmlFor={inputId} className="sr-only">
            댓글 입력
          </label>
          <textarea
            id={inputId}
            className="input textarea"
            rows={3}
            maxLength={limits.commentMax}
            placeholder="댓글을 입력하세요"
            value={body}
            onChange={(e) => {
              setBody(e.target.value);
              setError(null);
            }}
          />
          <div className="comment-form__foot">
            <span className="counter">
              {body.length}/{limits.commentMax}
            </span>
            <button type="submit" className="btn btn--primary btn--sm" disabled={add.isPending}>
              댓글 등록
            </button>
          </div>
          <FieldError message={error} />
        </form>
      ) : (
        <div className="comment-login">
          <p>{SOCIAL.commentLoginRequired}</p>
          <button type="button" className="btn btn--sm" onClick={() => openLogin()}>
            로그인
          </button>
        </div>
      )}
    </section>
  );
}
