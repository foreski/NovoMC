import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import {
  AppWindow,
  Check,
  Coffee,
  Cpu,
  FolderOpen,
  Gauge,
  Languages,
  Minimize2,
  Monitor,
  Palette,
  Pipette,
  RefreshCw,
  ArrowDownCircle,
  Bug,
  Sparkles,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Switch } from '../components/Switch';
import { ReportDialog } from '../components/ReportDialog';
import { api, type AfterLaunch, type JavaInstall, type Settings } from '../lib/api';
import { LANGUAGES, systemLang, useI18n, type Key, type Lang } from '../lib/i18n';
import { useUpdates } from '../lib/updates';
import { ACCENT_PRESETS, applyAccent, DEFAULT_ACCENT } from '../lib/theme';

/** Minecraft language codes with their own names. */
const GAME_LANGUAGES: [string, string][] = [
  ['en_US', 'English (US)'],
  ['en_GB', 'English (UK)'],
  ['de_DE', 'Deutsch'],
  ['es_ES', 'Español (España)'],
  ['es_MX', 'Español (México)'],
  ['fr_FR', 'Français'],
  ['it_IT', 'Italiano'],
  ['nl_NL', 'Nederlands'],
  ['pl_PL', 'Polski'],
  ['pt_BR', 'Português (Brasil)'],
  ['pt_PT', 'Português (Portugal)'],
  ['sv_SE', 'Svenska'],
  ['tr_TR', 'Türkçe'],
  ['ru_RU', 'Русский'],
  ['uk_UA', 'Українська'],
  ['ja_JP', '日本語'],
  ['ko_KR', '한국어'],
  ['zh_CN', '简体中文'],
  ['zh_TW', '繁體中文'],
];

const AFTER_LAUNCH: { id: AfterLaunch; label: Key; hint: Key; icon: LucideIcon }[] = [
  { id: 'keep', label: 'set.keepOpen', hint: 'set.keepOpenHint', icon: AppWindow },
  { id: 'minimize', label: 'set.minimize', hint: 'set.minimizeHint', icon: Minimize2 },
  { id: 'close', label: 'set.close', hint: 'set.closeHint', icon: X },
];

const select =
  'w-full rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 text-sm outline-none focus:border-accent/60';

const PRESETS = [
  { label: '854×480', width: 854, height: 480 },
  { label: '1280×720', width: 1280, height: 720 },
  { label: '1600×900', width: 1600, height: 900 },
  { label: '1920×1080', width: 1920, height: 1080 },
];

function Card({
  icon: Icon,
  title,
  text,
  children,
  className = '',
}: {
  icon: LucideIcon;
  title: string;
  text: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`glass rounded-3xl p-6 ${className}`}>
      <div className="flex items-start gap-4">
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-brand-a/30 to-brand-b/30 ring-1 ring-white/10">
          <Icon size={20} className="text-accent" />
        </div>
        <div>
          <h2 className="text-lg font-bold">{title}</h2>
          <p className="text-novo-muted">{text}</p>
        </div>
      </div>
      <div className="mt-5">{children}</div>
    </section>
  );
}

const gb = (mb: number) => `${(mb / 1024).toFixed(mb % 1024 === 0 ? 0 : 1)} GB`;

