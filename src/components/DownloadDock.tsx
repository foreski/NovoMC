import { AnimatePresence, motion } from 'motion/react';
import { CheckCircle2, Download, X, XCircle } from 'lucide-react';
import { formatBytes } from '../lib/format';
import { useT, type T } from '../lib/i18n';
import { installFraction, isRunning, useInstalls, type ActiveInstall } from '../lib/installs';
import { ProgressBar } from './ProgressBar';

function statusLine(i: ActiveInstall, t: T): string {
  switch (i.stage) {
    case 'metadata':
      return t('dock.reading');
    case 'files':
      return i.bytesTotal > 0
        ? `${formatBytes(i.bytesDone)} / ${formatBytes(i.bytesTotal)} · ${formatBytes(i.speed)}/s`
        : t('dock.files', { done: i.filesDone, total: i.filesTotal });
    case 'done':
      return t('dock.done', { count: i.filesTotal });
    case 'cancelled':
      return t('dock.cancelled');
    case 'failed':
      return i.error ?? t('dock.failed');
  }
}

/** Floating stack of install progress cards, visible on every page. */
export function DownloadDock() {
  const { installs, cancel, dismiss } = useInstalls();
  const t = useT();
  const list = Object.values(installs);

  return (
    <div className="pointer-events-none fixed right-5 bottom-5 z-50 flex w-80 flex-col gap-2">
      <AnimatePresence>
        {list.map((i) => {
          const running = isRunning(i);
          const tone = i.stage === 'done' ? 'done' : i.stage === 'failed' ? 'error' : 'brand';
          const Icon = i.stage === 'done' ? CheckCircle2 : i.stage === 'failed' ? XCircle : Download;
          const status = statusLine(i, t);
          return (
            <motion.div
              key={i.versionId}
              layout
              initial={{ opacity: 0, x: 40, scale: 0.95 }}
              animate={{ opacity: 1, x: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40, scale: 0.95 }}
              transition={{ type: 'spring', stiffness: 400, damping: 32 }}
              className="glass pointer-events-auto rounded-2xl p-3.5 shadow-2xl shadow-black/40"
            >
              <div className="flex items-center gap-3">
                <div
                  className={`grid h-9 w-9 shrink-0 place-items-center rounded-xl ${
                    tone === 'done' ? 'bg-play/15 text-play' : tone === 'error' ? 'bg-danger/15 text-danger' : 'bg-brand-a/20 text-accent'
                  }`}
                >
                  <Icon size={18} className={running ? 'animate-bounce' : ''} style={{ animationDuration: '1.4s' }} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="truncate font-bold">Minecraft {i.versionId}</span>
                    {running && <span className="font-mono text-xs text-novo-muted">{Math.floor(installFraction(i) * 100)}%</span>}
                  </div>
                  <div className={`truncate text-xs ${tone === 'error' ? 'text-danger' : 'text-novo-muted'}`} title={status}>
                    {status}
                  </div>
                </div>
                <button
                  onClick={() => (running ? cancel(i.versionId) : dismiss(i.versionId))}
                  aria-label={running ? t('dock.cancel') : t('common.dismiss')}
                  className="rounded-lg p-1.5 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text"
                >
                  <X size={15} />
                </button>
              </div>
              <div className="mt-3">
                <ProgressBar value={i.stage === 'metadata' ? null : installFraction(i)} tone={tone} />
              </div>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
