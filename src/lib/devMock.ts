// Development only: when the UI runs in a plain browser (`npm run dev` without
// Tauri), answer the Rust commands with sample data so pages can be designed
// and previewed there. Never part of a release build (see main.tsx).
import hero from '../../assets/home/hero.jpg';
import type { AccountStore, AppInfo, Instance, NewsItem, Server, Settings } from './api';

const now = Date.now();

const instances: Instance[] = [
  { id: 'pvp', name: 'Hypixel PvP', version: '1.8.9', loader: 'forge', loaderVersion: '1.8.9-11.15.1.2318-1.8.9', memoryMb: null,
    javaPath: null, jvmArgs: '', clientMod: true, createdAt: now - 9e8, lastPlayed: now - 3.6e6 },
  { id: 'survival', name: 'Survival', version: '1.21.4', loader: 'vanilla', loaderVersion: null, memoryMb: null,
    javaPath: null, jvmArgs: '', clientMod: false, createdAt: now - 5e8, lastPlayed: now - 8.6e7 },
];

const accounts: AccountStore = {
  activeUuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
  accounts: [{ uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5', username: 'NovoPlayer', kind: 'microsoft' }],
};

const news: NewsItem[] = [
  { id: 'n1', title: 'Sodium Renderer arrives', body: 'Terrain now draws region by region: a big FPS boost on 1.8.9.', date: '2026-10-27', tag: 'novomc', url: null,
    image: hero },
  { id: 'n2', title: 'Every menu, restyled', body: 'New Options and Video Settings screens and smooth menu animations.', date: '2026-10-27', tag: 'novomc', url: null },
  { id: 'n3', title: 'Minecraft 1.21.4', body: 'The Garden Awakens drop is out.', date: '2026-10-20', tag: 'release', url: null },
];

const servers: Server[] = [
  { name: 'Hypixel', address: 'mc.hypixel.net' },
  { name: 'Minemen', address: 'minemen.club' },
];

const settings = {
  memoryMb: 4096, optimizedFlags: true, javaPath: null, afterLaunch: 'minimize', resolution: null, fullscreen: false, accent: null,
  autoReportCrashes: true, discordPresence: true, uiLanguage: null, gameLanguage: null, lastVersion: '1.8.9', lastInstance: 'pvp', cloudSync: true,
} as unknown as Settings;

// Signed in once the mock sign-in runs.
let novo: Record<string, unknown> | null = null;
const CAPES = [['novo', 'Novo'], ['aurora', 'Aurora'], ['ember', 'Ember'], ['frost', 'Frost'], ['verdant', 'Verdant'], ['midnight', 'Midnight'], ['early_supporter', 'Early Supporter'], ['developer', 'Developer']];
const BANNERS = [['aurora', 'Aurora'], ['nebula', 'Nebula'], ['sunset', 'Sunset'], ['forest', 'Forest'], ['developer', 'Developer']];
const novoAccount = () => ({
  id: 'mock', displayName: 'Steve', discordName: 'steve', avatarUrl: null, cape: 'ember', banner: 'aurora', minecraft: [],
  capes: CAPES.map(([id, name], i) => ({ id, name, owned: i < 6 })),
  banners: BANNERS.map(([id, name], i) => ({ id, name, owned: i < 4 })),
});

const info: AppInfo = { name: 'NovoMC', version: '0.1.8', dataDir: 'C:\\NovoMC (mock)', totalMemoryMb: 16384 };

const replies: Record<string, (args: Record<string, unknown>) => unknown> = {
  app_info: () => info,
  list_instances: () => instances,
  list_accounts: () => accounts,
  get_settings: () => settings,
  update_settings: () => settings,
  get_news: () => news,
  list_servers: () => servers,
  save_servers: (a) => a.servers,
  ping_server: (a) => ({ online: a.address === 'mc.hypixel.net' ? 41823 : 2104, max: 200000, latencyMs: 38, motd: '' }),
  get_skin_head: () => null,
  novo_account: () => novo,
  novo_login: () => (novo = novoAccount()),
  novo_logout: () => (novo = null),
  novo_set_cape: (a) => (novo = { ...novo, cape: a.cape }),
  novo_set_banner: (a) => (novo = { ...novo, banner: a.banner }),
  novo_set_display_name: (a) => (novo = { ...novo, displayName: a.name }),
  novo_sync_settings: () => ({ action: 'off', localModified: null, cloudModified: null }),
  check_update: () => null,
  list_java: () => [],
  list_versions: () => [],
  // An empty config means every mod uses its catalog default.
  get_client_config: () => ({ version: 1, modules: {} }),
  save_client_config: () => null,
  // Play and Stop do nothing in the browser.
  launch_game: () => null,
  stop_game: () => null,
};

export function installDevMock() {
  let id = 0;
  const callbacks = new Map<number, (v: unknown) => void>();
  (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {
    transformCallback: (cb: (v: unknown) => void) => {
      callbacks.set(++id, cb);
      return id;
    },
    unregisterCallback: (cbId: number) => callbacks.delete(cbId),
    invoke: async (cmd: string, args: Record<string, unknown> = {}) => {
      if (cmd.startsWith('plugin:event|')) return 0;
      const reply = replies[cmd];
      if (!reply) throw new Error(`dev mock: no reply for ${cmd}`);
      return reply(args);
    },
    metadata: { currentWindow: { label: 'main' }, currentWebview: { label: 'main', windowLabel: 'main' } },
  };
}
