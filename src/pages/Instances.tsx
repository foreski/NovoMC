import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Check, Copy, FolderOpen, Hammer, Loader2, Play, Plus, Search, Settings2, Swords, Trash2 } from 'lucide-react';
import { InstanceIcon } from '../components/InstanceIcon';
import { Modal } from '../components/Modal';
import type { PageId } from '../components/Sidebar';
import { api, type ForgeVersions, type Instance, type JavaInstall, type Loader, type VersionKind, type VersionSummary } from '../lib/api';
import { formatAgo } from '../lib/format';
import { useGame } from '../lib/game';
import { useT, type Key } from '../lib/i18n';
import { useInstalls } from '../lib/installs';
import { useInstances } from '../lib/instances';
import { defaultLoader, FORGE_SUPPORTED, forgeBuild, loaderLabel, PVP_VERSION } from '../lib/loaders';

const KIND_LABEL: Record<VersionKind, Key> = {
  release: 'kind.release',
  snapshot: 'kind.snapshot',
  old_beta: 'kind.beta',
  old_alpha: 'kind.alpha',
  other: 'kind.other',
};

const input =
  'w-full rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 outline-none transition-colors placeholder:text-novo-muted focus:border-accent/60';
const label = 'mb-1.5 block text-xs font-bold tracking-[0.15em] text-novo-muted uppercase';

function useVersions() {
  const [versions, setVersions] = useState<VersionSummary[] | null>(null);
  useEffect(() => {
    api.listVersions(false).then(setVersions, () => setVersions([]));
  }, []);
  return versions;
}

