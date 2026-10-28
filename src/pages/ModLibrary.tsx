import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Check, ChevronDown, Gauge, LayoutDashboard, Move, Plus, RotateCcw, Search, Sparkles, Swords } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { InstanceIcon } from '../components/InstanceIcon';
import type { PageId } from '../components/Sidebar';
import { Switch } from '../components/Switch';
import { api, type ClientConfig } from '../lib/api';
import { novoSupported } from '../lib/loaders';
import { CATEGORIES, editModule, isEnabled, MODULES, settingValue, type ModuleDef, type SettingDef, type SettingValue } from '../lib/catalog';
import { useT, type Key } from '../lib/i18n';
import { useInstances } from '../lib/instances';

const CATEGORY_ICON: Record<string, LucideIcon> = { hud: LayoutDashboard, qol: Sparkles, performance: Gauge };
const CATEGORY_KEY: Record<string, Key> = { all: 'mods.all', hud: 'mods.catHud', visual: 'mods.catVisual', qol: 'mods.catQol', performance: 'mods.catPerformance' };

/** Instances the Novo Client runs in. */
const eligible = novoSupported;

function SettingControl({ def, value, onChange }: { def: SettingDef; value: SettingValue; onChange: (v: SettingValue) => void }) {
  switch (def.type) {
    case 'toggle':
      return <Switch size="sm" on={value === true} onChange={onChange} label={def.label} />;
    case 'color':
      return (
        <label className="flex items-center gap-2">
          <span className="font-mono text-xs text-novo-muted uppercase">{String(value)}</span>
          <span className="relative h-7 w-7 overflow-hidden rounded-lg ring-1 ring-white/20" style={{ background: String(value) }}>
            <input
              type="color"
              value={String(value)}
              onChange={(e) => onChange(e.target.value.toUpperCase())}
              className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
              aria-label={def.label}
            />
          </span>
        </label>
      );
    case 'number':
      return (
        <div className="flex items-center gap-3">
          <input
            type="range"
            min={def.min}
            max={def.max}
            step={def.step ?? 1}
            value={Number(value)}
            onChange={(e) => onChange(Number(e.target.value))}
            className="w-36 accent-[var(--color-accent)]"
            aria-label={def.label}
          />
          <span className="w-12 text-right font-mono text-sm font-semibold">{Number(value)}</span>
        </div>
      );
    case 'text':
      return (
        <input
          value={String(value)}
          maxLength={def.maxLength}
          onChange={(e) => onChange(e.target.value)}
          spellCheck={false}
          aria-label={def.label}
          className="w-40 rounded-lg border border-novo-border bg-novo-bg/70 px-3 py-1.5 text-sm outline-none focus:border-accent/60"
        />
      );
    case 'choice':
      return (
        <div className="flex gap-1 rounded-xl bg-novo-bg/70 p-1">
          {def.options?.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => onChange(o.value)}
              className={`rounded-lg px-2.5 py-1 text-xs font-semibold transition-colors ${
                value === o.value ? 'bg-accent/20 text-novo-text' : 'text-novo-muted hover:text-novo-text'
              }`}
            >
              {o.label}
            </button>
          ))}
        </div>
      );
  }
}

