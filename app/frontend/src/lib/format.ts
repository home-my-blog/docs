const TZ = 'Asia/Seoul';

function parts(iso: string, withTime: boolean): Record<string, string> | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const fmt = new Intl.DateTimeFormat('en-GB', {
    timeZone: TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' as const } : {}),
  });
  const out: Record<string, string> = {};
  for (const p of fmt.formatToParts(d)) out[p.type] = p.value;
  return out;
}

/** 2026.10.01 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const p = parts(iso, false);
  return p ? `${p.year}.${p.month}.${p.day}` : '';
}

/** 2026.10.01 14:03 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const p = parts(iso, true);
  return p ? `${p.year}.${p.month}.${p.day} ${p.hour}:${p.minute}` : '';
}

/** 14:03:05 */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: TZ,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).format(d);
}

/** 2026-10-01 → 10.01 (그래프 축) */
export function formatShortDay(date: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(date);
  return m ? `${m[2]}.${m[3]}` : date;
}

/** 잠금 해제까지 남은 분 (올림, 최소 1) */
export function minutesUntil(iso: string, now: number): number {
  const ms = new Date(iso).getTime() - now;
  if (Number.isNaN(ms)) return 10;
  return Math.max(1, Math.ceil(ms / 60_000));
}

/** 분류 색 점 (BM-04-3: 정해진 순서로 자동 배정) */
export const CATEGORY_COLORS = ['#f7c59f', '#a9cbef', '#a8dcc9', '#c7b8ea', '#f3e09a', '#f4b6c2', '#c5d6a0', '#d5dae2'];
export function categoryColor(index: number | undefined): string {
  const i = index ?? 0;
  return CATEGORY_COLORS[((i % CATEGORY_COLORS.length) + CATEGORY_COLORS.length) % CATEGORY_COLORS.length];
}