export function SettingsPage() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [totalMb, setTotalMb] = useState(16384);
  const [dataDir, setDataDir] = useState('');
  const [version, setVersion] = useState('');
  const [reporting, setReporting] = useState(false);
  const [javas, setJavas] = useState<JavaInstall[] | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const { t, choice, setChoice } = useI18n();

  const scanJava = () => {
    setJavas(null);
    api.listJava().then(setJavas, () => setJavas([]));
  };

  useEffect(() => {
    api.getSettings().then(setSettings, (e) => setError(String(e)));
    api.appInfo().then(
      (i) => {
        setTotalMb(i.totalMemoryMb);
        setDataDir(i.dataDir);
        setVersion(i.version);
      },
      () => {},
    );
    scanJava();
  }, []);

  /** Updates locally at once and saves shortly after the last change. */
  function change(patch: Partial<Settings>) {
    if (!settings) return;
    const next = { ...settings, ...patch };
    setSettings(next);
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      api.updateSettings(next).then(
        () => {
          setError(null);
          setSaved(true);
          setTimeout(() => setSaved(false), 1500);
        },
        (e) => setError(String(e)),
      );
    }, 400);
  }

  if (!settings) {
    return <div className="p-8 text-novo-muted">{error ?? t('common.loading')}</div>;
  }

  const maxMb = Math.max(2048, Math.floor(Math.min(totalMb, 32768) / 512) * 512);
  const pct = ((settings.memoryMb - 512) / (maxMb - 512)) * 100;
  const tooMuch = settings.memoryMb > totalMb * 0.75;
  const auto = settings.javaPath === null;
  const res = settings.resolution;
  const setAccent = (accent: string | null) => {
    applyAccent(accent);
    change({ accent });
  };
  const setSize = (key: 'width' | 'height', v: string) => {
    const n = parseInt(v, 10);
    if (!Number.isFinite(n)) return;
    change({ resolution: { width: res?.width ?? 854, height: res?.height ?? 480, [key]: n } });
  };
  const setUiLanguage = (uiLanguage: Lang | null) => {
    setChoice(uiLanguage);
    change({ uiLanguage });
  };

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5 p-8 pb-16">
      <header className="flex items-end justify-between">
        <div>
          <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('home.launcher')}</p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('nav.settings')}</h1>
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
      {error && <p className="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-danger">{error}</p>}

      <div className="grid items-start gap-5 lg:grid-cols-2">
        <Card icon={Cpu} title={t('inst.memory')} text={t('set.memoryText')}>
          <div className="flex items-baseline justify-between">
            <span className="font-mono text-3xl font-black">{gb(settings.memoryMb)}</span>
            <span className="text-xs text-novo-muted">{t('set.ofTotal', { total: gb(totalMb) })}</span>
          </div>
          <input
            type="range"
            min={512}
            max={maxMb}
            step={512}
            value={Math.min(settings.memoryMb, maxMb)}
            onChange={(e) => change({ memoryMb: Number(e.target.value) })}
            className="mt-4 h-2 w-full cursor-pointer appearance-none rounded-full accent-[var(--color-accent)]"
            style={{
              background: `linear-gradient(90deg, var(--color-brand-a), var(--color-brand-b) ${pct}%, rgb(255 255 255 / 0.08) ${pct}%)`,
            }}
          />
          <div className="mt-1 flex justify-between font-mono text-[11px] text-novo-muted">
            <span>512 MB</span>
            <span>{gb(maxMb)}</span>
          </div>
          {tooMuch && <p className="mt-3 text-sm text-[#facc15]">{t('set.tooMuch')}</p>}

          <button
            onClick={() => change({ optimizedFlags: !settings.optimizedFlags })}
            role="switch"
            aria-checked={settings.optimizedFlags}
            className="mt-5 flex w-full items-center gap-4 rounded-2xl border border-novo-border bg-novo-elev p-4 text-left transition-colors hover:border-novo-border-strong"
          >
            <Gauge size={20} className={settings.optimizedFlags ? 'text-play' : 'text-novo-muted'} />
            <div className="flex-1">
              <div className="font-semibold">{t('set.flags')}</div>
              <div className="text-xs text-novo-muted">{t('set.flagsHint')}</div>
            </div>
            <span
              className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${settings.optimizedFlags ? 'bg-play' : 'bg-novo-elev-2'}`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-[left] ${settings.optimizedFlags ? 'left-[22px]' : 'left-0.5'}`}
              />
            </span>
          </button>
        </Card>

        <Card icon={Sparkles} title={t('set.afterLaunch')} text={t('set.afterLaunchText')}>
          <div className="flex flex-col gap-2">
            {AFTER_LAUNCH.map(({ id, label, hint, icon: Icon }) => {
              const on = settings.afterLaunch === id;
              return (
                <button
                  key={id}
                  onClick={() => change({ afterLaunch: id })}
                  className={`flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-all ${
                    on
                      ? 'border-accent/60 bg-accent/10 shadow-[0_0_24px_-8px_var(--color-accent)]'
                      : 'border-novo-border hover:border-novo-border-strong'
                  }`}
                >
                  <Icon size={18} className={`shrink-0 ${on ? 'text-accent' : 'text-novo-muted'}`} />
                  <div>
                    <div className="font-semibold">{t(label)}</div>
                    <div className="text-xs text-novo-muted">{t(hint)}</div>
                  </div>
                  {on && <Check size={16} className="ml-auto shrink-0 text-accent" />}
                </button>
              );
            })}
          </div>
        </Card>

        <Card icon={Monitor} title={t('set.window')} text={t('set.windowText')}>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => change({ resolution: null })}
              className={`rounded-full border px-3 py-1.5 text-sm font-semibold transition-colors ${
                res === null ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
              }`}
            >
              {t('set.default')}
            </button>
            {PRESETS.map((p) => {
              const on = res?.width === p.width && res?.height === p.height;
              return (
                <button
                  key={p.label}
                  onClick={() => change({ resolution: { width: p.width, height: p.height } })}
                  className={`rounded-full border px-3 py-1.5 font-mono text-xs font-semibold transition-colors ${
                    on ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
                  }`}
                >
                  {p.label}
                </button>
              );
            })}
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {(['width', 'height'] as const).map((key, i) => (
              <label key={key} className="flex items-center gap-2">
                {i === 1 && <span className="text-novo-muted">×</span>}
                <input
                  type="number"
                  min={320}
                  value={res ? res[key] : key === 'width' ? 854 : 480}
                  onChange={(e) => setSize(key, e.target.value)}
                  aria-label={t(key === 'width' ? 'set.width' : 'set.height')}
                  className="w-24 rounded-xl border border-novo-border bg-novo-bg/70 px-3 py-2 text-center font-mono text-sm outline-none focus:border-accent/60"
                />
              </label>
            ))}
            <div className="ml-auto flex items-center gap-2 text-sm text-novo-muted">
              {t('set.fullscreen')}
              <Switch on={settings.fullscreen} onChange={(fullscreen) => change({ fullscreen })} label={t('set.fullscreen')} size="sm" />
            </div>
          </div>
        </Card>

        <Card icon={FolderOpen} title={t('set.folder')} text={t('set.folderText')}>
          <div className="flex items-center gap-2">
            <code
              className="min-w-0 flex-1 truncate rounded-xl bg-novo-bg/70 px-3 py-2.5 font-mono text-xs text-novo-muted select-text"
              title={dataDir}
            >
              {dataDir || '…'}
            </code>
            <button
              onClick={() => api.openDataFolder().catch((e) => setError(String(e)))}
              className="flex shrink-0 items-center gap-2 rounded-xl bg-novo-elev-2 px-4 py-2.5 text-sm font-semibold transition-colors hover:bg-novo-hover"
            >
              <FolderOpen size={15} /> {t('set.open')}
            </button>
          </div>
        </Card>

        <UpdatesCard version={version} />

        <Card icon={Bug} title={t('bug.cardTitle')} text={t('bug.cardText')}>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex min-w-0 flex-1 items-center gap-3 text-sm">
              <Switch on={settings.autoReportCrashes} onChange={(autoReportCrashes) => change({ autoReportCrashes })} label={t('bug.auto')} size="sm" />
              <span className="text-novo-muted">{t('bug.auto')}</span>
            </div>
            <button
              onClick={() => setReporting(true)}
              className="flex items-center gap-2 rounded-xl bg-novo-elev-2 px-4 py-2.5 text-sm font-semibold transition-colors hover:bg-novo-hover"
            >
              <Bug size={15} /> {t('bug.title')}
            </button>
          </div>
          <ReportDialog open={reporting} onClose={() => setReporting(false)} />
        </Card>

        <Card icon={Palette} title={t('set.appearance')} text={t('set.appearanceText')}>
          <div className="flex flex-wrap items-center gap-2">
            {ACCENT_PRESETS.map((p) => {
              const current = (settings.accent ?? DEFAULT_ACCENT).toUpperCase() === p.color;
              return (
                <button
                  key={p.color}
                  title={p.name}
                  aria-label={p.name}
                  onClick={() => setAccent(p.color === DEFAULT_ACCENT ? null : p.color)}
                  className={`grid h-9 w-9 place-items-center rounded-xl ring-2 transition-transform hover:scale-110 ${
                    current ? 'ring-white' : 'ring-transparent'
                  }`}
                  style={{ background: p.color, boxShadow: current ? `0 0 18px -4px ${p.color}` : undefined }}
                >
                  {current && <Check size={16} className="text-black/70" />}
                </button>
              );
            })}
            <label
              title={t('set.customColor')}
              className="relative ml-1 flex h-9 cursor-pointer items-center gap-2 rounded-xl border border-novo-border px-3 text-sm font-semibold text-novo-muted transition-colors hover:text-novo-text"
            >
              <Pipette size={15} />
              {t('inst.custom')}
              <input
                type="color"
                value={settings.accent ?? DEFAULT_ACCENT}
                onChange={(e) => setAccent(e.target.value.toUpperCase())}
                className="absolute inset-0 cursor-pointer opacity-0"
                aria-label={t('set.customColor')}
              />
            </label>
          </div>
          <div className="mt-4 flex items-center gap-3 rounded-xl bg-novo-bg/60 p-3 text-sm">
            <span className="rounded-lg bg-accent/20 px-3 py-1 font-semibold text-accent">{t('set.preview')}</span>
            <Switch on label={t('set.preview')} onChange={() => {}} size="sm" />
            <span className="font-mono text-xs text-novo-muted">{(settings.accent ?? DEFAULT_ACCENT).toUpperCase()}</span>
          </div>
        </Card>

        <Card icon={Languages} title={t('set.language')} text={t('set.languageText')}>
          <label className="mb-1.5 block text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">{t('set.uiLanguage')}</label>
          <select value={choice ?? ''} onChange={(e) => setUiLanguage((e.target.value || null) as Lang | null)} className={select}>
            <option value="">{t('set.systemLanguage', { name: LANGUAGES.find((l) => l.code === systemLang())?.name ?? 'English' })}</option>
            {LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.name}
              </option>
            ))}
          </select>
          <label className="mt-4 mb-1.5 block text-xs font-bold tracking-[0.15em] text-novo-muted uppercase">{t('set.gameLanguage')}</label>
          <select value={settings.gameLanguage ?? ''} onChange={(e) => change({ gameLanguage: e.target.value || null })} className={select}>
            <option value="">{t('set.gameLanguageKeep')}</option>
            {GAME_LANGUAGES.map(([code, name]) => (
              <option key={code} value={code}>
                {name}
              </option>
            ))}
          </select>
          <p className="mt-2 text-xs text-novo-muted">{t('set.gameLanguageHint')}</p>
        </Card>

        <Card
          className="lg:col-span-2"
          icon={Coffee}
          title="Java"
          text={t('set.javaText')}
        >
          <div className="flex gap-2">
            {[
              { on: auto, label: t('set.javaAuto'), pick: () => change({ javaPath: null }) },
              { on: !auto, label: t('inst.custom'), pick: () => change({ javaPath: settings.javaPath ?? javas?.[0]?.path ?? '' }) },
            ].map((o) => (
              <button
                key={o.label}
                onClick={o.pick}
                className={`rounded-full border px-4 py-2 text-sm font-semibold transition-all ${
                  o.on ? 'border-accent/50 bg-accent/15' : 'border-novo-border text-novo-muted hover:text-novo-text'
                }`}
              >
                {o.label}
              </button>
            ))}
          </div>
          {!auto && (
            <input
              value={settings.javaPath ?? ''}
              onChange={(e) => change({ javaPath: e.target.value })}
              placeholder="C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe"
              spellCheck={false}
              className="mt-3 w-full rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 font-mono text-xs outline-none focus:border-accent/60"
            />
          )}

          <div className="mt-5 flex items-center justify-between">
            <span className="text-xs font-bold tracking-[0.2em] text-novo-muted uppercase">{t('set.javaFound')}</span>
            <button
              onClick={scanJava}
              aria-label={t('set.scanAgain')}
              className="rounded-lg p-1.5 text-novo-muted hover:bg-novo-hover hover:text-novo-text"
            >
              <RefreshCw size={14} className={javas === null ? 'animate-spin' : ''} />
            </button>
          </div>
          <div className="mt-2 flex flex-col gap-1.5">
            {javas?.length === 0 && <p className="text-sm text-novo-muted">{t('set.javaNone')}</p>}
            {javas?.map((j) => {
              const chosen = !auto && settings.javaPath === j.path;
              return (
                <button
                  key={j.path}
                  onClick={() => change({ javaPath: j.path })}
                  title={t('set.javaUse')}
                  className={`flex items-center gap-3 rounded-xl border px-3 py-2.5 text-left transition-colors ${
                    chosen ? 'border-accent/50 bg-accent/10' : 'border-transparent bg-novo-elev hover:bg-novo-hover'
                  }`}
                >
                  <span className="grid h-9 w-12 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-brand-a/30 to-brand-b/30 font-mono text-sm font-black">
                    {j.major}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 text-sm font-semibold">
                      {j.version}
                      <span className="font-mono text-[10px] text-novo-muted">{j.arch}</span>
                      {j.managed && (
                        <span className="rounded bg-brand-b/20 px-1.5 py-0.5 text-[10px] font-bold text-[#d8b4fe] uppercase">NovoMC</span>
                      )}
                    </div>
                    <div className="truncate font-mono text-[11px] text-novo-muted">{j.path}</div>
                  </div>
                  {chosen && <Check size={16} className="text-accent" />}
                </button>
              );
            })}
          </div>
        </Card>
      </div>
    </div>
  );
}