function ModuleCard({
  def,
  config,
  onChange,
  disabled,
}: {
  def: ModuleDef;
  config: ClientConfig;
  onChange: (next: ClientConfig) => void;
  disabled: boolean;
}) {
  const [open, setOpen] = useState(false);
  const t = useT();
  const on = isEnabled(config, def);
  const Icon = CATEGORY_ICON[def.category] ?? Sparkles;
  const expandable = def.settings.length > 0 || !!def.hud;
  const moved = config.modules?.[def.id]?.x !== undefined;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.96 }}
      transition={{ type: 'spring', stiffness: 420, damping: 34 }}
      className={`overflow-hidden rounded-2xl border transition-colors ${
        on ? 'border-accent/40 bg-gradient-to-br from-brand-a/12 via-novo-panel/60 to-novo-panel/60' : 'border-novo-border bg-novo-elev'
      }`}
    >
      <div
        className={`flex items-center gap-3 p-4 ${expandable ? 'cursor-pointer' : ''}`}
        onClick={() => expandable && setOpen((o) => !o)}
      >
        <div
          className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ring-1 ring-white/10 transition-colors ${
            on ? 'bg-gradient-to-br from-brand-a/40 to-brand-b/40 text-white' : 'bg-novo-elev-2 text-novo-muted'
          }`}
        >
          <Icon size={18} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-semibold">{def.name}</div>
          <div className="truncate text-xs text-novo-muted" title={def.description}>
            {def.description}
          </div>
        </div>
        {expandable && <ChevronDown size={16} className={`text-novo-muted transition-transform ${open ? 'rotate-180' : ''}`} />}
        <Switch
          on={on}
          disabled={disabled}
          label={t('mods.toggle', { name: def.name })}
          onChange={(v) => onChange(editModule(config, def.id, (e) => void (e.enabled = v)))}
        />
      </div>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
          >
            <div className="flex flex-col gap-3 border-t border-novo-border px-4 py-3">
              {def.settings.map((s) => (
                <div key={s.key} className="flex items-center justify-between gap-4">
                  <span className="text-sm text-novo-muted">{s.label}</span>
                  <SettingControl
                    def={s}
                    value={settingValue(config, def, s)}
                    onChange={(v) => onChange(editModule(config, def.id, (e) => void (e.settings![s.key] = v)))}
                  />
                </div>
              ))}
              {def.hud && (
                <div className="flex items-center justify-between gap-4 rounded-xl bg-novo-bg/50 px-3 py-2 text-xs text-novo-muted">
                  <span className="flex items-center gap-1.5">
                    <Move size={13} /> {t('mods.moveHint')} <kbd className="rounded bg-novo-elev-2 px-1.5 font-mono">Right Shift</kbd>
                  </span>
                  {moved && (
                    <button
                      type="button"
                      onClick={() =>
                        onChange(
                          editModule(config, def.id, (e) => {
                            delete e.x;
                            delete e.y;
                            delete e.scale;
                          }),
                        )
                      }
                      className="flex items-center gap-1 rounded-lg px-2 py-1 font-semibold hover:bg-novo-hover hover:text-novo-text"
                    >
                      <RotateCcw size={12} /> {t('mods.resetPosition')}
                    </button>
                  )}
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

export function ModLibraryPage({ onNavigate }: { onNavigate: (p: PageId) => void }) {
  const { instances, update } = useInstances();
  const targets = useMemo(() => (instances ?? []).filter(eligible), [instances]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [config, setConfig] = useState<ClientConfig | null>(null);
  const [category, setCategory] = useState<string>('all');
  const [query, setQuery] = useState('');
  const [saved, setSaved] = useState(false);
  const t = useT();
  const [error, setError] = useState<string | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const selected = targets.find((i) => i.id === selectedId) ?? targets[0] ?? null;

  useEffect(() => {
    setConfig(null);
    if (selected) api.getClientConfig(selected.id).then(setConfig, (e) => setError(String(e)));
  }, [selected?.id]);

  function change(next: ClientConfig) {
    if (!selected) return;
    setConfig(next);
    clearTimeout(saveTimer.current);
    const id = selected.id;
    saveTimer.current = setTimeout(() => {
      api.saveClientConfig(id, next).then(
        () => {
          setError(null);
          setSaved(true);
          setTimeout(() => setSaved(false), 1400);
        },
        (e) => setError(String(e)),
      );
    }, 300);
  }

  // Each client has its own subset: modern mods for Fabric, everything but the modern-only ones for 1.8.9.
  const available = selected && selected.loader === 'fabric' ? MODULES.filter((m) => m.modern) : MODULES.filter((m) => m.legacy !== false);
  const shown = available.filter(
    (m) =>
      (category === 'all' || m.category === category) &&
      (!query.trim() || `${m.name} ${m.description}`.toLowerCase().includes(query.trim().toLowerCase())),
  );
  const onCount = config ? available.filter((m) => isEnabled(config, m)).length : 0;

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6 p-8 pb-36">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="flex items-center gap-2 text-xs font-bold tracking-[0.3em] text-brand-c uppercase">
            <Swords size={13} /> Novo Client
          </p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('nav.mods')}</h1>
          <p className="mt-2 text-novo-muted">{t('mods.subtitle')}</p>
        </div>
        <AnimatePresence>
          {saved && (
            <motion.span
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              className="flex items-center gap-1.5 rounded-full bg-play/15 px-3 py-1 text-sm font-semibold text-play"
            >
              <Check size={14} /> {t('mods.saved')}
            </motion.span>
          )}
        </AnimatePresence>
      </header>

      {instances !== null && targets.length === 0 ? (
        <button
          onClick={() => onNavigate('instances')}
          className="grid place-items-center rounded-3xl border border-dashed border-novo-border-strong py-14 text-center transition-colors hover:border-accent/50 hover:bg-accent/5"
        >
          <div style={{ animation: 'float 5s ease-in-out infinite' }}>
            <InstanceIcon name="Hypixel PvP" size={64} />
          </div>
          <p className="mt-4 font-semibold">{t('mods.noInstance')}</p>
          <p className="flex items-center gap-1 text-novo-muted">
            <Plus size={14} /> {t('mods.noInstanceHint')}
          </p>
        </button>
      ) : (
        selected && (
          <>
            <section className="glass flex flex-wrap items-center gap-4 rounded-3xl p-4">
              <div className="flex flex-1 flex-wrap gap-2">
                {targets.map((i) => (
                  <button
                    key={i.id}
                    onClick={() => setSelectedId(i.id)}
                    className={`flex items-center gap-2 rounded-xl border py-1.5 pr-3 pl-1.5 text-sm font-semibold transition-colors ${
                      i.id === selected.id ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
                    }`}
                  >
                    <InstanceIcon name={i.name} size={26} />
                    {i.name}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-3 rounded-2xl bg-novo-bg/60 py-2 pr-2 pl-4">
                <div className="text-right">
                  <div className="text-sm font-semibold">Novo Client</div>
                  <div className="text-xs text-novo-muted">{selected.clientMod ? t('mods.onCount', { on: onCount, total: available.length }) : t('mods.notInstalled')}</div>
                </div>
                <Switch
                  on={selected.clientMod}
                  label={t('mods.clientToggle')}
                  onChange={(clientMod) => update({ ...selected, clientMod }).catch((e) => setError(String(e)))}
                />
              </div>
            </section>

            {error && <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-danger">{error}</p>}

            <div className="flex flex-wrap items-center gap-2">
              {[{ id: 'all', name: 'All' }, ...CATEGORIES].map((c) => {
                const Icon = CATEGORY_ICON[c.id];
                return (
                  <button
                    key={c.id}
                    onClick={() => setCategory(c.id)}
                    className={`flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold transition-all ${
                      category === c.id
                        ? 'border-accent/50 bg-accent/15 shadow-[0_0_20px_-6px_var(--color-accent)]'
                        : 'border-novo-border text-novo-muted hover:text-novo-text'
                    }`}
                  >
                    {Icon && <Icon size={15} className={category === c.id ? 'text-accent' : ''} />}
                    {CATEGORY_KEY[c.id] ? t(CATEGORY_KEY[c.id]) : c.name}
                  </button>
                );
              })}
              <div className="ml-auto flex items-center gap-2 rounded-full border border-novo-border bg-novo-bg/60 px-4 py-2 focus-within:border-accent/60">
                <Search size={15} className="text-novo-muted" />
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t('mods.search')}
                  spellCheck={false}
                  className="w-36 bg-transparent text-sm outline-none placeholder:text-novo-muted"
                />
              </div>
            </div>

            <div className={`transition-opacity ${selected.clientMod ? '' : 'pointer-events-none opacity-40'}`}>
              {config ? (
                <motion.div layout className="grid items-start gap-3 md:grid-cols-2">
                  <AnimatePresence mode="popLayout">
                    {shown.map((m) => (
                      <ModuleCard key={m.id} def={m} config={config} onChange={change} disabled={!selected.clientMod} />
                    ))}
                  </AnimatePresence>
                </motion.div>
              ) : (
                <p className="text-novo-muted">{t('common.loading')}</p>
              )}
              {config && shown.length === 0 && <p className="p-8 text-center text-novo-muted">{t('mods.noMatch')}</p>}
            </div>
          </>
        )
      )}
    </div>
  );
}
