// Launcher self-update state: checked once at startup (quietly) and on demand from Settings.
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, onUpdateProgress, type UpdateInfo } from './api';

export type UpdateStatus = 'idle' | 'checking' | 'available' | 'current' | 'installing' | 'error';

interface UpdatesContext {
  status: UpdateStatus;
  update: UpdateInfo | null;
  /** 0..1 while installing, null if the size is unknown. */
  progress: number | null;
  error: string | null;
  /** Hides the banner until the next start (the Settings card still shows the update). */
  dismissed: boolean;
  dismiss: () => void;
  check: () => Promise<void>;
  install: () => Promise<void>;
}

const Ctx = createContext<UpdatesContext | null>(null);

export function UpdatesProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<UpdateStatus>('idle');
  const [update, setUpdate] = useState<UpdateInfo | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const checkingRef = useRef(false);

  const check = useCallback(async () => {
    if (checkingRef.current) return;
    checkingRef.current = true;
    setStatus('checking');
    setError(null);
    try {
      const found = await api.checkUpdate();
      setUpdate(found);
      if (found) {
        // Signed releases install automatically on startup (and after a manual check).
        setStatus('installing');
        setProgress(0);
        await api.installUpdate();
      } else {
        setStatus('current');
      }
    } catch (e) {
      setError(String(e));
      setStatus('error');
    } finally {
      checkingRef.current = false;
    }
  }, []);

  // Startup check; failures (offline, no release yet) only show in Settings.
  useEffect(() => {
    check();
  }, [check]);

  useEffect(() => {
    const unlisten = onUpdateProgress((p) => setProgress(p.total ? p.downloaded / p.total : null));
    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  const install = useCallback(async () => {
    setStatus('installing');
    setProgress(0);
    setError(null);
    try {
      await api.installUpdate();
    } catch (e) {
      setError(String(e));
      setStatus('error');
    }
  }, []);

  const value: UpdatesContext = { status, update, progress, error, dismissed, dismiss: () => setDismissed(true), check, install };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useUpdates(): UpdatesContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useUpdates must be used inside <UpdatesProvider>');
  return ctx;
}
