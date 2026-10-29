import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Check, Coins, Crown, Feather, Gift, Loader2, LockKeyhole, RefreshCw, ShoppingBag, Sparkles, Tag, WandSparkles } from 'lucide-react';
import { CapePreview } from '../components/NovoAccountCard';
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

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-7 p-8 pb-36">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div><p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('nav.marketplace')}</p><h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('shop.title')}</h1><p className="mt-2 max-w-2xl text-novo-muted">{t('shop.subtitle')}</p></div>
        <button onClick={() => { void refresh(); void loadStore(); }} title={t('shop.refresh')} className="rounded-xl border border-novo-border p-2.5 text-novo-muted transition hover:bg-novo-hover hover:text-novo-text"><RefreshCw size={17} /></button>
      </header>

      {(error || message) && <div role={error ? 'alert' : 'status'} className={`rounded-xl border px-4 py-3 text-sm ${error ? 'border-danger/30 bg-danger/10 text-danger' : 'border-play/30 bg-play/10 text-play'}`}>{error || message}</div>}

      <section className="grid gap-4 lg:grid-cols-[1.15fr_1fr]">
        <div className="glass relative flex min-h-52 flex-col justify-between overflow-hidden rounded-3xl p-6">
          <div className="absolute -right-10 -top-16 h-56 w-56 rounded-full bg-brand-a/20 blur-3xl" />
          <div className="relative flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.2em] text-novo-muted">{t('shop.wallet')}</p><div className="mt-3 flex items-center gap-3"><span className="grid h-12 w-12 place-items-center rounded-2xl bg-amber-300/15 text-amber-200"><Coins size={25} /></span><span className="text-4xl font-black tabular-nums">{(account.coins ?? 0).toLocaleString()}</span><span className="pt-3 text-sm font-bold text-amber-200">NC</span></div></div><Sparkles className="relative mt-1 text-brand-c" size={20} /></div>
          <p className="relative mt-6 text-sm text-novo-muted">{t('shop.walletHint')}</p>
        </div>

        <div className="glass rounded-3xl p-5">
          <div className="flex items-center gap-2"><Gift size={18} className="text-brand-c" /><h2 className="font-bold">{t('shop.redeemTitle')}</h2></div>
          <p className="mt-1 text-sm text-novo-muted">{t('shop.redeemHint')}</p>
          <form onSubmit={(event) => void redeem(event)} className="mt-4 flex gap-2">
            <input value={giftCode} onChange={(event) => setGiftCode(event.target.value)} autoCapitalize="characters" autoComplete="off" placeholder={t('shop.codePlaceholder')} className="min-w-0 flex-1 rounded-xl border border-novo-border bg-novo-bg/70 px-3 py-2.5 font-mono text-sm tracking-wider outline-none focus:border-accent/60" />
            <button disabled={!giftCode.trim() || busy !== null} className="flex items-center gap-2 rounded-xl bg-novo-elev-2 px-4 py-2.5 text-sm font-bold transition hover:bg-novo-hover disabled:opacity-40">{busy === 'gift' ? <Loader2 size={15} className="animate-spin" /> : <Tag size={15} />}{t('shop.redeem')}</button>
          </form>
        </div>
      </section>

      <section className="glass rounded-3xl p-5">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-bold">{t('shop.getCoins')}</h2><p className="mt-1 text-sm text-novo-muted">{t('shop.coinBundlesHint')}</p></div><span className="rounded-full border border-novo-border px-3 py-1 text-xs font-semibold text-novo-muted">Stripe Checkout · USD</span></div>
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
        <div className="mb-4 flex flex-wrap items-center justify-between gap-4"><div><h2 className="text-2xl font-black">{t('shop.catalog')}</h2><p className="mt-1 text-sm text-novo-muted">{t('shop.catalogHint')}</p></div><div className="flex flex-wrap gap-1 rounded-xl border border-novo-border bg-novo-bg/40 p-1">
          {(['all', 'cape', 'banner', 'wings', 'hat'] as Category[]).map((value) => <button key={value} onClick={() => setCategory(value)} className={`rounded-lg px-3 py-2 text-xs font-bold capitalize transition ${category === value ? 'bg-accent/15 text-accent' : 'text-novo-muted hover:text-novo-text'}`}>{t(`shop.category.${value}` as Key)}</button>)}
        </div></div>
        {store === null ? <div className="glass rounded-3xl p-10 text-center text-novo-muted"><Loader2 size={20} className="mx-auto animate-spin" /><p className="mt-3 text-sm">{t('shop.loading')}</p></div> : shown.length === 0 ? <div className="glass rounded-3xl p-10 text-center"><WandSparkles size={24} className="mx-auto text-brand-c" /><p className="mt-3 font-bold">{category === 'wings' || category === 'hat' ? t('shop.comingSoon') : t('shop.noItems')}</p><p className="mt-1 text-sm text-novo-muted">{category === 'wings' || category === 'hat' ? t('shop.moreCosmetics') : t('shop.noItemsHint')}</p></div> : <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
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

function StoreCard({ item, balance, equipped, busy, onBuy, onEquip }: { item: StoreItem; balance: number; equipped: boolean; busy: boolean; onBuy: () => void; onEquip: () => void }) {
  const t = useT();
  const preview = item.kind === 'cape' ? <div className="grid h-36 place-items-center bg-gradient-to-br from-novo-bg to-brand-a/10"><CapePreview id={item.id} scale={6} animated={item.animated} /></div>
    : item.kind === 'banner' ? <div className="h-36 bg-cover bg-center" style={{ backgroundImage: assetUrl(BANNERS, item.id) ? `url(${assetUrl(BANNERS, item.id)})` : undefined }} />
      : item.kind === 'wings' ? <div className="grid h-36 place-items-center bg-gradient-to-br from-novo-bg to-brand-a/10"><Feather size={40} className="text-brand-c" /></div>
        : item.kind === 'hat' ? <div className="grid h-36 place-items-center bg-gradient-to-br from-novo-bg to-amber-300/10"><Crown size={40} className="text-amber-200" /></div>
          : <div className="grid h-36 place-items-center bg-gradient-to-br from-novo-bg to-brand-a/10"><WandSparkles size={36} className="text-brand-c" /></div>;
  return <article className="glass group overflow-hidden rounded-3xl transition hover:border-novo-border-strong">
    <div className="relative overflow-hidden">{preview}<span className="absolute left-3 top-3 rounded-full border border-white/10 bg-black/50 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-white">{item.kind}</span>{equipped && <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-play/90 px-2 py-1 text-[10px] font-black text-black"><Check size={12} />{t('shop.equipped')}</span>}{item.animated && <span className="absolute bottom-3 left-3 flex items-center gap-1 rounded-full bg-black/60 px-2 py-1 text-[10px] font-black uppercase tracking-wider text-white"><Sparkles size={11} />{t('shop.animated')}</span>}{(item.kind === 'wings' || item.kind === 'hat') && <span className="absolute bottom-3 left-3 rounded-full bg-black/60 px-2 py-1 text-[10px] font-black uppercase tracking-wider text-white">{t('shop.only189')}</span>}</div>
    <div className="p-4"><div className="flex items-start justify-between gap-3"><div><h3 className="font-bold">{item.name}</h3><p className="mt-1 min-h-10 text-xs leading-relaxed text-novo-muted">{item.description}</p></div><p className="shrink-0 rounded-lg bg-amber-300/10 px-2 py-1 text-sm font-black text-amber-200">{item.price} NC</p></div>
      {item.owned ? <button onClick={onEquip} disabled={busy || equipped} className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl border border-novo-border bg-novo-elev-2 py-2.5 text-sm font-bold transition hover:bg-novo-hover disabled:opacity-50">{equipped ? <Check size={15} /> : <Sparkles size={15} />}{equipped ? t('shop.equipped') : t('shop.equip')}</button>
        : <button onClick={onBuy} disabled={busy || balance < item.price} className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand-a to-brand-b py-2.5 text-sm font-bold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-40"><ShoppingBag size={15} />{balance >= item.price ? t('shop.buy') : t('shop.needMore')}</button>}
    </div>
  </article>;
}

function LockerGroup({ title, kind, items, equipped, busy, onEquip }: { title: string; kind: 'cape' | 'banner' | 'wings' | 'hat'; items: { id: string; name: string; owned: boolean; animated?: CosmeticAnimation | null }[]; equipped: string | null; busy: boolean; onEquip: (id: string | null) => void }) {
  const t = useT();
  return <div><h3 className="mb-2 text-sm font-bold text-novo-muted">{title}</h3><div className="flex flex-wrap gap-2">
    <button onClick={() => onEquip(null)} disabled={busy || !equipped} className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold ${!equipped ? 'border-accent/50 bg-accent/10' : 'border-novo-border hover:bg-novo-hover'}`}>{t('shop.none')}</button>
    {items.map((item) => <button key={item.id} onClick={() => onEquip(item.id)} disabled={busy || equipped === item.id} title={item.name} className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-xs font-semibold ${equipped === item.id ? 'border-accent/50 bg-accent/10' : 'border-novo-border hover:bg-novo-hover'}`}>
      {kind === 'cape' ? <CapePreview id={item.id} scale={1.5} animated={item.animated} /> : kind === 'wings' ? <Feather size={14} className="text-brand-c" /> : kind === 'hat' ? <Crown size={14} className="text-amber-200" /> : <span className="h-4 w-8 rounded bg-cover bg-center" style={{ backgroundImage: assetUrl(BANNERS, item.id) ? `url(${assetUrl(BANNERS, item.id)})` : undefined }} />}{item.name}{equipped === item.id && <Check size={12} className="text-play" />}
    </button>)}
  </div></div>;
}
