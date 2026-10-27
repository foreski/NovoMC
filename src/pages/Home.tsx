import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Check, ChevronUp, Loader2, Newspaper, Play, Plus, Skull, Square, Terminal } from 'lucide-react';
import hero from '../../assets/home/hero.jpg';
import { InstanceIcon } from '../components/InstanceIcon';
import type { PageId } from '../components/Sidebar';
import { useAccounts } from '../lib/accounts';
import { NewsCard } from '../components/NewsCard';
import { api, type Instance, type LaunchState, type NewsItem } from '../lib/api';
import { useGame } from '../lib/game';
import { ServerCards } from '../components/ServerCards';
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

/** The instance list that opens upward from the Play button's arrow. */
function InstanceMenu({ instances, value, onChange, onClose }: {
  instances: Instance[];
  value: Instance;
  onChange: (id: string) => void;
  onClose: () => void;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: 8, scale: 0.97 }}
      transition={{ duration: 0.15 }}
      className="absolute right-0 bottom-full z-20 mb-2 max-h-72 w-72 overflow-auto rounded-2xl border border-novo-border-strong bg-novo-panel/95 p-1.5 shadow-2xl backdrop-blur-xl"
    >
      {instances.map((i) => (
        <button
          key={i.id}
          onClick={() => {
            onChange(i.id);
            onClose();
          }}
          className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-novo-hover"
        >
          <InstanceIcon name={i.name} size={30} />
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-semibold">{i.name}</div>
            <div className="font-mono text-[11px] text-novo-muted">
              {i.version} · {loaderLabel(i)}
            </div>
          </div>
          {i.id === value.id && <Check size={14} className="text-accent" />}
        </button>
      ))}
    </motion.div>
  );
}

/**
 * The big launch control: Play (or Stop while the game runs) with the instance
 * switcher attached on the right, as in PvP client launchers.
 */
function PlayCluster({ selected, instances, onSelect, onPlay }: {
  selected: Instance;
  instances: Instance[];
  onSelect: (id: string) => void;
  onPlay: () => void;
}) {
  const { launch, busy, stop } = useGame();
  const t = useT();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [open]);

  if (launch?.stage === 'running') {
    return (
      <button
        onClick={stop}
        className="flex h-[72px] w-80 items-center justify-center gap-3 rounded-2xl border border-danger/40 bg-danger/20 text-2xl font-black tracking-[0.2em] text-danger backdrop-blur-md transition-all hover:bg-danger/30 active:scale-[0.98]"
      >
        <Square size={22} fill="currentColor" />
        {t('home.stop')}
      </button>
    );
  }

  const progress = launch?.stage === 'java' || launch?.stage === 'installing' ? launch.progress : null;
  return (
    <div ref={ref} className="relative flex h-[72px] w-80 overflow-hidden rounded-2xl shadow-[0_18px_50px_-12px] shadow-brand-a/60">
      <button
        onClick={onPlay}
        disabled={busy}
        className="group relative flex-1 overflow-hidden bg-gradient-to-r from-brand-a to-brand-b text-white transition-[filter] enabled:hover:brightness-110 disabled:cursor-default"
      >
        {!busy && <span className="shine" />}
        {progress != null && <span className="absolute inset-y-0 left-0 bg-white/20 transition-[width]" style={{ width: `${progress * 100}%` }} />}
        <span className="relative flex items-center justify-center gap-3">
          {busy ? <Loader2 size={24} className="animate-spin" /> : <Play size={26} fill="currentColor" className="transition-transform group-hover:scale-110" />}
          <span className={busy ? 'text-base font-black tracking-wider' : 'text-2xl font-black tracking-[0.2em]'}>{buttonLabel(launch, t)}</span>
        </span>
        <span className="relative mt-0.5 block font-mono text-[10px] font-bold tracking-[0.2em] text-white/70">
          {busy && launch?.versionId ? `MINECRAFT ${launch.versionId}` : `${selected.version} · ${loaderLabel(selected).toUpperCase()}`}
        </span>
      </button>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={t('home.switchInstance')}
        title={t('home.switchInstance')}
        className="grid w-14 place-items-center border-l border-white/20 bg-brand-b text-white transition-[filter] hover:brightness-110"
      >
        <ChevronUp size={20} className={`transition-transform ${open ? '' : 'rotate-180'}`} />
      </button>
      <AnimatePresence>
        {open && <InstanceMenu instances={instances} value={selected} onChange={onSelect} onClose={() => setOpen(false)} />}
      </AnimatePresence>
    </div>
  );
}

/** One line under the instance name describing the last launch. */
function LaunchStatus() {
  const { launch, openConsole } = useGame();
  const t = useT();
  if (!launch) return null;
  const { stage } = launch;
  if (stage === 'running') {
    return (
      <p className="flex items-center gap-2 text-sm font-semibold text-play">
        <span className="h-2 w-2 animate-pulse rounded-full bg-play" /> {t('home.running', { version: launch.versionId })}
      </p>
    );
  }
  if (stage === 'crashed') {
    return (
      <button onClick={() => openConsole('crash')} className="flex items-center gap-2 text-sm font-semibold text-danger hover:underline">
        <Skull size={14} /> {t('home.crashed')}
      </button>
    );
  }
  if (stage === 'failed') return <p className="text-sm font-semibold text-danger">{launch.message}</p>;
  return null;
}

