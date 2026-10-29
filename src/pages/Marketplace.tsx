import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { cosmeticStill } from '../lib/models3d';
import { Check, Coins, Gift, Loader2, LockKeyhole, RefreshCw, ShoppingBag, Sparkles, Tag } from 'lucide-react';
import { ModelStage, type StageItem } from '../components/ModelStage';
import { api, type CosmeticAnimation, type NovoAccount, type StoreItem, type StoreState } from '../lib/api';
import { useNovo } from '../lib/novo';
import { useT, type Key } from '../lib/i18n';

const BANNERS = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/banners/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;
const assetUrl = (assets: Record<string, string>, id: string) => Object.entries(assets).find(([path]) => path.endsWith(`/${id}.png`))?.[1];
type Category = 'all' | 'cape' | 'banner' | 'wings' | 'hat';

/** The id equipped in a slot, for the store card's "Equipped" badge. */
function worn(account: NovoAccount, kind: StoreItem['kind']): string | null {
  if (kind === 'cape') return account.cape;
  if (kind === 'banner') return account.banner;
  if (kind === 'wings') return account.wing;
  return account.hat;
}

/**
 * The 3D model a store item is shown with. Capes, wings and hats all get one,
 * slowly turning, so the item is judged as the object it is rather than as an
 * icon; banners stay flat because they are a picture on a wall.
 */
function stageFor(item: { kind: StoreItem['kind'] | 'cape' | 'wings' | 'hat'; id: string; animated?: CosmeticAnimation | null }): StageItem | null {
  if (item.kind === 'cape') return { type: 'cape', id: item.id, animated: item.animated };
  if (item.kind === 'wings') return { type: 'wings', id: item.id };
  if (item.kind === 'hat') return { type: 'hat', id: item.id };
  return null;
}

