# Marketplace items from Discord

Cosmetic items in the launcher's Marketplace come from the D1 table `shop_items`
(relay/src/shop.js). A Discord bot can add items, change prices and copy, hide
them, or remove them — the launcher picks the change up on its next refresh, with
no Worker deploy.

The bot authenticates with the Worker secret **`SHOP_TOKEN`**. It is separate from
`NEWS_TOKEN` on purpose: the news bot can post announcements, not set prices.

## One-time setup

1. **Schema:** apply `relay/migrations/0003-shop-items.sql` to the `novo-accounts`
   database (it seeds the launch catalog):

   ```bash
   npx wrangler d1 execute novo-accounts --remote --file relay/migrations/0003-shop-items.sql
   ```

2. **Token:** create a random value for the bot and store it as a Worker secret:

   ```bash
   node -e "console.log(crypto.randomUUID()+crypto.randomUUID())"   # keep this for the bot
   npx wrangler secret put SHOP_TOKEN
   ```

   The Cloudflare dashboard equivalent is Settings → Variables and Secrets →
   Add → Secret, name `SHOP_TOKEN`.

3. **Deploy:** `npx wrangler deploy` (or paste the updated `relay/src/*.js` into the
   Worker editor). Until `SHOP_TOKEN` exists the admin API answers 503 and the
   store keeps working from the seeded rows.

## API

Base URL: the relay, i.e. `bugReportUrl` without `/report`.

| Request | What it does |
| --- | --- |
| `GET /admin/shop/items` | every item, hidden ones included, in display order |
| `POST /admin/shop/items` | add an item; 409 if the id is taken |
| `PATCH /admin/shop/items/<id>` | change `name`, `description`, `price`, `active`, `position` |
| `DELETE /admin/shop/items/<id>` | remove the row; the id can be reused afterwards |

All of them need `Authorization: Bearer <SHOP_TOKEN>`.

Item fields:

| Field | Add | Change | Notes |
| --- | --- | --- | --- |
| `id` | yes | no | 1-32 lowercase letters, digits or `_`; the cosmetic id for capes and banners |
| `kind` | yes | no | `cape`, `banner`, `wings` or `hat` |
| `name` | yes | yes | up to 40 characters, shown on the card |
| `description` | yes | yes | up to 160 characters |
| `price` | yes | yes | whole Novo Coins, 1-100000; what the purchase actually charges |
| `active` | no | yes | `false` hides it from the shop without deleting it (default `true`) |
| `position` | no | yes | display order, ascending; new items go last by default |

Capes, banners, wings and hats must name a **paid** id from `relay/src/capes.js`, so a
typo can't put an unwearable item on sale and a free cosmetic can't be sold twice.
Wings and hats only draw in the 1.8.9 client, and each id needs a mesh there
(`assets/novomc/models/wings/<id>.obj`, `models/hats/<id>.obj`); an id without one
sells a cosmetic nobody can see.

Errors are `{"error": "..."}` with 400 (bad field or body), 401 (wrong token),
404 (no such item), 409 (id already used) or 503 (no `SHOP_TOKEN`).

By hand:

```bash
# Add a cape to the shop for 500 NC
curl -X POST https://<worker>.workers.dev/admin/shop/items \
  -H "Authorization: Bearer $SHOP_TOKEN" -H "Content-Type: application/json" \
  -d '{"id":"dune","kind":"cape","name":"Dune","description":"Rippling sand tones.","price":500}'

# Run a weekend sale
curl -X PATCH https://<worker>/admin/shop/items/dune \
  -H "Authorization: Bearer $SHOP_TOKEN" -H "Content-Type: application/json" \
  -d '{"price":350}'

# Hide it again when the sale ends
curl -X PATCH https://<worker>/admin/shop/items/dune \
  -H "Authorization: Bearer $SHOP_TOKEN" -H "Content-Type: application/json" \
  -d '{"active":false}'
```

Changes are live for the next launcher refresh: the store endpoint is not cached,
so a bot edit shows up as soon as a player opens or refreshes the Marketplace.

## Gift codes

The same token mints coin gift codes, which players redeem in the Marketplace
(**Redeem a code** under *Buy coins*). Codes are 24 random hex characters shown
once as `NOVO-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx`; only their SHA-256 is stored in
`gift_codes`, so a code that is lost cannot be looked up again — mint another.

| Request | What it does |
| --- | --- |
| `GET /admin/gift-codes` | the last 50 codes: coin value, uses, expiry — never the code itself |
| `POST /admin/gift-codes` | mint codes; the display codes are in the response, once |

Both need `Authorization: Bearer <SHOP_TOKEN>`. Minting takes:

| Field | Notes |
| --- | --- |
| `coins` | required, 1-1000000 — added to each redeemer's wallet |
| `uses` | optional, 1-100000, default 1; how many players can redeem each code |
| `count` | optional, 1-25, default 1; how many codes to mint |
| `expiresInDays` | optional, 0 (never, the default) to 365 |

```bash
# 10 codes worth 500 NC, each for one player, valid for a week
curl -X POST https://<worker>.workers/admin/gift-codes \
  -H "Authorization: Bearer $SHOP_TOKEN" -H "Content-Type: application/json" \
  -d '{"coins":500,"count":10,"expiresInDays":7}'
# -> {"codes":["NOVO-1A2B-...", ...],"coins":500,"uses":1,"count":10,"expiresAt":1793...}
```

In the bot: `/gift create coins:500 uses:1 count:10 expires_days:7` replies
ephemerally with the codes, and `/gift list` shows what is outstanding (an `id`
is the start of the hash — enough to talk about a code, useless for redeeming
one). A player can redeem any code only once, and a code stops working when its
uses run out or it expires. Codes can't be revoked: mint with a short expiry
when that matters.

