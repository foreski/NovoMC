// The Novo account on the Accounts page: Discord sign-in, the Novo name,
// linked Minecraft accounts, the cape picker and settings sync.
import { useCallback, useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Check, Cloud, Link2, Loader2, Lock, LogOut, Pencil, Sparkles, Unlink, X } from 'lucide-react';
import { api, type NovoAccount, type Settings } from '../lib/api';
import { useAccounts } from '../lib/accounts';
import { useNovo } from '../lib/novo';
import { useT } from '../lib/i18n';
import { Switch } from './Switch';

// The same textures the clients draw (scripts/make-capes.mjs).
const CAPE_URLS = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/capes/*.png', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;
const BANNER_URLS = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/banners/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;

function capeUrl(id: string): string | undefined {
  return Object.entries(CAPE_URLS).find(([path]) => path.endsWith(`/${id}.png`))?.[1];
}
function bannerUrl(id: string | null): string | undefined {
  return id ? Object.entries(BANNER_URLS).find(([path]) => path.endsWith(`/${id}.png`))?.[1] : undefined;
}

/** The outer side of a cape texture (10x16 at 1,1 of 64x32), scaled up crisp. */
export function CapePreview({ id, scale = 5 }: { id: string; scale?: number }) {
  const url = capeUrl(id);
  return (
    <div
      className="rounded-sm shadow-lg shadow-black/40"
      style={{
        width: 10 * scale,
        height: 16 * scale,
        backgroundImage: url ? `url(${url})` : undefined,
        backgroundSize: `${64 * scale}px ${32 * scale}px`,
        backgroundPosition: `${-scale}px ${-scale}px`,
        imageRendering: 'pixelated',
      }}
    />
  );
}

function DiscordIcon({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.3 18.3 0 0 0-5.6 0L8.6 3a19.7 19.7 0 0 0-4.9 1.4C.6 9 0 13.6.3 18.1a19.9 19.9 0 0 0 6 3l1.3-2a12.9 12.9 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.8 19.8 0 0 0 6-3c.5-5.2-.8-9.7-3.8-13.7ZM8.5 15.4c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Zm7 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Z" />
    </svg>
  );
}

