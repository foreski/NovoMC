import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Boxes, Check, ChevronDown, Loader2, Newspaper, Play, Plus, Skull, Sparkles, Square, Terminal, UserCircle, Zap } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import logo from '../../assets/branding/novomc-logo.svg';
import { RingAvatar } from '../components/Avatar';
import { InstanceIcon } from '../components/InstanceIcon';
import type { PageId } from '../components/Sidebar';
import { useAccounts } from '../lib/accounts';
import { NewsCard } from '../components/NewsCard';
import { api, type AppInfo, type Instance, type LaunchState, type NewsItem } from '../lib/api';
import { useGame } from '../lib/game';
import { useT, type T } from '../lib/i18n';
import { useInstances } from '../lib/instances';
import { loaderLabel } from '../lib/loaders';

const rise = (delay: number) => ({
  initial: { opacity: 0, y: 16 },
  animate: { opacity: 1, y: 0 },
  transition: { delay, duration: 0.5, ease: [0.22, 1, 0.36, 1] as const },
});

function buttonLabel(launch: LaunchState | null, t: T): string {
  const pct = launch?.progress != null ? Math.floor(launch.progress * 100) : null;
  switch (launch?.stage) {
    case 'preparing':
      return t('home.preparing');
    case 'installing':
      return pct != null ? t('home.downloadingPct', { pct }) : t('home.downloading');
    case 'java':
      return pct != null ? t('home.gettingJava', { pct }) : t('home.findingJava');
    case 'starting':
      return t('home.starting');
    default:
      return t('home.play');
  }
}

function PlayButton({ onClick, disabled, subtitle }: { onClick: () => void; disabled?: boolean; subtitle?: string }) {
  const { launch, busy, stop } = useGame();
  const t = useT();
  const running = launch?.stage === 'running';

  if (running) {
    return (
      <button
        onClick={stop}
        className="group relative flex min-w-56 items-center justify-center gap-3 overflow-hidden rounded-2xl border border-danger/40 bg-danger/15 px-10 py-5 text-xl font-black tracking-[0.15em] text-danger transition-all hover:bg-danger/25 active:scale-[0.97]"
      >
        <Square size={20} fill="currentColor" />
        {t('home.stop')}
      </button>
    );
  }

  const progress = launch?.stage === 'java' || launch?.stage === 'installing' ? launch.progress : null;
  return (
    <button
      onClick={onClick}
      disabled={busy || disabled}
      className="group relative min-w-60 overflow-hidden rounded-2xl bg-gradient-to-b from-[#86efac] via-play to-play-dark px-12 py-3.5 text-xl font-black tracking-[0.15em] text-[#052e12] transition-transform duration-200 enabled:hover:scale-[1.04] enabled:active:scale-[0.97] disabled:cursor-default"
      style={{ animation: busy ? undefined : 'play-glow 2.6s ease-in-out infinite' }}
    >
      {!busy && <span className="shine" />}
      {progress != null && <span className="absolute inset-y-0 left-0 bg-white/25 transition-[width]" style={{ width: `${progress * 100}%` }} />}
      <span className="absolute inset-x-0 top-0 h-1/2 bg-white/20" />
      <span className="relative flex items-center justify-center gap-3">
        {busy ? <Loader2 size={22} className="animate-spin" /> : <Play size={24} fill="currentColor" className="transition-transform group-hover:scale-110" />}
        <span className={busy ? 'text-base' : ''}>{buttonLabel(launch, t)}</span>
      </span>
      <span className="relative mt-0.5 block text-[10px] font-bold tracking-[0.2em] opacity-70">
        {busy ? (launch?.versionId ? `MINECRAFT ${launch.versionId}` : t('home.pleaseWait')) : (subtitle ?? t('home.readyToLaunch'))}
      </span>
    </button>
  );
}