## Example bot (discord.js v14)

Slash commands under `/shop`, limited to members with **Manage Server**. The same
bot process can also mirror the news channel (see [NEWS_BOT.md](NEWS_BOT.md)); it
just needs both tokens.

```js
import { Client, Events, GatewayIntentBits, PermissionFlagsBits, REST, Routes, SlashCommandBuilder } from 'discord.js';

const RELAY = 'https://<your-worker>.workers.dev';
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.SHOP_TOKEN}` };

const commands = [
  new SlashCommandBuilder()
    .setName('shop')
    .setDescription('Manage Marketplace items')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
    .addSubcommand((s) => s.setName('list').setDescription('Show every item, hidden ones included'))
    .addSubcommand((s) =>
      s
        .setName('add')
        .setDescription('Add an item to the shop')
        .addStringOption((o) => o.setName('id').setDescription('Cape or banner id from capes.js').setRequired(true))
        .addStringOption((o) =>
          o
            .setName('kind')
            .setDescription('Cosmetic kind')
            .setRequired(true)
            .addChoices({ name: 'cape', value: 'cape' }, { name: 'banner', value: 'banner' }, { name: 'wings', value: 'wings' }, { name: 'hat', value: 'hat' }),
        )
        .addStringOption((o) => o.setName('name').setDescription('Card title').setRequired(true))
        .addStringOption((o) => o.setName('description').setDescription('One line for the card').setRequired(true))
        .addIntegerOption((o) => o.setName('price').setDescription('Price in Novo Coins').setRequired(true).setMinValue(1)),
    )
    .addSubcommand((s) =>
      s
        .setName('set')
        .setDescription('Change an item')
        .addStringOption((o) => o.setName('item').setDescription('Item id').setRequired(true))
        .addIntegerOption((o) => o.setName('price').setDescription('New price in Novo Coins').setMinValue(1))
        .addStringOption((o) => o.setName('name').setDescription('New card title'))
        .addStringOption((o) => o.setName('description').setDescription('New card text')),
    )
    .addSubcommand((s) =>
      s
        .setName('hide')
        .setDescription('Take an item out of the shop without deleting it')
        .addStringOption((o) => o.setName('item').setDescription('Item id').setRequired(true)),
    )
    .addSubcommand((s) =>
      s
        .setName('show')
        .setDescription('Put a hidden item back in the shop')
        .addStringOption((o) => o.setName('item').setDescription('Item id').setRequired(true)),
    )
    .addSubcommand((s) =>
      s
        .setName('remove')
        .setDescription('Delete an item')
        .addStringOption((o) => o.setName('item').setDescription('Item id').setRequired(true)),
    ),
].map((command) => command.toJSON());

const rest = new REST({ version: '10' }).setToken(process.env.DISCORD_TOKEN);
await rest.put(Routes.applicationGuildCommands(process.env.DISCORD_APP_ID, process.env.DISCORD_GUILD_ID), { body: commands });

const client = new Client({ intents: [GatewayIntentBits.Guilds] });
const call = async (method, path, body) => {
  const res = await fetch(`${RELAY}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data };
};

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand() || interaction.commandName !== 'shop') return;
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild)) {
    return interaction.reply({ content: 'You need **Manage Server** to change the shop.', ephemeral: true });
  }
  const sub = interaction.options.getSubcommand();
  const item = interaction.options.getString('item');
  let result;

  if (sub === 'list') {
    result = await call('GET', '/admin/shop/items');
    const lines = (result.data.items ?? []).map((i) => `\`${i.id}\` ${i.name} — ${i.price} NC${i.active ? '' : ' (hidden)'}`);
    return interaction.reply({ content: lines.join('\n').slice(0, 1900) || 'The shop is empty.', ephemeral: true });
  }
  if (sub === 'add') {
    result = await call('POST', '/admin/shop/items', {
      id: interaction.options.getString('id'),
      kind: interaction.options.getString('kind'),
      name: interaction.options.getString('name'),
      description: interaction.options.getString('description'),
      price: interaction.options.getInteger('price'),
    });
  } else if (sub === 'set') {
    const body = {};
    for (const key of ['price', 'name', 'description']) {
      const value = key === 'price' ? interaction.options.getInteger(key) : interaction.options.getString(key);
      if (value != null) body[key] = value;
    }
    if (Object.keys(body).length === 0) return interaction.reply({ content: 'Give at least one new value.', ephemeral: true });
    result = await call('PATCH', `/admin/shop/items/${encodeURIComponent(item)}`, body);
  } else if (sub === 'hide' || sub === 'show') {
    result = await call('PATCH', `/admin/shop/items/${encodeURIComponent(item)}`, { active: sub === 'show' });
  } else {
    result = await call('DELETE', `/admin/shop/items/${encodeURIComponent(item)}`);
  }

  const summary = result.ok
    ? sub === 'remove'
      ? `Removed \`${item}\`.`
      : `\`${result.data.item.id}\` — ${result.data.item.name}, ${result.data.item.price} NC${result.data.item.active ? '' : ' (hidden)'}`
    : `That didn't work (${result.status}): ${result.data.error ?? 'no detail'}`;
  return interaction.reply({ content: summary, ephemeral: true });
});

client.login(process.env.DISCORD_TOKEN);
```

Run it with `npm i discord.js` and these environment variables:
`DISCORD_TOKEN`, `DISCORD_APP_ID`, `DISCORD_GUILD_ID`, `SHOP_TOKEN`.

Purchases already owned by players are untouched by edits or removals: ownership
lives in the `grants` table, not in the catalog. Hidden or removed items simply
stop appearing in the shop and can no longer be bought.
