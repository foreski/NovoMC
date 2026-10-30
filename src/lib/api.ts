// Typed wrappers around the Rust commands. The UI never does game logic itself.
import { invoke } from '@tauri-apps/api/core';
import { listen, type UnlistenFn } from '@tauri-apps/api/event';

export interface AppInfo {
  name: string;
  version: string;
  dataDir: string;
  totalMemoryMb: number;
  /** The relay this build talks to, null when no config sets one. */
  apiBase: string | null;
  /** The config file that supplied it. */
  configFile: string | null;
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

/** Frames an animated cosmetic plays (`scripts/make-capes.mjs`); null/absent = still. */
export interface CosmeticAnimation {
  frames: number;
  fps: number;
}

export interface NovoCape {
  id: string;
  name: string;
  owned: boolean;
  animated?: CosmeticAnimation | null;
}

export interface NovoBanner {
  id: string;
  name: string;
  owned: boolean;
}

/** A 3D wing style; `flap` is the animation the client plays. */
export interface NovoWing {
  id: string;
  name: string;
  owned: boolean;
  flap?: { rate: number; amplitude: number } | null;
}

/** A 3D hat mesh mounted on the player's head. */
export interface NovoHat {
  id: string;
  name: string;
  owned: boolean;
}

/** The Novo account (Discord sign-in), separate from the Minecraft accounts. */
export interface NovoAccount {
  id: string;
  coins: number;
  displayName: string;
  discordName: string | null;
  avatarUrl: string | null;
  cape: string | null;
  banner: string | null;
  wing: string | null;
  hat: string | null;
  capes: NovoCape[];
  banners: NovoBanner[];
  wings: NovoWing[];
  hats: NovoHat[];
  minecraft: { uuid: string; name: string }[];
}

export type StoreCosmeticKind = 'cape' | 'banner' | 'wings' | 'hat';
export interface StoreItem {
  id: string;
  kind: StoreCosmeticKind;
  name: string;
  description: string;
  price: number;
  owned: boolean;
  animated?: CosmeticAnimation | null;
}
export interface CoinBundle {
  id: string;
  name: string;
  coins: number;
  price: number;
  currency: 'USD';
  badge?: string;
}
export interface StoreState {
  balance: number;
  paymentReady: boolean;
  items: StoreItem[];
  bundles: CoinBundle[];
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

/** The Novo account changed outside the UI, e.g. cosmetics picked in game. */
export function onNovoAccount(cb: () => void): Promise<UnlistenFn> {
  return listen('novo-account', () => cb());
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

/** A player's skin, for the launcher's skin changer. */
export interface SkinProfile {
  uuid: string;
  name: string;
  /** "classic" or "slim", as the skin declares. */
  model: string;
  /** The whole 64x64 sheet as a data URL, or null for an account on a default skin. */
  png: string | null;
}

/** Modrinth content the survival client installs. */
export type SurvivalKind = 'mod' | 'pack' | 'shader';

export interface SurvivalEdition {
  id: string;
  label: string;
  blurb: string;
  /** Java major version the game needs. */
  java: number;
  recommended: boolean;
}

export interface SurvivalItem {
  slug: string;
  kind: SurvivalKind;
  title: string;
  blurb: string;
  category: 'core' | 'animation' | 'world' | 'qol' | 'shader';
  /** The editions the item has a build for. */
  editions: string[];
  /** Part of the Vanilla+ bundle. */
  default: boolean;
  note: string | null;
}

export interface SurvivalInstalled {
  slug: string;
  kind: SurvivalKind;
  title: string;
  file: string;
  version: string;
}

export interface SurvivalStatus {
  instanceId: string;
  edition: string;
  installed: SurvivalInstalled[];
}

export interface SurvivalBundleResult {
  status: SurvivalStatus;
  failed: string[];
}

export interface SurvivalHit {
  slug: string;
  title: string;
  description: string;
  iconUrl: string | null;
  downloads: number;
}

export interface SurvivalProgress {
  instanceId: string;
  slug: string | null;
  done: number;
  total: number;
  message: string | null;
}

export function onSurvivalProgress(cb: (e: SurvivalProgress) => void): Promise<UnlistenFn> {
  return listen<SurvivalProgress>('survival-progress', (e) => cb(e.payload));
}

/** A local server the player runs from the launcher. */
export interface LocalServer {
  id: string;
  name: string;
  version: string;
  loader: Loader;
  loaderVersion: string | null;
  port: number;
  memoryMb: number;
  /** The instance whose mods this server mirrors, if any. */
  instanceId: string | null;
  createdAt: number;
  running: boolean;
}

export type ServerStage = 'starting' | 'running' | 'stopping' | 'stopped' | 'failed';

export interface ServerState {
  id: string;
  stage: ServerStage;
  message: string | null;
}

export interface ServerLog {
  id: string;
  lines: string[];
}

/** One `key=value` line of a server's `server.properties`. */
export interface ServerProperty {
  key: string;
  value: string;
}

/** How a server's mods compare to the instance it should mirror. */
export interface ModMatch {
  /** In the instance, not in the server. */
  missing: string[];
  /** In the server, not in the instance. */
  extra: string[];
  /** Same file name, different contents. */
  differing: string[];
  instanceMods: number;
  serverMods: number;
}

export function onServerState(cb: (e: ServerState) => void): Promise<UnlistenFn> {
  return listen<ServerState>('server-state', (e) => cb(e.payload));
}

export function onServerLog(cb: (e: ServerLog) => void): Promise<UnlistenFn> {
  return listen<ServerLog>('server-log', (e) => cb(e.payload));
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
  /** A player's skin sheet by username, or null when no such player exists. */
  lookupSkin: (name: string) => invoke<SkinProfile | null>('lookup_skin', { name }),
  /** A player's skin sheet by UUID. */
  getSkinPng: (uuid: string) => invoke<string | null>('get_skin_png', { uuid }),
  /** Uploads a skin to the signed-in Microsoft account. */
  applySkin: (png: string, model: string) => invoke<void>('apply_skin', { png, model }),
  /** Drops the signed-in account's custom skin. */
  resetSkin: () => invoke<void>('reset_skin'),
  novoAccount: () => invoke<NovoAccount | null>('novo_account'),
  novoLogin: () => invoke<NovoAccount>('novo_login'),
  novoLogout: () => invoke<void>('novo_logout'),
  novoSetDisplayName: (name: string) => invoke<NovoAccount>('novo_set_display_name', { name }),
  novoSetCape: (cape: string | null) => invoke<NovoAccount>('novo_set_cape', { cape }),
  novoSetWing: (wing: string | null) => invoke<NovoAccount>('novo_set_wing', { wing }),
  novoSetHat: (hat: string | null) => invoke<NovoAccount>('novo_set_hat', { hat }),
  novoSetBanner: (banner: string | null) => invoke<NovoAccount>('novo_set_banner', { banner }),
  novoShop: () => invoke<StoreState>('novo_shop'),
  novoPurchaseItem: (id: string) => invoke<NovoAccount>('novo_purchase_item', { id }),
  novoRedeemGift: (code: string) => invoke<NovoAccount>('novo_redeem_gift', { code }),
  /** Opens Stripe Checkout in its own small window; the wallet refreshes on focus. */
  novoCheckout: (bundleId: string) => invoke<void>('novo_checkout', { bundleId }),
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
  survivalEditions: () => invoke<SurvivalEdition[]>('survival_editions'),
  survivalCatalog: () => invoke<SurvivalItem[]>('survival_catalog'),
  survivalInstances: () => invoke<SurvivalStatus[]>('survival_instances'),
  survivalStatus: (instanceId: string) => invoke<SurvivalStatus>('survival_status', { instanceId }),
  /** Installs the game and Fabric, then creates the survival instance. */
  survivalCreate: (edition: string) => invoke<Instance>('survival_create', { edition }),
  /** `kind` is only needed for a search result that isn't in the catalogue. */
  survivalInstall: (instanceId: string, slug: string, kind?: SurvivalKind) =>
    invoke<SurvivalStatus>('survival_install', { instanceId, slug, kind: kind ?? null }),
  survivalRemove: (instanceId: string, slug: string) => invoke<SurvivalStatus>('survival_remove', { instanceId, slug }),
  survivalBundle: (instanceId: string) => invoke<SurvivalBundleResult>('survival_bundle', { instanceId }),
  survivalSearch: (instanceId: string, kind: SurvivalKind, query: string) =>
    invoke<SurvivalHit[]>('survival_search', { instanceId, kind, query }),
  listLocalServers: () => invoke<LocalServer[]>('list_local_servers'),
  /** Copies `instanceId`'s version, loader and mods onto a new local server. */
  createLocalServer: (server: { name: string; instanceId: string; port: number | null; memoryMb: number }) =>
    invoke<LocalServer>('create_local_server', { server }),
  deleteLocalServer: (id: string) => invoke<void>('delete_local_server', { id }),
  startLocalServer: (id: string) => invoke<void>('start_local_server', { id }),
  stopLocalServer: (id: string) => invoke<void>('stop_local_server', { id }),
  matchServerMods: (id: string) => invoke<ModMatch>('match_server_mods', { id }),
  /** Copies the instance's mods onto the server, removing ones it no longer has. */
  applyServerMods: (id: string) => invoke<ModMatch>('apply_server_mods', { id }),
  openLocalServerFolder: (id: string) => invoke<void>('open_local_server_folder', { id }),
  /** The editable settings of a local server's `server.properties`. */
  readServerProperties: (id: string) => invoke<ServerProperty[]>('read_server_properties', { id }),
  /** Saves settings and returns the server, whose port may have changed. */
  saveServerProperties: (id: string, values: ServerProperty[]) =>
    invoke<LocalServer>('save_server_properties', { id, values }),
  /** Starts an instance; with `server`, the game joins it right away. */
  launchGame: (instanceId: string, server?: string) => invoke<void>('launch_game', { instanceId, server: server ?? null }),
  listServers: () => invoke<Server[]>('list_servers'),
  saveServers: (servers: Server[]) => invoke<Server[]>('save_servers', { servers }),
  pingServer: (address: string) => invoke<ServerStatus>('ping_server', { address }),
  stopGame: () => invoke<void>('stop_game'),
  getSettings: () => invoke<Settings>('get_settings'),
  updateSettings: (settings: Settings) => invoke<Settings>('update_settings', { settings }),
};