/** Launcher version, update status and the check / install buttons. */
function UpdatesCard({ version }: { version: string }) {
  const { t } = useI18n();
  const { status, update, progress, error, check, install } = useUpdates();
  const statusText =
    status === 'checking'
      ? t('upd.checking')
      : status === 'available' && update
        ? t('upd.available', { version: update.version })
        : status === 'installing'
          ? t('upd.installing', { pct: progress != null ? Math.floor(progress * 100) : 0 })
          : status === 'current'
            ? t('upd.current')
            : null;

  return (
    <Card icon={ArrowDownCircle} title={t('upd.title')} text={t('upd.text')}>
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm font-bold">NovoMC {version ? `v${version}` : '…'}</div>
          {statusText && <div className={`text-xs ${status === 'available' ? 'text-accent' : 'text-novo-muted'}`}>{statusText}</div>}
          {status === 'error' && error && <div className="text-xs text-danger">{error}</div>}
        </div>
        {status === 'available' || status === 'installing' ? (
          <button
            onClick={install}
            disabled={status === 'installing'}
            className="rounded-xl bg-gradient-to-r from-brand-a to-brand-b px-4 py-2.5 text-sm font-bold text-white transition-all hover:brightness-110 disabled:opacity-70"
          >
            {t('upd.updateNow')}
          </button>
        ) : (
          <button
            onClick={check}
            disabled={status === 'checking'}
            className="flex items-center gap-2 rounded-xl bg-novo-elev-2 px-4 py-2.5 text-sm font-semibold transition-colors hover:bg-novo-hover disabled:opacity-60"
          >
            <RefreshCw size={15} className={status === 'checking' ? 'animate-spin' : ''} /> {t('upd.check')}
          </button>
        )}
      </div>
    </Card>
  );
}
