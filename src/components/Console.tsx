import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Bug, Check, Copy, Skull, Square, Terminal, X } from 'lucide-react';
import type { LogLine } from '../lib/api';
import { useGame, type ConsoleTab } from '../lib/game';
import { useT, type Key } from '../lib/i18n';
import { ReportDialog } from './ReportDialog';

type LevelFilter = 'all' | 'warn' | 'error';

const LEVEL_STYLE: Record<string, string> = {
  ERROR: 'text-danger',
  FATAL: 'text-danger font-bold',
  WARN: 'text-[#facc15]',
  DEBUG: 'text-novo-muted/70',
  TRACE: 'text-novo-muted/50',
};

function matches(line: LogLine, filter: LevelFilter): boolean {
  if (filter === 'all') return true;
  if (filter === 'error') return line.level === 'ERROR' || line.level === 'FATAL';
  return line.level === 'WARN' || line.level === 'ERROR' || line.level === 'FATAL';
}

function formatTime(ms: number | null): string {
  return ms ? new Date(ms).toLocaleTimeString(undefined, { hour12: false }) : '';
}

function LogView({ lines }: { lines: LogLine[] }) {
  const t = useT();
  const ref = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  // Follow new output unless the user scrolled up.
  useEffect(() => {
    const el = ref.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [lines]);

  return (
    <div
      ref={ref}
      onScroll={(e) => {
        const el = e.currentTarget;
        stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
      }}
      className="h-full overflow-auto px-5 py-3 font-mono text-[12px] leading-relaxed select-text"
    >
      {lines.length === 0 ? (
        <p className="text-novo-muted">{t('console.empty')}</p>
      ) : (
        lines.map((l, i) => (
          <div key={i} className={`whitespace-pre-wrap break-all ${LEVEL_STYLE[l.level] ?? 'text-[#c8cfdb]'}`}>
            {l.time && <span className="mr-2 text-novo-muted/60">{formatTime(l.time)}</span>}
            {l.thread && <span className="mr-2 text-brand-b/80">[{l.thread}]</span>}
            {l.message}
          </div>
        ))
      )}
    </div>
  );
}

/** Slide-up drawer with live game output and the crash report. */
export function Console() {
  const { launch, logs, consoleTab, openConsole, closeConsole, busy, stop } = useGame();
  const t = useT();
  const [filter, setFilter] = useState<LevelFilter>('all');
  const [copied, setCopied] = useState(false);
  const [reporting, setReporting] = useState(false);
  const crash = launch?.crashReport ?? null;
  const shown = useMemo(() => logs.filter((l) => matches(l, filter)), [logs, filter]);

  async function copy() {
    const text =
      consoleTab === 'crash' && crash ? crash.content : shown.map((l) => `[${l.level || '-'}] ${l.message}`).join('\n');
    await navigator.clipboard.writeText(text).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 1400);
  }

  const tabs: { id: ConsoleTab; label: Key; show: boolean }[] = [
    { id: 'log', label: 'console.log', show: true },
    { id: 'crash', label: 'console.crashReport', show: !!crash },
  ];
  const filterLabel: Record<LevelFilter, Key> = { all: 'console.all', warn: 'console.warnings', error: 'console.errors' };

  return (
    <AnimatePresence>
      {consoleTab && (
        <>
          <motion.div
            key="scrim"
            className="absolute inset-0 z-30 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={closeConsole}
          />
          <motion.section
            key="console"
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', stiffness: 380, damping: 38 }}
            className="absolute inset-x-0 bottom-0 z-40 flex h-[70%] flex-col overflow-hidden rounded-t-3xl border-t border-white/10 bg-[#080a10]/95 shadow-[0_-20px_60px_rgb(0_0_0/0.5)] backdrop-blur-xl"
          >
            <div className="flex items-center gap-3 border-b border-novo-border px-5 py-3">
              <Terminal size={18} className="text-accent" />
              <span className="font-bold">{t('console.title')}</span>
              {launch && <span className="font-mono text-xs text-novo-muted">Minecraft {launch.versionId}</span>}
              <div className="ml-3 flex gap-1 rounded-xl bg-novo-elev p-1">
                {tabs
                  .filter((tab) => tab.show)
                  .map((tab) => (
                    <button
                      key={tab.id}
                      onClick={() => openConsole(tab.id)}
                      className={`flex items-center gap-1.5 rounded-lg px-3 py-1 text-xs font-semibold transition-colors ${
                        consoleTab === tab.id ? 'bg-novo-elev-2 text-novo-text' : 'text-novo-muted hover:text-novo-text'
                      } ${tab.id === 'crash' ? 'text-danger' : ''}`}
                    >
                      {tab.id === 'crash' && <Skull size={12} />}
                      {t(tab.label)}
                    </button>
                  ))}
              </div>
              {consoleTab === 'log' && (
                <div className="flex gap-1">
                  {(['all', 'warn', 'error'] as const).map((f) => (
                    <button
                      key={f}
                      onClick={() => setFilter(f)}
                      className={`rounded-lg px-2.5 py-1 text-xs font-semibold capitalize transition-colors ${
                        filter === f ? 'bg-accent/15 text-accent' : 'text-novo-muted hover:text-novo-text'
                      }`}
                    >
                      {t(filterLabel[f])}
                    </button>
                  ))}
                </div>
              )}
              <div className="ml-auto flex items-center gap-1">
                {busy && (
                  <button
                    onClick={stop}
                    className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-danger transition-colors hover:bg-danger/10"
                  >
                    <Square size={12} fill="currentColor" /> {t('console.stopGame')}
                  </button>
                )}
                <button onClick={copy} aria-label={t('common.copy')} className="rounded-lg p-2 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text">
                  {copied ? <Check size={15} className="text-play" /> : <Copy size={15} />}
                </button>
                <button onClick={closeConsole} aria-label={t('console.close')} className="rounded-lg p-2 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text">
                  <X size={16} />
                </button>
              </div>
            </div>

            <div className="min-h-0 flex-1">
              {consoleTab === 'crash' && crash ? (
                <div className="flex h-full flex-col">
                  <div className="flex items-center gap-3 border-b border-danger/20 bg-danger/10 px-5 py-3 text-sm">
                    <Skull size={18} className="shrink-0 text-danger" />
                    <div className="min-w-0">
                      <div className="font-semibold text-danger">
                        {launch?.exitCode != null ? t('console.crashedCode', { code: launch.exitCode }) : t('console.crashed')}
                      </div>
                      <div className="truncate font-mono text-xs text-novo-muted select-text">{crash.path}</div>
                    </div>
                    <button
                      onClick={() => setReporting(true)}
                      className="ml-auto flex shrink-0 items-center gap-1.5 rounded-lg bg-danger/20 px-3 py-1.5 text-xs font-semibold text-danger transition-colors hover:bg-danger/30"
                    >
                      <Bug size={13} /> {t('bug.reportCrash')}
                    </button>
                  </div>
                  <pre className="min-h-0 flex-1 overflow-auto px-5 py-3 font-mono text-[12px] leading-relaxed whitespace-pre-wrap text-[#c8cfdb] select-text">
                    {crash.content}
                  </pre>
                </div>
              ) : (
                <LogView lines={shown} />
              )}
            </div>
          </motion.section>
        </>
      )}
      <ReportDialog key="report" open={reporting} onClose={() => setReporting(false)} instanceId={launch?.instanceId} />
    </AnimatePresence>
  );
}
