import { useCallback, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Check, Loader2, RotateCcw, Search, Shirt, Upload, UserRound } from 'lucide-react';
import { isTauri } from '@tauri-apps/api/core';
import { ModelStage } from '../components/ModelStage';
import { api, type SkinProfile } from '../lib/api';
import { useAccounts } from '../lib/accounts';
import { useT } from '../lib/i18n';

/** A skin the player looked at or picked, kept so the grid survives a restart. */
interface SavedSkin {
  name: string;
  model: 'classic' | 'slim';
  /** The 64x64 sheet as a data URL. */
  png: string;
  /** Picked from a file on disk rather than looked up by name. */
  local?: boolean;
}

const STORAGE = 'novo.skins';
const MAX_SAVED = 8;

function readSaved(): SavedSkin[] {
  try {
    const raw = localStorage.getItem(STORAGE);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as SavedSkin[]).filter((s) => typeof s?.png === 'string') : [];
  } catch {
    return [];
  }
}

function writeSaved(skins: SavedSkin[]) {
  try { localStorage.setItem(STORAGE, JSON.stringify(skins.slice(0, MAX_SAVED))); } catch { /* cache is best-effort */ }
}

/** Mojang's own rule: 3-16 letters, digits and underscores. */
const NAME = /^[A-Za-z0-9_]{3,16}$/;

/**
 * The launcher's skin changer: a username or a file on the left, the model it
 * becomes on the right, and the sheet uploaded only when Apply is pressed.
 *
 * Every preview is the real player model built from the skin's UV layout, so
 * what is shown is what the game draws. Nothing is bundled: the sheet comes
 * from the player's own account or from a file the player picked.
 */