export function NovoAccountCard({ onMarketplace }: { onMarketplace?: () => void }) {
  const t = useT();
  const { accounts } = useAccounts();
  const { account, update, signIn, signOut } = useNovo();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [settings, setSettings] = useState<Settings | null>(null);

  useEffect(() => {
    api.getSettings().then(setSettings, () => {});
  }, []);

  /** Runs an account call, showing a spinner on `what` and any error. */
  const run = useCallback(async (what: string, call: () => Promise<NovoAccount | null | void>) => {
    setBusy(what);
    setError(null);
    try {
      const next = await call();
      if (next !== undefined && next !== null) update(next);
    } catch (e) {
      const msg = String(e);
      if (!msg.includes('cancelled')) setError(msg);
      // A lapsed session signs us out.
      if (msg.includes('signed out')) await signOut();
    } finally {
      setBusy(null);
    }
  }, [signOut, update]);

  if (!account) {
    return (
      <div className="glass relative flex flex-col overflow-hidden rounded-3xl p-6">
        <div className="absolute -top-20 -left-16 h-56 w-56 rounded-full bg-[#5865F2]/20 blur-3xl" />
        <div className="relative grid h-12 w-12 place-items-center rounded-2xl bg-[#5865F2]/30 ring-1 ring-white/10">
          <Sparkles size={22} className="text-white" />
        </div>
        <h2 className="relative mt-4 text-xl font-bold">{t('novo.title')}</h2>
        <p className="relative mt-1 flex-1 text-novo-muted">{t('novo.hint')}</p>
        {error && <p className="relative mt-3 text-xs text-danger">{error}</p>}
        <button
          onClick={() => run('login', signIn)}
          disabled={busy !== null}
          className="relative mt-5 flex items-center justify-center gap-2 rounded-2xl bg-[#5865F2] py-3 font-bold text-white shadow-[0_10px_30px_-10px] shadow-[#5865F2]/70 transition-all hover:brightness-110 active:scale-[0.98] disabled:opacity-60"
        >
          {busy ? <Loader2 size={18} className="animate-spin" /> : <DiscordIcon />}
          <span>{busy ? t('acc.signingIn') : t('novo.signIn')}</span>
        </button>
      </div>
    );
  }

  const linked = new Set(account.minecraft.map((m) => m.uuid));
  const unlinked = accounts.filter((a) => a.kind === 'microsoft' && !linked.has(a.uuid));

  async function saveName() {
    const name = editing?.trim();
    setEditing(null);
    if (name && name !== account?.displayName) await run('name', () => api.novoSetDisplayName(name));
  }

  async function toggleSync(cloudSync: boolean) {
    if (!settings) return;
    const next = await api.updateSettings({ ...settings, cloudSync }).catch((e) => {
      setError(String(e));
      return null;
    });
    if (next) setSettings(next);
  }

  return (
    <div className="glass relative flex flex-col gap-6 overflow-hidden rounded-3xl p-6">
      <div className="absolute -top-24 -right-16 h-64 w-64 rounded-full bg-brand-a/15 blur-3xl" />
      {bannerUrl(account.banner) && <div className="pointer-events-none absolute inset-x-0 top-0 h-36 bg-cover bg-center opacity-60" style={{ backgroundImage: `linear-gradient(180deg,transparent,rgba(11,14,20,.96)),url(${bannerUrl(account.banner)})` }} />}

      <div className="relative flex items-center gap-4">
        {account.avatarUrl ? (
          <img src={account.avatarUrl} alt="" className="h-14 w-14 rounded-2xl ring-1 ring-white/10" />
        ) : (
          <div className="grid h-14 w-14 place-items-center rounded-2xl bg-[#5865F2]/30 text-white ring-1 ring-white/10">
            <DiscordIcon size={26} />
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold tracking-[0.2em] text-brand-c uppercase">{t('novo.title')}</p>
          {editing !== null ? (
            <form
              className="mt-1 flex items-center gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                saveName();
              }}
            >
              <input
                autoFocus
                value={editing}
                maxLength={24}
                onChange={(e) => setEditing(e.target.value)}
                onKeyDown={(e) => e.key === 'Escape' && setEditing(null)}
                aria-label={t('novo.name')}
                className="min-w-0 flex-1 rounded-lg bg-novo-bg/70 px-2 py-1 text-lg font-bold outline-none ring-1 ring-novo-border-strong focus:ring-accent"
              />
              <button type="submit" aria-label={t('novo.save')} className="rounded-lg p-1.5 text-play hover:bg-novo-hover">
                <Check size={18} />
              </button>
              <button type="button" onClick={() => setEditing(null)} aria-label={t('title.close')} className="rounded-lg p-1.5 text-novo-muted hover:bg-novo-hover">
                <X size={18} />
              </button>
            </form>
          ) : (
            <button onClick={() => setEditing(account.displayName)} className="group mt-0.5 flex items-center gap-2 text-left" aria-label={t('novo.rename')}>
              <span className="truncate text-2xl font-black">{account.displayName}</span>
              {busy === 'name' ? <Loader2 size={15} className="animate-spin text-novo-muted" /> : <Pencil size={15} className="text-novo-muted opacity-0 transition-opacity group-hover:opacity-100" />}
            </button>
          )}
          {account.discordName && (
            <p className="flex items-center gap-1.5 text-sm text-novo-muted">
              <DiscordIcon size={13} /> {account.discordName}
            </p>
          )}
        </div>
        <button
          onClick={() => run('logout', async () => { await signOut(); return undefined; })}
          disabled={busy !== null}
          className="flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold text-novo-muted transition-colors hover:bg-danger/10 hover:text-danger"
        >
          {busy === 'logout' ? <Loader2 size={15} className="animate-spin" /> : <LogOut size={15} />}
          {t('novo.signOut')}
        </button>
      </div>

      {error && <p className="relative -mt-3 text-xs text-danger">{error}</p>}

      <section className="relative flex items-center justify-between gap-3 rounded-2xl bg-novo-elev p-4">
        <div><p className="font-semibold">{t('marketplace.banners')} · {t('novo.cape')}</p><p className="text-xs text-novo-muted">{t('marketplace.changeCosmetics')}</p></div>
        <button onClick={onMarketplace} className="rounded-xl bg-novo-elev-2 px-4 py-2 text-sm font-semibold hover:bg-novo-hover">{t('marketplace.open')}</button>
      </section>

      <section className="relative flex flex-col gap-3">
        <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('novo.linked')}</h3>
        <div className="flex flex-wrap gap-2">
          {account.minecraft.length === 0 && <p className="text-sm text-novo-muted">{t('novo.noneLinked')}</p>}
          {account.minecraft.map((m) => (
            <span key={m.uuid} className="flex items-center gap-2 rounded-xl bg-novo-elev-2 py-1.5 pr-1.5 pl-3 text-sm font-semibold">
              {m.name}
              <button
                onClick={() => run(`unlink:${m.uuid}`, () => api.novoUnlink(m.uuid))}
                aria-label={t('novo.unlink', { name: m.name })}
                className="rounded-lg p-1 text-novo-muted hover:bg-danger/10 hover:text-danger"
              >
                {busy === `unlink:${m.uuid}` ? <Loader2 size={13} className="animate-spin" /> : <Unlink size={13} />}
              </button>
            </span>
          ))}
          {unlinked.map((a) => (
            <button
              key={a.uuid}
              onClick={() => run(`link:${a.uuid}`, () => api.novoLink(a.uuid))}
              className="flex items-center gap-2 rounded-xl border border-dashed border-novo-border-strong px-3 py-1.5 text-sm text-novo-muted transition-colors hover:border-accent hover:text-novo-text"
            >
              {busy === `link:${a.uuid}` ? <Loader2 size={13} className="animate-spin" /> : <Link2 size={13} />}
              {t('novo.link', { name: a.username })}
            </button>
          ))}
        </div>
      </section>

      {settings && (
        <section className="relative flex items-center gap-3 rounded-2xl bg-novo-elev p-4">
          <Cloud size={18} className="shrink-0 text-brand-c" />
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{t('novo.sync')}</p>
            <p className="text-xs text-novo-muted">{t('novo.syncHint')}</p>
          </div>
          <Switch on={settings.cloudSync} onChange={toggleSync} label={t('novo.sync')} size="sm" />
        </section>
      )}
    </div>
  );
}

export function CapeTile({
  selected,
  locked,
  label,
  onClick,
  children,
}: {
  selected: boolean;
  locked?: boolean;
  label: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <motion.button
      whileHover={locked ? undefined : { y: -3 }}
      onClick={onClick}
      disabled={locked}
      aria-pressed={selected}
      title={label}
      className={`relative flex w-[86px] flex-col items-center gap-2 rounded-2xl p-3 transition-colors ${
        selected ? 'bg-novo-selected ring-2 ring-accent' : 'bg-novo-elev hover:bg-novo-hover'
      } ${locked ? 'cursor-not-allowed opacity-45' : ''}`}
    >
      {children}
      <span className="w-full truncate text-center text-[11px] font-semibold">{label}</span>
      {locked && <Lock size={12} className="absolute top-2 right-2 text-novo-muted" />}
    </motion.button>
  );
}
