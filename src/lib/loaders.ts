import type { Instance, Loader } from './api';

/** NovoMC's PvP mods target this version (on Forge), so it's the default. */
export const PVP_VERSION = '1.8.9';
/** Versions whose Forge NovoMC can install (legacy installer format). */
export const FORGE_SUPPORTED = ['1.8.9'];

/** `1.8.9-11.15.1.2318-1.8.9` -> `11.15.1.2318`. */
export const forgeBuild = (full: string) => full.split('-')[1] ?? full;

export function loaderLabel(i: Instance): string {
  return i.loader === 'forge' && i.loaderVersion ? `Forge ${forgeBuild(i.loaderVersion)}` : 'Vanilla';
}

/** The loader a new instance of `version` starts with: Forge where available. */
export const defaultLoader = (version: string): Loader => (FORGE_SUPPORTED.includes(version) ? 'forge' : 'vanilla');