export function SkinsPage() {
  const t = useT();
  const { active } = useAccounts();
  const [name, setName] = useState('');
  const [model, setModel] = useState<'classic' | 'slim'>('classic');
  const [skin, setSkin] = useState<{ png: string | null; label: string; model: 'classic' | 'slim' } | null>(null);
  const [saved, setSaved] = useState<SavedSkin[]>(readSaved);
  const [busy, setBusy] = useState<'search' | 'apply' | 'reset' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const file = useRef<HTMLInputElement>(null);

  const remember = useCallback((entry: SavedSkin | null) => {
    if (!entry) return;
    setSaved((current) => {
      const next = [entry, ...current.filter((s) => s.png !== entry.png)].slice(0, MAX_SAVED);
      writeSaved(next);
      return next;
    });
  }, []);

  // The account's own skin, so the changer opens on what the player wears now.
  useEffect(() => {
    if (!active || skin) return;
    let live = true;
    void (async () => {
      try {
        const png = await api.getSkinPng(active.uuid);
        if (!live || !png) return;
        setSkin({ png, label: active.username, model: 'classic' });
        remember({ name: active.username, model: 'classic', png });
      } catch {
        /* the search box still works without it */
      }
    })();
    return () => { live = false; };
  }, [active, skin, remember]);

  async function search(event: FormEvent) {
    event.preventDefault();
    const wanted = name.trim();
    if (!NAME.test(wanted)) {
      setError(t('skins.badName'));
      return;
    }
    setBusy('search'); setError(null); setMessage(null);
    try {
      const profile: SkinProfile | null = await api.lookupSkin(wanted);
      if (!profile) {
        setError(t('skins.notFound', { name: wanted }));
        return;
      }
      if (!profile.png) {
        setError(t('skins.defaultSkin', { name: profile.name }));
        return;
      }
      const slim = profile.model === 'slim' ? 'slim' : 'classic';
      setModel(slim);
      setSkin({ png: profile.png, label: profile.name, model: slim });
      remember({ name: profile.name, model: slim, png: profile.png });
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  function pick(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0];
    event.target.value = '';
    if (!chosen) return;
    if (!chosen.name.toLowerCase().endsWith('.png')) {
      setError(t('skins.notPng'));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const png = String(reader.result);
      setError(null); setMessage(null);
      setSkin({ png, label: chosen.name.replace(/\.png$/i, ''), model });
      remember({ name: chosen.name.replace(/\.png$/i, ''), model, png, local: true });
    };
    reader.onerror = () => setError(t('skins.unreadable'));
    reader.readAsDataURL(chosen);
  }

  async function apply() {
    if (!skin?.png) {
      setError(t('skins.pickFirst'));
      return;
    }
    if (!isTauri()) {
      setError(t('skins.needsLauncher'));
      return;
    }
    setBusy('apply'); setError(null); setMessage(null);
    try {
      await api.applySkin(skin.png, model);
      setMessage(t('skins.applied'));
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  async function reset() {
    setBusy('reset'); setError(null); setMessage(null);
    try {
      await api.resetSkin();
      setSkin(null);
      setMessage(t('skins.resetDone'));
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 p-6 pb-32 lg:p-8 lg:pb-32">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('nav.skins')}</p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('skins.title')}</h1>
          <p className="mt-2 max-w-2xl text-novo-muted">{t('skins.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => void reset()} disabled={busy !== null} className="flex items-center gap-2 rounded-2xl border border-novo-border px-4 py-2.5 text-sm font-bold text-novo-muted transition hover:bg-novo-hover hover:text-novo-text disabled:opacity-40">
            {busy === 'reset' ? <Loader2 size={15} className="animate-spin" /> : <RotateCcw size={15} />}
            {t('skins.reset')}
          </button>
          <button onClick={() => void apply()} disabled={busy !== null || !skin?.png} className="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b px-4 py-2.5 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40">
            {busy === 'apply' ? <Loader2 size={15} className="animate-spin" /> : <Check size={15} />}
            {t('skins.apply')}
          </button>
        </div>
      </header>

      {(error || message) && <div role={error ? 'alert' : 'status'} className={`rounded-xl border px-4 py-3 text-sm ${error ? 'border-danger/30 bg-danger/10 text-danger' : 'border-play/30 bg-play/10 text-play'}`}>{error || message}</div>}

      <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_400px]">
        <div className="flex flex-col gap-5">
          <div className="glass rounded-3xl p-5">
            <div className="flex flex-wrap items-center gap-2">
              <form onSubmit={(event) => void search(event)} className="flex min-w-0 flex-1 items-center gap-2 rounded-xl border border-novo-border bg-novo-bg/70 px-3 py-2 focus-within:border-accent/60">
                <UserRound size={16} className="shrink-0 text-novo-muted" />
                <input value={name} onChange={(event) => setName(event.target.value)} maxLength={16} autoComplete="off" spellCheck={false} placeholder={t('skins.searchPlaceholder')} className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
                <button disabled={busy !== null || !name.trim()} className="rounded-lg px-2 py-1 text-xs font-bold text-accent transition hover:bg-accent/10 disabled:opacity-40">
                  {busy === 'search' ? <Loader2 size={14} className="animate-spin" /> : t('skins.search')}
                </button>
              </form>
              <button onClick={() => file.current?.click()} className="flex items-center gap-2 rounded-xl border border-novo-border bg-novo-elev-2 px-4 py-2.5 text-sm font-bold transition hover:bg-novo-hover">
                <Upload size={15} />
                {t('skins.selectFile')}
              </button>
              <input ref={file} type="file" accept="image/png" onChange={pick} className="hidden" />
            </div>
            <p className="mt-2 text-xs text-novo-muted">{t('skins.hint')}</p>

            <div className="mt-5 border-t border-novo-border pt-4">
              <h2 className="font-bold">{t('skins.modelType')}</h2>
              <p className="mt-1 text-sm text-novo-muted">{t('skins.modelHint')}</p>
              <div className="mt-3 inline-flex gap-1 rounded-xl border border-novo-border bg-novo-bg/40 p-1">
                {(['classic', 'slim'] as const).map((value) => (
                  <button key={value} onClick={() => setModel(value)} aria-pressed={model === value} className={`rounded-lg px-4 py-2 text-sm font-bold capitalize transition ${model === value ? 'bg-accent/15 text-accent' : 'text-novo-muted hover:text-novo-text'}`}>{t(`skins.${value}`)}</button>
                ))}
              </div>
            </div>
          </div>

          <div className="glass rounded-3xl p-5">
            <div className="flex items-end justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold">{t('skins.saved')}</h2>
                <p className="mt-1 text-sm text-novo-muted">{t('skins.savedHint')}</p>
              </div>
              {saved.length > 0 && (
                <button onClick={() => { writeSaved([]); setSaved([]); }} className="text-xs font-bold text-novo-muted transition hover:text-novo-text">{t('skins.clear')}</button>
              )}
            </div>
            {saved.length === 0 ? (
              <div className="mt-4 rounded-2xl border border-dashed border-novo-border-strong p-8 text-center">
                <Shirt size={22} className="mx-auto text-brand-c" />
                <p className="mt-2 text-sm text-novo-muted">{t('skins.empty')}</p>
              </div>
            ) : (
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-4">
                {saved.map((entry) => {
                  const selected = skin?.png === entry.png;
                  return (
                    <button key={entry.png.slice(-64)} onClick={() => { setSkin({ png: entry.png, label: entry.name, model: entry.model }); setModel(entry.model); setError(null); }} title={entry.name} className={`group overflow-hidden rounded-2xl border text-left transition ${selected ? 'border-accent/60 bg-accent/10' : 'border-novo-border hover:border-novo-border-strong hover:bg-novo-hover'}`}>
                      <div className="relative h-40 bg-gradient-to-br from-novo-bg to-brand-a/10">
                        <ModelStage item={{ type: 'skin', skinUrl: entry.png, slim: entry.model === 'slim' }} className="absolute inset-0" />
                        {selected && <span className="absolute right-2 top-2 grid h-6 w-6 place-items-center rounded-full bg-play text-black"><Check size={13} /></span>}
                        {entry.local && <span className="absolute left-2 top-2 rounded-full bg-black/55 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white/85">{t('skins.local')}</span>}
                      </div>
                      <div className="px-3 py-2">
                        <p className="truncate text-sm font-bold">{entry.name}</p>
                        <p className="text-[11px] font-semibold uppercase tracking-wider text-novo-muted">{t(`skins.${entry.model}`)}</p>
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <aside className="lg:sticky lg:top-4 lg:self-start">
          <div className="glass overflow-hidden rounded-3xl">
            <div className="flex items-center justify-between border-b border-novo-border px-4 py-3">
              <h2 className="font-bold">{t('skins.preview')}</h2>
              <span className="rounded-full border border-novo-border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-novo-muted">{t('skins.drag')}</span>
            </div>
            <div className="relative aspect-[3/4] bg-gradient-to-b from-brand-a/10 via-transparent to-brand-a/5">
              {skin?.png
                ? <ModelStage item={{ type: 'skin', skinUrl: skin.png, slim: model === 'slim' }} interactive className="absolute inset-0" />
                : <div className="absolute inset-0 grid place-items-center px-6 text-center">
                    <div>
                      <Search size={26} className="mx-auto text-brand-c" />
                      <p className="mt-3 text-sm text-novo-muted">{t('skins.previewEmpty')}</p>
                    </div>
                  </div>}
            </div>
            <div className="border-t border-novo-border p-4">
              <p className="truncate font-bold">{skin?.label ?? t('skins.nobody')}</p>
              <p className="text-xs font-semibold uppercase tracking-wider text-novo-muted">{t(`skins.${model}`)} · 64×64</p>
              <button onClick={() => void apply()} disabled={busy !== null || !skin?.png} className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b py-3 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40">
                {busy === 'apply' ? <Loader2 size={15} className="animate-spin" /> : <Check size={15} />}
                {t('skins.apply')}
              </button>
              {!active && <p className="mt-2 text-center text-[11px] text-novo-muted">{t('skins.needAccount')}</p>}
            </div>
          </div>
        </aside>
      </section>
    </div>
  );
}
