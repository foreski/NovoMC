import { useCallback, useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { Archive, Box, Check, Download, FlaskConical, Plus, RefreshCw, RotateCcw, Search, Sparkles, X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { ProgressBar } from '../components/ProgressBar';
import { api, type VersionKind, type VersionSummary } from '../lib/api';
import { formatDate } from '../lib/format';
import { useT, type Key } from '../lib/i18n';
import { installFraction, isRunning, useInstalls } from '../lib/installs';

type Filter = 'release' | 'snapshot' | 'old';

const FILTERS: { id: Filter; label: Key; icon: LucideIcon }[] = [
  { id: 'release', label: 'ver.releases', icon: Box },
  { id: 'snapshot', label: 'ver.snapshots', icon: FlaskConical },
  { id: 'old', label: 'ver.old', icon: Archive },
];

const KIND: Record<VersionKind, { label: Key; icon: LucideIcon; badge: string }> = {
  release: { label: 'kind.release', icon: Box, badge: 'bg-brand-a/15 text-accent' },
  snapshot: { label: 'kind.snapshot', icon: FlaskConical, badge: 'bg-brand-c/15 text-brand-c' },
  old_beta: { label: 'kind.beta', icon: Archive, badge: 'bg-[#facc15]/12 text-[#facc15]' },
  old_alpha: { label: 'kind.alpha', icon: Archive, badge: 'bg-[#fb923c]/12 text-[#fb923c]' },
  other: { label: 'kind.other', icon: Box, badge: 'bg-novo-elev-2 text-novo-muted' },
};

function filterOf(kind: VersionKind): Filter {
  return kind === 'release' ? 'release' : kind === 'snapshot' ? 'snapshot' : 'old';
}

/** Download / progress / installed control for one version. */
function InstallControl({
  id,
  installed,
  big = false,
  onCreateInstance,
}: {
  id: string;
  installed: boolean;
  big?: boolean;
  onCreateInstance: (versionId: string) => void;
}) {
  const { installs, completed, start, cancel } = useInstalls();
  const t = useT();
  const job = installs[id];
  const isInstalled = installed || completed.has(id);

  if (job && isRunning(job)) {
    const pct = Math.floor(installFraction(job) * 100);
    return (
      <div className={`flex items-center gap-3 ${big ? 'w-72' : 'w-56'}`}>
        <div className="flex-1">
          <div className="mb-1.5 flex justify-between font-mono text-[11px] text-novo-muted">
            <span>{job.stage === 'metadata' ? t('ver.preparing') : t('dock.files', { done: job.filesDone, total: job.filesTotal })}</span>
            <span className="text-novo-text">{job.stage === 'metadata' ? '' : `${pct}%`}</span>
          </div>
          <ProgressBar value={job.stage === 'metadata' ? null : installFraction(job)} />
        </div>
        <button
          onClick={() => cancel(id)}
          aria-label={t('common.cancel')}
          className="rounded-lg p-1.5 text-novo-muted transition-colors hover:bg-danger/10 hover:text-danger"
        >
          <X size={16} />
        </button>
      </div>
    );
  }

  if (job?.stage === 'failed') {
    return (
      <button
        onClick={() => start(id)}
        title={job.error ?? undefined}
        className="flex items-center gap-2 rounded-xl bg-danger/15 px-4 py-2 font-semibold text-danger transition-colors hover:bg-danger/25"
      >
        <RotateCcw size={15} /> {t('ver.retry')}
      </button>
    );
  }

  if (isInstalled) {
    return (
      <div className="flex items-center gap-1">
        <span className="flex items-center gap-1.5 px-2 text-sm font-semibold text-play">
          <Check size={15} strokeWidth={3} /> {t('ver.installed')}
        </span>
        <button
          onClick={() => onCreateInstance(id)}
          title={t('ver.createInstanceHint')}
          className="flex items-center gap-1.5 rounded-xl bg-novo-elev-2 px-3 py-2 text-sm font-bold transition-colors hover:bg-gradient-to-r hover:from-brand-a hover:to-brand-b"
        >
          <Plus size={14} /> {t('ver.instance')}
        </button>
        <button
          onClick={() => start(id)}
          title={t('ver.repair')}
          className="rounded-lg p-2 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text"
        >
          <RefreshCw size={14} />
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={() => start(id)}
      className={`group relative flex items-center gap-2 overflow-hidden rounded-xl font-bold text-white transition-all active:scale-[0.97] ${
        big
          ? 'bg-gradient-to-r from-brand-a to-brand-b px-6 py-3 text-base shadow-[0_10px_30px_-10px] shadow-brand-b/70 hover:brightness-110'
          : 'bg-novo-elev-2 px-4 py-2 text-sm hover:bg-gradient-to-r hover:from-brand-a hover:to-brand-b'
      }`}
    >
      {big && <span className="shine" />}
      <Download size={big ? 18 : 15} className="relative transition-transform group-hover:translate-y-0.5" />
      <span className="relative">{t('ver.download')}</span>
    </button>
  );
}

export function VersionsPage({ onCreateInstance }: { onCreateInstance: (versionId: string) => void }) {
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<Set<Filter>>(new Set(['release']));
  const [query, setQuery] = useState('');
  const t = useT();

  const load = useCallback((refresh: boolean) => {
    setLoading(true);
    api
      .listVersions(refresh)
      .then((v) => {
        setVersions(v);
        setError(null);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(false);
  }, [load]);

  const latest = versions.find((v) => v.kind === 'release');
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return versions.filter((v) => (q ? v.id.toLowerCase().includes(q) : filters.has(filterOf(v.kind))));
  }, [versions, filters, query]);

  function toggle(f: Filter) {
    setFilters((prev) => {
      const next = new Set(prev);
      if (next.has(f) && next.size > 1) next.delete(f);
      else next.add(f);
      return next;
    });
  }

  return (
    // Extra bottom padding keeps the last rows clear of the download dock.
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-8 pb-36">
      <header>
        <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('inst.eyebrow')}</p>
        <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('nav.versions')}</h1>
        <p className="mt-2 text-novo-muted">{t('ver.subtitle')}</p>
      </header>

      {latest && (
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="relative overflow-hidden rounded-3xl border border-white/10 bg-novo-panel/40 p-7"
        >
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_left,rgb(91_140_255/0.3),transparent_60%),radial-gradient(ellipse_at_right,rgb(34_211_238/0.18),transparent_55%)]" />
          <div className="grid-bg absolute inset-0" />
          <div className="relative flex flex-wrap items-center justify-between gap-6">
            <div>
              <p className="flex items-center gap-2 text-xs font-bold tracking-[0.25em] text-brand-c uppercase">
                <Sparkles size={13} /> {t('ver.latest')}
              </p>
              <div className="mt-2 font-mono text-5xl font-black tracking-tight">{latest.id}</div>
              <p className="mt-1 text-novo-muted">{t('ver.released', { date: formatDate(latest.releaseTime) })}</p>
            </div>
            <InstallControl id={latest.id} installed={latest.installed} big onCreateInstance={onCreateInstance} />
          </div>
        </motion.section>
      )}

      <div className="flex flex-wrap items-center gap-2">
        {FILTERS.map(({ id, label, icon: Icon }) => {
          const on = filters.has(id) && !query;
          return (
            <button
              key={id}
              onClick={() => toggle(id)}
              className={`flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold transition-all ${
                on
                  ? 'border-accent/50 bg-accent/15 text-novo-text shadow-[0_0_20px_-6px_var(--color-accent)]'
                  : 'border-novo-border text-novo-muted hover:border-novo-border-strong hover:text-novo-text'
              }`}
            >
              <Icon size={15} className={on ? 'text-accent' : ''} />
              {t(label)}
            </button>
          );
        })}
        <div className="ml-auto flex items-center gap-2">
          <div className="flex items-center gap-2 rounded-full border border-novo-border bg-novo-bg/60 px-4 py-2 focus-within:border-accent/60">
            <Search size={15} className="text-novo-muted" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('ver.search')}
              spellCheck={false}
              className="w-40 bg-transparent text-sm outline-none placeholder:text-novo-muted"
            />
          </div>
          <button
            onClick={() => load(true)}
            aria-label={t('ver.refresh')}
            className="rounded-full border border-novo-border p-2.5 text-novo-muted transition-colors hover:border-novo-border-strong hover:text-novo-text"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {error && <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-danger">{t('ver.loadError', { error })}</p>}

      <section className="glass overflow-hidden rounded-3xl">
        {loading && versions.length === 0
          ? Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="flex items-center gap-4 border-b border-novo-border px-5 py-4 last:border-0">
                <div className="h-10 w-10 animate-pulse rounded-xl bg-novo-elev-2" />
                <div className="h-4 w-32 animate-pulse rounded bg-novo-elev-2" />
              </div>
            ))
          : shown.map((v) => {
              const kind = KIND[v.kind];
              const Icon = kind.icon;
              return (
                <div
                  key={v.id}
                  className="group flex items-center gap-4 border-b border-novo-border px-5 py-3 transition-colors last:border-0 hover:bg-white/[0.03]"
                >
                  <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-novo-elev-2 text-novo-muted transition-colors group-hover:text-accent">
                    <Icon size={18} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-mono text-base font-bold">{v.id}</span>
                      <span className={`rounded-md px-2 py-0.5 text-[10px] font-bold tracking-wider uppercase ${kind.badge}`}>
                        {t(kind.label)}
                      </span>
                    </div>
                    <div className="text-xs text-novo-muted">{formatDate(v.releaseTime)}</div>
                  </div>
                  <InstallControl id={v.id} installed={v.installed} onCreateInstance={onCreateInstance} />
                </div>
              );
            })}
        {!loading && shown.length === 0 && !error && <p className="p-8 text-center text-novo-muted">{t('ver.noMatch')}</p>}
      </section>
      <p className="-mt-3 text-center text-xs text-novo-muted">{t('ver.count', { shown: shown.length, total: versions.length })}</p>
    </div>
  );
}
