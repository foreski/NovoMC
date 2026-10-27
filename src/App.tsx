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
import { api } from './lib/api';
import { applyAccent } from './lib/theme';
import { GameProvider } from './lib/game';
import { InstallsProvider } from './lib/installs';
import { InstancesProvider } from './lib/instances';
import { AccountsPage } from './pages/Accounts';
import { HomePage } from './pages/Home';
import { InstancesPage } from './pages/Instances';
import { ModLibraryPage } from './pages/ModLibrary';
import { SettingsPage } from './pages/Settings';
import { VersionsPage } from './pages/Versions';

function Page({
  page,
  onNavigate,
  createFor,
  onCreateInstance,
}: {
  page: PageId;
  onNavigate: (p: PageId) => void;
  createFor?: string;
  onCreateInstance: (versionId: string) => void;
}) {
  switch (page) {
    case 'home':
      return <HomePage onNavigate={onNavigate} />;
    case 'instances':
      return <InstancesPage onNavigate={onNavigate} createFor={createFor} />;
    case 'versions':
      return <VersionsPage onCreateInstance={onCreateInstance} />;
    case 'accounts':
      return <AccountsPage />;
    case 'mods':
      return <ModLibraryPage onNavigate={onNavigate} />;
    case 'settings':
      return <SettingsPage />;
  }
}

export default function App() {
  const [page, setPage] = useState<PageId>('home');

  // The saved accent applies from the first frame of every page.
  useEffect(() => {
    api.getSettings().then((s) => applyAccent(s.accent), () => {});
  }, []);
  // Version to prefill when Versions' "+ Instance" opens the create dialog.
  const [createFor, setCreateFor] = useState<string>();
  const navigate = (p: PageId) => {
    setCreateFor(undefined);
    setPage(p);
  };
  const createInstanceFor = (versionId: string) => {
    setCreateFor(versionId);
    setPage('instances');
  };

  return (
    <MotionConfig reducedMotion="user">
      <I18nProvider>
      <UpdatesProvider>
      <AccountsProvider>
        <InstallsProvider>
          <GameProvider>
            <InstancesProvider>
              <div className="relative isolate flex h-full flex-col">
                <Backdrop />
                <TitleBar page={page} onNavigate={navigate} />
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
                          <Page page={page} onNavigate={navigate} createFor={createFor} onCreateInstance={createInstanceFor} />
                        </motion.div>
                      </AnimatePresence>
                    </main>
                    <Console />
                  </div>
                </div>
                <DownloadDock />
                <BugToast />
              </div>
            </InstancesProvider>
          </GameProvider>
        </InstallsProvider>
      </AccountsProvider>
      </UpdatesProvider>
      </I18nProvider>
    </MotionConfig>
  );
}
