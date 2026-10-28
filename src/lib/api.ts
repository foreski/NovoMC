// Typed wrappers around the Rust commands. The UI never does game logic itself.
import { invoke } from '@tauri-apps/api/core';
import { listen, type UnlistenFn } from '@tauri-apps/api/event';

export interface AppInfo {
  name: string;
  version: string;
  dataDir: string;
  totalMemoryMb: number;
}

export type AccountKind = 'offline' | 'microsoft';

export interface Account {
  uuid: string;
  username: string;
  kind: AccountKind;
}

export interface AccountStore {
  activeUuid: string | null;
  accounts: Account[];
}

export type VersionKind = 'release' | 'snapshot' | 'old_beta' | 'old_alpha' | 'other';

export interface VersionSummary {
  id: string;
  kind: VersionKind;
  releaseTime: string;
  installed: boolean;
}

export type InstallStage = 'metadata' | 'files' | 'done' | 'failed' | 'cancelled';

export interface InstallEvent {
  versionId: string;
  stage: InstallStage;
  filesDone: number;
  filesTotal: number;
  bytesDone: number;
  bytesTotal: number;
  error: string | null;
}

export type AfterLaunch = 'keep' | 'minimize' | 'close';

export interface Settings {
  memoryMb: number;
  optimizedFlags: boolean;
  javaPath: string | null;
  afterLaunch: AfterLaunch;
  /** Game window size; null = Minecraft's default (854×480). */
  resolution: { width: number; height: number } | null;
  fullscreen: boolean;
  /** Launcher accent `#RRGGBB`; null = Novo blue. */
  accent: string | null;
  /** Send redacted crash reports to NovoMC automatically. */
  autoReportCrashes: boolean;
  /** Show what you're playing in Discord. */
  discordPresence: boolean;
  /** Launcher UI language (`en`, `de`, `es`, `fr`, `pt-BR`); null = system language. */
  uiLanguage: string | null;
  /** Minecraft language code (`de_DE`); null = leave the game's choice. */
  gameLanguage: string | null;
  lastVersion: string | null;
  lastInstance: string | null;
  /** Keep Novo Client settings in the Novo account. */
  cloudSync: boolean;
}

export interface NovoCape {
  id: string;
  name: string;
  owned: boolean;
}

/** The Novo account (Discord sign-in), separate from the Minecraft accounts. */
export interface NovoAccount {
  id: string;
  displayName: string;
  discordName: string | null;
  avatarUrl: string | null;
  cape: string | null;
  capes: NovoCape[];
  minecraft: { uuid: string; name: string }[];
}

export interface SyncResult {
  action: 'off' | 'none' | 'uploaded' | 'downloaded' | 'conflict';
  localModified: number | null;
  cloudModified: number | null;
}

export type Loader = 'vanilla' | 'forge' | 'fabric';

export interface ForgeVersions {
  /** Full versions (`1.8.9-11.15.1.2318-1.8.9`), newest first. */
  versions: string[];
  recommended: string | null;
}

export interface Instance {
  id: string;
  name: string;
  version: string;
  loader: Loader;
  /** Full loader version, e.g. Forge `1.8.9-11.15.1.2318-1.8.9`. */
  loaderVersion: string | null;
  /** null = use the launcher's memory setting. */
  memoryMb: number | null;
  /** null = use the launcher's Java setting. */
  javaPath: string | null;
  jvmArgs: string;
  /** Install the NovoMC Client mod (Forge 1.8.9 instances). */
  clientMod: boolean;
  createdAt: number;
  lastPlayed: number | null;
}

export interface JavaInstall {
  path: string;
  major: number;
  version: string;
  arch: string;
  managed: boolean;
}

export type LaunchStage = 'preparing' | 'installing' | 'java' | 'starting' | 'running' | 'exited' | 'crashed' | 'failed';

export interface LaunchState {
  instanceId: string;
  /** Empty until the backend has read the instance. */
  versionId: string;
  stage: LaunchStage;
  progress: number | null;
  message: string | null;
  exitCode: number | null;
  crashReport: { path: string; content: string } | null;
}

export interface LogLine {
  time: number | null;
  level: string;
  thread: string | null;
  message: string;
}

export interface NewsItem {
  id: string;
  title: string;
  body: string;
  /** ISO date or date-time. */
  date: string;
  tag: 'novomc' | 'release' | 'snapshot';
  url: string | null;
  /** Picture for the card (announcements posted from Discord). */
  image?: string | null;
}

/** `config/novomc/modules.json` of an instance (see the mod's ModConfig). */
export interface ClientConfig {
  version?: number;
  modules?: Record<
    string,
    { enabled?: boolean; x?: number; y?: number; scale?: number; settings?: Record<string, boolean | number | string> }
  >;
}

export function onInstallProgress(cb: (e: InstallEvent) => void): Promise<UnlistenFn> {
  return listen<InstallEvent>('install-progress', (e) => cb(e.payload));
}

export function onLaunchState(cb: (e: LaunchState) => void): Promise<UnlistenFn> {
  return listen<LaunchState>('launch-state', (e) => cb(e.payload));
}

export interface UpdateInfo {
  version: string;
  currentVersion: string;
  notes: string | null;
  date: string | null;
}

export interface UpdateProgress {
  downloaded: number;
  total: number | null;
}

