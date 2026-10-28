import { useState } from 'react';
import { LoaderCircle, Sparkles } from 'lucide-react';
import { motion } from 'motion/react';
import { useNovo } from '../lib/novo';
import { useT } from '../lib/i18n';

function DiscordMark() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden><path d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.3 18.3 0 0 0-5.6 0L8.6 3a19.7 19.7 0 0 0-4.9 1.4C.6 9 0 13.6.3 18.1a19.9 19.9 0 0 0 6 3l1.3-2a12.9 12.9 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.8 19.8 0 0 0 6-3c.5-5.2-.8-9.7-3.8-13.7ZM8.5 15.4c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Zm7 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Z" /></svg>;
}

export function LoginScreen() {
  const t = useT();
  const { signIn, offline, error } = useNovo();
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  async function login() {
    setBusy(true); setFailure(null);
    try { await signIn(); } catch (e) { const message = String(e); if (!message.includes('cancelled')) setFailure(message); }
    finally { setBusy(false); }
  }

  return <div className="relative grid min-h-0 flex-1 place-items-center overflow-hidden px-6 py-12">
    <div className="pointer-events-none absolute -top-20 left-1/2 h-80 w-80 -translate-x-1/2 rounded-full bg-brand-b/20 blur-[100px]" />
    <motion.section initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="glass relative w-full max-w-md overflow-hidden rounded-[32px] p-8 text-center shadow-2xl shadow-black/30">
      <div className="mx-auto grid h-16 w-16 place-items-center rounded-3xl bg-gradient-to-br from-brand-a/40 to-brand-b/40 ring-1 ring-white/15"><Sparkles size={28} /></div>
      <p className="mt-6 text-xs font-bold tracking-[0.28em] text-brand-c uppercase">Novo Client</p>
      <h1 className="gradient-text mt-2 text-4xl font-black">{t('login.title')}</h1>
      <p className="mx-auto mt-3 max-w-sm text-sm leading-6 text-novo-muted">{t('login.hint')}</p>
      {offline && <p className="mt-4 rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{t('login.offline')} {error}</p>}
      {failure && <p className="mt-4 rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{failure}</p>}
      <button onClick={() => void login()} disabled={busy} className="mt-7 flex w-full items-center justify-center gap-3 rounded-2xl bg-[#5865F2] py-3.5 font-bold text-white shadow-[0_12px_32px_-12px] shadow-[#5865F2]/80 transition hover:brightness-110 active:scale-[0.99] disabled:opacity-60">
        {busy ? <LoaderCircle size={20} className="animate-spin" /> : <DiscordMark />}{busy ? t('login.signingIn') : t('novo.signIn')}
      </button>
    </motion.section>
  </div>;
}
