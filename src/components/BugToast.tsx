import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { Bug, ExternalLink, X } from 'lucide-react';
import { api, onBugReported, type ReportResult } from '../lib/api';
import { useT } from '../lib/i18n';

/** Bottom-left notice after a crash report is sent automatically. */
export function BugToast() {
  const t = useT();
  const [state, setState] = useState<{ ok: ReportResult | null; err: string | null } | null>(null);

  useEffect(() => {
    const unlisten = onBugReported((e) => {
      setState({ ok: e.Ok ?? null, err: e.Err ?? null });
      setTimeout(() => setState(null), 9000);
    });
    return () => {
      unlisten.then((f) => f());
    };
  }, []);

  return (
    <AnimatePresence>
      {state && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
          className="glass fixed bottom-5 left-5 z-50 flex max-w-sm items-center gap-3 rounded-2xl p-3 pr-2 shadow-2xl shadow-black/40"
        >
          <Bug size={18} className={state.err ? 'text-danger' : 'text-accent'} />
          <div className="min-w-0 flex-1 text-sm">
            <div className="font-semibold">{state.err ? t('bug.autoFailed') : t('bug.autoSent')}</div>
            {state.err && <div className="truncate text-xs text-novo-muted">{state.err}</div>}
            {state.ok?.issue && (
              <button onClick={() => api.openUrl(state.ok!.issue!)} className="flex items-center gap-1 text-xs text-accent hover:underline">
                {t('bug.viewIssue')} <ExternalLink size={11} />
              </button>
            )}
          </div>
          <button onClick={() => setState(null)} aria-label={t('common.dismiss')} className="rounded-lg p-1.5 text-novo-muted hover:bg-novo-hover">
            <X size={14} />
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
