import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { isTauri } from '@tauri-apps/api/core';
import { AnimatePresence, motion } from 'motion/react';
import {
  AlertTriangle,
  Check,
  FolderOpen,
  Loader2,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Server,
  SlidersHorizontal,
  Square,
  Terminal,
  Trash2,
  X,
} from 'lucide-react';
import { api, onServerLog, onServerState, type LocalServer, type ModMatch, type ServerStage, type ServerState } from '../lib/api';
import { useT, type Key } from '../lib/i18n';
import { useInstances } from '../lib/instances';
import { loaderLabel } from '../lib/loaders';
import { FIELD_KEYS, SERVER_FIELD_GROUPS, SERVER_FIELDS, fieldsOf, type ServerField } from '../lib/serverProperties';

const STAGE_STYLE: Record<ServerStage, string> = {
  starting: 'bg-amber-400/15 text-amber-300',
  running: 'bg-play/15 text-play',
  stopping: 'bg-amber-400/15 text-amber-300',
  stopped: 'bg-novo-elev-2 text-novo-muted',
  failed: 'bg-danger/15 text-danger',
};

const STAGE_KEY: Record<ServerStage, Key> = {
  starting: 'servers.stage.starting',
  running: 'servers.stage.running',
  stopping: 'servers.stage.stopping',
  stopped: 'servers.stage.stopped',
  failed: 'servers.stage.failed',
};

const loaderOf = (server: LocalServer) =>
  server.loader === 'fabric' && server.loaderVersion ? `Fabric ${server.loaderVersion}` : server.loader === 'forge' ? 'Forge' : 'Vanilla';

function MatchPanel({ match, busy, onApply }: { match: ModMatch; busy: boolean; onApply: () => void }) {
  const t = useT();
  const aligned = match.missing.length === 0 && match.differing.length === 0 && match.extra.length === 0;
  const row = (label: string, files: string[], tone: string) =>
    files.length > 0 && (
      <div key={label} className="flex gap-2 text-xs">
        <span className={`w-28 shrink-0 font-semibold ${tone}`}>
          {label} ({files.length})
        </span>
        <span className="min-w-0 flex-1 break-words font-mono text-novo-muted">{files.join(', ')}</span>
      </div>
    );

  return (
    <div className="flex flex-col gap-2 rounded-2xl border border-novo-border bg-novo-bg/50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm font-semibold">
          {aligned ? (
            <span className="flex items-center gap-2 text-play">
              <Check size={15} strokeWidth={3} /> {t('servers.matchClean')}
            </span>
          ) : (
            t('servers.matchCounts', {
              instance: match.instanceMods,
              server: match.serverMods,
              different: match.missing.length + match.differing.length,
            })
          )}
        </p>
        {!aligned && (
          <button
            type="button"
            disabled={busy}
            onClick={onApply}
            className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b px-3.5 py-2 text-xs font-bold text-white transition-all hover:brightness-110 disabled:opacity-50"
          >
            {busy ? <Loader2 size={13} className="animate-spin" /> : <RefreshCw size={13} />} {t('servers.matchApply')}
          </button>
        )}
      </div>
      {row(t('servers.matchMissing'), match.missing, 'text-amber-300')}
      {row(t('servers.matchDiffering'), match.differing, 'text-accent')}
      {row(t('servers.matchExtra'), match.extra, 'text-novo-muted')}
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${checked ? 'bg-accent' : 'bg-novo-border-strong'}`}
    >
      <span
        className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all ${checked ? 'left-[22px]' : 'left-0.5'}`}
      />
    </button>
  );
}

const INPUT_CLASS =
  'rounded-lg border border-novo-border bg-novo-bg/70 px-3 py-1.5 text-sm text-novo-text outline-none transition-colors focus:border-accent/60';

