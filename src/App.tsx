/** Hauptlayout: Titelleiste, Seitenleiste, Kopfzeile mit Account, animierte Seiten, Fortschrittsleiste. */
import { useCallback, useEffect, useState } from 'react';
import { AccountMenu } from './components/AccountMenu';
import { Background } from './components/Background';
import { LaunchBar, Toasts } from './components/Common';
import { ConfirmHost } from './components/Confirm';
import { Sidebar } from './components/Sidebar';
import { Splash } from './components/Splash';
import { TitleBar } from './components/TitleBar';
import { Home } from './pages/Home';
import { Extras } from './pages/Extras';
import { Servers } from './pages/Servers';
import { Shop } from './pages/Shop';
import { Modpacks } from './pages/Modpacks';
import { Settings } from './pages/Settings';
import { AppProvider, useApp } from './store';
import { setTranslateLanguage } from './autotranslate';

function Shell() {
  const { page, packId, t, reloadInstances, progress, download, settings } = useApp();

  // Texte mit fest im Code stehendem Deutsch (Shop, Server ...) bei Sprache Englisch uebersetzen
  const language = settings?.language ?? 'de';
  useEffect(() => setTranslateLanguage(language), [language]);
  const [booted, setBooted] = useState(false);
  const onSplashDone = useCallback(() => {
    setBooted(true);
    void reloadInstances(); // Standard-Instanz wurde ggf. gerade angelegt
  }, [reloadInstances]);

  const titles = { home: t('nav.home'), modpacks: t('nav.modpacks'), servers: t('nav.servers'), shop: t('nav.shop'), extras: t('nav.extras'), settings: t('nav.settings') };

  return (
    <div className="flex h-full flex-col">
      <Background />
      <TitleBar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main className="relative flex min-w-0 flex-1 flex-col">
          <div className="flex h-[68px] shrink-0 items-center px-8">
            <div className="mx-auto flex w-full max-w-6xl items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="h-5 w-1 rounded-full bg-accent-400" />
              <span className="font-display text-sm font-bold uppercase tracking-[0.3em] text-ink-100/80">{titles[page]}</span>
            </div>
            <AccountMenu />
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto pb-8 pl-8 pr-[22px] pt-2 [scrollbar-gutter:stable]">
            {/* key = page → Animation bei jedem Seitenwechsel */}
            <div key={`${page}-${packId ?? ''}`} className="page-enter">
              {page === 'home' && <Home />}
              {page === 'modpacks' && <Modpacks />}
              {page === 'servers' && <Servers />}
              {page === 'shop' && <Shop />}
              {page === 'extras' && <Extras />}
              {page === 'settings' && <Settings />}
            </div>
            {(progress || download) && <div className="h-20" />}
          </div>
          <LaunchBar />
        </main>
      </div>
      <Toasts />
      <ConfirmHost />
      {!booted && <Splash onDone={onSplashDone} />}
    </div>
  );
}

export default function App() {
  return (
    <AppProvider>
      <Shell />
    </AppProvider>
  );
}
