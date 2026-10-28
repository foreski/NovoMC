# Handoff: login screen, settings cog, Marketplace with cosmetic banners

Repo: `foreski/NovoMC` (public). Novo Client is a Tauri 2 + React 19 + Tailwind 4 launcher (`src/` UI,
`src-tauri/src/` Rust). Its 1.8.9 Forge client is `client-mod/`, its 26.3 Fabric client is `client-mod-modern/`,
and its Cloudflare Worker backend is `relay/` (D1 database `NOVO_DB`). The latest release is 0.3.0. Read
`docs/DEVELOPMENT.md`, especially the "Novo accounts" section, before you start.

## Rules
- Commit, push or release only when the owner asks. Releases use
  `npm run release -- --version X.Y.Z --publish --notes "..."`.
- Before any push or release, scan for secrets. The Microsoft client ID is the only allowed exception. Never commit
  `%USERPROFILE%\.tauri\novomc-updater.key`, `novomc.config.json` or `relay/.news-token`.
- Never use OptiFine code.
- Match the existing style: short comments, the `api.ts` wrappers, i18n keys in all 5 locales
  (`src/locales/{en,de,es,fr,pt-BR}.ts`, each typed `Record<Key, string>`, so `tsc` fails if one is missing).
- Deploy the relay with `cd relay && npx.cmd -y wrangler@4 deploy` (wrangler is logged in). Apply D1 schema changes
  with `npx.cmd -y wrangler@4 d1 execute novo-accounts --remote --file schema.sql`.
- Checks: `cd src-tauri && cargo test`, `npx tsc -b`, `node relay/test/<name>.test.mjs` for each relay test, and
  `npm run mod` for client changes. You can preview the UI in a browser with `npm run dev`. `src/lib/devMock.ts`
  fakes the Rust commands there, so add mocks for any new commands.

## What exists now
- **Pages:** `src/App.tsx` switches on `PageId` (`src/components/Sidebar.tsx`: home, instances, versions, mods,
  settings, accounts). The tabs (`NavTabs`) and the player chip (`AccountButton`, which opens Accounts) are in
  `src/components/TitleBar.tsx`.
- **Novo account:** it signs in with Discord through the relay. The Rust commands are in `src-tauri/src/novo/mod.rs`:
  `novo_account`, `novo_login`, `novo_logout`, `novo_set_display_name`, `novo_set_cape`, `novo_link`,
  `novo_unlink` and `novo_sync_settings`. Their TS wrappers are `api.novo*` in `src/lib/api.ts`. The UI is
  `src/components/NovoAccountCard.tsx` on the Accounts page. That card keeps the account in local state; nothing
  shares it yet.
- **Capes:** the textures come from `scripts/make-capes.mjs` and live in
  `client-mod/src/main/resources/assets/novomc/textures/capes/*.png`. The relay's list of capes is
  `relay/src/capes.js`, and locked capes are granted through the `grants` table. The account API
  (`relay/src/account.js`) returns `capes: [{id, name, owned}]` and the equipped `cape`. `/presence/sync` returns
  the online players' capes, and the clients draw them (`NovoCapesModule` on 1.8.9, `Cosmetics.java` on 26.3).

## Task 1: a login screen on first launch (Novo account required)
- When no Novo account is signed in (`api.novoAccount()` returns `null`), show a full-window login screen instead
  of the launcher. Show it with the same Backdrop and glass style, plus a big "Sign in with Discord" button that
  calls `api.novoLogin()`, plus errors. Hide the title bar's tabs and chip while it's showing, but keep the window
  buttons.
- After a successful sign-in, show the launcher (the "panel"). A Microsoft account stays optional, and players add
  it later in Accounts.
- **Shared state:** move the Novo account into a provider, for example `src/lib/novo.tsx` with a `NovoProvider` and
  `useNovo()` that offer `account`, `loaded`, `signIn`, `signOut` and `refresh`, following `src/lib/accounts.tsx`.
  Wrap the app in it in `App.tsx`. Make `NovoAccountCard` use it.
