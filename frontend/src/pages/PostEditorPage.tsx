import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState, type ChangeEvent, type FormEvent, type KeyboardEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { errorMessage, isApiError, isNotFound } from '../api/client';
import { createPost, getCategories, getLastCategory, getPostForEdit, updatePost, uploadImage } from '../api/endpoints';
import { qk, useConfig } from '../api/queries';
import type { Category, MeResponse, PostSaveRequest, UploadedImage, Visibility } from '../api/types';
import { MarkdownView } from '../components/MarkdownView';
import { NotFound } from '../components/NotFound';
import { RequireLogin } from '../components/RequireLogin';
import { ErrorBox, FieldError, FormMessage, Loading } from '../components/Status';
import { ALLOWED_IMAGE_TYPES } from '../lib/config';
import { useUnsavedChangesPrompt } from '../lib/useUnsavedChangesPrompt';
import { isValidTag, normalizeTag } from '../lib/validation';
import { POST, SOCIAL } from '../messages';

export function PostEditorPage() {
  const { postId } = useParams();
  return (
    <RequireLogin>
      {(me) => (postId ? <EditLoader me={me} postId={postId} /> : <CreateLoader me={me} />)}
    </RequireLogin>
  );
}

function NoBlog() {
  return <NotFound what="블로그" />;
}

function CreateLoader({ me }: { me: MeResponse }) {
  const blogId = me.blog?.id;
  const cats = useQuery({
    queryKey: qk.categories(blogId ?? 0),
    queryFn: () => getCategories(blogId as number),
    enabled: !!blogId,
  });
  const last = useQuery({ queryKey: qk.lastCategory, queryFn: getLastCategory, enabled: !!blogId, retry: false });
  if (!blogId) return <NoBlog />;
  if (cats.isPending || last.isPending) return <Loading />;
  if (cats.error) return <ErrorBox error={cats.error} onRetry={() => void cats.refetch()} />;

  const categories = cats.data;
  const lastId = last.data?.categoryId;
  const defaultCat =
    categories.find((c) => c.id === lastId) ?? categories.find((c) => c.isDefault) ?? categories[0] ?? null;

  return (
    <EditorForm
      mode="create"
      blogId={blogId}
      categories={categories}
      initial={{
        title: '',
        body: '',
        categoryId: defaultCat?.id ?? null,
        visibility: 'PUBLIC',
        tags: [],
        images: [],
        coverImageId: null,
      }}
    />
  );
}

function EditLoader({ me, postId }: { me: MeResponse; postId: string }) {
  const blogId = me.blog?.id;
  const src = useQuery({ queryKey: qk.postEdit(postId), queryFn: () => getPostForEdit(postId), retry: false, staleTime: Infinity });
  const cats = useQuery({
    queryKey: qk.categories(blogId ?? 0),
    queryFn: () => getCategories(blogId as number),
    enabled: !!blogId,
  });
  if (src.isPending || (blogId && cats.isPending)) return <Loading />;
  if (isNotFound(src.error) || (src.error && isApiError(src.error) && src.error.status === 403)) return <NotFound what="글" />;
  if (src.error) return <ErrorBox error={src.error} onRetry={() => void src.refetch()} />;
  if (!blogId) return <NoBlog />;
  if (cats.error) return <ErrorBox error={cats.error} onRetry={() => void cats.refetch()} />;

  const s = src.data;
  return (
    <EditorForm
      key={s.id}
      mode="edit"
      postId={s.id}
      blogId={blogId}
      categories={cats.data ?? []}
      initial={{
        title: s.title,
        body: s.body,
        categoryId: s.categoryId,
        visibility: s.visibility,
        tags: s.tags ?? [],
        images: s.images ?? [],
        coverImageId: s.coverImageId ?? null,
      }}
    />
  );
}

interface EditorInitial {
  title: string;
  body: string;
  categoryId: number | null;
  visibility: Visibility;
  tags: string[];
  images: UploadedImage[];
  coverImageId: number | null;
}

interface EditorFormProps {
  mode: 'create' | 'edit';
  postId?: number;
  blogId: number;
  categories: Category[];
  initial: EditorInitial;
}

type FieldKey = 'title' | 'body' | 'categoryId' | 'tags';