export function HomePage({ onNavigate }: { onNavigate: (p: PageId) => void }) {
  const { active } = useAccounts();
  const { play, openConsole, busy } = useGame();
  const { instances } = useInstances();
  const t = useT();
  const [news, setNews] = useState<NewsItem[] | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    api.getNews().then(setNews, () => setNews([]));
    api.getSettings().then((s) => setSelectedId((id) => id ?? s.lastInstance), () => {});
  }, []);

  // The chosen instance, else the last played, else the most recent.
  const selected = instances?.find((i) => i.id === selectedId) ?? instances?.[0] ?? null;

  function onPlay(server?: string) {
    if (!selected) return onNavigate('instances');
    if (!active) return onNavigate('accounts');
    play(selected.id, server);
  }

  return (
    <div className="flex min-h-full flex-col">
      {/* Hero: in-game artwork with the instance and the launch controls on top. */}
      <section className="relative flex min-h-[440px] flex-[1_0_58vh] flex-col justify-end overflow-hidden">
        {/* Masked rather than faded to a colour, so it melts into the app backdrop below. */}
        <div
          className="pointer-events-none absolute inset-0"
          style={{ maskImage: 'linear-gradient(to bottom, black 45%, transparent)', WebkitMaskImage: 'linear-gradient(to bottom, black 45%, transparent)' }}
        >
          <motion.img
            src={hero}
            alt=""
            initial={{ scale: 1.08, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ duration: 1.2, ease: [0.22, 1, 0.36, 1] }}
            className="h-full w-full object-cover object-[center_62%]"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-black/60 via-black/15 to-transparent" />
        </div>

        <motion.div {...rise(0.1)} className="absolute top-6 right-8 flex items-center gap-2">
          <button
            onClick={() => openConsole('log')}
            aria-label={t('home.openConsole')}
            title={t('console.title')}
            className="grid h-11 w-11 place-items-center rounded-full border border-white/15 bg-black/35 text-white/70 backdrop-blur-md transition-colors hover:border-white/30 hover:text-white"
          >
            <Terminal size={18} />
          </button>
        </motion.div>

        <div className="relative flex items-end justify-between gap-8 px-10 pb-10">
          <div className="min-w-0">
            <motion.p {...rise(0.15)} className="text-xs font-bold tracking-[0.35em] text-brand-c uppercase drop-shadow">
              Novo Client · 1.8.9 PvP
            </motion.p>
            {selected ? (
              <motion.h1
                {...rise(0.2)}
                className="mt-2 line-clamp-2 max-w-xl text-6xl leading-[1.02] font-black tracking-tight text-white drop-shadow-[0_4px_24px_rgb(0_0_0/0.6)]"
              >
                {selected.name}
              </motion.h1>
            ) : (
              <motion.div {...rise(0.2)}>
                <h1 className="mt-2 text-6xl leading-[1.02] font-black tracking-tight text-white drop-shadow-[0_4px_24px_rgb(0_0_0/0.6)]">
                  {t('home.noInstance1')} {t('home.noInstance2')}
                </h1>
                <p className="mt-3 text-base text-white/70">{t('home.noInstanceHint')}</p>
              </motion.div>
            )}
            <div className="mt-3 min-h-5">
              <LaunchStatus />
            </div>
          </div>

          <motion.div {...rise(0.3)} className="shrink-0">
            {selected ? (
              <PlayCluster selected={selected} instances={instances ?? []} onSelect={setSelectedId} onPlay={() => onPlay()} />
            ) : (
              <button
                onClick={() => onNavigate('instances')}
                disabled={instances === null}
                className="group relative flex h-[72px] w-80 items-center justify-center gap-3 overflow-hidden rounded-2xl bg-gradient-to-r from-brand-a to-brand-b text-lg font-black tracking-wider text-white shadow-[0_18px_50px_-12px] shadow-brand-a/60"
              >
                <span className="shine" />
                <Plus size={22} className="relative" />
                <span className="relative">{t('home.createInstance')}</span>
              </button>
            )}
          </motion.div>
        </div>
      </section>

      {/* News on the left, quick-join servers on the right. */}
      <div className="grid flex-1 gap-6 px-10 pt-2 pb-8 lg:grid-cols-[minmax(0,1fr)_340px]">
        <section className="min-w-0">
          <motion.h2 {...rise(0.35)} className="mb-3 flex items-center gap-2 text-xs font-bold tracking-[0.25em] text-novo-muted uppercase">
            <Newspaper size={14} /> {t('home.news')}
          </motion.h2>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {(news ?? []).slice(0, 3).map((item, i) => (
              <NewsCard key={item.id} item={item} index={i} />
            ))}
          </div>
        </section>
        <motion.div {...rise(0.4)}>
          <ServerCards compact onJoin={(address) => onPlay(address)} disabled={busy} />
        </motion.div>
      </div>
    </div>
  );
}
