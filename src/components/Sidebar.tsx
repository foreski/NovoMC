import { motion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, Loader2, Plus, Boxes, Home, Shirt, Store, UserCircle } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAccounts } from '../lib/accounts';
import { useNovo } from '../lib/novo';
import { useT, type Key } from '../lib/i18n';
import { RingAvatar } from './Avatar';

const BANNER_URLS = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/banners/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;

export type PageId = 'home' | 'instances' | 'marketplace' | 'skins' | 'settings';

const items: { id: PageId; label: Key; icon: LucideIcon }[] = [
  { id: 'home', label: 'nav.home', icon: Home },
  { id: 'instances', label: 'nav.instances', icon: Boxes },
  { id: 'marketplace', label: 'nav.marketplace', icon: Store },
  { id: 'skins', label: 'nav.skins', icon: Shirt },
];

/**
 * Page tabs for the title bar. Below 1024px only the selected tab keeps its
 * label, so the bar fits the window's minimum width.
 */
export function NavTabs({ page, onChange }: { page: PageId; onChange: (p: PageId) => void }) {
  const t = useT();
  return (
    <nav className="flex h-full items-center gap-1">
      {items.map(({ id, label, icon: Icon }) => {
        const selected = id === page;
        return (
          <button
            key={id}
            onClick={() => onChange(id)}
            title={t(label)}
            className={`relative flex h-9 items-center gap-2 rounded-xl px-3 text-sm font-semibold whitespace-nowrap transition-colors ${
              selected ? 'text-novo-text' : 'text-novo-muted hover:bg-novo-hover hover:text-novo-text'
            }`}
          >
            {selected && (
              <motion.span
                layoutId="nav-pill"
                className="absolute inset-0 rounded-xl bg-gradient-to-b from-brand-a/25 to-brand-b/15 ring-1 ring-white/10"
                transition={{ type: 'spring', stiffness: 500, damping: 38 }}
              >
                <span className="absolute -bottom-[7px] left-1/2 h-0.5 w-6 -translate-x-1/2 rounded-full bg-gradient-to-r from-brand-a to-brand-b shadow-[0_0_10px_2px_rgb(120_120_255/0.6)]" />
              </motion.span>
            )}
            <Icon
              size={16}
              className={`relative transition-[filter,color] ${selected ? 'text-accent drop-shadow-[0_0_6px_rgb(106_162_255/0.8)]' : ''}`}
            />
            <span className={`relative ${selected ? '' : 'hidden lg:inline'}`}>{t(label)}</span>
          </button>
        );
      })}
    </nav>
  );
}

/** Profile menu for switching or adding Microsoft accounts. */
export function AccountButton({ onSettings }: { onSettings: () => void }) {
  const { accounts, active, select, signInMicrosoft, remove } = useAccounts();
  const { account: novo } = useNovo();
  const t = useT();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const root = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', escape); };
  }, [open]);
  async function addAccount() {
    setBusy(true); setError(null);
    try { await signInMicrosoft(); setOpen(false); }
    catch (e) { if (!String(e).includes('cancelled')) setError(String(e)); }
    finally { setBusy(false); }
  }
  return (
    <div ref={root} className="relative">
    <button aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((v) => !v)} className="relative flex h-9 items-center gap-2 overflow-hidden rounded-full border border-novo-border bg-novo-elev py-1 pr-3.5 pl-1 transition-colors hover:border-novo-border-strong hover:bg-novo-hover">
      {novo?.banner && <span aria-hidden className="pointer-events-none absolute inset-0 opacity-35" style={{ backgroundImage: `linear-gradient(90deg,rgba(11,14,20,.45),rgba(11,14,20,.9)),url(${Object.entries(BANNER_URLS).find(([path]) => path.endsWith(`/${novo.banner}.png`))?.[1] ?? ''})`, backgroundSize: 'cover', backgroundPosition: 'center' }} />}
      {novo ? (
        <>
          {novo.avatarUrl ? <img src={novo.avatarUrl} alt="" className="relative h-[26px] w-[26px] rounded-full" /> : <span className="relative grid h-7 w-7 place-items-center rounded-full bg-brand-a/30 text-xs font-bold">{novo.displayName.slice(0, 1)}</span>}
          <span className="relative max-w-32 truncate text-sm font-semibold">{novo.displayName}</span>
          <span className="relative h-1.5 w-1.5 rounded-full bg-play shadow-[0_0_6px_var(--color-play)]" />
        </>
      ) : active ? (
        <>
          <RingAvatar name={active.username} uuid={active.uuid} size={26} />
          <span className="max-w-32 truncate text-sm font-semibold">{active.username}</span>
          <span className="h-1.5 w-1.5 rounded-full bg-play shadow-[0_0_6px_var(--color-play)]" />
        </>
      ) : (
        <>
          <span className="grid h-7 w-7 place-items-center rounded-full border border-dashed border-novo-border-strong text-novo-muted">
            <UserCircle size={15} />
          </span>
          <span className="text-sm text-novo-muted">{t('nav.addPlayer')}</span>
        </>
      )}
      <ChevronDown size={14} className="relative text-novo-muted" />
    </button>
    {open && <div role="menu" className="absolute right-0 top-full z-50 mt-2 w-80 overflow-hidden rounded-2xl border border-novo-border bg-novo-panel shadow-2xl shadow-black/50">
      <div className="border-b border-novo-border px-4 py-3"><p className="text-xs font-bold uppercase tracking-wider text-novo-muted">{t('novo.linked')}</p></div>
      <div className="max-h-64 overflow-y-auto p-2">
        {accounts.filter((a) => a.kind === 'microsoft').map((a) => <div key={a.uuid} className="flex items-center gap-3 rounded-xl px-2 py-2 hover:bg-novo-hover">
          <RingAvatar name={a.username} uuid={a.uuid} size={30} />
          <button role="menuitem" onClick={() => { void select(a.uuid); setOpen(false); }} className="min-w-0 flex-1 truncate text-left text-sm font-semibold">{a.username}</button>
          {active?.uuid === a.uuid && <Check size={16} className="text-play" />}
          <button onClick={() => void remove(a.uuid)} aria-label={t('acc.remove', { name: a.username })} title={t('acc.remove', { name: a.username })} className="rounded-lg px-2 py-1 text-xs text-novo-muted hover:bg-danger/10 hover:text-danger">×</button>
        </div>)}
        {accounts.filter((a) => a.kind === 'microsoft').length === 0 && <p className="px-3 py-4 text-sm text-novo-muted">{t('nav.addPlayer')}</p>}
      </div>
      {error && <p className="px-4 pb-2 text-xs text-danger">{error}</p>}
      <div className="border-t border-novo-border p-2">
        <button role="menuitem" disabled={busy} onClick={() => void addAccount()} className="flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-accent hover:bg-novo-hover disabled:opacity-60">{busy ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}{busy ? t('acc.signingIn') : t('acc.signIn')}</button>
        <button role="menuitem" onClick={() => { setOpen(false); onSettings(); }} className="w-full rounded-xl px-3 py-2.5 text-left text-sm text-novo-muted hover:bg-novo-hover hover:text-novo-text">{t('nav.settings')} · {t('novo.title')}</button>
      </div>
    </div>}
    </div>
  );
}