/** 글쓰기·수정 (CF-05, CF-13-6, CF-20, CF-22) */
function EditorForm({ mode, postId, blogId, categories, initial }: EditorFormProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { limits } = useConfig();
  const ids = { title: useId(), body: useId(), category: useId(), tag: useId(), file: useId() };
  const titleRef = useRef<HTMLInputElement>(null);
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const [title, setTitle] = useState(initial.title);
  const [body, setBody] = useState(initial.body);
  const [categoryId, setCategoryId] = useState<number | null>(initial.categoryId);
  const [visibility, setVisibility] = useState<Visibility>(initial.visibility);
  const [tags, setTags] = useState<string[]>(initial.tags);
  const [tagInput, setTagInput] = useState('');
  const [images, setImages] = useState<UploadedImage[]>(initial.images);
  const [coverImageId, setCoverImageId] = useState<number | null>(initial.coverImageId);
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const [errors, setErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [tagError, setTagError] = useState<string | null>(null);
  const [imageError, setImageError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const busy = useRef(false);

  const dirty =
    title !== initial.title ||
    body !== initial.body ||
    categoryId !== initial.categoryId ||
    visibility !== initial.visibility ||
    tags.join('\u0000') !== initial.tags.join('\u0000') ||
    coverImageId !== initial.coverImageId ||
    tagInput.trim() !== '';
  const allowNavigation = useUnsavedChangesPrompt(dirty);

  const usedImages = images.filter((img) => body.includes(img.url));

  /* ---------- 태그 ---------- */
  const addTag = (raw: string): boolean => {
    const tag = normalizeTag(raw);
    if (!tag) return true;
    if (!isValidTag(tag, limits.tagMax)) {
      setTagError(POST.tagRule(limits.tagMax));
      return false;
    }
    if (tags.some((t) => t.toLowerCase() === tag.toLowerCase())) {
      setTagError(POST.tagDuplicate);
      return false;
    }
    if (tags.length >= limits.tagsPerPost) {
      setTagError(POST.tagLimit(limits.tagsPerPost));
      return false;
    }
    setTags([...tags, tag]);
    setTagError(null);
    return true;
  };
  const onTagKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      if (addTag(tagInput)) setTagInput('');
    } else if (e.key === 'Backspace' && tagInput === '' && tags.length > 0) {
      setTags(tags.slice(0, -1));
    }
  };

  /* ---------- 이미지 ---------- */
  const insertAtCursor = (snippet: string) => {
    const el = bodyRef.current;
    const start = el?.selectionStart ?? body.length;
    const end = el?.selectionEnd ?? body.length;
    const before = body.slice(0, start);
    const after = body.slice(end);
    const pre = before && !before.endsWith('\n') ? '\n' : '';
    const text = `${pre}${snippet}\n`;
    setBody(before + text + after);
    requestAnimationFrame(() => {
      if (!el) return;
      const pos = before.length + text.length;
      el.focus();
      el.setSelectionRange(pos, pos);
    });
  };

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!ALLOWED_IMAGE_TYPES.includes(file.type) || file.size > limits.imageMaxBytes) {
      setImageError(SOCIAL.imageRule);
      return;
    }
    if (usedImages.length >= limits.imagesPerPost) {
      setImageError(POST.imageLimit(limits.imagesPerPost));
      return;
    }
    setUploading(true);
    setImageError(null);
    try {
      const img = await uploadImage(file);
      setImages((list) => [...list, img]);
      insertAtCursor(`![](${img.url})`);
    } catch (err) {
      setImageError(errorMessage(err));
    } finally {
      setUploading(false);
    }
  };

  /* ---------- 공개 여부 ---------- */
  const onVisibility = (next: Visibility) => {
    if (next === visibility) return;
    if (visibility === 'PRIVATE' && next === 'PUBLIC' && !window.confirm(POST.toPublicConfirm)) return;
    setVisibility(next);
  };

  /* ---------- 저장 ---------- */
  const validate = (): Partial<Record<FieldKey, string>> => {
    const e: Partial<Record<FieldKey, string>> = {};
    const t = title.trim();
    if (!t) e.title = POST.titleRequired;
    else if (t.length > limits.postTitleMax) e.title = POST.titleTooLong(limits.postTitleMax);
    if (!body.trim()) e.body = POST.bodyRequired;
    else if (body.length > limits.postBodyMax) e.body = POST.bodyTooLong(limits.postBodyMax);
    if (categoryId === null) e.categoryId = POST.categoryRequired;
    return e;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (busy.current) return; // 연타 방지 (CF-05-11)
    let finalTags = tags;
    if (tagInput.trim()) {
      const tag = normalizeTag(tagInput);
      if (isValidTag(tag, limits.tagMax) && !tags.some((t) => t.toLowerCase() === tag.toLowerCase()) && tags.length < limits.tagsPerPost) {
        finalTags = [...tags, tag];
        setTags(finalTags);
        setTagInput('');
      }
    }
    const v = validate();
    setErrors(v);
    if (v.title) {
      setTab('write');
      return titleRef.current?.focus();
    }
    if (v.body) {
      setTab('write');
      requestAnimationFrame(() => bodyRef.current?.focus());
      return;
    }
    if (v.categoryId) return;

    const imageIds = usedImages.map((i) => i.id);
    const req: PostSaveRequest = {
      title: title.trim(),
      body,
      categoryId: categoryId as number,
      visibility,
      tags: finalTags,
      imageIds,
      coverImageId: coverImageId !== null && imageIds.includes(coverImageId) ? coverImageId : null,
    };

    busy.current = true;
    setSaving(true);
    setFormError(null);
    try {
      let id: number;
      if (mode === 'edit' && postId !== undefined) {
        await updatePost(postId, req);
        id = postId;
      } else {
        id = (await createPost(blogId, req)).id;
      }
      void queryClient.invalidateQueries({ queryKey: ['blog'] });
      void queryClient.invalidateQueries({ queryKey: ['post', String(id)] });
      void queryClient.invalidateQueries({ queryKey: ['manage'] });
      void queryClient.invalidateQueries({ queryKey: qk.lastCategory });
      void queryClient.invalidateQueries({ queryKey: qk.home });
      allowNavigation();
      navigate(`/posts/${id}`, { replace: mode === 'edit' });
    } catch (err) {
      // 저장에 실패해도 입력한 내용은 그대로 둔다 (CF-05-8)
      if (isNotFound(err)) {
        setFormError(POST.notFound);
      } else if (isApiError(err) && Object.keys(err.fields).length > 0) {
        const f = err.fields;
        setErrors({ title: f.title, body: f.body, categoryId: f.categoryId, tags: f.tags });
        if (!f.title && !f.body && !f.categoryId && !f.tags) setFormError(errorMessage(err));
      } else {
        setFormError(errorMessage(err));
      }
    } finally {
      busy.current = false;
      setSaving(false);
    }
  };

  return (
    <form className="editor" onSubmit={onSubmit} noValidate>
      <div className="editor__head">
        <h1 className="page-title">{mode === 'edit' ? '글 수정' : '글쓰기'}</h1>
        <div className="editor__actions">
          <button type="button" className="btn btn--ghost" onClick={() => navigate(-1)}>
            취소
          </button>
          <button type="submit" className="btn btn--primary" disabled={saving || uploading}>
            {saving ? '저장하는 중…' : '저장'}
          </button>
        </div>
      </div>

      <div className="editor__meta">
        <div className="field">
          <label htmlFor={ids.category} className="field__label">
            분류
          </label>
          <select
            id={ids.category}
            className="input select"
            value={categoryId ?? ''}
            onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : null)}
          >
            {categoryId === null && <option value="">분류 선택</option>}
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
          <FieldError message={errors.categoryId} />
        </div>
        <fieldset className="field radio-group radio-group--inline">
          <legend className="field__label">공개 여부</legend>
          <label className="radio">
            <input type="radio" name="visibility" checked={visibility === 'PUBLIC'} onChange={() => onVisibility('PUBLIC')} />
            공개
          </label>
          <label className="radio">
            <input type="radio" name="visibility" checked={visibility === 'PRIVATE'} onChange={() => onVisibility('PRIVATE')} />
            비공개
          </label>
        </fieldset>
      </div>

      <div className="field">
        <label htmlFor={ids.title} className="sr-only">
          제목
        </label>
        <input
          id={ids.title}
          ref={titleRef}
          className="input editor__title"
          placeholder="제목을 입력하세요"
          value={title}
          maxLength={limits.postTitleMax + 20}
          aria-invalid={!!errors.title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <div className="field__foot">
          <FieldError message={errors.title} />
          <span className="counter">
            {title.trim().length}/{limits.postTitleMax}
          </span>
        </div>
      </div>

      <div className="editor__tabs tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'write'} className={`tabs__tab${tab === 'write' ? ' is-active' : ''}`} onClick={() => setTab('write')}>
          작성
        </button>
        <button type="button" role="tab" aria-selected={tab === 'preview'} className={`tabs__tab${tab === 'preview' ? ' is-active' : ''}`} onClick={() => setTab('preview')}>
          미리보기
        </button>
        <div className="editor__tools">
          <input
            id={ids.file}
            ref={fileRef}
            type="file"
            accept={ALLOWED_IMAGE_TYPES.join(',')}
            className="sr-only"
            onChange={(e) => void onFile(e)}
          />
          <button type="button" className="btn btn--sm btn--outline" disabled={uploading} onClick={() => fileRef.current?.click()}>
            {uploading ? '올리는 중…' : '이미지 올리기'}
          </button>
        </div>
      </div>

      {tab === 'write' ? (
        <div className="field">
          <label htmlFor={ids.body} className="sr-only">
            본문 (마크다운)
          </label>
          <textarea
            id={ids.body}
            ref={bodyRef}
            className="input textarea editor__body"
            placeholder="본문을 마크다운으로 입력하세요"
            value={body}
            aria-invalid={!!errors.body}
            onChange={(e) => setBody(e.target.value)}
          />
        </div>
      ) : (
        <div className="editor__preview card">
          {body.trim() ? <MarkdownView source={body} /> : <p className="muted">미리 볼 내용이 없습니다</p>}
        </div>
      )}
      <div className="field__foot">
        <FieldError message={errors.body} />
        <span className={`counter${body.length > limits.postBodyMax ? ' is-over' : ''}`}>
          {body.length.toLocaleString()}/{limits.postBodyMax.toLocaleString()}
        </span>
      </div>
      <FormMessage kind="error" message={imageError} />

      {usedImages.length > 0 && (
        <fieldset className="field cover-picker">
          <legend className="field__label">
            대표 사진 <span className="muted">({usedImages.length}/{limits.imagesPerPost})</span>
          </legend>
          <div className="cover-picker__list">
            <label className={`cover-picker__item cover-picker__none${coverImageId === null ? ' is-selected' : ''}`}>
              <input type="radio" name="cover" checked={coverImageId === null} onChange={() => setCoverImageId(null)} />
              없음
            </label>
            {usedImages.map((img) => (
              <label key={img.id} className={`cover-picker__item${coverImageId === img.id ? ' is-selected' : ''}`}>
                <input type="radio" name="cover" checked={coverImageId === img.id} onChange={() => setCoverImageId(img.id)} />
                <img src={img.url} alt="" />
              </label>
            ))}
          </div>
        </fieldset>
      )}

      <div className="field">
        <label htmlFor={ids.tag} className="field__label">
          태그 <span className="muted">({tags.length}/{limits.tagsPerPost})</span>
        </label>
        <div className="tag-input">
          {tags.map((t) => (
            <span key={t} className="tag tag--chip">
              #{t}
              <button type="button" aria-label={`${t} 태그 지우기`} onClick={() => setTags(tags.filter((x) => x !== t))}>
                ×
              </button>
            </span>
          ))}
          <input
            id={ids.tag}
            className="tag-input__field"
            placeholder={tags.length >= limits.tagsPerPost ? '' : '태그 입력 후 Enter'}
            value={tagInput}
            disabled={tags.length >= limits.tagsPerPost}
            onChange={(e) => {
              setTagInput(e.target.value);
              setTagError(null);
            }}
            onKeyDown={onTagKey}
            onBlur={() => {
              if (tagInput.trim() && addTag(tagInput)) setTagInput('');
            }}
          />
        </div>
        <FieldError message={tagError ?? errors.tags} />
      </div>

      <FormMessage kind="error" message={formError} />
      <div className="editor__bottom">
        <button type="submit" className="btn btn--primary" disabled={saving || uploading}>
          {saving ? '저장하는 중…' : '저장'}
        </button>
      </div>
    </form>
  );
}
