export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

/** The launcher UI language (set on <html lang> by I18nProvider), for Intl formatting. */
const locale = () => document.documentElement.lang || undefined;

/** "just now", "5 minutes ago", "3 hours ago", "2 days ago" (in the UI language), or a date. */
export function formatAgo(ms: number): string {
  const s = (Date.now() - ms) / 1000;
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' });
  if (s < 60) return rtf.format(0, 'second');
  if (s < 3600) return rtf.format(-Math.floor(s / 60), 'minute');
  if (s < 86400) return rtf.format(-Math.floor(s / 3600), 'hour');
  if (s < 86400 * 30) return rtf.format(-Math.floor(s / 86400), 'day');
  return new Date(ms).toLocaleDateString(locale(), { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDate(iso: string): string {
  // A bare `YYYY-MM-DD` means that calendar day, not midnight UTC (which is the day before in the Americas).
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  const date = day ? new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3])) : new Date(iso);
  return date.toLocaleDateString(locale(), { year: 'numeric', month: 'short', day: 'numeric' });
}