/** Searchable version list; releases only unless "All" is on. */
function VersionSelect({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  const versions = useVersions();
  const t = useT();
  const [query, setQuery] = useState('');
  const [all, setAll] = useState(false);
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (versions ?? []).filter((v) => (q ? v.id.toLowerCase().includes(q) : all || v.kind === 'release'));
  }, [versions, query, all]);

  return (
    <div>
      <div className="flex gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-xl border border-novo-border bg-novo-bg/70 px-3 focus-within:border-accent/60">
          <Search size={14} className="text-novo-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('inst.searchVersions')}
            spellCheck={false}
            className="w-full bg-transparent py-2 text-sm outline-none placeholder:text-novo-muted"
          />
        </div>
        <button
          type="button"
          onClick={() => setAll((a) => !a)}
          className={`rounded-xl border px-3 text-xs font-semibold transition-colors ${
            all ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
          }`}
        >
          {t('inst.snapshotsOld')}
        </button>
      </div>
      <div className="mt-2 max-h-52 overflow-y-auto rounded-xl border border-novo-border bg-novo-bg/40 p-1">
        {versions === null && <p className="p-3 text-sm text-novo-muted">{t('inst.loadingVersions')}</p>}
        {shown.map((v) => (
          <button
            type="button"
            key={v.id}
            onClick={() => onChange(v.id)}
            className={`flex w-full items-center gap-2 rounded-lg px-3 py-1.5 text-left transition-colors ${
              v.id === value ? 'bg-accent/15 text-novo-text' : 'hover:bg-novo-hover'
            }`}
          >
            <span className="font-mono text-sm font-semibold">{v.id}</span>
            {v.id === PVP_VERSION && (
              <span className="flex items-center gap-1 rounded bg-brand-b/20 px-1.5 py-0.5 text-[10px] font-bold text-[#d8b4fe] uppercase">
                <Swords size={10} /> PvP
              </span>
            )}
            {v.kind !== 'release' && <span className="text-[11px] text-novo-muted">{t(KIND_LABEL[v.kind])}</span>}
            <span className="ml-auto flex items-center gap-2">
              {v.installed && <span className="text-[11px] text-play">{t('inst.installed')}</span>}
              {v.id === value && <Check size={14} className="text-accent" />}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}

/** Vanilla / Forge choice, plus the Forge build (recommended preselected). */
function LoaderPicker({
  version,
  loader,
  loaderVersion,
  onChange,
}: {
  version: string;
  loader: Loader;
  loaderVersion: string | null;
  onChange: (loader: Loader, loaderVersion: string | null) => void;
}) {
  const forgeOk = FORGE_SUPPORTED.includes(version);
  const t = useT();
  const [forge, setForge] = useState<ForgeVersions | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setForge(null);
    setError(null);
    if (forgeOk) api.listForgeVersions(version).then(setForge, (e) => setError(String(e)));
  }, [version, forgeOk]);

  // Fall back to vanilla on versions without Forge; pick a build once the list arrives.
  useEffect(() => {
    if (!forgeOk && loader !== 'vanilla') onChange('vanilla', null);
    else if (loader === 'forge' && forge && !(loaderVersion && forge.versions.includes(loaderVersion))) {
      onChange('forge', forge.recommended ?? forge.versions[0] ?? null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forgeOk, forge, loader]);

  const chip = (on: boolean) =>
    `flex items-center gap-2 rounded-xl border px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
      on ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted enabled:hover:text-novo-text'
    }`;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" onClick={() => onChange('vanilla', null)} className={chip(loader === 'vanilla')}>
          Vanilla
        </button>
        <button
          type="button"
          disabled={!forgeOk}
          onClick={() => onChange('forge', loaderVersion)}
          className={chip(loader === 'forge')}
        >
          <Hammer size={14} /> Forge
        </button>
        {loader === 'forge' &&
          (forge ? (
            <select
              value={loaderVersion ?? ''}
              onChange={(e) => onChange('forge', e.target.value)}
              className="rounded-xl border border-novo-border bg-novo-bg/70 px-3 py-2 font-mono text-sm outline-none focus:border-accent/60"
            >
              {forge.versions.map((v) => (
                <option key={v} value={v}>
                  {forgeBuild(v)}
                  {v === forge.recommended ? ` (${t('inst.recommended')})` : ''}
                </option>
              ))}
            </select>
          ) : (
            !error && <Loader2 size={16} className="animate-spin text-novo-muted" />
          ))}
      </div>
      {error && <p className="mt-1.5 text-xs text-danger">{t('inst.forgeLoadError', { error })}</p>}
      {!forgeOk && <p className="mt-1.5 text-xs text-novo-muted">{t('inst.forgeAvailable', { versions: FORGE_SUPPORTED.join(', ') })}</p>}
      {loader === 'forge' && forgeOk && <p className="mt-1.5 text-xs text-novo-muted">{t('inst.forgeAuto')}</p>}
    </div>
  );
}

function CreateDialog({ open, onClose, initialVersion }: { open: boolean; onClose: () => void; initialVersion?: string }) {
  const { create } = useInstances();
  const { start } = useInstalls();
  const t = useT();
  const [name, setName] = useState('');
  const [version, setVersion] = useState(initialVersion ?? PVP_VERSION);
  const [loader, setLoader] = useState<Loader>(defaultLoader(initialVersion ?? PVP_VERSION));
  const [loaderVersion, setLoaderVersion] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) {
      const v = initialVersion ?? PVP_VERSION;
      setName(initialVersion ? `Minecraft ${initialVersion}` : '');
      setVersion(v);
      setLoader(defaultLoader(v));
      setLoaderVersion(null);
      setError(null);
    }
  }, [open, initialVersion]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await create(name.trim() || `Minecraft ${version}`, version, loader, loader === 'forge' ? loaderVersion : null);
      // Start downloading right away so Play is instant later.
      const installed = (await api.listVersions(false)).find((v) => v.id === version)?.installed;
      if (!installed) start(version);
      onClose();
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('inst.new')}>
      <form onSubmit={submit} className="flex flex-col gap-5">
        <div className="flex items-center gap-4">
          <InstanceIcon name={name || `Minecraft ${version}`} size={56} />
          <div className="flex-1">
            <label className={label}>{t('inst.name')}</label>
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={`Minecraft ${version}`}
              maxLength={40}
              className={input}
            />
          </div>
        </div>
        <div>
          <label className={label}>{t('inst.version')}</label>
          <VersionSelect value={version} onChange={setVersion} />
        </div>
        <div>
          <label className={label}>{t('inst.loader')}</label>
          <LoaderPicker
            version={version}
            loader={loader}
            loaderVersion={loaderVersion}
            onChange={(l, lv) => {
              setLoader(l);
              setLoaderVersion(lv);
            }}
          />
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <button
          type="submit"
          disabled={busy || (loader === 'forge' && !loaderVersion)}
          className="relative flex items-center justify-center gap-2 overflow-hidden rounded-2xl bg-gradient-to-r from-brand-a to-brand-b py-3 font-bold text-white shadow-[0_10px_30px_-10px] shadow-brand-b/70 transition-all hover:brightness-110 disabled:opacity-50"
        >
          <span className="shine" />
          {busy ? <Loader2 size={18} className="animate-spin" /> : <Plus size={18} />}
          <span className="relative">{t('inst.create')}</span>
        </button>
      </form>
    </Modal>
  );
}

