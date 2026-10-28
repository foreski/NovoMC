import { useState } from 'react';
import { Lock, X } from 'lucide-react';
import { CapePreview, CapeTile } from '../components/NovoAccountCard';
import { useNovo } from '../lib/novo';
import { useT } from '../lib/i18n';
import { api } from '../lib/api';

const BANNERS = import.meta.glob('../../client-mod/src/main/resources/assets/novomc/textures/banners/*.png', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;
const assetUrl = (assets: Record<string, string>, id: string) => Object.entries(assets).find(([path]) => path.endsWith(`/${id}.png`))?.[1];

export function MarketplacePage() {
  const t = useT();
  const { account, update } = useNovo();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function equip(kind: 'cape' | 'banner', id: string | null) {
    if (!account) return;
    setBusy(true); setError(null);
    try { update(kind === 'cape' ? await api.novoSetCape(id) : await api.novoSetBanner(id)); }
    catch (e) { setError(String(e)); }
    finally { setBusy(false); }
  }

  if (!account) return <div className="p-10 text-novo-muted">{t('marketplace.signInRequired')}</div>;
  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8 p-8">
      <header><p className="text-xs font-bold tracking-[0.3em] text-brand-c uppercase">{t('nav.marketplace')}</p><h1 className="gradient-text mt-2 text-5xl font-black tracking-tight">{t('marketplace.title')}</h1><p className="mt-2 text-novo-muted">{t('marketplace.hint')}</p></header>
      {error && <p className="text-sm text-danger">{error}</p>}
      <section className="glass rounded-3xl p-6">
        <h2 className="text-xl font-bold">{t('novo.cape')}</h2><p className="mt-1 text-sm text-novo-muted">{t('novo.capeHint')}</p>
        <div className="mt-5 flex flex-wrap gap-3">
          <CapeTile selected={!account.cape} label={t('novo.noCape')} onClick={() => void equip('cape', null)}><div className="grid h-20 w-[50px] place-items-center rounded-sm border border-dashed border-novo-border-strong text-novo-muted"><X size={16} /></div></CapeTile>
          {account.capes.map((cape) => <CapeTile key={cape.id} selected={account.cape === cape.id} locked={!cape.owned || busy} label={cape.name} onClick={() => void equip('cape', cape.id)}><CapePreview id={cape.id} /></CapeTile>)}
        </div>
      </section>
      <section className="glass rounded-3xl p-6">
        <h2 className="text-xl font-bold">{t('marketplace.banners')}</h2><p className="mt-1 text-sm text-novo-muted">{t('marketplace.bannerHint')}</p>
        <div className="mt-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <button disabled={busy} onClick={() => void equip('banner', null)} aria-pressed={!account.banner} className={`group relative overflow-hidden rounded-2xl border p-1 text-left ${!account.banner ? 'border-accent ring-2 ring-accent' : 'border-novo-border hover:border-novo-border-strong'}`}>
            <div className="grid h-24 place-items-center rounded-xl bg-novo-elev text-sm text-novo-muted">{t('marketplace.noBanner')}</div><span className="block px-2 py-2 text-sm font-semibold">{t('marketplace.none')}</span>
          </button>
          {account.banners.map((banner) => {
            const url = assetUrl(BANNERS, banner.id);
            return <button key={banner.id} disabled={!banner.owned || busy} onClick={() => void equip('banner', banner.id)} aria-pressed={account.banner === banner.id} className={`group relative overflow-hidden rounded-2xl border p-1 text-left transition ${account.banner === banner.id ? 'border-accent ring-2 ring-accent' : 'border-novo-border hover:border-novo-border-strong'} ${!banner.owned ? 'cursor-not-allowed opacity-50' : ''}`}>
              <div className="h-24 rounded-xl bg-cover bg-center" style={{ backgroundImage: url ? `url(${url})` : undefined }} />
              <span className="flex items-center justify-between px-2 py-2 text-sm font-semibold">{banner.name}{!banner.owned && <Lock size={13} className="text-novo-muted" />}</span>
            </button>;
          })}
        </div>
      </section>
    </div>
  );
}
