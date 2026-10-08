import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Pagination } from './Pagination';

describe('Pagination (CF-10-3)', () => {
  it('페이지가 하나면 그리지 않는다', () => {
    const { container } = render(<Pagination page={1} totalPages={1} onChange={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('현재 페이지를 강조하고 첫 페이지에서 이전 버튼을 막는다', () => {
    render(<Pagination page={1} totalPages={2} onChange={() => undefined} />);
    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '2' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeEnabled();
  });

  it('마지막 페이지에서 다음 버튼을 막고, 번호를 누르면 그 페이지로', () => {
    const onChange = vi.fn();
    render(<Pagination page={3} totalPages={3} onChange={onChange} />);
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '1' }));
    expect(onChange).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }));
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('현재 페이지 번호를 다시 눌러도 이동하지 않는다', () => {
    const onChange = vi.fn();
    render(<Pagination page={2} totalPages={3} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: '2' }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
