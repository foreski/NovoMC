// Launch state and game logs, shared by Home, Versions and the console.
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { isTauri } from '@tauri-apps/api/core';
import { api, onGameLog, onLaunchState, type LaunchState, type LogLine, type SyncResult } from './api';
import { SyncConflictDialog } from '../components/SyncConflictDialog';

/** Lines kept in memory for the console. */
const MAX_LINES = 5000;

export type ConsoleTab = 'log' | 'crash';

interface GameContext {
  launch: LaunchState | null;
  logs: LogLine[];
  /** True from clicking Play until the game exits. */
  busy: boolean;
  /** Launches an instance, optionally straight into a server. */
  play: (instanceId: string, server?: string) => void;
  stop: () => void;
  consoleTab: ConsoleTab | null;
  openConsole: (tab?: ConsoleTab) => void;
  closeConsole: () => void;
}

const Ctx = createContext<GameContext | null>(null);

const ACTIVE = new Set(['preparing', 'installing', 'java', 'starting', 'running']);

export function GameProvider({ children }: { children: ReactNode }) {
  const [launch, setLaunch] = useState<LaunchState | null>(null);
  const [logs, setLogs] = useState<LogLine[]>([]);
  const [consoleTab, setConsoleTab] = useState<ConsoleTab | null>(null);

  useEffect(() => {
    if (!isTauri()) return;
    const offState = onLaunchState((s) => {
      setLaunch(s);
      if (s.stage === 'crashed') setConsoleTab(s.crashReport ? 'crash' : 'log');
    });
    const offLog = onGameLog((lines) => setLogs((prev) => [...prev, ...lines].slice(-MAX_LINES)));
    return () => {
      offState.then((f) => f());
      offLog.then((f) => f());
    };
  }, []);

  const [conflict, setConflict] = useState<{ instanceId: string; server?: string; sync: SyncResult } | null>(null);

  const start = useCallback((instanceId: string, server?: string) => {
    api.launchGame(instanceId, server).catch((e) => {
      // The backend also emits a `failed` state; this covers errors before it can.
      setLaunch((s) => (s && ACTIVE.has(s.stage) && s.stage !== 'running' ? { ...s, stage: 'failed', message: String(e) } : s));
    });
  }, []);

  const play = useCallback(
    (instanceId: string, server?: string) => {
      setLogs([]);
      setLaunch({ instanceId, versionId: '', stage: 'preparing', progress: null, message: null, exitCode: null, crashReport: null });
      // Novo account settings sync first; being offline never blocks playing.
      api.novoSyncSettings(instanceId).then(
        (sync) => {
          if (sync.action === 'conflict') {
            setLaunch(null);
            setConflict({ instanceId, server, sync });
          } else start(instanceId, server);
        },
        () => start(instanceId, server),
      );
    },
    [start],
  );

  const resolve = useCallback(
    (side: 'local' | 'cloud' | null) => {
      const c = conflict;
      setConflict(null);
      if (!c || !side) return;
      setLaunch({ instanceId: c.instanceId, versionId: '', stage: 'preparing', progress: null, message: null, exitCode: null, crashReport: null });
      api.novoSyncSettings(c.instanceId, side).finally(() => start(c.instanceId, c.server));
    },
    [conflict, start],
  );

  const stop = useCallback(() => {
    api.stopGame().catch(() => {});
  }, []);

  const value: GameContext = {
    launch,
    logs,
    busy: !!launch && ACTIVE.has(launch.stage),
    play,
    stop,
    consoleTab,
    openConsole: (tab = 'log') => setConsoleTab(tab),
    closeConsole: () => setConsoleTab(null),
  };
  return (
    <Ctx.Provider value={value}>
      {children}
      <SyncConflictDialog conflict={conflict?.sync ?? null} onResolve={resolve} />
    </Ctx.Provider>
  );
}

export function useGame(): GameContext {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useGame must be used inside <GameProvider>');
  return ctx;
}
