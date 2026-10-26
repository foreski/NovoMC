// Tracks version installs across pages from the backend's `install-progress` events.
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { isTauri } from '@tauri-apps/api/core';
import { api, onInstallProgress, type InstallEvent } from './api';

export interface ActiveInstall extends InstallEvent {
  /** Smoothed download speed in bytes per second. */
  speed: number;
}

interface InstallsContext {
  installs: Record<string, ActiveInstall>;
  /** Versions that finished installing during this session. */
  completed: Set<string>;
  start: (id: string) => void;
  cancel: (id: string) => void;
  dismiss: (id: string) => void;
}

const Ctx = createContext<InstallsContext | null>(null);

export const isRunning = (i?: InstallEvent) => i?.stage === 'metadata' || i?.stage === 'files';

/** Fraction complete (0-1), by bytes when sizes are known, else by files. */
export function installFraction(i: InstallEvent): number {
  if (i.stage === 'done') return 1;
  if (i.bytesTotal > 0) return Math.min(i.bytesDone / i.bytesTotal, 1);
  return i.filesTotal > 0 ? i.filesDone / i.filesTotal : 0;
}

export function InstallsProvider({ children }: { children: ReactNode }) {
  const [installs, setInstalls] = useState<Record<string, ActiveInstall>>({});
  const [completed, setCompleted] = useState<Set<string>>(new Set());
  const samples = useRef<Record<string, { t: number; bytes: number; speed: number }>>({});

  const dismiss = useCallback((id: string) => {
    setInstalls(({ [id]: _, ...rest }) => rest);
    delete samples.current[id];
  }, []);

  useEffect(() => {
    if (!isTauri()) return;
    const unlisten = onInstallProgress((e) => {
      const now = performance.now();
      const prev = samples.current[e.versionId];
      let speed = prev?.speed ?? 0;
      if (prev && now > prev.t) {
        const instant = ((e.bytesDone - prev.bytes) / (now - prev.t)) * 1000;
        speed = Math.max(0, speed * 0.7 + instant * 0.3);
      }
      samples.current[e.versionId] = { t: now, bytes: e.bytesDone, speed };
      setInstalls((all) => ({ ...all, [e.versionId]: { ...e, speed } }));

      if (e.stage === 'done') {
        setCompleted((s) => new Set(s).add(e.versionId));
        setTimeout(() => dismiss(e.versionId), 4000);
      } else if (e.stage === 'cancelled') {
        setTimeout(() => dismiss(e.versionId), 1500);
      }
    });
    return () => {
      unlisten.then((f) => f());
    };
  }, [dismiss]);

  const start = useCallback((id: string) => {
    setInstalls((all) =>
      isRunning(all[id])
        ? all
        : {
            ...all,
            [id]: { versionId: id, stage: 'metadata', filesDone: 0, filesTotal: 0, bytesDone: 0, bytesTotal: 0, error: null, speed: 0 },
          },
    );
    api.installVersion(id).catch((err) => {
      const message = String(err);
      if (message === 'Cancelled' || message.endsWith('already installing')) return;
      // The final progress event normally reports failures; this covers errors it can't (e.g. outside Tauri).
      setInstalls((all) => (all[id] && isRunning(all[id]) ? { ...all, [id]: { ...all[id], stage: 'failed', error: message } } : all));
    });
  }, []);

  const cancel = useCallback((id: string) => {
    api.cancelInstall(id).catch(() => {});
  }, []);

  return <Ctx.Provider value={{ installs, completed, start, cancel, dismiss }}>{children}</Ctx.Provider>;
}

export function useInstalls(): InstallsContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useInstalls must be used inside <InstallsProvider>');
  return ctx;
}