function FieldRow({ field, value, onChange }: { field: ServerField; value: string; onChange: (value: string) => void }) {
  if (field.kind === 'bool') {
    return (
      <div className="flex flex-col gap-0.5 py-2">
        <div className="flex items-center justify-between gap-3">
          <span className="text-sm">{field.label}</span>
          <Toggle checked={value === 'true'} onChange={(on) => onChange(on ? 'true' : 'false')} />
        </div>
        {field.hint && <span className="text-[11px] text-novo-muted">{field.hint}</span>}
      </div>
    );
  }

  const options = field.options ?? [];
  return (
    <div className="flex flex-col gap-1 py-2">
      <span className="text-sm">{field.label}</span>
      {field.kind === 'select' ? (
        <select value={value} onChange={(e) => onChange(e.target.value)} className={INPUT_CLASS}>
          {value === '' && <option value="">—</option>}
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
          {value !== '' && !options.includes(value) && <option value={value}>{value}</option>}
        </select>
      ) : (
        <input
          value={value}
          onChange={(e) => onChange(field.kind === 'number' ? e.target.value.replace(/[^0-9-]/g, '') : e.target.value)}
          placeholder={field.placeholder}
          inputMode={field.kind === 'number' ? 'numeric' : 'text'}
          spellCheck={false}
          className={`${INPUT_CLASS} font-mono`}
        />
      )}
      {field.hint && <span className="text-[11px] text-novo-muted">{field.hint}</span>}
    </div>
  );
}