export function MarketplacePage() {
  const t = useT();
  const { account, update, refresh } = useNovo();
  const [store, setStore] = useState<StoreState | null>(null);
  const [category, setCategory] = useState<Category>('all');
  const [giftCode, setGiftCode] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loadStore = useCallback(async () => {
    try { setStore(await api.novoShop()); setError(null); }
    catch (e) { setError(String(e)); }
  }, []);

  useEffect(() => { void loadStore(); }, [loadStore, account?.coins]);
  useEffect(() => {
    const onFocus = () => { void refresh(); void loadStore(); };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [loadStore, refresh]);

  const items = store?.items ?? [];
  const shown = useMemo(() => items.filter((item) => category === 'all' || item.kind === category), [items, category]);
  const ownedCapes = account?.capes.filter((item) => item.owned) ?? [];
  const ownedBanners = account?.banners.filter((item) => item.owned) ?? [];
  const ownedWings = account?.wings.filter((item) => item.owned) ?? [];
  const ownedHats = account?.hats.filter((item) => item.owned) ?? [];

  async function buy(item: StoreItem) {
    setBusy(item.id); setError(null); setMessage(null);
    try {
      const next = await api.novoPurchaseItem(item.id);
      update(next);
      if (item.kind === 'cape') update(await api.novoSetCape(item.id));
      if (item.kind === 'banner') update(await api.novoSetBanner(item.id));
      if (item.kind === 'wings') update(await api.novoSetWing(item.id));
      if (item.kind === 'hat') update(await api.novoSetHat(item.id));
      setMessage(t('shop.purchaseSuccess', { name: item.name }));
      await loadStore();
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  async function redeem(event: FormEvent) {
    event.preventDefault();
    if (!giftCode.trim()) return;
    setBusy('gift'); setError(null); setMessage(null);
    try {
      update(await api.novoRedeemGift(giftCode));
      setGiftCode('');
      setMessage(t('shop.giftSuccess'));
      await loadStore();
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  async function buyCoins(bundleId: string) {
    setBusy(`bundle:${bundleId}`); setError(null); setMessage(null);
    try { await api.novoCheckout(bundleId); }
    catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  async function equip(kind: 'cape' | 'banner' | 'wings' | 'hat', id: string | null) {
    setBusy(`equip:${kind}:${id ?? 'none'}`); setError(null);
    try {
      const next = kind === 'cape' ? await api.novoSetCape(id)
        : kind === 'banner' ? await api.novoSetBanner(id)
          : kind === 'wings' ? await api.novoSetWing(id)
            : await api.novoSetHat(id);
      update(next);
    } catch (e) { setError(String(e)); }
    finally { setBusy(null); }
  }

  if (!account) return <div className="p-10 text-novo-muted">{t('marketplace.signInRequired')}</div>;

  const featured = shown.find((item) => item.kind === 'wings') ?? shown[0] ?? items[0];
  const capacity = [ownedCapes.length, ownedBanners.length, ownedWings.length, ownedHats.length].reduce((a, b) => a + b, 0);

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6 p-6 pb-32 lg:p-8 lg:pb-32">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('nav.marketplace')}</p>
          <h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('shop.title')}</h1>
          <p className="mt-2 max-w-2xl text-novo-muted">{t('shop.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-2 rounded-2xl border border-amber-300/25 bg-amber-300/10 px-4 py-2.5">
            <Coins size={17} className="text-amber-200" />
            <span className="text-lg font-black tabular-nums">{(account.coins ?? 0).toLocaleString()}</span>
            <span className="text-xs font-bold text-amber-200">NC</span>
          </span>
          <button onClick={() => { void refresh(); void loadStore(); }} title={t('shop.refresh')} aria-label={t('shop.refresh')} className="rounded-2xl border border-novo-border p-2.5 text-novo-muted transition hover:bg-novo-hover hover:text-novo-text"><RefreshCw size={17} /></button>
        </div>
      </header>

      {(error || message) && <div role={error ? 'alert' : 'status'} className={`rounded-xl border px-4 py-3 text-sm ${error ? 'border-danger/30 bg-danger/10 text-danger' : 'border-play/30 bg-play/10 text-play'}`}>{error || message}</div>}

      <section className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        {featured && <FeaturedCard item={featured} equipped={worn(account, featured.kind) === featured.id} balance={store?.balance ?? account.coins} busy={busy !== null} onBuy={() => void buy(featured)} onEquip={() => void equip(featured.kind, featured.id)} />}

        <div className="flex flex-col gap-4">
          <div className="glass rounded-3xl p-5">
            <div className="flex items-center justify-between"><div><p className="text-xs font-bold uppercase tracking-[0.2em] text-novo-muted">{t('shop.wallet')}</p><p className="mt-1 text-sm text-novo-muted">{t('shop.walletHint')}</p></div><Sparkles className="text-brand-c" size={18} /></div>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <div className="rounded-2xl bg-novo-bg/50 p-3"><p className="text-2xl font-black tabular-nums">{(account.coins ?? 0).toLocaleString()}</p><p className="text-[11px] font-bold uppercase tracking-wider text-amber-200">Novo Coins</p></div>
              <div className="rounded-2xl bg-novo-bg/50 p-3"><p className="text-2xl font-black tabular-nums">{capacity}</p><p className="text-[11px] font-bold uppercase tracking-wider text-novo-muted">{t('shop.locker')}</p></div>
            </div>
          </div>

          <div className="glass rounded-3xl p-5">
            <div className="flex items-center gap-2"><Gift size={17} className="text-brand-c" /><h2 className="font-bold">{t('shop.redeemTitle')}</h2></div>
            <p className="mt-1 text-sm text-novo-muted">{t('shop.redeemHint')}</p>
            <form onSubmit={(event) => void redeem(event)} className="mt-3 flex gap-2">
              <input value={giftCode} onChange={(event) => setGiftCode(event.target.value)} autoCapitalize="characters" autoComplete="off" placeholder={t('shop.codePlaceholder')} className="min-w-0 flex-1 rounded-xl border border-novo-border bg-novo-bg/70 px-3 py-2.5 font-mono text-sm tracking-wider outline-none focus:border-accent/60" />
              <button disabled={!giftCode.trim() || busy !== null} className="flex items-center gap-2 rounded-xl bg-novo-elev-2 px-4 py-2.5 text-sm font-bold transition hover:bg-novo-hover disabled:opacity-40">{busy === 'gift' ? <Loader2 size={15} className="animate-spin" /> : <Tag size={15} />}{t('shop.redeem')}</button>
            </form>
          </div>
        </div>
      </section>

      <section className="glass rounded-3xl p-5">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-lg font-bold">{t('shop.getCoins')}</h2><p className="mt-1 text-sm text-novo-muted">{t('shop.coinBundlesHint')}</p></div><span className="rounded-full border border-novo-border px-3 py-1 text-xs font-semibold text-novo-muted">Stripe Checkout · USD</span></div>
        <div className="grid gap-3 sm:grid-cols-3">
          {(store?.bundles ?? []).map((bundle) => <article key={bundle.id} className={`relative rounded-2xl border p-4 ${bundle.badge ? 'border-accent/40 bg-accent/5' : 'border-novo-border bg-novo-bg/30'}`}>
            {bundle.badge && <span className="absolute -top-2 right-3 rounded-full bg-gradient-to-r from-brand-a to-brand-b px-2.5 py-1 text-[9px] font-black tracking-wider text-white">{bundle.badge}</span>}
            <p className="text-sm font-bold text-novo-muted">{bundle.name}</p><p className="mt-1 text-2xl font-black">{bundle.coins.toLocaleString()} <span className="text-sm font-bold text-amber-200">NC</span></p>
            <button onClick={() => void buyCoins(bundle.id)} disabled={!store?.paymentReady || busy !== null} className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b px-4 py-2.5 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-45">{busy === `bundle:${bundle.id}` ? <Loader2 size={15} className="animate-spin" /> : <ShoppingBag size={15} />}{store?.paymentReady ? `$${bundle.price.toFixed(2)} · ${t('shop.buyCoins')}` : t('shop.checkoutSoon')}</button>
          </article>)}
          {store && !store.paymentReady && <p className="sm:col-span-3 text-xs text-novo-muted">{t('shop.checkoutSetup')}</p>}
        </div>
      </section>

      <section>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
          <div><h2 className="text-2xl font-black">{t('shop.catalog')}</h2><p className="mt-1 text-sm text-novo-muted">{t('shop.catalogHint')}</p></div>
          <div className="flex flex-wrap gap-1 rounded-xl border border-novo-border bg-novo-bg/40 p-1">
            {(['all', 'cape', 'banner', 'wings', 'hat'] as Category[]).map((value) => <button key={value} onClick={() => setCategory(value)} aria-pressed={category === value} className={`rounded-lg px-3 py-2 text-xs font-bold capitalize transition ${category === value ? 'bg-accent/15 text-accent' : 'text-novo-muted hover:text-novo-text'}`}>{t(`shop.category.${value}` as Key)}</button>)}
          </div>
        </div>
        {store === null ? <div className="glass rounded-3xl p-10 text-center text-novo-muted"><Loader2 size={20} className="mx-auto animate-spin" /><p className="mt-3 text-sm">{t('shop.loading')}</p></div>
          : shown.length === 0 ? <div className="glass rounded-3xl p-10 text-center"><Sparkles size={24} className="mx-auto text-brand-c" /><p className="mt-3 font-bold">{t('shop.noItems')}</p><p className="mt-1 text-sm text-novo-muted">{t('shop.noItemsHint')}</p></div>
            : <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
              {shown.map((item) => <StoreCard key={`${item.kind}:${item.id}`} item={item} balance={store.balance} equipped={worn(account, item.kind) === item.id} busy={busy !== null} onBuy={() => void buy(item)} onEquip={() => void equip(item.kind, item.id)} />)}
            </div>}
      </section>

      <section className="glass rounded-3xl p-5">
        <div className="mb-4 flex items-center gap-2"><LockKeyhole size={17} className="text-brand-c" /><div><h2 className="font-bold">{t('shop.locker')}</h2><p className="text-xs text-novo-muted">{t('shop.lockerHint')}</p></div></div>
        <div className="grid gap-5 md:grid-cols-2">
          <LockerGroup title={t('marketplace.banners')} kind="banner" items={ownedBanners} equipped={account.banner} busy={busy !== null} onEquip={(id) => void equip('banner', id)} />
          <LockerGroup title={t('novo.cape')} kind="cape" items={ownedCapes} equipped={account.cape} busy={busy !== null} onEquip={(id) => void equip('cape', id)} />
          <LockerGroup title={t('shop.category.wings')} kind="wings" items={ownedWings} equipped={account.wing} busy={busy !== null} onEquip={(id) => void equip('wings', id)} />
          <LockerGroup title={t('shop.category.hat')} kind="hat" items={ownedHats} equipped={account.hat} busy={busy !== null} onEquip={(id) => void equip('hat', id)} />
        </div>
      </section>
    </div>
  );
}

/** The item the store leads with: model on the left, price and action on the right. */
function FeaturedCard({ item, equipped, balance, busy, onBuy, onEquip }: { item: StoreItem; equipped: boolean; balance: number; busy: boolean; onBuy: () => void; onEquip: () => void }) {
  const t = useT();
  const stage = stageFor(item);
  return (
    <article className="glass relative overflow-hidden rounded-3xl p-5 sm:p-6">
      <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-brand-a/20 blur-3xl" />
      <div className="relative grid gap-5 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] sm:items-center">
        <div className="relative aspect-square overflow-hidden rounded-2xl bg-gradient-to-br from-novo-bg to-brand-a/10 ring-1 ring-white/5">
          {stage ? <ModelStage item={stage} interactive className="absolute inset-0" /> : <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: assetUrl(BANNERS, item.id) ? `url(${assetUrl(BANNERS, item.id)})` : undefined }} />}
          <span className="pointer-events-none absolute bottom-2 left-1/2 -translate-x-1/2 rounded-full bg-black/45 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-white/80">{t('shop.turn')}</span>
        </div>
        <div>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-brand-a/20 px-2.5 py-1 text-[10px] font-black uppercase tracking-wider text-brand-c">{t('shop.featured')}</span>
            <span className="rounded-full border border-white/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-novo-muted">{item.kind}</span>
            {equipped && <span className="flex items-center gap-1 rounded-full bg-play/90 px-2 py-1 text-[10px] font-black text-black"><Check size={11} />{t('shop.equipped')}</span>}
          </div>
          <h2 className="mt-3 text-3xl font-black leading-tight">{item.name}</h2>
          <p className="mt-2 text-sm leading-relaxed text-novo-muted">{item.description}</p>
          <p className="mt-4 text-2xl font-black text-amber-200">{item.price} <span className="text-sm">NC</span></p>
          <div className="mt-4">
            {item.owned ? <button onClick={onEquip} disabled={busy || equipped} className="flex w-full items-center justify-center gap-2 rounded-xl border border-novo-border bg-novo-elev-2 py-3 text-sm font-bold transition hover:bg-novo-hover disabled:opacity-50">{equipped ? <Check size={15} /> : <Sparkles size={15} />}{equipped ? t('shop.equipped') : t('shop.equip')}</button>
              : <button onClick={onBuy} disabled={busy || balance < item.price} className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b py-3 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40"><ShoppingBag size={15} />{balance >= item.price ? t('shop.buy') : t('shop.needMore')}</button>}
          </div>
        </div>
      </div>
    </article>
  );
}

function StoreCard({ item, balance, equipped, busy, onBuy, onEquip }: { item: StoreItem; balance: number; equipped: boolean; busy: boolean; onBuy: () => void; onEquip: () => void }) {
  const t = useT();
  const stage = stageFor(item);
  return <article className="glass group flex flex-col overflow-hidden rounded-3xl transition hover:border-novo-border-strong">
    <div className="relative h-48 overflow-hidden bg-gradient-to-br from-novo-bg to-brand-a/10">
      {stage ? <ModelStage item={stage} className="absolute inset-0" /> : <div className="absolute inset-0 bg-cover bg-center" style={{ backgroundImage: assetUrl(BANNERS, item.id) ? `url(${assetUrl(BANNERS, item.id)})` : undefined }} />}
      <span className="absolute left-3 top-3 rounded-full border border-white/10 bg-black/50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-white">{item.kind}</span>
      {equipped && <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-play/90 px-2 py-1 text-[10px] font-black text-black"><Check size={12} />{t('shop.equipped')}</span>}
      {item.animated && <span className="absolute bottom-3 left-3 flex items-center gap-1 rounded-full bg-black/60 px-2 py-1 text-[10px] font-black uppercase tracking-wider text-white"><Sparkles size={11} />{t('shop.animated')}</span>}
      {(item.kind === 'wings' || item.kind === 'hat') && <span className="absolute bottom-3 right-3 rounded-full bg-black/60 px-2 py-1 text-[10px] font-black uppercase tracking-wider text-white">{t('shop.only189')}</span>}
    </div>
    <div className="flex flex-1 flex-col p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0"><h3 className="truncate font-bold">{item.name}</h3><p className="mt-1 min-h-10 text-xs leading-relaxed text-novo-muted">{item.description}</p></div>
        <p className="shrink-0 rounded-lg bg-amber-300/10 px-2 py-1 text-sm font-black text-amber-200">{item.price}<span className="text-[10px]"> NC</span></p>
      </div>
      <div className="mt-auto pt-3">
        {item.owned ? <button onClick={onEquip} disabled={busy || equipped} className="flex w-full items-center justify-center gap-2 rounded-xl border border-novo-border bg-novo-elev-2 py-2.5 text-sm font-bold transition hover:bg-novo-hover disabled:opacity-50">{equipped ? <Check size={15} /> : <Sparkles size={15} />}{equipped ? t('shop.equipped') : t('shop.equip')}</button>
          : <button onClick={onBuy} disabled={busy || balance < item.price} className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b py-2.5 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40"><ShoppingBag size={15} />{balance >= item.price ? t('shop.buy') : t('shop.needMore')}</button>}
      </div>
    </div>
  </article>;
}

/**
 * A tile's picture: the banner art as it is, or a still of the 3D piece.
 *
 * The collection holds dozens of tiles at once and a browser only keeps a
 * handful of live WebGL canvases, so only the big cards spin; these are drawn
 * once and cached.
 */
function LockerThumb({ kind, id }: { kind: 'cape' | 'banner' | 'wings' | 'hat'; id: string }) {
  const [src, setSrc] = useState<string | null>(kind === 'banner' ? assetUrl(BANNERS, id) ?? null : null);
  useEffect(() => {
    if (kind === 'banner') {
      setSrc(assetUrl(BANNERS, id) ?? null);
      return;
    }
    let live = true;
    cosmeticStill(kind, id).then((png) => { if (live) setSrc(png); }, () => {});
    return () => { live = false; };
  }, [kind, id]);
  if (!src) return <span className="absolute inset-0 rounded-lg bg-white/5" />;
  return <img src={src} alt="" className={`absolute inset-0 h-full w-full object-contain ${kind === 'banner' ? 'rounded-lg object-cover' : ''}`} />;
}

/** One slot of the collection: a tile per owned item, each an object you can look around. */
function LockerGroup({ title, kind, items, equipped, busy, onEquip }: { title: string; kind: 'cape' | 'banner' | 'wings' | 'hat'; items: { id: string; name: string; owned: boolean; animated?: CosmeticAnimation | null }[]; equipped: string | null; busy: boolean; onEquip: (id: string | null) => void }) {
  const t = useT();
  return <div>
    <h3 className="mb-2 text-sm font-bold text-novo-muted">{title}</h3>
    <div className="flex flex-wrap gap-2">
      <button onClick={() => onEquip(null)} disabled={busy || !equipped} className={`flex h-[76px] w-[64px] flex-col items-center justify-center gap-1 rounded-2xl border text-[11px] font-semibold ${!equipped ? 'border-accent/50 bg-accent/10' : 'border-novo-border hover:bg-novo-hover'}`}>
        <span className="grid h-10 w-10 place-items-center rounded-full border border-dashed border-novo-border-strong text-novo-muted">—</span>
        {t('shop.none')}
      </button>
      {items.map((item) => {
        const selected = equipped === item.id;
        return <button key={item.id} onClick={() => onEquip(item.id)} disabled={busy || selected} title={item.name} className={`flex h-[76px] w-[64px] flex-col items-center gap-1 rounded-2xl border p-1 text-[11px] font-semibold transition ${selected ? 'border-accent/60 bg-accent/10' : 'border-novo-border hover:bg-novo-hover'}`}>
          <span className="relative h-11 w-full">
            <LockerThumb kind={kind} id={item.id} />
          </span>
          <span className="w-full truncate text-center">{item.name}</span>
        </button>;
      })}
    </div>
  </div>;
}
