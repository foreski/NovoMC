import { useEffect, useState } from 'react';
import { AnimatePresence, MotionConfig, motion } from 'motion/react';
import { Backdrop } from './components/Backdrop';
import { Console } from './components/Console';
import { DownloadDock } from './components/DownloadDock';
import { UpdateBanner } from './components/UpdateBanner';
import { BugToast } from './components/BugToast';
import type { PageId } from './components/Sidebar';
import { TitleBar } from './components/TitleBar';
import { I18nProvider } from './lib/i18n';
import { UpdatesProvider } from './lib/updates';
import { AccountsProvider } from './lib/accounts';
import { NovoProvider, useNovo } from './lib/novo';
import { api } from './lib/api';
import { applyAccent } from './lib/theme';
import { GameProvider } from './lib/game';
import { InstallsProvider } from './lib/installs';
import { InstancesProvider } from './lib/instances';
import { HomePage } from './pages/Home';
import { InstancesPage } from './pages/Instances';
import { SettingsPage } from './pages/Settings';
import { MarketplacePage } from './pages/Marketplace';
import { LoginScreen } from './components/LoginScreen';
import { useT } from './lib/i18n';

function Page({
  page,
  onNavigate,
}: {
  page: PageId;
  onNavigate: (p: PageId) => void;
}) {
  switch (page) {
    case 'home':
      return <HomePage onNavigate={onNavigate} />;
    case 'instances':
      return <InstancesPage onNavigate={onNavigate} />;
    case 'marketplace':
      return <MarketplacePage />;
    case 'settings':
      return <SettingsPage onNavigate={onNavigate} />;
  }
}

function LauncherApp() {
  const novo = useNovo();
  const t = useT();
  // In development, ?page=mods (etc.) opens a page directly, for screenshots.
  const [page, setPage] = useState<PageId>(
    () => ((import.meta.env.DEV && new URLSearchParams(location.search).get('page')) as PageId) || 'home',
  );

  // The saved accent applies from the first frame of every page.
  useEffect(() => {
    api.getSettings().then((s) => applyAccent(s.accent), () => {});
  }, []);
  const navigate = (p: PageId) => {
    setPage(p);
  };

  return (
      <div className="relative isolate flex h-full flex-col">
        <Backdrop />
        <TitleBar page={page} onNavigate={navigate} showNavigation={novo.loaded && !!novo.account} />
        {!novo.loaded ? <div className="flex-1" /> : !novo.account ? <LoginScreen /> : <div className="flex min-h-0 flex-1 flex-col">
          {novo.offline && <div className="border-b border-amber-400/20 bg-amber-400/10 px-4 py-1.5 text-center text-xs text-amber-200">{t('login.offline')}</div>}
                <div className="flex min-h-0 flex-1">
                  <div className="relative min-w-0 flex-1 overflow-hidden">
                    <UpdateBanner />
                    <main className="absolute inset-0 overflow-x-hidden overflow-y-auto">
                      <AnimatePresence mode="wait">
                        <motion.div
                          key={page}
                          className="min-h-full"
                          initial={{ opacity: 0, y: 14, filter: 'blur(6px)' }}
                          animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
                          exit={{ opacity: 0, y: -8, filter: 'blur(4px)' }}
                          transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
                        >
                          <Page page={page} onNavigate={navigate} />
                        </motion.div>
                      </AnimatePresence>
                    </main>
                    <Console />
                  </div>
                </div>
          <DownloadDock />
          <BugToast />
        </div>}
      </div>
  );
}

export default function App() {
  return <MotionConfig reducedMotion="user"><I18nProvider><UpdatesProvider><AccountsProvider><NovoProvider><InstallsProvider><GameProvider><InstancesProvider><LauncherApp /></InstancesProvider></GameProvider></InstallsProvider></NovoProvider></AccountsProvider></UpdatesProvider></I18nProvider></MotionConfig>;
}
