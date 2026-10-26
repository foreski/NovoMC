// Instance list shared by Home, Instances and Versions.
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, type Instance, type Loader } from './api';
import { useGame } from './game';

interface InstancesContext {
  /** null while loading. */
  instances: Instance[] | null;
  error: string | null;
  refresh: () => Promise<void>;
  /** These reject with the backend's message so dialogs can show it. */
  create: (name: string, version: string, loader: Loader, loaderVersion: string | null) => Promise<Instance>;
  update: (instance: Instance) => Promise<Instance>;
  duplicate: (id: string) => Promise<Instance>;
  remove: (id: string) => Promise<void>;
}

const Ctx = createContext<InstancesContext | null>(null);

export function InstancesProvider({ children }: { children: ReactNode }) {
  const [instances, setInstances] = useState<Instance[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { launch } = useGame();

  const refresh = useCallback(
    () =>
      api.listInstances().then(
        (list) => {
          setInstances(list);
          setError(null);
        },
        (e) => setError(String(e)),
      ),
    [],
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Launching updates "last played", which changes the order.
  useEffect(() => {
    if (launch?.stage === 'running') refresh();
  }, [launch?.stage, refresh]);

  const after = useCallback(<T,>(p: Promise<T>) => p.then((v) => (refresh(), v)), [refresh]);

  const value: InstancesContext = {
    instances,
    error,
    refresh,
    create: (name, version, loader, loaderVersion) => after(api.createInstance(name, version, loader, loaderVersion)),
    update: (instance) => after(api.updateInstance(instance)),
    duplicate: (id) => after(api.duplicateInstance(id)),
    remove: (id) => after(api.deleteInstance(id)),
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useInstances(): InstancesContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useInstances must be used inside <InstancesProvider>');
  return ctx;
}
