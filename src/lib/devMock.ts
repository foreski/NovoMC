// Development only: when the UI runs in a plain browser (`npm run dev` without
// Tauri), answer the Rust commands with sample data so pages can be designed
// and previewed there. Never part of a release build (see main.tsx).
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

// Empty like the real bundled fallback: announcements are posted by the bot.
const news: NewsItem[] = [];

const servers: Server[] = [
  { name: 'Hypixel', address: 'mc.hypixel.net' },
  { name: 'Minemen', address: 'minemen.club' },
];

const settings = {
  memoryMb: 4096, optimizedFlags: true, javaPath: null, afterLaunch: 'minimize', resolution: null, fullscreen: false, accent: null,
  autoReportCrashes: true, discordPresence: true, uiLanguage: null, gameLanguage: null, lastVersion: '1.8.9', lastInstance: 'pvp', cloudSync: true,
} as unknown as Settings;

// Signed in once the mock sign-in runs. Ids and ownership loosely follow
// relay/src/capes.js so the browser preview matches a real account.
let novo: Record<string, unknown> | null = null;
const CAPES: [string, string, { frames: number; fps: number } | null][] = [
  ['novo', 'Novo', null], ['arctic_cipher', 'Arctic Cipher', null], ['frostpeak', 'Frostpeak', null], ['tidal', 'Tidal', null],
  ['compass', 'Compass', null], ['astral', 'Astral', null], ['wolfshade', 'Wolfshade', null], ['inferno', 'Inferno', null],
  ['venom', 'Venom', null], ['lunar_rose', 'Lunar Rose', null], ['drake', 'Drake', null], ['aurora_flow', 'Aurora Flow', { frames: 24, fps: 12 }],
  ['ember_flow', 'Ember Flow', { frames: 24, fps: 12 }], ['solar_eclipse', 'Solar Eclipse', null],
];
const BANNERS = [['aurora', 'Aurora'], ['nebula', 'Nebula'], ['sunset', 'Sunset'], ['forest', 'Forest'], ['circuit', 'Circuit'], ['developer', 'Developer']];
const WINGS = [
  ['azure_wings', 'Azure Wings'], ['verdant_wings', 'Verdant Wings'], ['violet_wings', 'Violet Wings'],
  ['ender_dragon_wings', 'Ender Dragon Wings'], ['phantom_wings', 'Phantom Wings'], ['monochrome_wings', 'Monochrome Wings'],
];
const HATS = [
  ['wizard_hat', 'Wizard Hat'], ['demon_horns', 'Demon Horns'], ['angel_halo', 'Angel Halo'],
  ['royal_crown', 'Royal Crown'], ['crystal_antlers', 'Crystal Antlers'], ['ender_crown', 'Ender Crown'],
  ['phantom_hood', 'Phantom Hood'], ['miner_helmet', "Miner's Helmet"], ['golden_laurel', 'Golden Laurel'],
];
const novoAccount = () => ({
  id: 'mock', displayName: 'Steve', discordName: 'steve', avatarUrl: null, cape: 'aurora_flow', banner: 'aurora',
  wing: 'ender_dragon_wings', hat: 'wizard_hat', minecraft: [],
  coins: 2400,
  capes: CAPES.map(([id, name, animated], i) => ({ id, name, owned: i < 8 || id === 'aurora_flow', animated })),
  banners: BANNERS.map(([id, name], i) => ({ id, name, owned: i < 4 })),
  wings: WINGS.map(([id, name], i) => ({ id, name, owned: i !== 1, flap: null })),
  hats: HATS.map(([id, name], i) => ({ id, name, owned: i !== 4 })),
});

/** A crude 64x64 skin, so the changer has something to preview in the browser. */
let mockSkinPng: string | null = null;
function mockSkin(): string {
  if (mockSkinPng) return mockSkinPng;
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const c = canvas.getContext('2d')!;
  c.fillStyle = '#5b8cff';
  c.fillRect(0, 0, 64, 64);
  c.fillStyle = '#e6b98f';
  c.fillRect(8, 8, 8, 8);
  c.fillRect(16, 20, 24, 12);
  c.fillStyle = '#243a6b';
  c.fillRect(20, 20, 24, 12);
  c.fillRect(0, 20, 16, 12);
  c.fillStyle = '#1b2440';
  c.fillRect(0, 16, 16, 4);
  c.fillRect(40, 16, 16, 4);
  mockSkinPng = canvas.toDataURL('image/png');
  return mockSkinPng;
}

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
  novo_set_wing: (a) => (novo = { ...novo, wing: a.wing }),
  novo_set_hat: (a) => (novo = { ...novo, hat: a.hat }),
  novo_set_display_name: (a) => (novo = { ...novo, displayName: a.name }),
  get_skin_png: () => mockSkin(),
  lookup_skin: (a) => ({ uuid: 'mock', name: a.name, model: 'classic', png: mockSkin() }),
  apply_skin: () => null,
  reset_skin: () => null,
  novo_sync_settings: () => ({ action: 'off', localModified: null, cloudModified: null }),
  novo_shop: () => {
    const account = (novo ?? novoAccount()) as ReturnType<typeof novoAccount>;
    const price = (id: string) => 120 + (id.length % 7) * 60;
    return {
      balance: account.coins,
      paymentReady: true,
      items: [
        ...CAPES.map(([id, name, animated]) => ({
          id, kind: 'cape' as const, name, description: animated ? 'A cape that moves on its own loop.' : 'A still cape, cut from one sheet.',
          price: price(id), owned: account.capes.some((c: { id: string; owned: boolean }) => c.id === id && c.owned), animated,
        })),
        ...BANNERS.map(([id, name]) => ({ id, kind: 'banner' as const, name, description: 'A banner for your profile card.', price: price(id), owned: false })),
        ...WINGS.map(([id, name]) => ({ id, kind: 'wings' as const, name, description: 'A pair of wings, 1.8.9 only.', price: price(id), owned: account.wings.some((w: { id: string; owned: boolean }) => w.id === id && w.owned) })),
        ...HATS.map(([id, name]) => ({ id, kind: 'hat' as const, name, description: 'A hat, 1.8.9 only.', price: price(id), owned: account.hats.some((h: { id: string; owned: boolean }) => h.id === id && h.owned) })),
      ],
      bundles: [
        { id: 'small', name: 'Pocket change', coins: 500, price: 4.99, currency: 'USD' as const },
        { id: 'medium', name: 'Collector', coins: 1200, price: 9.99, currency: 'USD' as const, badge: 'POPULAR' },
        { id: 'large', name: 'Whole wardrobe', coins: 3000, price: 19.99, currency: 'USD' as const },
      ],
    };
  },
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
