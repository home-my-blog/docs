import { defaultUrlTransform, type UrlTransform } from 'react-markdown';

/**
 * 링크·이미지 주소 허용 목록 (research §6, NF-07).
 * - 이미지: http(s): 와 /api/images/ 만
 * - 링크: http(s):, mailto:, 같은 사이트 경로(/…, #…)만
 */
export const safeUrlTransform: UrlTransform = (url, key, node) => {
  const value = url.trim();
  const isImage = node.tagName === 'img' && key === 'src';
  if (isImage) {
    if (value.startsWith('/api/images/')) return value;
    return /^https?:\/\//i.test(value) ? defaultUrlTransform(value) : '';
  }
  if (/^(https?:|mailto:)/i.test(value)) return defaultUrlTransform(value);
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  if (value.startsWith('#')) return value;
  return '';
};
