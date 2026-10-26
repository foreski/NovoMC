/** Brand-gradient progress bar with a moving sheen. `value` null = indeterminate. */
export function ProgressBar({ value, tone = 'brand' }: { value: number | null; tone?: 'brand' | 'done' | 'error' }) {
  const fill =
    tone === 'done'
      ? 'from-play to-[#86efac]'
      : tone === 'error'
        ? 'from-danger to-[#fca5a5]'
        : 'from-brand-a via-brand-b to-brand-c';
  return (
    <div className="relative h-1.5 overflow-hidden rounded-full bg-white/8">
      <div
        className={`absolute inset-y-0 left-0 overflow-hidden rounded-full bg-gradient-to-r ${fill} transition-[width] duration-300 ease-out`}
        style={{ width: value === null ? '35%' : `${Math.max(value * 100, 2)}%`, animation: value === null ? 'indeterminate 1.2s ease-in-out infinite' : undefined }}
      >
        {tone === 'brand' && <span className="shine" style={{ animationDuration: '1.6s' }} />}
      </div>
    </div>
  );
}
