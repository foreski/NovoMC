import type { Instance, Loader } from './api';

/** NovoMC's PvP mods target this version (on Forge), so it's the default. */
export const PVP_VERSION = '1.8.9';
/** The modern Novo Client's version (on Fabric). */
export const MODERN_VERSION = '26.3';
/** Versions whose Forge Novo Client can install (legacy installer format). */
export const FORGE_SUPPORTED = ['1.8.9'];

/** `1.8.9-11.15.1.2318-1.8.9` -> `11.15.1.2318`. */
export const forgeBuild = (full: string) => full.split('-')[1] ?? full;

export function loaderLabel(i: Instance): string {
  if (i.loader === 'forge' && i.loaderVersion) return `Forge ${forgeBuild(i.loaderVersion)}`;
  if (i.loader === 'fabric' && i.loaderVersion) return `Fabric ${i.loaderVersion}`;
  return 'Vanilla';
}

/** Whether the Novo Client runs in this instance: 1.8.9 Forge, or the modern version on Fabric. */
export const novoSupported = (i: Instance) =>
  (i.loader === 'forge' && i.version === PVP_VERSION) || (i.loader === 'fabric' && i.version === MODERN_VERSION);

/** The loader a new instance of `version` starts with: Forge on 1.8.9, Fabric on the modern Novo version. */
export const defaultLoader = (version: string): Loader =>
  FORGE_SUPPORTED.includes(version) ? 'forge' : version === MODERN_VERSION ? 'fabric' : 'vanilla';
