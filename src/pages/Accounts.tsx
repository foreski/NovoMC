import { useEffect, useState, type MouseEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Check, Copy, Loader2, LogIn, MousePointerClick, Trash2 } from 'lucide-react';
import { Avatar } from '../components/Avatar';
import { NovoAccountCard } from '../components/NovoAccountCard';
import type { Account } from '../lib/api';
import { useAccounts } from '../lib/accounts';
import { useT } from '../lib/i18n';

const SPRING = { type: 'spring', stiffness: 420, damping: 32 } as const;

function MicrosoftCard() {
  const t = useT();
  const { signInMicrosoft } = useAccounts();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function signIn() {
    setBusy(true);
    setError(null);
    try {
      await signInMicrosoft();
    } catch (e) {
      const msg = String(e);
      // Closing the window isn't an error worth showing.
      if (!msg.includes('cancelled')) setError(msg);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="glass relative flex flex-col overflow-hidden rounded-3xl p-6">
      <div className="absolute -right-20 -bottom-20 h-56 w-56 rounded-full bg-brand-b/20 blur-3xl" />
      <span className="shine opacity-30" style={{ animationDuration: '6s' }} />
      <div className="relative flex items-start justify-between">
        <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-brand-b/40 to-brand-a/30 ring-1 ring-white/10">
          <LogIn size={22} className="text-white" />
        </div>
      </div>
      <h2 className="relative mt-4 text-xl font-bold">{t('acc.microsoft')}</h2>
      <p className="relative mt-1 flex-1 text-novo-muted">{t('acc.microsoftHint')}</p>
      {error && <p className="relative mt-3 text-xs text-danger">{error}</p>}
      <button
        onClick={signIn}
        disabled={busy}
        className="group relative mt-5 flex items-center justify-center gap-2 overflow-hidden rounded-2xl bg-gradient-to-r from-brand-b to-brand-a py-3 font-bold text-white shadow-[0_10px_30px_-10px] shadow-brand-b/70 transition-all hover:brightness-110 active:scale-[0.98] disabled:opacity-60"
      >
        {!busy && <span className="shine" />}
        {busy ? <Loader2 size={18} className="relative animate-spin" /> : <LogIn size={18} className="relative" />}
        <span className="relative">{busy ? t('acc.signingIn') : t('acc.signIn')}</span>
      </button>
    </div>
  );
}

function PlayerCard({ account, active }: { account: Account; active: boolean }) {
  const { select, remove } = useAccounts();
  const [confirming, setConfirming] = useState(false);
  const [copied, setCopied] = useState(false);
  const [spot, setSpot] = useState({ x: 0, y: 0 });
  const t = useT();

  useEffect(() => {
    if (!confirming) return;
    const timer = setTimeout(() => setConfirming(false), 3000);
    return () => clearTimeout(timer);
  }, [confirming]);

  function track(e: MouseEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    setSpot({ x: e.clientX - r.left, y: e.clientY - r.top });
  }

  async function copy(e: MouseEvent) {
    e.stopPropagation();
    await navigator.clipboard.writeText(account.uuid).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 1400);
  }

  function onRemove(e: MouseEvent) {
    e.stopPropagation();
    if (confirming) remove(account.uuid);
    else setConfirming(true);
  }

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.85, y: 20 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.8, filter: 'blur(6px)', transition: { duration: 0.2 } }}
      transition={SPRING}
      whileHover={active ? undefined : { y: -4 }}
      className={`rounded-3xl p-[1.5px] ${
        active ? 'gradient-border shadow-[0_0_50px_-12px_rgb(180_91_255/0.7)]' : 'bg-novo-border hover:bg-novo-border-strong'
      }`}
    >
      <div
        onMouseMove={track}
        onClick={() => !active && select(account.uuid)}
        className={`group relative h-full overflow-hidden rounded-[calc(1.5rem-1.5px)] bg-novo-panel p-5 ${active ? '' : 'cursor-pointer'}`}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
          style={{ background: `radial-gradient(260px circle at ${spot.x}px ${spot.y}px, rgb(255 255 255 / 0.07), transparent 60%)` }}
        />
        {active && <div className="absolute inset-0 bg-gradient-to-br from-brand-a/15 via-transparent to-brand-b/15" />}

        <div className="relative flex items-center gap-4">
          <div className="relative">
            <Avatar name={account.username} uuid={account.uuid} size={60} />
            {active && (
              <span className="absolute -right-1 -bottom-1 grid h-5 w-5 place-items-center rounded-full bg-play text-[#052e12] ring-4 ring-novo-panel">
                <Check size={12} strokeWidth={3} />
              </span>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-lg font-bold">{account.username}</div>
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
              <span className="rounded-md bg-novo-elev-2 px-2 py-0.5 text-[10px] font-bold tracking-wider text-novo-muted uppercase">
                {account.kind === 'offline' ? t('account.offline') : t('account.microsoft')}
              </span>
              {active ? (
                <span className="flex items-center gap-1.5 rounded-md bg-play/15 px-2 py-0.5 text-[10px] font-bold tracking-wider text-play uppercase">
                  <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-play" /> {t('acc.active')}
                </span>
              ) : (
                <span className="flex items-center gap-1 text-[11px] text-novo-muted opacity-0 transition-opacity group-hover:opacity-100">
                  <MousePointerClick size={12} /> {t('acc.clickSelect')}
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="relative mt-4 flex items-center gap-2">
          <button
            onClick={copy}
            title={t('acc.copyUuid')}
            className="flex min-w-0 flex-1 items-center gap-2 rounded-xl bg-novo-bg/70 px-3 py-2 font-mono text-[11px] text-novo-muted transition-colors hover:text-novo-text"
          >
            <span className="truncate">{account.uuid}</span>
            {copied ? <Check size={13} className="shrink-0 text-play" /> : <Copy size={13} className="shrink-0" />}
          </button>
          <motion.button
            layout
            onClick={onRemove}
            aria-label={t('acc.remove', { name: account.username })}
            className={`flex h-9 items-center justify-center rounded-xl text-sm font-semibold transition-colors ${
              confirming ? 'bg-danger/20 px-3 text-danger' : 'w-9 text-novo-muted hover:bg-danger/10 hover:text-danger'
            }`}
          >
            {confirming ? t('acc.removeConfirm') : <Trash2 size={16} />}
          </motion.button>
        </div>
      </div>
    </motion.div>
  );
}

export function AccountsPage() {
  const { accounts, active, error, refresh } = useAccounts();
  const t = useT();

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-8">
      <header>
        <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('home.players')}</p>
        <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('nav.accounts')}</h1>
        <p className="mt-2 text-novo-muted">
          {accounts.length === 0
            ? t('acc.getStarted')
            : `${t(accounts.length === 1 ? 'acc.onePlayer' : 'acc.players', { count: accounts.length })} · ${active ? t('acc.playingAs', { name: active.username }) : t('acc.noneSelected')}`}
        </p>
      </header>

      <MicrosoftCard />

      <NovoAccountCard />

      <section className="flex flex-col gap-3">
        <h2 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('acc.saved')}</h2>
        {error && <p className="text-danger">{error}</p>}
        {accounts.length === 0 ? (
          <div className="grid place-items-center rounded-3xl border border-dashed border-novo-border-strong py-14 text-center">
            <div className="flex -space-x-3" style={{ animation: 'float 5s ease-in-out infinite' }}>
              {['Nova', 'Pixel', 'Blaze'].map((n, i) => (
                <div key={n} className="rounded-xl ring-4 ring-novo-bg" style={{ opacity: 0.35 + i * 0.2 }}>
                  <Avatar name={n} size={44} />
                </div>
              ))}
            </div>
            <p className="mt-4 font-semibold">{t('acc.empty')}</p>
            <p className="text-novo-muted">{t('acc.emptyHint')}</p>
          </div>
        ) : (
          <motion.div layout className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <AnimatePresence mode="popLayout">
              {accounts.map((a) => (
                <PlayerCard key={a.uuid} account={a} active={a.uuid === active?.uuid} />
              ))}
            </AnimatePresence>
          </motion.div>
        )}
      </section>
    </div>
  );
}