function SettingsPanel({ server, onSaved }: { server: LocalServer; onSaved: (server: LocalServer) => void }) {
  const t = useT();
  const [draft, setDraft] = useState<Record<string, string> | null>(null);
  const [extra, setExtra] = useState<string[]>([]);
  /** Keys already in the file, so an emptied optional field can still be saved. */
  const [present, setPresent] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    setSaved(false);
    api.readServerProperties(server.id).then(
      (properties) => {
        const values: Record<string, string> = {};
        for (const property of properties) values[property.key] = property.value;
        setDraft(values);
        setPresent(new Set(properties.map((p) => p.key)));
        setExtra(properties.map((p) => p.key).filter((key) => !FIELD_KEYS.has(key)));
      },
      (e) => setError(String(e)),
    );
  }, [server.id]);

  useEffect(load, [load]);

  function set(key: string, value: string) {
    setDraft((all) => (all ? { ...all, [key]: value } : all));
    setSaved(false);
  }

  async function save() {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      const keys = [...SERVER_FIELDS.map((f) => f.key), ...extra].filter(
        (key) => draft[key] !== undefined && (draft[key] !== '' || present.has(key)),
      );
      const updated = await api.saveServerProperties(
        server.id,
        keys.map((key) => ({ key, value: draft[key] })),
      );
      onSaved(updated);
      setSaved(true);
    } catch (err) {
      setError(String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('servers.settings')}</h3>
          <p className="mt-0.5 text-xs text-novo-muted">{t('servers.settingsHint')}</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={saving}
            onClick={load}
            title={t('servers.settingsReload')}
            className="flex items-center gap-1.5 rounded-lg bg-novo-elev-2 px-2.5 py-1.5 text-xs font-semibold text-novo-muted transition-colors hover:text-novo-text disabled:opacity-40"
          >
            <RotateCcw size={12} /> {t('servers.settingsReload')}
          </button>
          <button
            type="button"
            disabled={saving || !draft}
            onClick={() => void save()}
            className="flex items-center gap-1.5 rounded-lg bg-gradient-to-r from-brand-a to-brand-b px-3 py-1.5 text-xs font-bold text-white transition-all hover:brightness-110 disabled:opacity-50"
          >
            {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />} {t('servers.settingsSave')}
          </button>
        </div>
      </div>

      {error && <p className="rounded-xl border border-danger/30 bg-danger/10 p-2.5 text-xs text-danger">{error}</p>}
      {saved && (
        <p className="flex items-center gap-1.5 text-xs text-play">
          <Check size={13} strokeWidth={3} /> {t('servers.settingsSaved')}
        </p>
      )}

      {!draft ? (
        <p className="text-xs text-novo-muted">{t('common.loading')}</p>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {SERVER_FIELD_GROUPS.map((group) => {
            const fields = fieldsOf(group.id);
            if (fields.length === 0) return null;
            return (
              <section key={group.id} className="rounded-2xl border border-novo-border bg-novo-bg/50 p-3">
                <h4 className="mb-1 text-[10px] font-bold tracking-[0.2em] text-novo-muted uppercase">{group.label}</h4>
                <div className="flex flex-col divide-y divide-novo-border/60">
                  {fields.map((field) => (
                    <FieldRow key={field.key} field={field} value={draft[field.key] ?? ''} onChange={(v) => set(field.key, v)} />
                  ))}
                </div>
              </section>
            );
          })}
          {extra.length > 0 && (
            <section className="rounded-2xl border border-novo-border bg-novo-bg/50 p-3">
              <h4 className="mb-1 text-[10px] font-bold tracking-[0.2em] text-novo-muted uppercase">
                {t('servers.settingsOther')}
              </h4>
              <p className="mb-1 text-[11px] text-novo-muted">{t('servers.settingsOtherHint')}</p>
              <div className="flex flex-col divide-y divide-novo-border/60">
                {extra.map((key) => (
                  <FieldRow
                    key={key}
                    field={{ key, label: key, kind: 'text', group: 'general' }}
                    value={draft[key] ?? ''}
                    onChange={(v) => set(key, v)}
                  />
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

export function ServersPage() {
  const t = useT();
  const { instances } = useInstances();
  const [servers, setServers] = useState<LocalServer[] | null>(null);
  const [states, setStates] = useState<Record<string, ServerState>>({});
  const [logs, setLogs] = useState<Record<string, string[]>>({});
  const [matches, setMatches] = useState<Record<string, ModMatch>>({});
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const [settingsOpen, setSettingsOpen] = useState<Record<string, boolean>>({});
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [instanceId, setInstanceId] = useState('');
  const [port, setPort] = useState('');
  const [memory, setMemory] = useState(4096);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const consoleRef = useRef<HTMLPreElement>(null);

  const refresh = useCallback(() => api.listLocalServers().then(setServers, (e) => setError(String(e))), []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!instanceId && instances?.length) setInstanceId(instances[0].id);
  }, [instances, instanceId]);

  useEffect(() => {
    if (!isTauri()) return;
    const offState = onServerState((e) => {
      setStates((all) => ({ ...all, [e.id]: e }));
      if (e.stage === 'stopped' || e.stage === 'failed') refresh();
    });
    const offLog = onServerLog((e) =>
      setLogs((all) => ({ ...all, [e.id]: [...(all[e.id] ?? []), ...e.lines].slice(-800) })),
    );
    return () => {
      offState.then((f) => f());
      offLog.then((f) => f());
    };
  }, [refresh]);

  const shownConsole = servers?.find((s) => open[s.id])?.id ?? null;
  useEffect(() => {
    const el = consoleRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [logs, shownConsole]);

  async function create(e: FormEvent) {
    e.preventDefault();
    if (!instanceId) return;
    setCreating(true);
    setError(null);
    try {
      const made = await api.createLocalServer({
        name,
        instanceId,
        port: port.trim() ? Number(port) : null,
        memoryMb: memory,
      });
      setServers((all) => [made, ...(all ?? [])]);
      setShowForm(false);
      setName('');
      setPort('');
    } catch (err) {
      setError(String(err));
    } finally {
      setCreating(false);
    }
  }

  async function act(id: string, work: () => Promise<unknown>) {
    setBusy(id);
    setError(null);
    try {
      await work();
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(null);
    }
  }

  async function toggleConsole(id: string) {
    const next = !open[id];
    setOpen((all) => ({ ...all, [id]: next }));
    if (next && !matches[id] && servers?.find((s) => s.id === id)?.instanceId) {
      await act(id, async () => {
        const match = await api.matchServerMods(id);
        setMatches((all) => ({ ...all, [id]: match }));
      });
    }
  }

  const forgeBlocked = instances?.find((i) => i.id === instanceId)?.loader === 'forge';

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-8 pb-36">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] text-brand-c uppercase">
            <Server size={13} /> Novo Client
          </p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('servers.title')}</h1>
          <p className="mt-2 max-w-2xl text-novo-muted">{t('servers.subtitle')}</p>
        </div>
        <button
          type="button"
          onClick={() => setShowForm((v) => !v)}
          className="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-5 py-3 font-bold text-white transition-all hover:brightness-110 active:scale-[0.97]"
        >
          {showForm ? <X size={17} /> : <Plus size={17} />} {showForm ? t('common.cancel') : t('servers.new')}
        </button>
      </header>

      {error && <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-sm text-danger">{error}</p>}

      <AnimatePresence>
        {showForm && (
          <motion.form
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            onSubmit={(e) => void create(e)}
            className="flex flex-col gap-4 rounded-3xl border border-accent/30 bg-gradient-to-br from-brand-a/10 via-novo-panel/70 to-novo-panel/70 p-6"
          >
            <div className="grid gap-4 md:grid-cols-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">{t('servers.name')}</span>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t('servers.namePlaceholder')}
                  maxLength={40}
                  spellCheck={false}
                  className="rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 outline-none focus:border-accent/60"
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">{t('servers.matchInstance')}</span>
                <select
                  value={instanceId}
                  onChange={(e) => setInstanceId(e.target.value)}
                  className="rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 outline-none focus:border-accent/60"
                >
                  {(instances ?? []).map((i) => (
                    <option key={i.id} value={i.id} disabled={i.loader === 'forge'}>
                      {i.name} · {i.version} · {loaderLabel(i)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">{t('servers.port')}</span>
                <input
                  value={port}
                  onChange={(e) => setPort(e.target.value.replace(/[^0-9]/g, ''))}
                  placeholder={t('servers.portAuto')}
                  inputMode="numeric"
                  className="rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 outline-none focus:border-accent/60"
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="flex items-center justify-between text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">
                  {t('servers.memory')} <span className="font-mono text-novo-text normal-case">{memory} MB</span>
                </span>
                <input
                  type="range"
                  min={1024}
                  max={8192}
                  step={512}
                  value={memory}
                  onChange={(e) => setMemory(Number(e.target.value))}
                  className="mt-2 accent-[var(--color-accent)]"
                />
              </label>
            </div>

            <p className="text-xs text-novo-muted">{t('servers.matchHint')}</p>
            <p className="flex items-start gap-2 text-xs text-amber-300/90">
              <AlertTriangle size={14} className="mt-0.5 shrink-0" /> {t('servers.eula')}
            </p>
            {forgeBlocked && (
              <p className="text-xs text-danger">{t('servers.forgeUnsupported')}</p>
            )}

            <button
              type="submit"
              disabled={creating || forgeBlocked || !instanceId}
              className="flex items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b py-3 font-bold text-white transition-all hover:brightness-110 active:scale-[0.99] disabled:opacity-50"
            >
              {creating ? <Loader2 size={17} className="animate-spin" /> : <Plus size={17} />}
              {creating ? t('servers.creating') : t('servers.create')}
            </button>
          </motion.form>
        )}
      </AnimatePresence>

      {servers === null ? (
        <p className="text-novo-muted">{t('common.loading')}</p>
      ) : servers.length === 0 ? (
        <button
          type="button"
          onClick={() => setShowForm(true)}
          className="grid place-items-center rounded-3xl border border-dashed border-novo-border-strong py-14 text-center transition-colors hover:border-accent/50 hover:bg-accent/5"
        >
          <Server size={40} className="text-novo-muted" />
          <p className="mt-3 font-semibold">{t('servers.empty')}</p>
          <p className="text-sm text-novo-muted">{t('servers.emptyHint')}</p>
        </button>
      ) : (
        <div className="flex flex-col gap-4">
          {servers.map((server) => {
            const stage = states[server.id]?.stage;
            const live = stage === 'starting' || stage === 'running' || stage === 'stopping' || (server.running && stage === undefined);
            const label: ServerStage = stage ?? (server.running ? 'running' : 'stopped');
            const instance = instances?.find((i) => i.id === server.instanceId);
            return (
              <motion.section
                key={server.id}
                layout
                className="overflow-hidden rounded-3xl border border-novo-border bg-novo-elev"
              >
                <div className="flex flex-wrap items-center gap-3 p-4">
                  <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-brand-a/35 to-brand-b/35 text-white ring-1 ring-white/10">
                    <Server size={19} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-bold">{server.name}</span>
                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold uppercase ${STAGE_STYLE[label]}`}>
                        {t(STAGE_KEY[label])}
                      </span>
                    </div>
                    <p className="truncate text-xs text-novo-muted">
                      {server.version} · {loaderOf(server)} · {t('servers.port')} <span className="font-mono">{server.port}</span>
                      {instance && ` · ${instance.name}`}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      disabled={busy === server.id || live}
                      onClick={() => void act(server.id, () => api.startLocalServer(server.id))}
                      className="flex items-center gap-1.5 rounded-xl bg-play/15 px-3 py-2 text-sm font-bold text-play transition-colors hover:bg-play/25 disabled:opacity-40"
                    >
                      {busy === server.id && !live ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />}
                      {t('servers.start')}
                    </button>
                    <button
                      type="button"
                      disabled={!live}
                      onClick={() => void act(server.id, () => api.stopLocalServer(server.id))}
                      className="flex items-center gap-1.5 rounded-xl bg-novo-elev-2 px-3 py-2 text-sm font-bold transition-colors hover:bg-danger/15 hover:text-danger disabled:opacity-40"
                    >
                      <Square size={14} /> {t('servers.stop')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setSettingsOpen((all) => ({ ...all, [server.id]: !all[server.id] }))}
                      title={t('servers.settingsHint')}
                      className={`flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm font-bold transition-colors ${
                        settingsOpen[server.id] ? 'bg-accent/15 text-accent' : 'bg-novo-elev-2 text-novo-muted hover:text-novo-text'
                      }`}
                    >
                      <SlidersHorizontal size={14} /> {t('servers.settingsButton')}
                    </button>
                    <button
                      type="button"
                      onClick={() => void toggleConsole(server.id)}
                      className={`flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm font-bold transition-colors ${
                        open[server.id] ? 'bg-accent/15 text-accent' : 'bg-novo-elev-2 text-novo-muted hover:text-novo-text'
                      }`}
                    >
                      <Terminal size={14} /> {t('servers.console')}
                    </button>
                    <button
                      type="button"
                      title={t('servers.folder')}
                      onClick={() => void act(server.id, () => api.openLocalServerFolder(server.id))}
                      className="grid h-9 w-9 place-items-center rounded-xl bg-novo-elev-2 text-novo-muted transition-colors hover:text-novo-text"
                    >
                      <FolderOpen size={15} />
                    </button>
                    <button
                      type="button"
                      title={t('servers.delete')}
                      disabled={busy === server.id || live}
                      onClick={() => {
                        if (confirm(t('servers.deleteAsk', { name: server.name }))) void act(server.id, async () => {
                          await api.deleteLocalServer(server.id);
                          await refresh();
                        });
                      }}
                      className="grid h-9 w-9 place-items-center rounded-xl bg-novo-elev-2 text-novo-muted transition-colors hover:bg-danger/15 hover:text-danger disabled:opacity-40"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>

                <AnimatePresence initial={false}>
                  {settingsOpen[server.id] && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="overflow-hidden border-t border-novo-border"
                    >
                      <SettingsPanel
                        server={server}
                        onSaved={(updated) =>
                          setServers((all) =>
                            all?.map((s) => (s.id === updated.id ? { ...s, port: updated.port } : s)) ?? null,
                          )
                        }
                      />
                    </motion.div>
                  )}
                </AnimatePresence>

                <AnimatePresence initial={false}>
                  {open[server.id] && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-novo-border"
                    >
                      <div className="flex flex-col gap-3 p-4">
                        {server.instanceId && (
                          <>
                            <div className="flex items-center justify-between">
                              <h3 className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('servers.match')}</h3>
                              <button
                                type="button"
                                disabled={busy === server.id}
                                onClick={() =>
                                  void act(server.id, async () => {
                                    const match = await api.matchServerMods(server.id);
                                    setMatches((all) => ({ ...all, [server.id]: match }));
                                  })
                                }
                                className="flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-semibold text-novo-muted hover:bg-novo-hover hover:text-novo-text"
                              >
                                <RefreshCw size={12} /> {t('servers.matchCheck')}
                              </button>
                            </div>
                            {matches[server.id] ? (
                              <MatchPanel
                                match={matches[server.id]}
                                busy={busy === server.id}
                                onApply={() =>
                                  void act(server.id, async () => {
                                    const match = await api.applyServerMods(server.id);
                                    setMatches((all) => ({ ...all, [server.id]: match }));
                                  })
                                }
                              />
                            ) : (
                              <p className="text-xs text-novo-muted">{t('servers.matchChecking')}</p>
                            )}
                          </>
                        )}
                        <pre
                          ref={shownConsole === server.id ? consoleRef : undefined}
                          className="max-h-64 overflow-y-auto rounded-2xl bg-black/50 p-3 font-mono text-[11px] leading-relaxed text-novo-muted"
                        >
                          {(logs[server.id] ?? []).join('\n') || t('servers.noLogs')}
                        </pre>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.section>
            );
          })}
        </div>
      )}
    </div>
  );
}
