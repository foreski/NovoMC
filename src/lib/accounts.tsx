// Shared account state for the sidebar chip, Home and the Accounts page.
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, type Account, type AccountStore } from './api';

interface AccountsContext {
  accounts: Account[];
  active: Account | null;
  error: string | null;
  refresh: () => Promise<void>;
  /** Microsoft sign-in; rejects with the reason (cancelled, no Minecraft, ...). */
  signInMicrosoft: () => Promise<void>;
  remove: (uuid: string) => Promise<void>;
  select: (uuid: string) => Promise<void>;
}

const Ctx = createContext<AccountsContext | null>(null);

export function AccountsProvider({ children }: { children: ReactNode }) {
  const [store, setStore] = useState<AccountStore>({ activeUuid: null, accounts: [] });
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (call: Promise<AccountStore>) => {
    const next = await call;
    setStore(next);
    setError(null);
  }, []);

  const refresh = useCallback(
    () => run(api.listAccounts()).catch((e) => setError(String(e))),
    [run],
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value: AccountsContext = {
    accounts: store.accounts,
    active: store.accounts.find((a) => a.uuid === store.activeUuid) ?? null,
    error,
    refresh,
    signInMicrosoft: () => run(api.microsoftLogin()),
    remove: (uuid) => run(api.removeAccount(uuid)).catch((e) => setError(String(e))),
    select: (uuid) => run(api.setActiveAccount(uuid)).catch((e) => setError(String(e))),
  };

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAccounts(): AccountsContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAccounts must be used inside <AccountsProvider>');
  return ctx;
}
