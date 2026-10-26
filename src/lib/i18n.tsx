// Launcher UI translations. English (locales/en.ts) is the source; every other
// locale is typed against it, so a missing string fails the build.
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import de from '../locales/de';
import en, { type Key } from '../locales/en';
import es from '../locales/es';
import fr from '../locales/fr';
import ptBR from '../locales/pt-BR';
import { api } from './api';

export type { Key };
export type Lang = 'en' | 'de' | 'es' | 'fr' | 'pt-BR';

export const LANGUAGES: { code: Lang; name: string }[] = [
  { code: 'en', name: 'English' },
  { code: 'de', name: 'Deutsch' },
  { code: 'es', name: 'Español' },
  { code: 'fr', name: 'Français' },
  { code: 'pt-BR', name: 'Português (Brasil)' },
];

const DICTIONARIES: Record<Lang, Record<Key, string>> = { en, de, es, fr, 'pt-BR': ptBR };

export type Vars = Record<string, string | number>;
export type T = (key: Key, vars?: Vars) => string;

/** The system language if NovoMC has it, else English. */
export function systemLang(): Lang {
  for (const tag of navigator.languages ?? [navigator.language]) {
    if (tag.toLowerCase().startsWith('pt')) return 'pt-BR';
    const base = tag.slice(0, 2).toLowerCase();
    if (LANGUAGES.some((l) => l.code === base)) return base as Lang;
  }
  return 'en';
}

export function translate(lang: Lang, key: Key, vars?: Vars): string {
  const text = DICTIONARIES[lang][key] ?? en[key];
  return vars ? text.replace(/\{(\w+)\}/g, (m, name: string) => (name in vars ? String(vars[name]) : m)) : text;
}

interface I18nContext {
  lang: Lang;
  /** The saved choice; null follows the system language. */
  choice: Lang | null;
  setChoice: (lang: Lang | null) => void;
  t: T;
}

const Ctx = createContext<I18nContext | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<Lang | null>(null);

  useEffect(() => {
    api.getSettings().then(
      (s) => setChoiceState(LANGUAGES.some((l) => l.code === s.uiLanguage) ? (s.uiLanguage as Lang) : null),
      () => {},
    );
  }, []);

  const lang = choice ?? systemLang();
  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const t = useCallback<T>((key, vars) => translate(lang, key, vars), [lang]);
  return <Ctx.Provider value={{ lang, choice, setChoice: setChoiceState, t }}>{children}</Ctx.Provider>;
}

export function useI18n(): I18nContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>');
  return ctx;
}

/** Shorthand for components that only need `t`. */
export function useT(): T {
  return useI18n().t;
}
