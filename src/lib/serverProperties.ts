// The `server.properties` keys Novo Client gives a typed control. Keys in the
// file that aren't listed here still show up under "Other keys", so nothing a
// mod adds is lost when the panel saves.

export type FieldKind = 'bool' | 'text' | 'number' | 'select';

export type GroupId = 'general' | 'gameplay' | 'world' | 'access' | 'pack' | 'network';

export interface ServerField {
  key: string;
  label: string;
  kind: FieldKind;
  group: GroupId;
  hint?: string;
  options?: string[];
  min?: number;
  max?: number;
  placeholder?: string;
}

export const SERVER_FIELD_GROUPS: { id: GroupId; label: string }[] = [
  { id: 'general', label: 'General' },
  { id: 'gameplay', label: 'Gameplay' },
  { id: 'world', label: 'World' },
  { id: 'access', label: 'Access and chat' },
  { id: 'pack', label: 'Resource pack' },
  { id: 'network', label: 'Network, RCON and query' },
];

export const SERVER_FIELDS: ServerField[] = [
  // General
  {
    key: 'motd',
    label: 'MOTD',
    kind: 'text',
    group: 'general',
    hint: 'The line shown in the multiplayer server list.',
  },
  { key: 'server-port', label: 'Port', kind: 'number', group: 'general', min: 1, max: 65535 },
  {
    key: 'server-ip',
    label: 'Bind address',
    kind: 'text',
    group: 'general',
    placeholder: 'Empty for every interface',
  },
  { key: 'max-players', label: 'Max players', kind: 'number', group: 'general', min: 1, max: 1000 },
  {
    key: 'online-mode',
    label: 'Online mode',
    kind: 'bool',
    group: 'general',
    hint: 'Verify players with Mojang. Turn off for cracked clients.',
  },
  { key: 'enable-status', label: 'Show in server list', kind: 'bool', group: 'general' },
  {
    key: 'player-idle-timeout',
    label: 'Idle timeout (minutes)',
    kind: 'number',
    group: 'general',
    min: 0,
    max: 600,
    hint: '0 keeps idle players connected.',
  },
  { key: 'enforce-secure-profile', label: 'Require signed chat', kind: 'bool', group: 'general' },
  { key: 'prevent-proxy-connections', label: 'Block proxy connections', kind: 'bool', group: 'general' },
  { key: 'hide-online-players', label: 'Hide player list', kind: 'bool', group: 'general' },

  // Gameplay
  {
    key: 'gamemode',
    label: 'Default gamemode',
    kind: 'select',
    group: 'gameplay',
    options: ['survival', 'creative', 'adventure', 'spectator'],
  },
  { key: 'force-gamemode', label: 'Force gamemode on join', kind: 'bool', group: 'gameplay' },
  {
    key: 'difficulty',
    label: 'Difficulty',
    kind: 'select',
    group: 'gameplay',
    options: ['peaceful', 'easy', 'normal', 'hard'],
  },
  { key: 'hardcore', label: 'Hardcore', kind: 'bool', group: 'gameplay' },
  { key: 'pvp', label: 'PvP', kind: 'bool', group: 'gameplay' },
  { key: 'allow-flight', label: 'Allow flight', kind: 'bool', group: 'gameplay' },
  { key: 'enable-command-block', label: 'Command blocks', kind: 'bool', group: 'gameplay' },
  {
    key: 'op-permission-level',
    label: 'Operator level',
    kind: 'select',
    group: 'gameplay',
    options: ['1', '2', '3', '4'],
  },
  {
    key: 'function-permission-level',
    label: 'Function level',
    kind: 'select',
    group: 'gameplay',
    options: ['1', '2', '3', '4'],
  },
  {
    key: 'spawn-protection',
    label: 'Spawn protection (blocks)',
    kind: 'number',
    group: 'gameplay',
    min: 0,
    max: 1000,
  },

  // World
  { key: 'level-name', label: 'World folder', kind: 'text', group: 'world', placeholder: 'world' },
  { key: 'level-seed', label: 'Seed', kind: 'text', group: 'world', hint: 'Only used for a new world.' },
  {
    key: 'level-type',
    label: 'World type',
    kind: 'select',
    group: 'world',
    options: [
      'minecraft:normal',
      'minecraft:flat',
      'minecraft:large_biomes',
      'minecraft:amplified',
      'minecraft:single_biome_surface',
    ],
  },
  {
    key: 'generator-settings',
    label: 'Generator settings',
    kind: 'text',
    group: 'world',
    placeholder: '{}',
    hint: 'JSON, used by flat and single-biome worlds.',
  },
  { key: 'allow-nether', label: 'Nether', kind: 'bool', group: 'world' },
  { key: 'spawn-monsters', label: 'Hostile mobs', kind: 'bool', group: 'world' },
  { key: 'spawn-animals', label: 'Passive mobs', kind: 'bool', group: 'world' },
  { key: 'spawn-npcs', label: 'Villagers', kind: 'bool', group: 'world' },
  { key: 'view-distance', label: 'View distance', kind: 'number', group: 'world', min: 3, max: 32 },
  {
    key: 'simulation-distance',
    label: 'Simulation distance',
    kind: 'number',
    group: 'world',
    min: 3,
    max: 32,
  },
  { key: 'max-world-size', label: 'Max world size', kind: 'number', group: 'world', min: 1, max: 29999984 },
  {
    key: 'entity-broadcast-range-percentage',
    label: 'Entity broadcast range (%)',
    kind: 'number',
    group: 'world',
    min: 10,
    max: 1000,
  },

  // Access
  { key: 'white-list', label: 'Whitelist', kind: 'bool', group: 'access' },
  { key: 'enforce-whitelist', label: 'Kick players not whitelisted', kind: 'bool', group: 'access' },
  { key: 'broadcast-console-to-ops', label: 'Send console to operators', kind: 'bool', group: 'access' },
  { key: 'broadcast-rcon-to-ops', label: 'Send RCON to operators', kind: 'bool', group: 'access' },

  // Resource pack
  {
    key: 'resource-pack',
    label: 'Pack URL',
    kind: 'text',
    group: 'pack',
    placeholder: 'https://…/pack.zip',
  },
  { key: 'resource-pack-prompt', label: 'Pack prompt', kind: 'text', group: 'pack' },
  { key: 'resource-pack-sha1', label: 'Pack SHA-1', kind: 'text', group: 'pack' },
  { key: 'require-resource-pack', label: 'Require the pack', kind: 'bool', group: 'pack' },

  // Network
  {
    key: 'network-compression-threshold',
    label: 'Compression threshold',
    kind: 'number',
    group: 'network',
    min: -1,
    max: 65535,
    hint: '-1 disables compression.',
  },
  { key: 'rate-limit', label: 'Packet rate limit', kind: 'number', group: 'network', min: 0 },
  { key: 'enable-rcon', label: 'RCON', kind: 'bool', group: 'network' },
  { key: 'rcon.port', label: 'RCON port', kind: 'number', group: 'network', min: 1, max: 65535 },
  { key: 'rcon.password', label: 'RCON password', kind: 'text', group: 'network' },
  { key: 'enable-query', label: 'Query', kind: 'bool', group: 'network' },
  { key: 'query.port', label: 'Query port', kind: 'number', group: 'network', min: 1, max: 65535 },
];

/** The fields for one group, in declaration order. */
export function fieldsOf(group: GroupId): ServerField[] {
  return SERVER_FIELDS.filter((f) => f.group === group);
}

/** Strings a vanilla `server.properties` has; used when a file is missing. */
export const FIELD_KEYS = new Set(SERVER_FIELDS.map((f) => f.key));
