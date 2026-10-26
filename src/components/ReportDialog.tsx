import { useEffect, useState, type FormEvent } from 'react';
import { Bug, CheckCircle2, ExternalLink, Loader2 } from 'lucide-react';
import { api, type ReportResult } from '../lib/api';
import { useT } from '../lib/i18n';
import { useInstances } from '../lib/instances';
import { Modal } from './Modal';

const input =
  'w-full rounded-xl border border-novo-border bg-novo-bg/70 px-4 py-2.5 outline-none transition-colors placeholder:text-novo-muted focus:border-accent/60';
const label = 'mb-1.5 block text-xs font-bold tracking-[0.15em] text-novo-muted uppercase';

/** "Report a bug": files a GitHub issue through NovoMC's relay. */
export function ReportDialog({ open, onClose, instanceId }: { open: boolean; onClose: () => void; instanceId?: string | null }) {
  const t = useT();
  const { instances } = useInstances();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [instance, setInstance] = useState<string>('');
  const [includeLogs, setIncludeLogs] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ReportResult | null>(null);

  useEffect(() => {
    if (open) {
      setTitle('');
      setDescription('');
      setInstance(instanceId ?? instances?.[0]?.id ?? '');
      setIncludeLogs(true);
      setError(null);
      setResult(null);
    }
  }, [open, instanceId, instances]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setResult(await api.reportBug({ title, description, instanceId: instance || null, includeLogs }));
    } catch (err) {
      setError(String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={t('bug.title')}>
      {result ? (
        <div className="flex flex-col items-center gap-3 py-4 text-center">
          <CheckCircle2 size={40} className="text-play" />
          <p className="font-semibold">{result.duplicate ? t('bug.duplicate') : t('bug.sent')}</p>
          {result.issue && (
            <button onClick={() => api.openUrl(result.issue!)} className="flex items-center gap-1.5 text-sm text-accent hover:underline">
              {t('bug.viewIssue')} <ExternalLink size={13} />
            </button>
          )}
          <button onClick={onClose} className="mt-2 rounded-xl bg-novo-elev-2 px-5 py-2 font-semibold hover:bg-novo-hover">
            {t('console.close')}
          </button>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4">
          <p className="text-sm text-novo-muted">{t('bug.intro')}</p>
          <div>
            <label className={label}>{t('bug.summary')}</label>
            <input autoFocus value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} placeholder={t('bug.summaryHint')} className={input} />
          </div>
          <div>
            <label className={label}>{t('bug.details')}</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={4000}
              rows={5}
              placeholder={t('bug.detailsHint')}
              className={`${input} resize-none`}
            />
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <select value={instance} onChange={(e) => setInstance(e.target.value)} className={`${input} w-auto flex-1 text-sm`}>
              <option value="">{t('bug.noInstance')}</option>
              {instances?.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.name} ({i.version})
                </option>
              ))}
            </select>
            <label className="flex items-center gap-2 text-sm text-novo-muted">
              <input type="checkbox" checked={includeLogs} onChange={(e) => setIncludeLogs(e.target.checked)} className="accent-[var(--color-accent)]" />
              {t('bug.includeLogs')}
            </label>
          </div>
          <p className="text-xs text-novo-muted">{t('bug.privacy')}</p>
          {error && <p className="text-sm text-danger">{error}</p>}
          <button
            type="submit"
            disabled={busy || !title.trim()}
            className="flex items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-brand-a to-brand-b py-3 font-bold text-white transition-all hover:brightness-110 disabled:opacity-50"
          >
            {busy ? <Loader2 size={18} className="animate-spin" /> : <Bug size={18} />}
            {t('bug.send')}
          </button>
        </form>
      )}
    </Modal>
  );
}
