/** Animated on/off switch. */
export function Switch({
  on,
  onChange,
  label,
  size = 'md',
  disabled,
}: {
  on: boolean;
  onChange: (on: boolean) => void;
  label: string;
  size?: 'sm' | 'md';
  disabled?: boolean;
}) {
  const [w, h, knob] = size === 'sm' ? ['w-9', 'h-5', 'h-4 w-4'] : ['w-11', 'h-6', 'h-5 w-5'];
  const travel = size === 'sm' ? 'translate-x-4' : 'translate-x-5';
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={disabled}
      onClick={(e) => {
        e.stopPropagation();
        onChange(!on);
      }}
      className={`relative shrink-0 rounded-full p-0.5 transition-colors disabled:opacity-40 ${w} ${h} ${
        on ? 'bg-accent shadow-[0_0_14px_-4px_var(--color-accent)]' : 'bg-novo-elev-2'
      }`}
    >
      <span className={`block rounded-full bg-white shadow transition-transform duration-200 ${knob} ${on ? travel : 'translate-x-0'}`} />
    </button>
  );
}