function EditDialog({ instance, onClose }: { instance: Instance | null; onClose: () => void }) {
  const { update } = useInstances();
  const t = useT();
  const [draft, setDraft] = useState<Instance | null>(instance);
  const [defaultMb, setDefaultMb] = useState(4096);
  const [totalMb, setTotalMb] = useState(16384);
  const [javas, setJavas] = useState<JavaInstall[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraft(instance);
    setError(null);
    if (!instance) return;
    api.getSettings().then((s) => setDefaultMb(s.memoryMb), () => {});
    api.appInfo().then((i) => setTotalMb(i.totalMemoryMb), () => {});
    api.listJava().then(setJavas, () => {});
  }, [instance]);

  async function save(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    try {
      await update(draft);
      onClose();
    } catch (err) {
      setError(String(err));
    }
  }

  const set = (patch: Partial<Instance>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const maxMb = Math.max(2048, Math.floor(Math.min(totalMb, 32768) / 512) * 512);

  return (
    <Modal open={!!instance} onClose={onClose} title={t('inst.settings')} width="max-w-xl">
      {draft && (
        <form onSubmit={save} className="flex flex-col gap-5">
          <div className="flex items-center gap-4">
            <InstanceIcon name={draft.name} size={56} />
            <div className="flex-1">
              <label className={label}>{t('inst.name')}</label>
              <input value={draft.name} onChange={(e) => set({ name: e.target.value })} maxLength={40} className={input} />
            </div>
          </div>
          <div>
            <label className={label}>{t('inst.version')}</label>
            <VersionSelect value={draft.version} onChange={(version) => set({ version })} />
          </div>

          <div>
            <label className={label}>{t('inst.loader')}</label>
            <LoaderPicker
              version={draft.version}
              loader={draft.loader}
              loaderVersion={draft.loaderVersion}
              onChange={(loader, loaderVersion) => set({ loader, loaderVersion })}
            />
          </div>

          <div>
            <label className={label}>{t('inst.memory')}</label>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => set({ memoryMb: null })}
                className={`rounded-xl border px-3 py-2 text-sm font-semibold ${draft.memoryMb === null ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted'}`}
              >
                {t('inst.launcherDefault', { gb: (defaultMb / 1024).toFixed(1) })}
              </button>
              <button
                type="button"
                onClick={() => set({ memoryMb: draft.memoryMb ?? defaultMb })}
                className={`rounded-xl border px-3 py-2 text-sm font-semibold ${draft.memoryMb !== null ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted'}`}
              >
                {t('inst.custom')}
              </button>
            </div>
            {draft.memoryMb !== null && (
              <div className="mt-3 flex items-center gap-4">
                <input
                  type="range"
                  min={512}
                  max={maxMb}
                  step={512}
                  value={Math.min(draft.memoryMb, maxMb)}
                  onChange={(e) => set({ memoryMb: Number(e.target.value) })}
                  className="flex-1 accent-[var(--color-accent)]"
                />
                <span className="w-16 text-right font-mono font-bold">{(draft.memoryMb / 1024).toFixed(1)} GB</span>
              </div>
            )}
          </div>

          <div>
            <label className={label}>Java</label>
            <select
              value={draft.javaPath ?? ''}
              onChange={(e) => set({ javaPath: e.target.value || null })}
              className={`${input} text-sm`}
            >
              <option value="">{t('inst.javaAuto')}</option>
              {javas.map((j) => (
                <option key={j.path} value={j.path}>
                  Java {j.major} ({j.version}) {j.managed ? '· Novo Client' : ''}
                </option>
              ))}
              {draft.javaPath && !javas.some((j) => j.path === draft.javaPath) && <option value={draft.javaPath}>{draft.javaPath}</option>}
            </select>
          </div>

          <div>
            <label className={label}>{t('inst.jvmArgs')}</label>
            <input
              value={draft.jvmArgs}
              onChange={(e) => set({ jvmArgs: e.target.value })}
              placeholder="-Dsome.property=true"
              spellCheck={false}
              className={`${input} font-mono text-xs`}
            />
            <p className="mt-1.5 text-xs text-novo-muted">{t('inst.jvmArgsHint')}</p>
          </div>

          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="rounded-xl px-4 py-2 font-semibold text-novo-muted hover:bg-novo-hover">
              {t('common.cancel')}
            </button>
            <button type="submit" className="rounded-xl bg-gradient-to-r from-brand-a to-brand-b px-5 py-2 font-bold text-white hover:brightness-110">
              {t('common.save')}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}

function DeleteDialog({ instance, onClose }: { instance: Instance | null; onClose: () => void }) {
  const { remove } = useInstances();
  const t = useT();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => setError(null), [instance]);

  return (
    <Modal open={!!instance} onClose={onClose} title={t('inst.delete')}>
      {instance && (
        <div className="flex flex-col gap-5">
          <p>{t('inst.deleteConfirm', { name: instance.name })}</p>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <button onClick={onClose} className="rounded-xl px-4 py-2 font-semibold text-novo-muted hover:bg-novo-hover">
              {t('common.cancel')}
            </button>
            <button
              onClick={() => remove(instance.id).then(onClose, (e) => setError(String(e)))}
              className="rounded-xl bg-danger/20 px-5 py-2 font-bold text-danger hover:bg-danger/30"
            >
              {t('inst.recycle')}
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function InstanceCard({
  instance,
  onEdit,
  onDelete,
  onPlay,
}: {
  instance: Instance;
  onEdit: () => void;
  onDelete: () => void;
  onPlay: () => void;
}) {
  const { duplicate } = useInstances();
  const { launch, busy } = useGame();
  const t = useT();
  const mine = launch?.instanceId === instance.id && busy;
  const action = 'rounded-lg p-2 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text';

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.9, y: 16 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.85, transition: { duration: 0.18 } }}
      transition={{ type: 'spring', stiffness: 400, damping: 32 }}
      className={`glass group relative flex flex-col overflow-hidden rounded-3xl p-5 transition-colors ${mine ? 'border-play/50' : 'hover:border-novo-border-strong'}`}
    >
      {mine && <div className="absolute inset-0 bg-gradient-to-br from-play/10 to-transparent" />}
      <div className="relative flex items-start gap-4">
        <InstanceIcon name={instance.name} size={56} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-lg font-bold">{instance.name}</div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            <span className="rounded-md bg-brand-a/15 px-2 py-0.5 font-mono text-[11px] font-bold text-accent">{instance.version}</span>
            <span
              className={`rounded-md px-2 py-0.5 text-[10px] font-bold tracking-wider uppercase ${
                instance.loader === 'forge' ? 'bg-[#fb923c]/15 text-[#fdba74]' : 'bg-novo-elev-2 text-novo-muted'
              }`}
            >
              {loaderLabel(instance)}
            </span>
            {instance.version === PVP_VERSION && <Swords size={13} className="text-[#d8b4fe]" />}
          </div>
        </div>
      </div>
      <div className="relative mt-3 text-xs text-novo-muted">
        {mine ? (
          <span className="flex items-center gap-1.5 text-play">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-play" />
            {launch?.stage === 'running' ? t('inst.running') : t('inst.starting')}
          </span>
        ) : instance.lastPlayed ? (
          t('inst.played', { ago: formatAgo(instance.lastPlayed) })
        ) : (
          t('inst.neverPlayed')
        )}
      </div>
      <div className="relative mt-4 flex items-center gap-1">
        <button
          onClick={onPlay}
          disabled={busy}
          className="mr-auto flex items-center gap-1.5 rounded-xl bg-gradient-to-b from-[#86efac] to-play px-4 py-2 text-sm font-black tracking-wider text-[#052e12] shadow-[0_6px_20px_-8px_var(--color-play)] transition-transform enabled:hover:scale-[1.04] enabled:active:scale-[0.97] disabled:opacity-40"
        >
          <Play size={13} fill="currentColor" /> {t('home.play')}
        </button>
        <button onClick={onEdit} title={t('nav.settings')} aria-label={t('nav.settings')} className={action}>
          <Settings2 size={16} />
        </button>
        <button
          onClick={() => api.openInstanceFolder(instance.id)}
          title={t('inst.openFolder')}
          aria-label={t('inst.openFolder')}
          className={action}
        >
          <FolderOpen size={16} />
        </button>
        <button onClick={() => duplicate(instance.id)} title={t('inst.duplicate')} aria-label={t('inst.duplicate')} className={action}>
          <Copy size={16} />
        </button>
        <button onClick={onDelete} title={t('common.delete')} aria-label={t('common.delete')} className={`${action} hover:!text-danger`}>
          <Trash2 size={16} />
        </button>
      </div>
    </motion.div>
  );
}

export function InstancesPage({ onNavigate, createFor }: { onNavigate: (p: PageId) => void; createFor?: string }) {
  const { instances, error } = useInstances();
  const { play } = useGame();
  const t = useT();
  const [creating, setCreating] = useState(!!createFor);
  const [editing, setEditing] = useState<Instance | null>(null);
  const [deleting, setDeleting] = useState<Instance | null>(null);

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-8 pb-36">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('inst.eyebrow')}</p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('nav.instances')}</h1>
          <p className="mt-2 text-novo-muted">{t('inst.subtitle')}</p>
        </div>
        <button
          onClick={() => setCreating(true)}
          className="relative flex shrink-0 items-center gap-2 overflow-hidden rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-5 py-3 font-bold text-white shadow-[0_10px_30px_-10px] shadow-brand-b/70 transition-all hover:brightness-110 active:scale-[0.97]"
        >
          <span className="shine" />
          <Plus size={18} className="relative" />
          <span className="relative">{t('inst.new')}</span>
        </button>
      </header>

      {error && <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-danger">{error}</p>}

      {instances?.length === 0 ? (
        <button
          onClick={() => setCreating(true)}
          className="group grid place-items-center rounded-3xl border border-dashed border-novo-border-strong py-16 text-center transition-colors hover:border-accent/50 hover:bg-accent/5"
        >
          <div style={{ animation: 'float 5s ease-in-out infinite' }}>
            <InstanceIcon name="Novo Client" size={64} />
          </div>
          <p className="mt-4 font-semibold">{t('inst.empty')}</p>
          <p className="text-novo-muted">{t('inst.emptyHint')}</p>
        </button>
      ) : (
        <motion.div layout className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <AnimatePresence mode="popLayout">
            {instances?.map((i) => (
              <InstanceCard
                key={i.id}
                instance={i}
                onEdit={() => setEditing(i)}
                onDelete={() => setDeleting(i)}
                onPlay={() => {
                  play(i.id);
                  onNavigate('home');
                }}
              />
            ))}
          </AnimatePresence>
        </motion.div>
      )}

      <CreateDialog open={creating} onClose={() => setCreating(false)} initialVersion={createFor} />
      <EditDialog instance={editing} onClose={() => setEditing(null)} />
      <DeleteDialog instance={deleting} onClose={() => setDeleting(null)} />
    </div>
  );
}
