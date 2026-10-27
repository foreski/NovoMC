import { useEffect, useState, type FormEvent } from 'react';
import { motion } from 'motion/react';
import { Globe, Plus, RefreshCw, Server as ServerIcon, Wifi, WifiOff, X } from 'lucide-react';
import { api, type Server, type ServerStatus } from '../lib/api';
import { useT } from '../lib/i18n';

type Status = ServerStatus | 'loading' | 'offline';

/** Favorite servers with live player counts; Join launches the selected instance straight into one. */
export function ServerCards({ onJoin, disabled }: { onJoin: (address: string) => void; disabled: boolean }) {
  const t = useT();
  const [servers, setServers] = useState<Server[]>([]);
  const [status, setStatus] = useState<Record<string, Status>>({});
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState<string | null>(null);

  const ping = (list: Server[]) => {
    for (const s of list) {
      setStatus((st) => ({ ...st, [s.address]: 'loading' }));
      api.pingServer(s.address).then(
        (r) => setStatus((st) => ({ ...st, [s.address]: r })),
        () => setStatus((st) => ({ ...st, [s.address]: 'offline' })),
      );
    }
  };

  useEffect(() => {
    api.listServers().then((list) => {
      setServers(list);
      ping(list);
    }, () => {});
  }, []);

  async function save(next: Server[]) {
    try {
      setServers(await api.saveServers(next));
      setError(null);
      return true;
    } catch (e) {
      setError(String(e));
      return false;
    }
  }

  async function add(e: FormEvent) {
    e.preventDefault();
    const entry = { name: name.trim() || address.trim(), address: address.trim() };
    if (await save([...servers, entry])) {
      ping([entry]);
      setAdding(false);
      setName('');
      setAddress('');
    }
  }

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-xs font-bold tracking-[0.25em] text-novo-muted uppercase">
        <Globe size={14} /> {t('srv.title')}
        <button onClick={() => ping(servers)} aria-label={t('srv.refresh')} className="ml-1 rounded p-1 hover:bg-novo-hover hover:text-novo-text">
          <RefreshCw size={12} />
        </button>
      </h2>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {servers.map((s, i) => {
          const st = status[s.address];
          const up = st && typeof st === 'object';
          return (
            <motion.div
              key={s.address}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
              className="glass group relative flex items-center gap-3 rounded-2xl p-3"
            >
              <div className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-brand-a/30 to-brand-b/30 ring-1 ring-white/10">
                <ServerIcon size={18} className="text-accent" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate font-semibold">{s.name}</div>
                <div className="flex items-center gap-1.5 truncate text-xs text-novo-muted">
                  {st === 'loading' || !st ? (
                    t('srv.pinging')
                  ) : up ? (
                    <>
                      <Wifi size={11} className="text-play" />
                      {t('srv.players', { online: st.online.toLocaleString(), max: st.max.toLocaleString() })} · {st.latencyMs} ms
                    </>
                  ) : (
                    <>
                      <WifiOff size={11} className="text-danger" /> {t('srv.offline')}
                    </>
                  )}
                </div>
              </div>
              <button
                onClick={() => onJoin(s.address)}
                disabled={disabled}
                className="rounded-xl bg-gradient-to-b from-[#86efac] to-play px-3 py-1.5 text-xs font-black tracking-wider text-[#052e12] transition-transform enabled:hover:scale-[1.04] disabled:opacity-40"
              >
                {t('srv.join')}
              </button>
              <button
                onClick={() => save(servers.filter((x) => x !== s))}
                aria-label={t('srv.remove', { name: s.name })}
                className="absolute -top-2 -right-2 hidden rounded-full bg-novo-elev-2 p-1 text-novo-muted ring-1 ring-novo-border group-hover:block hover:text-danger"
              >
                <X size={12} />
              </button>
            </motion.div>
          );
        })}
        {adding ? (
          <form onSubmit={add} className="glass flex flex-col gap-2 rounded-2xl p-3">
            <input autoFocus value={address} onChange={(e) => setAddress(e.target.value)} placeholder={t('srv.address')} spellCheck={false}
              className="rounded-lg border border-novo-border bg-novo-bg/70 px-3 py-1.5 text-sm outline-none focus:border-accent/60" />
            <div className="flex gap-2">
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t('srv.name')}
                className="min-w-0 flex-1 rounded-lg border border-novo-border bg-novo-bg/70 px-3 py-1.5 text-sm outline-none focus:border-accent/60" />
              <button type="submit" disabled={!address.trim()} className="rounded-lg bg-accent/20 px-3 text-sm font-semibold text-accent disabled:opacity-40">
                {t('srv.add')}
              </button>
            </div>
            {error && <p className="text-xs text-danger">{error}</p>}
          </form>
        ) : (
          <button
            onClick={() => setAdding(true)}
            className="flex items-center justify-center gap-2 rounded-2xl border border-dashed border-novo-border-strong p-3 text-sm text-novo-muted transition-colors hover:border-accent/50 hover:text-novo-text"
          >
            <Plus size={16} /> {t('srv.addServer')}
          </button>
        )}
      </div>
    </section>
  );
}
