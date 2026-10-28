// The NovoMC Client's module catalog, shared with the mod itself so the Mod
// Library always matches what the mod supports.
import raw from '../../client-mod/src/main/resources/assets/novomc/catalog.json';
import type { ClientConfig } from './api';

export type SettingValue = boolean | number | string;

export interface SettingDef {
  key: string;
  type: 'toggle' | 'color' | 'number' | 'choice' | 'text';
  label: string;
  default: SettingValue;
  maxLength?: number;
  min?: number;
  max?: number;
  step?: number;
  options?: { value: string; label: string }[];
}

export interface ModuleDef {
  id: string;
  name: string;
  category: string;
  enabled: boolean;
  description: string;
  hud?: { x: number; y: number; stack?: string; center?: boolean };
  settings: SettingDef[];
  /** Also in the modern (Fabric) client. */
  modern?: boolean;
  /** False for modern-only mods (1.9+ combat), which 1.8.9 doesn't have. */
  legacy?: boolean;
}

const catalog = raw as unknown as { categories: { id: string; name: string }[]; modules: ModuleDef[] };

export const CATEGORIES = catalog.categories;
export const MODULES = catalog.modules;

export function isEnabled(cfg: ClientConfig, m: ModuleDef): boolean {
  return cfg.modules?.[m.id]?.enabled ?? m.enabled;
}

export function settingValue(cfg: ClientConfig, m: ModuleDef, s: SettingDef): SettingValue {
  return cfg.modules?.[m.id]?.settings?.[s.key] ?? s.default;
}

/** Returns a copy of `cfg` with one module's entry changed by `edit`. */
export function editModule(
  cfg: ClientConfig,
  id: string,
  edit: (entry: NonNullable<ClientConfig['modules']>[string]) => void,
): ClientConfig {
  const modules = { ...(cfg.modules ?? {}) };
  const entry = { ...(modules[id] ?? {}), settings: { ...(modules[id]?.settings ?? {}) } };
  edit(entry);
  modules[id] = entry;
  return { ...cfg, version: 1, modules };
}
