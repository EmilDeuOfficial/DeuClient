/**
 * Splash-/Ladebildschirm beim Start des Launchers:
 * animiertes DeuClient-Logo, Fortschrittsbalken und Statustexte. Führt dabei die echten
 * Initialisierungsschritte aus (Online-Prüfung, Accounts auffrischen, Standard-Instanz anlegen).
 */
import { useEffect, useState } from 'react';
import emblem from '../assets/emblem.png';
import { api } from '../api';
import { translate, type TKey } from '../i18n';
import type { Language } from '../../shared/types';

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function Splash({ onDone }: { onDone: () => void }) {
  const [lang, setLang] = useState<Language>('de');
  const [status, setStatus] = useState<TKey>('splash.start');
  const [progress, setProgress] = useState(0.04);
  const [offline, setOffline] = useState(false);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const step = async (key: TKey, pct: number, work: () => Promise<unknown>, minMs = 450) => {
        if (cancelled) return;
        setStatus(key);
        await Promise.all([work().catch(() => undefined), wait(minMs)]);
        if (!cancelled) setProgress(pct);
      };
      await step('splash.start', 0.18, async () => setLang((await api.settings.get()).language));
      await step('splash.updates', 0.45, async () => setOffline(!(await api.app.checkOnline())), 650);
      await step('splash.profiles', 0.72, () => api.accounts.refresh());
      await step('splash.instances', 0.94, () => api.instances.ensureDefault());
      setStatus('splash.ready');
      setProgress(1);
      await wait(450);
      setLeaving(true);
      await wait(550);
      if (!cancelled) onDone();
    })();
    return () => {
      cancelled = true;
    };
  }, [onDone]);

  return (
    <div
      className={`drag fixed inset-0 z-[100] flex flex-col items-center justify-center bg-ink-950 transition-opacity duration-300 ${
        leaving ? 'pointer-events-none opacity-0' : 'opacity-100'
      }`}
    >
      <img src={emblem} alt="DeuClient" className="relative mb-8 h-28 w-28" draggable={false} />

      <h1 className="relative font-display text-4xl font-black tracking-[0.18em] text-accent-gradient">DEUCLIENT</h1>

      {/* Fortschritt */}
      <div className="relative mt-12 w-[380px]">
        <div className="h-1.5 overflow-hidden rounded-full bg-white/[0.08]">
          <div
            className="h-full rounded-full bg-accent-500 transition-[width] duration-300"
            style={{ width: `${progress * 100}%` }}
          />
        </div>
        <div className="mt-3 flex items-center justify-between text-xs">
          <span key={status} className="animate-fade-up font-semibold text-ink-100/80">
            {translate(lang, status)}
          </span>
          <span className="font-mono text-accent-300/80">{Math.round(progress * 100)}%</span>
        </div>
        {offline && <div className="mt-2 text-center text-[11px] text-amber-300/80">{translate(lang, 'splash.offline')}</div>}
      </div>
    </div>
  );
}