function InstancePicker({ instances, value, onChange }: { instances: Instance[]; value: Instance; onChange: (id: string) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const t = useT();

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="glass flex items-center gap-2 rounded-xl px-3 py-1.5 text-sm font-semibold transition-colors hover:border-novo-border-strong"
      >
        <span className="font-mono text-accent">{value.version}</span>
        <span className="text-novo-muted">·</span>
        {loaderLabel(value)}
        <span className="text-novo-muted">·</span>
        {t('home.switchInstance')}
        <ChevronDown size={14} className={`text-novo-muted transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.97 }}
            transition={{ duration: 0.15 }}
            className="absolute top-full left-0 z-20 mt-2 max-h-72 w-72 overflow-auto rounded-2xl border border-novo-border-strong bg-novo-panel/95 p-1.5 shadow-2xl backdrop-blur-xl"
          >
            {instances.map((i) => (
              <button
                key={i.id}
                onClick={() => {
                  onChange(i.id);
                  setOpen(false);
                }}
                className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-novo-hover"
              >
                <InstanceIcon name={i.name} size={30} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold">{i.name}</div>
                  <div className="font-mono text-[11px] text-novo-muted">{i.version}</div>
                </div>
                {i.id === value.id && <Check size={14} className="text-accent" />}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function HeroOrb() {
  return (
    <div className="relative hidden h-64 w-64 shrink-0 lg:block">
      <div
        className="absolute inset-4 rounded-full gradient-border opacity-60 blur-2xl"
        style={{ animation: 'angle 4s linear infinite, spin 18s linear infinite' }}
      />
      <div className="absolute inset-0 rounded-full border border-white/10" style={{ animation: 'spin 30s linear infinite' }}>
        <span className="absolute top-0 left-1/2 h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand-c shadow-[0_0_12px_3px_var(--color-brand-c)]" />
      </div>
      <div className="absolute inset-8 rounded-full border border-white/5" style={{ animation: 'spin 20s linear infinite reverse' }}>
        <span className="absolute bottom-0 left-1/2 h-1.5 w-1.5 -translate-x-1/2 translate-y-1/2 rounded-full bg-brand-b shadow-[0_0_10px_3px_var(--color-brand-b)]" />
      </div>
      <img
        src={logo}
        alt=""
        className="absolute inset-12 h-40 w-40 drop-shadow-[0_20px_40px_rgb(91_140_255/0.5)]"
        style={{ animation: 'float 6s ease-in-out infinite' }}
      />
    </div>
  );
}

function StatTile({ icon: Icon, label, value, onClick }: { icon: LucideIcon; label: string; value: string; onClick?: () => void }) {
  return (
    <button
      onClick={onClick}
      className="glass group flex items-center gap-4 rounded-2xl p-4 text-left transition-all hover:-translate-y-0.5 hover:border-novo-border-strong"
    >
      <div className="grid h-11 w-11 place-items-center rounded-xl bg-gradient-to-br from-brand-a/30 to-brand-b/30 ring-1 ring-white/10">
        <Icon size={20} className="text-accent" />
      </div>
      <div className="min-w-0">
        <div className="text-xs font-medium tracking-wider text-novo-muted uppercase">{label}</div>
        <div className="truncate text-lg font-bold">{value}</div>
      </div>
    </button>
  );
}

/** One line under the Play button describing the last launch. */
function LaunchStatus() {
  const { launch, openConsole } = useGame();
  const t = useT();
  if (!launch) return null;
  const { stage } = launch;
  if (stage === 'running') {
    return (
      <p className="flex items-center gap-2 text-sm text-play">
        <span className="h-2 w-2 animate-pulse rounded-full bg-play" /> {t('home.running', { version: launch.versionId })}
      </p>
    );
  }
  if (stage === 'crashed') {
    return (
      <button onClick={() => openConsole('crash')} className="flex items-center gap-2 text-sm text-danger hover:underline">
        <Skull size={14} /> {t('home.crashed')}
      </button>
    );
  }
  if (stage === 'failed') return <p className="text-sm text-danger">{launch.message}</p>;
  return null;
}

export function HomePage({ onNavigate }: { onNavigate: (p: PageId) => void }) {
  const { active, accounts } = useAccounts();
  const { play, openConsole } = useGame();
  const { instances } = useInstances();
  const t = useT();
  const [info, setInfo] = useState<AppInfo | null>(null);
  const [news, setNews] = useState<NewsItem[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    api.appInfo().then(setInfo, () => {});
    api.getNews().then(setNews, () => setNews([]));
    api.getSettings().then((s) => setSelectedId((id) => id ?? s.lastInstance), () => {});
  }, []);

  // The chosen instance, else the last played, else the most recent.
  const selected = instances?.find((i) => i.id === selectedId) ?? instances?.[0] ?? null;

  function onPlay() {
    if (!selected) return onNavigate('instances');
    if (!active) return onNavigate('accounts');
    play(selected.id);
  }

  return (
    <div className="flex min-h-full flex-col gap-5 p-8">
      <motion.section {...rise(0)} className="relative rounded-3xl border border-white/10 bg-novo-panel/40 p-10">
        <div className="absolute inset-0 overflow-hidden rounded-3xl">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,rgb(91_140_255/0.35),transparent_55%),radial-gradient(ellipse_at_bottom_right,rgb(180_91_255/0.3),transparent_55%),radial-gradient(circle_at_75%_35%,rgb(34_211_238/0.15),transparent_40%)]" />
          <div className="grid-bg absolute inset-0" />
        </div>

        <div className="relative flex items-center justify-between gap-8">
          <div className="max-w-lg">
            <motion.p {...rise(0.1)} className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] text-brand-c uppercase">
              <Sparkles size={14} /> {t('home.readyWhenYouAre')}
            </motion.p>
            {selected ? (
              <motion.div {...rise(0.15)}>
                <h1 className="gradient-text mt-3 line-clamp-2 text-6xl leading-[1.02] font-black tracking-tight">{selected.name}</h1>
                <div className="mt-3">
                  <InstancePicker instances={instances ?? []} value={selected} onChange={setSelectedId} />
                </div>
              </motion.div>
            ) : (
              <motion.div {...rise(0.15)}>
                <h1 className="gradient-text mt-3 text-6xl leading-[1.02] font-black tracking-tight">
                  {t('home.noInstance1')}
                  <br />
                  {t('home.noInstance2')}
                </h1>
                <p className="mt-4 text-base text-novo-muted">{t('home.noInstanceHint')}</p>
              </motion.div>
            )}

            <motion.div {...rise(0.28)} className="mt-8 flex flex-wrap items-center gap-4">
              {selected || instances === null ? (
                <PlayButton
                  onClick={onPlay}
                  disabled={instances === null}
                  subtitle={
                    selected
                      ? t('home.readySubtitle', { version: selected.version, loader: loaderLabel(selected).split(' ')[0].toUpperCase() })
                      : undefined
                  }
                />
              ) : (
                <button
                  onClick={() => onNavigate('instances')}
                  className="group relative flex items-center gap-3 overflow-hidden rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-10 py-5 text-lg font-black tracking-wider text-white shadow-[0_10px_30px_-10px] shadow-brand-b/70"
                >
                  <span className="shine" />
                  <Plus size={22} className="relative" />
                  <span className="relative">{t('home.createInstance')}</span>
                </button>
              )}
              <button
                onClick={() => onNavigate('accounts')}
                className="glass flex items-center gap-3 rounded-2xl py-2 pr-4 pl-2 transition-colors hover:border-novo-border-strong"
              >
                {active ? (
                  <>
                    <RingAvatar name={active.username} size={36} />
                    <div className="text-left">
                      <div className="text-[11px] tracking-wider text-novo-muted uppercase">{t('home.playingAs')}</div>
                      <div className="font-bold">{active.username}</div>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="grid h-10 w-10 place-items-center rounded-xl border border-dashed border-novo-border-strong">
                      <UserCircle size={18} className="text-novo-muted" />
                    </div>
                    <span className="text-novo-muted">{t('home.choosePlayer')}</span>
                  </>
                )}
              </button>
              <button
                onClick={() => openConsole('log')}
                aria-label={t('home.openConsole')}
                title={t('console.title')}
                className="glass grid h-14 w-14 place-items-center rounded-2xl text-novo-muted transition-colors hover:border-novo-border-strong hover:text-novo-text"
              >
                <Terminal size={20} />
              </button>
            </motion.div>
            <div className="mt-4 min-h-5">
              <LaunchStatus />
            </div>
          </div>

          <HeroOrb />
        </div>
      </motion.section>

      <motion.div {...rise(0.35)} className="grid grid-cols-3 gap-4">
        <StatTile
          icon={Boxes}
          label={t('nav.instances')}
          value={instances ? String(instances.length) : '…'}
          onClick={() => onNavigate('instances')}
        />
        <StatTile icon={UserCircle} label={t('home.players')} value={String(accounts.length)} onClick={() => onNavigate('accounts')} />
        <StatTile icon={Zap} label={t('home.launcher')} value={info ? `v${info.version}` : '…'} />
      </motion.div>

      {news && news.length > 0 && (
        <section>
          <motion.h2 {...rise(0.4)} className="mb-3 flex items-center gap-2 text-xs font-bold tracking-[0.25em] text-novo-muted uppercase">
            <Newspaper size={14} /> {t('home.news')}
          </motion.h2>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {news.map((item, i) => (
              <NewsCard key={item.id} item={item} index={i} />
            ))}
          </div>
        </section>
      )}

      <motion.footer {...rise(0.45)} className="mt-auto truncate font-mono text-[11px] text-novo-muted/70">
        {info ? `${t('home.data')} · ${info.dataDir}` : t('home.connecting')}
      </motion.footer>
    </div>
  );
}
