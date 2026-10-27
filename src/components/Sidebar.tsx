import { motion } from 'motion/react';
import { Boxes, Home, Layers, Package, Settings, UserCircle } from 'lucide-react';
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

/** The signed-in player, top right; opens Accounts. */
export function AccountButton({ onClick }: { onClick: () => void }) {
  const { active } = useAccounts();
  const t = useT();
  return (
    <button
      onClick={onClick}
      className="flex h-9 items-center gap-2 rounded-full border border-novo-border bg-novo-elev py-1 pr-3.5 pl-1 transition-colors hover:border-novo-border-strong hover:bg-novo-hover"
    >
      {active ? (
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
    </button>
  );
}
