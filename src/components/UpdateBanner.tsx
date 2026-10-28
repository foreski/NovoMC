import { AnimatePresence, motion } from 'motion/react';
import { ArrowDownCircle, Loader2, X } from 'lucide-react';
import { useT } from '../lib/i18n';
import { useUpdates } from '../lib/updates';

/** Floating notice at the top of the window when a newer NovoMC is out. */
export function UpdateBanner() {
  const { status, update, progress, dismissed, dismiss, install } = useUpdates();
  const t = useT();
  const installing = status === 'installing';
  const show = !!update && (installing || (status === 'available' && !dismissed));

  return (
    <AnimatePresence>
      {show && update && (
        <motion.div
          initial={{ opacity: 0, y: -16 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -16 }}
          transition={{ type: 'spring', stiffness: 420, damping: 34 }}
          className="pointer-events-none absolute inset-x-0 top-3 z-20 flex justify-center"
        >
          <div className="glass pointer-events-auto flex items-center gap-3 rounded-2xl py-2 pr-2 pl-4 shadow-2xl shadow-black/40">
            <ArrowDownCircle size={18} className="text-accent" />
            <span className="text-sm">
              {installing
                ? t('upd.installing', { pct: progress != null ? Math.floor(progress * 100) : 0 })
                : t('upd.available', { version: update.version })}
            </span>
            {installing && <Loader2 size={14} className="animate-spin text-accent" />}
            {!installing && <button
              onClick={install}
              className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-brand-a to-brand-b px-3 py-1.5 text-sm font-bold text-white transition-all hover:brightness-110"
            >{t('upd.updateNow')}</button>}
            {!installing && (
              <button
                onClick={dismiss}
                aria-label={t('common.dismiss')}
                className="rounded-lg p-1.5 text-novo-muted transition-colors hover:bg-novo-hover hover:text-novo-text"
              >
                <X size={15} />
              </button>
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
