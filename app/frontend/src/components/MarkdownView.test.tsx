import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MarkdownView } from './MarkdownView';

describe('MarkdownView (NF-07)', () => {
  it('원시 HTML(<script>, onerror)을 그리지 않는다', () => {
    const src = 'hello\n\n<script>window.__xss = 1</script>\n\n<img src=x onerror="alert(1)">\n\n<b>bold</b>';
    const { container } = render(<MarkdownView source={src} />);
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
    expect(container.innerHTML).not.toContain('onerror');
    expect(container.textContent).toContain('hello');
  });

  it('javascript: 링크와 허용되지 않은 이미지 주소를 지운다', () => {
    const src = '[click](javascript:alert(1)) ![a](data:image/png;base64,AAAA) ![b](/api/images/abc.png) [ok](https://example.com)';
    const { container } = render(<MarkdownView source={src} />);
    const links = Array.from(container.querySelectorAll('a'));
    expect(links.some((a) => (a.getAttribute('href') ?? '').startsWith('javascript'))).toBe(false);
    const ok = links.find((a) => a.textContent === 'ok');
    expect(ok).toHaveAttribute('href', 'https://example.com');
    expect(ok).toHaveAttribute('rel', 'noopener noreferrer nofollow');
    const imgs = Array.from(container.querySelectorAll('img')).map((i) => i.getAttribute('src'));
    expect(imgs).toEqual(['/api/images/abc.png']);
  });

  it('GFM 표와 취소선을 그린다', () => {
    const { container } = render(<MarkdownView source={'| a | b |\n|---|---|\n| 1 | 2 |\n\n~~old~~'} />);
    expect(container.querySelector('table')).not.toBeNull();
    expect(container.querySelector('del')?.textContent).toBe('old');
  });
});
