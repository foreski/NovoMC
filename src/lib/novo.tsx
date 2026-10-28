import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, type NovoAccount } from './api';

interface NovoContext {
  account: NovoAccount | null;
  loaded: boolean;
  offline: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  update: (account: NovoAccount) => void;
}

const Ctx = createContext<NovoContext | null>(null);
const CACHE = 'novo.lastAccount';

function cachedAccount(): NovoAccount | null {
  try {
    const raw = localStorage.getItem(CACHE);
    return raw ? JSON.parse(raw) as NovoAccount : null;
  } catch {
    return null;
  }
}

function clearCachedAccount() {
  try { localStorage.removeItem(CACHE); } catch { /* cache is best-effort */ }
}

export function NovoProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<NovoAccount | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [offline, setOffline] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const update = useCallback((next: NovoAccount) => {
    setAccount(next);
    setOffline(false);
    setError(null);
    try { localStorage.setItem(CACHE, JSON.stringify(next)); } catch { /* cache is best-effort */ }
  }, []);

  const refresh = useCallback(async () => {
    try {
      const next = await api.novoAccount();
      setAccount(next);
      setOffline(false);
      setError(null);
      if (next) {
        try { localStorage.setItem(CACHE, JSON.stringify(next)); } catch { /* cache is best-effort */ }
      } else {
        clearCachedAccount();
      }
    } catch (e) {
      const remembered = cachedAccount();
      if (remembered) setAccount(remembered);
      setOffline(true);
      setError(String(e));
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const signIn = useCallback(async () => {
    update(await api.novoLogin());
  }, [update]);

  const signOut = useCallback(async () => {
    await api.novoLogout();
    setAccount(null);
    setOffline(false);
    setError(null);
    clearCachedAccount();
  }, []);

  const value = useMemo(() => ({ account, loaded, offline, error, refresh, signIn, signOut, update }), [account, loaded, offline, error, refresh, signIn, signOut, update]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useNovo(): NovoContext {
  const value = useContext(Ctx);
  if (!value) throw new Error('useNovo must be used inside <NovoProvider>');
  return value;
}
