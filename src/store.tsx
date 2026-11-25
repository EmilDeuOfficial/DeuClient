/**
 * Globaler App-Zustand (React Context): Einstellungen, Accounts, Instanzen, Spielstatus,
 * Start-Fortschritt und Toast-Benachrichtigungen.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { AccountsState, GameState, Instance, ProgressEvent, Settings, UpdateStatus } from '../shared/types';
import { api, onEvent } from './api';
import { translate, type TKey } from './i18n';

export type Page = 'home' | 'modpacks' | 'servers' | 'shop' | 'extras' | 'settings';

export interface Toast {
  id: number;
  kind: 'info' | 'success' | 'error';
  text: string;
}

interface AppState {
  /** Zustand des automatischen Updates */
  update: UpdateStatus;
  page: Page;
  setPage: (p: Page) => void;
  /** Geöffnetes Modpack (Detailansicht) oder null = Übersicht */
  packId: string | null;
  openPack: (id: string | null) => void;
  settings: Settings | null;
  updateSettings: (patch: Partial<Settings>) => Promise<void>;
  accounts: AccountsState;
  setAccounts: (s: AccountsState) => void;
  instances: Instance[];
  reloadInstances: () => Promise<void>;
  selectedInstance: Instance | undefined;
  game: GameState;
  progress: ProgressEvent | null;
  /** Download-Fortschritt außerhalb des Spielstarts (Mods, Java, Modpacks) */
  download: ProgressEvent | null;
  launching: boolean;
  launch: (instanceId: string, joinServer?: string) => Promise<void>;
  toasts: Toast[];
  toast: (text: string, kind?: Toast['kind']) => void;
  t: (key: TKey) => string;
}

const Ctx = createContext<AppState | null>(null);

export function useApp(): AppState {
  const c = useContext(Ctx);
  if (!c) throw new Error('useApp außerhalb von AppProvider');
  return c;
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [page, setPageRaw] = useState<Page>('home');
  const [packId, setPackId] = useState<string | null>(null);
  const setPage = useCallback((p: Page) => {
    setPageRaw(p);
    setPackId(null);
  }, []);
  const openPack = useCallback((id: string | null) => {
    setPageRaw('modpacks');
    setPackId(id);
  }, []);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [update, setUpdate] = useState<UpdateStatus>({ state: 'idle' });
  const [accounts, setAccounts] = useState<AccountsState>({ accounts: [], activeUuid: null });
  const [instances, setInstances] = useState<Instance[]>([]);
  const [game, setGame] = useState<GameState>({ running: false });
  const [progress, setProgress] = useState<ProgressEvent | null>(null);
  const [download, setDownload] = useState<ProgressEvent | null>(null);
  const [launching, setLaunching] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastId = useRef(0);
  const downloadTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const hideTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const toast = useCallback((text: string, kind: Toast['kind'] = 'info') => {
    const id = ++toastId.current;
    setToasts((t) => [...t, { id, kind, text }].slice(-3));
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), kind === 'error' ? 7000 : 3800);
  }, []);

  const reloadInstances = useCallback(async () => setInstances(await api.instances.list()), []);

  // Anfangsdaten + Ereignisse aus dem Main-Prozess
  useEffect(() => {
    void api.settings.get().then(setSettings);
    void api.accounts.get().then(setAccounts);
    void reloadInstances();
    void api.game.state().then(setGame);
    void api.update.status().then(setUpdate);
    const offs = [
      onEvent('accounts:changed', setAccounts),
      onEvent('instances:changed', setInstances),
      onEvent('game:state', setGame),
      onEvent('update:status', setUpdate),
      onEvent('launch:progress', (e) => {
        setProgress(e);
        clearTimeout(hideTimer.current);
        if (e.stage === 'running' || e.stage === 'done' || e.stage === 'error') {
          hideTimer.current = setTimeout(() => setProgress(null), e.stage === 'error' ? 6000 : 2500);
        }
      }),
      onEvent('download:progress', (e) => {
        clearTimeout(downloadTimer.current);
        if (e.stage === 'idle') {
          setDownload(null); // alles fertig: Leiste leise ausblenden
          return;
        }
        setDownload(e);
        // fertig: kurz anzeigen; sonst nach 8 s ohne neue Meldung ausblenden (damit nichts haengen bleibt)
        downloadTimer.current = setTimeout(() => setDownload(null), 8000);
      }),
    ];
    return () => offs.forEach((f) => f());
  }, [reloadInstances]);

  const updateSettings = useCallback(async (patch: Partial<Settings>) => {
    setSettings((s) => (s ? { ...s, ...patch } : s)); // sofort anzeigen
    setSettings(await api.settings.save(patch));
  }, []);

  // Animationen in der Oberflaeche an/aus (Einstellungen)
  useEffect(() => {
    document.documentElement.classList.toggle('no-motion', settings?.animations === false);
  }, [settings?.animations]);

  const lang = settings?.language ?? 'de';
  const t = useCallback((key: TKey) => translate(lang, key), [lang]);

  const launch = useCallback(
    async (instanceId: string, joinServer?: string) => {
      if (launching || game.running) return;
      setLaunching(true);
      setProgress({ stage: 'auth', text: translate(lang, 'home.launching'), percent: 0 });
      try {
        if (settings?.selectedInstance !== instanceId) await updateSettings({ selectedInstance: instanceId });
        await api.game.launch({ instanceId, joinServer });
      } catch (e) {
        toast(`${translate(lang, 'launch.error')}: ${(e as Error).message}`, 'error');
      } finally {
        setLaunching(false);
      }
    },
    [launching, game.running, lang, settings?.selectedInstance, updateSettings, toast],
  );

  const selectedInstance = useMemo(
    () => instances.find((i) => i.id === settings?.selectedInstance) ?? instances[0],
    [instances, settings?.selectedInstance],
  );

  const value: AppState = {
    update, page, setPage, packId, openPack, settings, updateSettings, accounts, setAccounts, instances, reloadInstances, selectedInstance,
    game, progress, download, launching, launch, toasts, toast, t,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
