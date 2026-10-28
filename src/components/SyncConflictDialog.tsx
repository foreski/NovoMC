// Asked before a launch when mod settings changed both on this PC and in the
// Novo account since they last synced (see src-tauri/src/novo/sync.rs).
import { Cloud, Monitor } from 'lucide-react';
import type { SyncResult } from '../lib/api';
import { useT } from '../lib/i18n';
import { Modal } from './Modal';

export function SyncConflictDialog({ conflict, onResolve }: { conflict: SyncResult | null; onResolve: (side: 'local' | 'cloud' | null) => void }) {
  const t = useT();
  const when = (ms: number | null) => (ms ? t('sync.changed', { time: new Date(ms).toLocaleString() }) : '');
  const option = (side: 'local' | 'cloud', label: string, time: number | null, Icon: typeof Cloud) => (
    <button
      onClick={() => onResolve(side)}
      className="flex flex-1 flex-col items-center gap-2 rounded-2xl bg-novo-elev p-4 text-center transition-colors hover:bg-novo-hover hover:ring-2 hover:ring-accent"
    >
      <Icon size={24} className="text-brand-c" />
      <span className="font-bold">{label}</span>
      <span className="text-xs text-novo-muted">{when(time)}</span>
    </button>
  );
  return (
    <Modal open={conflict !== null} onClose={() => onResolve(null)} title={t('sync.title')}>
      <p className="text-novo-muted">{t('sync.text')}</p>
      <div className="mt-5 flex gap-3">
        {option('local', t('sync.local'), conflict?.localModified ?? null, Monitor)}
        {option('cloud', t('sync.cloud'), conflict?.cloudModified ?? null, Cloud)}
      </div>
      <p className="mt-4 text-xs text-novo-muted">{t('sync.backup')}</p>
    </Modal>
  );
}