export function onUpdateProgress(cb: (e: UpdateProgress) => void): Promise<UnlistenFn> {
  return listen<UpdateProgress>('update-progress', (e) => cb(e.payload));
}

export interface BugReport {
  title: string;
  description: string;
  instanceId: string | null;
  includeLogs: boolean;
}

export interface ReportResult {
  /** The GitHub issue the report was filed as (or added to). */
  issue: string | null;
  duplicate: boolean;
}

/** A crash report finished sending: the result, or an error message. */
export function onBugReported(cb: (e: { Ok?: ReportResult; Err?: string }) => void): Promise<UnlistenFn> {
  return listen<{ Ok?: ReportResult; Err?: string }>('bug-reported', (e) => cb(e.payload));
}

export interface Server {
  name: string;
  /** host or host:port */
  address: string;
}

export interface ServerStatus {
  online: number;
  max: number;
  latencyMs: number;
  motd: string;
}

export function onGameLog(cb: (lines: LogLine[]) => void): Promise<UnlistenFn> {
  return listen<LogLine[]>('game-log', (e) => cb(e.payload));
}

export const api = {
  appInfo: () => invoke<AppInfo>('app_info'),
  openDataFolder: () => invoke<void>('open_data_folder'),
  /** A newer launcher, or null when up to date. */
  checkUpdate: () => invoke<UpdateInfo | null>('check_update'),
  /** Downloads and installs the checked update; NovoMC restarts when done. */
  installUpdate: () => invoke<void>('install_update'),
  getNews: () => invoke<NewsItem[]>('get_news'),
  openUrl: (url: string) => invoke<void>('open_url', { url }),
  /** Files a bug report as a GitHub issue (through NovoMC's relay). */
  reportBug: (report: BugReport) => invoke<ReportResult>('report_bug', { report }),
  listAccounts: () => invoke<AccountStore>('list_accounts'),
  removeAccount: (uuid: string) => invoke<AccountStore>('remove_account', { uuid }),
  /** Opens the Microsoft sign-in window; resolves once signed in (rejects if cancelled). */
  microsoftLogin: () => invoke<AccountStore>('microsoft_login'),
  /** The player's skin head as a data URL (null for default skins). */
  getSkinHead: (uuid: string) => invoke<string | null>('get_skin_head', { uuid }),
  novoAccount: () => invoke<NovoAccount | null>('novo_account'),
  novoLogin: () => invoke<NovoAccount>('novo_login'),
  novoLogout: () => invoke<void>('novo_logout'),
  novoSetDisplayName: (name: string) => invoke<NovoAccount>('novo_set_display_name', { name }),
  novoSetCape: (cape: string | null) => invoke<NovoAccount>('novo_set_cape', { cape }),
  novoLink: (uuid: string) => invoke<NovoAccount>('novo_link', { uuid }),
  novoUnlink: (uuid: string) => invoke<NovoAccount>('novo_unlink', { uuid }),
  /** resolve: which side wins ('local' uploads, 'cloud' downloads). */
  novoSyncSettings: (instanceId: string, resolve?: 'local' | 'cloud') =>
    invoke<SyncResult>('novo_sync_settings', { instanceId, resolve: resolve ?? null }),
  setActiveAccount: (uuid: string) => invoke<AccountStore>('set_active_account', { uuid }),
  listVersions: (refresh = false) => invoke<VersionSummary[]>('list_versions', { refresh }),
  installVersion: (id: string) => invoke<void>('install_version', { id }),
  cancelInstall: (id: string) => invoke<void>('cancel_install', { id }),
  listJava: () => invoke<JavaInstall[]>('list_java'),
  listInstances: () => invoke<Instance[]>('list_instances'),
  createInstance: (name: string, version: string, loader: Loader, loaderVersion: string | null) =>
    invoke<Instance>('create_instance', { instance: { name, version, loader, loaderVersion } }),
  listForgeVersions: (minecraft: string) => invoke<ForgeVersions>('list_forge_versions', { minecraft }),
  /** Newest stable Fabric Loader for the version, or null if Fabric doesn't support it. */
  fabricLoader: (minecraft: string) => invoke<string | null>('fabric_loader', { minecraft }),
  updateInstance: (instance: Instance) => invoke<Instance>('update_instance', { instance }),
  duplicateInstance: (id: string) => invoke<Instance>('duplicate_instance', { id }),
  deleteInstance: (id: string) => invoke<void>('delete_instance', { id }),
  openInstanceFolder: (id: string) => invoke<void>('open_instance_folder', { id }),
  getClientConfig: (id: string) => invoke<ClientConfig>('get_client_config', { id }),
  saveClientConfig: (id: string, config: ClientConfig) => invoke<void>('save_client_config', { id, config }),
  /** Starts an instance; with `server`, the game joins it right away. */
  launchGame: (instanceId: string, server?: string) => invoke<void>('launch_game', { instanceId, server: server ?? null }),
  listServers: () => invoke<Server[]>('list_servers'),
  saveServers: (servers: Server[]) => invoke<Server[]>('save_servers', { servers }),
  pingServer: (address: string) => invoke<ServerStatus>('ping_server', { address }),
  stopGame: () => invoke<void>('stop_game'),
  getSettings: () => invoke<Settings>('get_settings'),
  updateSettings: (settings: Settings) => invoke<Settings>('update_settings', { settings }),
};
