import { useEffect, useState } from 'react';
import { isTauri } from '@tauri-apps/api/core';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { Copy, Minus, Square, X } from 'lucide-react';
import { useT } from '../lib/i18n';
import logo from '../../assets/branding/novomc-logo.svg';
import { AccountButton, NavTabs, type PageId } from './Sidebar';

// Null when the UI runs in a plain browser (vite dev preview).
const win = isTauri() ? getCurrentWindow() : null;

/** Custom frameless title bar with the page tabs and the account. Empty space is a drag region. */
export function TitleBar({ page, onNavigate }: { page: PageId; onNavigate: (p: PageId) => void }) {
  const [maximized, setMaximized] = useState(false);
  const t = useT();

  useEffect(() => {
    if (!win) return;
    win.isMaximized().then(setMaximized);
    const unlisten = win.onResized(() => win.isMaximized().then(setMaximized));
    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  const btn = 'grid h-full w-12 place-items-center text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text';

  return (
    <header
      data-tauri-drag-region
      className="relative flex h-14 shrink-0 items-center gap-4 bg-novo-panel/50 backdrop-blur-xl"
    >
      <div data-tauri-drag-region className="flex shrink-0 items-center gap-2.5 pl-3.5">
        <img src={logo} alt="" className="pointer-events-none h-5 w-5 drop-shadow-[0_0_8px_rgb(180_91_255/0.6)]" />
        {/* The wordmark gives way to the page tabs on narrower windows. */}
        <span data-tauri-drag-region className="hidden text-[13px] font-bold tracking-[0.12em] whitespace-nowrap xl:inline">
          NOVO <span className="text-accent">CLIENT</span>
        </span>
      </div>
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-brand-b/50 to-transparent" />
      <NavTabs page={page} onChange={onNavigate} />
      <div data-tauri-drag-region className="flex-1 self-stretch" />
      <AccountButton onClick={() => onNavigate('accounts')} />
      <div className="flex h-full shrink-0">
        <button className={btn} onClick={() => win?.minimize()} aria-label={t('title.minimize')}>
          <Minus size={15} />
        </button>
        <button className={btn} onClick={() => win?.toggleMaximize()} aria-label={t('title.maximize')}>
          {maximized ? <Copy size={13} className="-scale-x-100" /> : <Square size={13} />}
        </button>
        <button
          className={`${btn} hover:!bg-[#c42b1c] hover:!text-white`}
          onClick={() => win?.close()}
          aria-label={t('title.close')}
        >
          <X size={16} />
        </button>
      </div>
    </header>
  );
}
