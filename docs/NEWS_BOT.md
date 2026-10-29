# Launcher news from Discord

The **News** cards on the launcher's Home page come from the relay worker (`relay/src/news.js`).
A Discord bot publishes them; every Novo Client launcher reads them on start
(cached for up to a minute at Cloudflare's edge). If the relay can't be reached, the launcher
shows the news bundled with it (`src-tauri/src/news.json`).

## One-time setup (Cloudflare dashboard)

1. **Storage:** Workers & Pages → KV → *Create a namespace*, e.g. `novo-news`.
2. **Bind it:** open the relay worker → Settings → Bindings → *Add* → KV namespace.
   Variable name **`NEWS`**, namespace `novo-news`.
3. **Bot token:** Settings → Variables and Secrets → *Add* → type *Secret*, name **`NEWS_TOKEN`**,
   value: a long random string (for example the output of
   `node -e "console.log(crypto.randomUUID()+crypto.randomUUID())"`). Give the same value to your bot.
4. **Code:** paste the updated `relay/src/worker.js` and the new `relay/src/news.js` into the
   worker's editor (keep `presence.js`), then *Deploy*.

With wrangler instead: see the comments in `relay/wrangler.toml`.

## API

Base URL: the relay, i.e. `bugReportUrl` without `/report` (`https://<worker>.workers.dev`).

| Request | Auth | What it does |
| --- | --- | --- |
| `GET /news` | none | `{"items": [...]}`, newest first (max 20) |
| `POST /news` | `Authorization: Bearer <NEWS_TOKEN>` | create, or replace the item with the same `id` |
| `DELETE /news/<id>` | `Authorization: Bearer <NEWS_TOKEN>` | remove an item |

`POST /news` body (JSON):

| Field | Required | Notes |
| --- | --- | --- |
| `title` | yes | up to 100 characters |
| `body` | yes | up to 400 characters (the card shows about two lines) |
| `id` | no | letters, digits, `-`, `_`; use the Discord message id so edits and deletes line up |
| `url` | no | https link opened when the card is clicked (e.g. the message link) |
| `image` | no | https picture for the card header (e.g. the first attachment's URL) |
| `date` | no | ISO date/time; defaults to now |

Errors come back as `{"error": "..."}` with status 400 (bad field), 401 (wrong token), 404 or 413.

## Example bot (discord.js v14)

Mirrors an announcements channel: new messages are published, edits update the card, deletes remove it.
The first line of a message is the title, the rest is the body.

```js
import { Client, Events, GatewayIntentBits } from 'discord.js';

const RELAY = 'https://<your-worker>.workers.dev';
const CHANNEL = '<announcements channel id>';
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.NEWS_TOKEN}` };

const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMessages, GatewayIntentBits.MessageContent] });

async function publish(message) {
  if (message.channelId !== CHANNEL || message.author?.bot) return;
  const [title, ...rest] = message.content.trim().split('\n');
  const res = await fetch(`${RELAY}/news`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      id: message.id,
      title: title.replace(/[*_#`]/g, '').slice(0, 100),
      body: (rest.join(' ').trim() || title).slice(0, 400),
      url: message.url,
      image: message.attachments.find((a) => a.contentType?.startsWith('image/'))?.url,
      date: message.createdAt.toISOString(),
    }),
  });
  if (!res.ok) console.error('news post failed:', await res.text());
}

client.on(Events.MessageCreate, publish);
client.on(Events.MessageUpdate, (_, after) => after.fetch().then(publish));
client.on(Events.MessageDelete, (m) => {
  if (m.channelId === CHANNEL) fetch(`${RELAY}/news/${m.id}`, { method: 'DELETE', headers });
});
client.login(process.env.DISCORD_TOKEN);
```

Note: Discord attachment URLs expire after a while; for pictures that should stay up, upload
them somewhere permanent and pass that link as `image`.

The same bot can also run the Marketplace: see [SHOP_BOT.md](SHOP_BOT.md) for the shop
item API and its slash commands.

Test it by hand:

```bash
curl -X POST https://<your-worker>.workers.dev/news -H "Authorization: Bearer $NEWS_TOKEN" -H "Content-Type: application/json" -d "{\"title\":\"Hello\",\"body\":\"First post from Discord\"}"
```
