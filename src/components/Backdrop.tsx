/** Ambient background: slow drifting brand-color glows over a faint grid. */
export function Backdrop() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
      <div
        className="orb -left-[10%] -top-[25%] h-[70vh] w-[70vh] bg-brand-a/25"
        style={{ animation: 'drift-a 26s ease-in-out infinite' }}
      />
      <div
        className="orb -right-[15%] top-[20%] h-[65vh] w-[65vh] bg-brand-b/20"
        style={{ animation: 'drift-b 32s ease-in-out infinite' }}
      />
      <div
        className="orb -bottom-[30%] left-[30%] h-[55vh] w-[55vh] bg-brand-c/12"
        style={{ animation: 'drift-a 38s ease-in-out infinite reverse' }}
      />
      <div className="grid-bg absolute inset-0" />
    </div>
  );
}