- **Signing out:** "Sign out" goes back to the login screen.
- **Startup:** while `novo_account` is loading, show a blank backdrop or a spinner, so the login screen doesn't
  flash when the player is already signed in.
- **Offline:** if `novo_account` fails because the network is down, don't lock the player out. Remember the last
  account (a `novoLastAccount` field in settings, or localStorage) and open the launcher with a small "offline"
  note. Only a real signed-out state (no token, or the relay answers 401) shows the login screen.
- **Dev mock:** in `devMock.ts`, `novo_account` returns `null` until `novo_login` runs. Keep it that way, so the
  login screen can be previewed.

## Task 2: Settings becomes a cog next to the player chip (top right)
- Remove `settings` from the `items` tab list in `Sidebar.tsx`, but keep `settings` as a `PageId`.
- In `TitleBar.tsx`, put an icon button (lucide `Settings`) right beside `AccountButton`. It opens the settings
  page, shows as active when that page is open, and has an aria-label/title from `t('nav.settings')`.
- Search for other places that navigate to `'settings'` and make sure they still work.

## Task 3: a Marketplace page with cosmetic banners
- **New page:** add `PageId 'marketplace'` (label key `nav.marketplace`, lucide `Store` icon) to the tabs, and a new
  `src/pages/Marketplace.tsx`. It has two sections:
  - **Capes:** move the cape picker here from `NovoAccountCard`. Reuse `CapePreview` and the `CapeTile` style
    exported from that file. Leave only a short "Change cape in Marketplace" link on the Accounts card.
  - **Banners:** a new cosmetic. A banner is a wide profile background image shown behind the player's name: on the
    Novo account card, on the title bar's player chip (as a subtle strip), and on the login screen after sign-in.
    Showing banners in game is optional for later.
- **Banner assets:** add a generator, `scripts/make-banners.mjs`, like `make-capes.mjs` (it writes PNGs with no
  dependencies). Write a few banners, for example 300x80 or 600x160 px, into
  `client-mod/src/main/resources/assets/novomc/textures/banners/<id>.png` (or `src/assets/banners/` if they're
  launcher-only). Load them in the UI with `import.meta.glob(..., { eager: true, query: '?url', import: 'default' })`,
  the same way `NovoAccountCard` loads capes.
- **Relay:** in `relay/src/capes.js`, add `BANNERS` (ids, names, `free`) and `ownedBanners()`. Locked banners are
  granted through the `grants` table using a `banner:<id>` key, or a new `banner_grants` table. Add a `banner` column
  to `users` with `ALTER TABLE users ADD COLUMN banner TEXT;`: add it to `schema.sql` and run it on the remote D1.
  `account()` in `relay/src/account.js` returns `banner` and `banners: [{id, name, owned}]`. `PATCH /account/me`
  accepts `{banner: id|null}` and checks ownership, the same way it does for `cape`. Extend
  `relay/test/account.test.mjs`.
- **Launcher:** in `src-tauri/src/novo/mod.rs`, add `banner` and `banners` to `NovoAccount`, add a
  `novo_set_banner(banner: Option<String>)` command (copy `novo_set_cape`), and register it in `src-tauri/src/lib.rs`. Add an api
  wrapper and mock it in the dev mock.
- **Strings:** add i18n strings for the Marketplace, banners and the login screen to all 5 locales.

## Done means
- On a first install with no Novo session, the login screen appears. Signing in opens the launcher, and signing out
  returns to the login screen.
- The cog sits next to the player chip and opens Settings, and the Settings tab is gone from the tabs.
- The Marketplace tab shows capes and banners. Picking one saves to the relay and shows up on the account card and
  the chip.
- `cargo test`, `npx tsc -b` and the relay tests pass, and the relay is deployed with the schema change.
  `docs/DEVELOPMENT.md` covers the login gate, the Marketplace and banners.
- Nothing is committed or released until the owner asks.
