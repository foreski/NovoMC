import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { isTauri } from '@tauri-apps/api/core';
import { AnimatePresence, motion } from 'motion/react';
import { Check, Download, Loader2, Package, Play, Plus, Search, Sparkles, Trash2, Trees, Wand2 } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { PageId } from '../components/Sidebar';
import {
  api,
  onSurvivalProgress,
  type SurvivalEdition,
  type SurvivalHit,
  type SurvivalInstalled,
  type SurvivalItem,
  type SurvivalKind,
  type SurvivalProgress,
  type SurvivalStatus,
} from '../lib/api';
import { useGame } from '../lib/game';
import { useT, type Key } from '../lib/i18n';
import { useInstances } from '../lib/instances';

type Tab = 'setup' | 'shaders' | 'packs';

const KIND_ICON: Record<SurvivalKind, LucideIcon> = { mod: Package, pack: Sparkles, shader: Wand2 };

const CATEGORIES: { id: string; label: Key }[] = [
  { id: 'core', label: 'survival.catCore' },
  { id: 'animation', label: 'survival.catAnimation' },
  { id: 'world', label: 'survival.catWorld' },
  { id: 'qol', label: 'survival.catQol' },
];

function ItemCard({
  item,
  installed,
  busy,
  onInstall,
  onRemove,
}: {
  item: { slug: string; title: string; blurb: string; note?: string | null; kind: SurvivalKind };
  installed?: SurvivalInstalled;
  busy: boolean;
  onInstall: () => void;
  onRemove: () => void;
}) {
  const t = useT();
  const Icon = KIND_ICON[item.kind];
  return (
    <div
      className={`flex items-start gap-3 rounded-2xl border p-3.5 transition-colors ${
        installed ? 'border-play/30 bg-play/[0.06]' : 'border-novo-border bg-novo-elev hover:border-novo-border-strong'
      }`}
    >
      <div
        className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl ring-1 ring-white/10 ${
          installed ? 'bg-play/15 text-play' : 'bg-novo-elev-2 text-novo-muted'
        }`}
      >
        <Icon size={16} />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate font-semibold">{item.title}</span>
          {installed && (
            <span className="flex shrink-0 items-center gap-1 rounded-full bg-play/15 px-1.5 py-0.5 text-[10px] font-bold text-play">
              <Check size={10} strokeWidth={3} /> {t('survival.installed')}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs leading-relaxed text-novo-muted">{item.blurb}</p>
        {item.note && <p className="mt-1 text-[11px] leading-relaxed text-amber-300/80">{item.note}</p>}
      </div>
      <button
        type="button"
        disabled={busy}
        onClick={installed ? onRemove : onInstall}
        title={installed ? t('survival.remove') : t('survival.install')}
        className={`flex shrink-0 items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-xs font-bold transition-colors disabled:opacity-50 ${
          installed
            ? 'text-novo-muted hover:bg-danger/10 hover:text-danger'
            : 'bg-novo-elev-2 text-novo-text hover:bg-gradient-to-r hover:from-brand-a hover:to-brand-b hover:text-white'
        }`}
      >
        {busy ? (
          <Loader2 size={13} className="animate-spin" />
        ) : installed ? (
          <Trash2 size={13} />
        ) : (
          <Download size={13} />
        )}
        <span className="hidden sm:inline">{installed ? t('survival.remove') : t('survival.install')}</span>
      </button>
    </div>
  );
}

function HitCard({ hit, installed, busy, onInstall }: { hit: SurvivalHit; installed: boolean; busy: boolean; onInstall: () => void }) {
  const t = useT();
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-novo-border bg-novo-elev p-3.5">
      {hit.iconUrl ? (
        <img src={hit.iconUrl} alt="" className="h-9 w-9 shrink-0 rounded-xl bg-novo-elev-2 object-cover" />
      ) : (
        <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-novo-elev-2 text-novo-muted">
          <Package size={16} />
        </div>
      )}
      <div className="min-w-0 flex-1">
        <div className="truncate font-semibold">{hit.title}</div>
        <p className="line-clamp-2 text-xs leading-relaxed text-novo-muted">{hit.description}</p>
      </div>
      <button
        type="button"
        disabled={busy || installed}
        onClick={onInstall}
        className={`flex shrink-0 items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-xs font-bold transition-colors disabled:opacity-60 ${
          installed
            ? 'bg-play/15 text-play'
            : 'bg-novo-elev-2 hover:bg-gradient-to-r hover:from-brand-a hover:to-brand-b hover:text-white'
        }`}
      >
        {busy ? <Loader2 size={13} className="animate-spin" /> : installed ? <Check size={13} strokeWidth={3} /> : <Download size={13} />}
        <span className="hidden sm:inline">{installed ? t('survival.installed') : t('survival.install')}</span>
      </button>
    </div>
  );
}

export function SurvivalPage({ onNavigate }: { onNavigate: (p: PageId) => void }) {
  const t = useT();
  const { instances, refresh } = useInstances();
  const { play, launch } = useGame();
  const [editions, setEditions] = useState<SurvivalEdition[]>([]);
  const [catalog, setCatalog] = useState<SurvivalItem[]>([]);
  const [profiles, setProfiles] = useState<SurvivalStatus[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('setup');
  const [picking, setPicking] = useState(false);
  const [creating, setCreating] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [progress, setProgress] = useState<SurvivalProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [failed, setFailed] = useState<string[]>([]);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<SurvivalHit[] | null>(null);
  const [searching, setSearching] = useState(false);

  const loadProfiles = useCallback(async () => {
    const list = await api.survivalInstances();
    setProfiles(list);
    setSelected((current) => (current && list.some((p) => p.instanceId === current) ? current : (list[0]?.instanceId ?? null)));
  }, []);

  useEffect(() => {
    api.survivalEditions().then(setEditions, (e) => setError(String(e)));
    api.survivalCatalog().then(setCatalog, (e) => setError(String(e)));
    loadProfiles().catch((e) => setError(String(e)));
  }, [loadProfiles]);

  useEffect(() => {
    if (!isTauri()) return;
    const off = onSurvivalProgress(setProgress);
    return () => {
      off.then((f) => f());
    };
  }, []);

  const profile = profiles?.find((p) => p.instanceId === selected) ?? null;
  const instance = instances?.find((i) => i.id === selected) ?? null;
  const edition = profile?.edition ?? '';
  const installed = useMemo(() => new Map((profile?.installed ?? []).map((i) => [i.slug, i])), [profile]);
  const available = useMemo(
    () => catalog.filter((item) => item.editions.includes(edition)),
    [catalog, edition],
  );
  const bundle = available.filter((item) => item.default);
  const bundleDone = bundle.filter((item) => installed.has(item.slug)).length;
  const mods = available.filter((item) => item.kind === 'mod');
  const featured = available.filter((item) => item.kind === (tab === 'shaders' ? 'shader' : 'pack'));
  const running = launch?.instanceId === selected && ['preparing', 'installing', 'java', 'starting', 'running'].includes(launch.stage);

  useEffect(() => {
    setHits(null);
    setQuery('');
  }, [tab, selected]);

  async function create(editionId: string) {
    setCreating(editionId);
    setError(null);
    try {
      const created = await api.survivalCreate(editionId);
      await refresh();
      await loadProfiles();
      setSelected(created.id);
      setPicking(false);
    } catch (e) {
      setError(String(e));
    } finally {
      setCreating(null);
    }
  }

  async function install(slug: string, kind?: SurvivalKind) {
    if (!profile) return;
    setBusy(slug);
    setError(null);
    try {
      const next = await api.survivalInstall(profile.instanceId, slug, kind);
      setProfiles((list) => list?.map((p) => (p.instanceId === next.instanceId ? next : p)) ?? list);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(null);
      setProgress(null);
    }
  }

  async function remove(slug: string) {
    if (!profile) return;
    setBusy(slug);
    setError(null);
    try {
      const next = await api.survivalRemove(profile.instanceId, slug);
      setProfiles((list) => list?.map((p) => (p.instanceId === next.instanceId ? next : p)) ?? list);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(null);
    }
  }

  async function installBundle() {
    if (!profile) return;
    setBusy('bundle');
    setError(null);
    setFailed([]);
    try {
      const result = await api.survivalBundle(profile.instanceId);
      setProfiles((list) => list?.map((p) => (p.instanceId === result.status.instanceId ? result.status : p)) ?? list);
      setFailed(result.failed);
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(null);
      setProgress(null);
    }
  }

  async function search(e: FormEvent) {
    e.preventDefault();
    if (!profile) return;
    setSearching(true);
    setError(null);
    try {
      setHits(await api.survivalSearch(profile.instanceId, tab === 'shaders' ? 'shader' : 'pack', query));
    } catch (err) {
      setError(String(err));
    } finally {
      setSearching(false);
    }
  }

  const showPicker = !profile || picking;

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 p-8 pb-36">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] text-brand-c uppercase">
            <Trees size={13} /> Novo Client
          </p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('survival.title')}</h1>
          <p className="mt-2 max-w-2xl text-novo-muted">{t('survival.subtitle')}</p>
        </div>
        {profile && (
          <button
            type="button"
            disabled={running || !instance}
            onClick={() => selected && play(selected)}
            className="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-6 py-3 font-bold text-white shadow-[0_10px_30px_-12px] shadow-brand-b/70 transition-all hover:brightness-110 active:scale-[0.97] disabled:opacity-60"
          >
            {running ? <Loader2 size={18} className="animate-spin" /> : <Play size={18} />}
            {running ? t('survival.starting') : t('survival.play')}
          </button>
        )}
      </header>

      {error && (
        <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-sm text-danger">{error}</p>
      )}

      <AnimatePresence>
        {progress && busy === 'bundle' && progress.total > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="rounded-2xl border border-accent/30 bg-accent/[0.07] p-4"
          >
            <div className="flex items-center justify-between text-sm font-semibold">
              <span className="flex items-center gap-2">
                <Loader2 size={14} className="animate-spin text-accent" />
                {t('survival.bundleProgress', { done: progress.done, total: progress.total })}
              </span>
              <span className="font-mono text-xs text-novo-muted">
                {progress.slug ? catalog.find((i) => i.slug === progress.slug)?.title ?? progress.slug : ''}
              </span>
            </div>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-novo-bg/70">
              <div
                className="h-full rounded-full bg-gradient-to-r from-brand-a to-brand-b transition-[width]"
                style={{ width: `${Math.round((progress.done / Math.max(progress.total, 1)) * 100)}%` }}
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {failed.length > 0 && (
        <div className="rounded-2xl border border-amber-400/30 bg-amber-400/10 p-4 text-sm text-amber-200">
          <p className="font-semibold">{t('survival.bundleFailed')}</p>
          <ul className="mt-1 list-inside list-disc text-xs">
            {failed.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      )}

      {profiles === null ? (
        <p className="text-novo-muted">{t('common.loading')}</p>
      ) : (
        <>
          {profiles.length > 0 && (
            <div className="glass flex flex-wrap items-center gap-2 rounded-3xl p-3">
              {profiles.map((p) => {
                const name = instances?.find((i) => i.id === p.instanceId)?.name ?? p.instanceId;
                const mine = p.instanceId === selected && !picking;
                return (
                  <button
                    key={p.instanceId}
                    type="button"
                    onClick={() => {
                      setSelected(p.instanceId);
                      setPicking(false);
                    }}
                    className={`flex items-center gap-2 rounded-2xl border py-2 pr-3.5 pl-3 text-sm font-semibold transition-colors ${
                      mine ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
                    }`}
                  >
                    <span className="grid h-6 w-6 place-items-center rounded-lg bg-gradient-to-br from-brand-a/40 to-brand-b/40 text-[10px] font-black text-white">
                      {p.edition.replace('.', '').slice(0, 2)}
                    </span>
                    <span className="max-w-40 truncate">{name}</span>
                    <span className="font-mono text-[11px] text-novo-muted">{p.installed.length}</span>
                  </button>
                );
              })}
              <button
                type="button"
                onClick={() => setPicking(true)}
                className={`ml-auto flex items-center gap-1.5 rounded-2xl border px-3.5 py-2 text-sm font-semibold transition-colors ${
                  picking ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
                }`}
              >
                <Plus size={15} /> {t('survival.newInstall')}
              </button>
            </div>
          )}

          {showPicker ? (
            <section className="grid gap-4 md:grid-cols-2">
              {editions.map((edition) => (
                <motion.div
                  key={edition.id}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`relative overflow-hidden rounded-3xl border p-6 ${
                    edition.recommended
                      ? 'border-accent/40 bg-gradient-to-br from-brand-a/15 via-novo-panel/70 to-novo-panel/70'
                      : 'border-novo-border bg-novo-elev'
                  }`}
                >
                  {edition.recommended && (
                    <span className="absolute top-5 right-5 rounded-full bg-accent/20 px-2.5 py-1 text-[10px] font-bold tracking-wider text-accent uppercase">
                      {t('survival.recommended')}
                    </span>
                  )}
                  <div className="flex items-baseline gap-2">
                    <h2 className="text-3xl font-black tracking-tight">{edition.label}</h2>
                    <span className="font-mono text-xs text-novo-muted">Java {edition.java}</span>
                  </div>
                  <p className="mt-2 text-sm text-novo-muted">{edition.blurb}</p>
                  <ul className="mt-4 flex flex-col gap-1.5 text-sm">
                    {['survival.featureAnims', 'survival.featureShaders', 'survival.featureLibraries', 'survival.featureVillages'].map((key) => (
                      <li key={key} className="flex items-center gap-2">
                        <Check size={14} className="text-play" /> {t(key as Key)}
                      </li>
                    ))}
                  </ul>
                  <button
                    type="button"
                    disabled={creating !== null}
                    onClick={() => void create(edition.id)}
                    className="mt-6 flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b py-3 font-bold text-white transition-all hover:brightness-110 active:scale-[0.98] disabled:opacity-60"
                  >
                    {creating === edition.id ? <Loader2 size={17} className="animate-spin" /> : <Plus size={17} />}
                    {creating === edition.id ? t('survival.creating') : t('survival.create')}
                  </button>
                  {edition.recommended && creating === null && (
                    <p className="mt-2 text-center text-[11px] text-novo-muted">{t('survival.createHint')}</p>
                  )}
                </motion.div>
              ))}
              <p className="text-xs text-novo-muted md:col-span-2">{t('survival.javaNote')}</p>
            </section>
          ) : (
            profile && (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  {(
                    [
                      ['setup', 'survival.tabSetup'],
                      ['shaders', 'survival.tabShaders'],
                      ['packs', 'survival.tabPacks'],
                    ] as [Tab, Key][]
                  ).map(([id, label]) => (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setTab(id)}
                      className={`rounded-full border px-4 py-2 text-sm font-semibold transition-all ${
                        tab === id
                          ? 'border-accent/50 bg-accent/15 shadow-[0_0_20px_-6px_var(--color-accent)]'
                          : 'border-novo-border text-novo-muted hover:text-novo-text'
                      }`}
                    >
                      {t(label)}
                    </button>
                  ))}
                  <span className="ml-auto flex items-center gap-2 text-xs text-novo-muted">
                    <Trees size={13} /> {instance?.name ?? profile.instanceId}
                  </span>
                </div>

                {tab === 'setup' && (
                  <>
                    <section className="rounded-3xl border border-accent/25 bg-gradient-to-r from-brand-a/10 to-novo-panel/60 p-5">
                      <div className="flex flex-wrap items-center justify-between gap-4">
                        <div>
                          <h2 className="text-lg font-bold">{t('survival.bundle')}</h2>
                          <p className="text-sm text-novo-muted">{t('survival.bundleHint', { done: bundleDone, total: bundle.length })}</p>
                        </div>
                        <button
                          type="button"
                          disabled={busy !== null || bundleDone === bundle.length}
                          onClick={() => void installBundle()}
                          className="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-5 py-2.5 text-sm font-bold text-white transition-all hover:brightness-110 active:scale-[0.98] disabled:opacity-50"
                        >
                          {busy === 'bundle' ? <Loader2 size={16} className="animate-spin" /> : <Download size={16} />}
                          {busy === 'bundle'
                            ? t('survival.installingBundle')
                            : bundleDone === bundle.length
                              ? t('survival.bundleReady')
                              : bundleDone > 0
                                ? t('survival.finishSetup')
                                : t('survival.installAll')}
                        </button>
                      </div>
                      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-novo-bg/70">
                        <div
                          className="h-full rounded-full bg-gradient-to-r from-brand-a to-brand-b transition-[width]"
                          style={{ width: `${Math.round((bundleDone / Math.max(bundle.length, 1)) * 100)}%` }}
                        />
                      </div>
                    </section>

                    {CATEGORIES.map((category) => {
                      const items = mods.filter((item) => item.category === category.id);
                      if (items.length === 0) return null;
                      return (
                        <section key={category.id} className="flex flex-col gap-2">
                          <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t(category.label)}</h3>
                          <div className="grid gap-2 lg:grid-cols-2">
                            {items.map((item) => (
                              <ItemCard
                                key={item.slug}
                                item={item}
                                installed={installed.get(item.slug)}
                                busy={busy === item.slug || busy === 'bundle'}
                                onInstall={() => void install(item.slug)}
                                onRemove={() => void remove(item.slug)}
                              />
                            ))}
                          </div>
                        </section>
                      );
                    })}
                  </>
                )}

                {tab !== 'setup' && (
                  <>
                    <p className="text-xs text-novo-muted">
                      {tab === 'shaders' ? t('survival.shaderHint') : t('survival.packHint')}
                    </p>
                    <form onSubmit={(e) => void search(e)} className="flex items-center gap-2">
                      <div className="flex flex-1 items-center gap-2 rounded-2xl border border-novo-border bg-novo-bg/60 px-4 py-2.5 focus-within:border-accent/60">
                        <Search size={15} className="text-novo-muted" />
                        <input
                          value={query}
                          onChange={(e) => setQuery(e.target.value)}
                          placeholder={t('survival.searchPlaceholder')}
                          spellCheck={false}
                          className="w-full bg-transparent text-sm outline-none placeholder:text-novo-muted"
                        />
                      </div>
                      <button
                        type="submit"
                        disabled={searching || !profile}
                        className="rounded-2xl bg-novo-elev-2 px-4 py-2.5 text-sm font-bold transition-colors hover:bg-gradient-to-r hover:from-brand-a hover:to-brand-b hover:text-white disabled:opacity-50"
                      >
                        {searching ? <Loader2 size={15} className="animate-spin" /> : t('survival.search')}
                      </button>
                    </form>

                    <section className="flex flex-col gap-2">
                      <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">
                        {tab === 'shaders' ? t('survival.catShader') : t('survival.catPacks')}
                      </h3>
                      <div className="grid gap-2 lg:grid-cols-2">
                        {featured.map((item) => (
                          <ItemCard
                            key={item.slug}
                            item={item}
                            installed={installed.get(item.slug)}
                            busy={busy === item.slug || busy === 'bundle'}
                            onInstall={() => void install(item.slug)}
                            onRemove={() => void remove(item.slug)}
                          />
                        ))}
                      </div>
                    </section>

                    {hits !== null && (
                      <section className="flex flex-col gap-2">
                        <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('survival.searchResults')}</h3>
                        {hits.length === 0 ? (
                          <p className="rounded-2xl border border-novo-border bg-novo-elev p-4 text-sm text-novo-muted">{t('survival.noResults')}</p>
                        ) : (
                          <div className="grid gap-2 lg:grid-cols-2">
                            {hits.map((hit) => (
                              <HitCard
                                key={hit.slug}
                                hit={hit}
                                installed={installed.has(hit.slug)}
                                busy={busy === hit.slug}
                                onInstall={() => void install(hit.slug, tab === 'shaders' ? 'shader' : 'pack')}
                              />
                            ))}
                          </div>
                        )}
                      </section>
                    )}
                  </>
                )}
              </>
            )
          )}
        </>
      )}

      {!profile && profiles?.length === 0 && (
        <button
          type="button"
          onClick={() => onNavigate('instances')}
          className="text-left text-xs text-novo-muted underline decoration-dotted underline-offset-4 hover:text-novo-text"
        >
          {t('survival.instancesHint')}
        </button>
      )}
    </div>
  );
}
