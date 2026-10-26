import { motion } from 'motion/react';
import { Boxes, ChevronRight, Home, Layers, Package, Settings, UserCircle } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { useAccounts } from '../lib/accounts';
import { useT, type Key } from '../lib/i18n';
import { RingAvatar } from './Avatar';

export type PageId = 'home' | 'instances' | 'versions' | 'mods' | 'settings' | 'accounts';

const items: { id: PageId; label: Key; icon: LucideIcon }[] = [
  { id: 'home', label: 'nav.home', icon: Home },
  { id: 'instances', label: 'nav.instances', icon: Boxes },
  { id: 'versions', label: 'nav.versions', icon: Layers },
  { id: 'mods', label: 'nav.mods', icon: Package },
  { id: 'settings', label: 'nav.settings', icon: Settings },
  { id: 'accounts', label: 'nav.accounts', icon: UserCircle },
];

export function Sidebar({ page, onChange }: { page: PageId; onChange: (p: PageId) => void }) {
  const { active } = useAccounts();
  const t = useT();

  return (
    <nav className="flex w-56 shrink-0 flex-col gap-1 border-r border-novo-border bg-novo-panel/40 p-3 backdrop-blur-xl">
      {items.map(({ id, label, icon: Icon }) => {
        const selected = id === page;
        return (
          <button
            key={id}
            onClick={() => onChange(id)}
            className={`relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-left font-medium transition-colors ${
              selected ? 'text-novo-text' : 'text-novo-muted hover:bg-novo-hover hover:text-novo-text'
            }`}
          >
            {selected && (
              <motion.span
                layoutId="nav-pill"
                className="absolute inset-0 rounded-xl bg-gradient-to-r from-brand-a/25 via-brand-b/10 to-transparent ring-1 ring-white/10"
                transition={{ type: 'spring', stiffness: 500, damping: 38 }}
              >
                <span className="absolute top-1/2 -left-3 h-5 w-1 -translate-y-1/2 rounded-r-full bg-gradient-to-b from-brand-a to-brand-b shadow-[0_0_12px_2px_rgb(120_120_255/0.6)]" />
              </motion.span>
            )}
            <Icon
              size={18}
              className={`relative transition-[filter,color] ${selected ? 'text-accent drop-shadow-[0_0_6px_rgb(106_162_255/0.8)]' : ''}`}
            />
            <span className="relative">{t(label)}</span>
          </button>
        );
      })}

      <button
        onClick={() => onChange('accounts')}
        className="group mt-auto flex items-center gap-3 rounded-2xl border border-novo-border bg-novo-elev p-2.5 text-left transition-all hover:border-novo-border-strong hover:bg-novo-hover"
      >
        {active ? (
          <>
            <RingAvatar name={active.username} size={34} />
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold">{active.username}</div>
              <div className="flex items-center gap-1.5 text-xs text-novo-muted">
                <span className="h-1.5 w-1.5 rounded-full bg-play shadow-[0_0_6px_var(--color-play)]" />
                {active.kind === 'offline' ? t('account.offline') : t('account.microsoft')}
              </div>
            </div>
          </>
        ) : (
          <>
            <div className="grid h-9 w-9 place-items-center rounded-xl border border-dashed border-novo-border-strong text-novo-muted">
              <UserCircle size={18} />
            </div>
            <span className="flex-1 text-novo-muted">{t('nav.addPlayer')}</span>
          </>
        )}
        <ChevronRight size={16} className="text-novo-muted transition-transform group-hover:translate-x-0.5" />
      </button>
    </nav>
  );
}
