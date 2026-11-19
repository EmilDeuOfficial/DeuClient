/** Konsolen-/Log-Fenster: zeigt die Ausgabe von Minecraft live mit Farben, Filter und Auto-Scroll. */
import { useEffect, useMemo, useRef, useState } from 'react';
import type { GameState, Language } from '../shared/types';
import { api, onEvent } from './api';
import { Icon } from './components/Icons';
import { TitleBar } from './components/TitleBar';
import { translate, type TKey } from './i18n';

function lineClass(line: string): string {
  if (line.startsWith('[DeuClient]')) return 'text-accent-300';
  if (/\b(ERROR|FATAL|Exception|SEVERE)\b/.test(line) || /^\s+at /.test(line)) return 'text-red-300';
  if (/\bWARN(ING)?\b/.test(line)) return 'text-amber-200';
  if (/\bDEBUG\b/.test(line)) return 'text-ink-200/50';
  return 'text-ink-100/85';
}

export default function ConsoleApp() {
  const [lines, setLines] = useState<string[]>([]);
  const [filter, setFilter] = useState('');
  const [auto, setAuto] = useState(true);
  const [game, setGame] = useState<GameState>({ running: false });
  const [lang, setLang] = useState<Language>('de');
  const [copied, setCopied] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);
  const t = (k: TKey) => translate(lang, k);

  useEffect(() => {
    void api.settings.get().then((s) => setLang(s.language));
    void api.console.getLogs().then(setLines);
    void api.game.state().then(setGame);
    const offs = [
      onEvent('game:log', (l) => setLines((prev) => {
        const next = prev.concat(l);
        return next.length > 5000 ? next.slice(-5000) : next;
      })),
      onEvent('game:state', setGame),
    ];
    return () => offs.forEach((f) => f());
  }, []);

  useEffect(() => {
    if (auto) bottom.current?.scrollIntoView({ block: 'end' });
  }, [lines, auto]);

  const shown = useMemo(() => (filter ? lines.filter((l) => l.toLowerCase().includes(filter.toLowerCase())) : lines), [lines, filter]);

  return (
    <div className="flex h-full flex-col bg-ink-950">
      <TitleBar title={t('console.title')} subtitle={game.running ? 'Minecraft läuft' : undefined} />
      <div className="flex items-center gap-2 border-b border-white/[0.06] bg-ink-900/80 px-3 py-2">
        <div className="relative flex-1">
          <Icon name="search" size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-accent-300/60" />
          <input className="input !py-1.5 !pl-9 text-xs" placeholder={t('console.filter')} value={filter} onChange={(e) => setFilter(e.target.value)} />
        </div>
        <button className={`btn-ghost !px-3 !py-1.5 text-xs ${auto ? '!border-accent-400/50 !text-accent-200' : ''}`} onClick={() => setAuto((a) => !a)}>
          <Icon name="chevronDown" size={13} /> {t('console.autoscroll')}
        </button>
        <button
          className="btn-ghost !px-3 !py-1.5 text-xs"
          onClick={() => {
            void navigator.clipboard.writeText(shown.join('\n'));
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
          }}
        >
          <Icon name="copy" size={13} /> {copied ? t('console.copied') : t('console.copy')}
        </button>
        <button className="btn-ghost !px-3 !py-1.5 text-xs" onClick={() => { void api.console.clear(); setLines([]); }}>
          <Icon name="trash" size={13} /> {t('console.clear')}
        </button>
        <button className="btn-ghost !px-3 !py-1.5 text-xs" onClick={() => api.console.openLogsFolder()}>
          <Icon name="folder" size={13} /> {t('console.logs')}
        </button>
        {game.running && (
          <button className="btn-danger !px-3 !py-1.5 text-xs" onClick={() => api.game.kill()}>
            <Icon name="stop" size={12} /> {t('launch.kill')}
          </button>
        )}
      </div>
      <div className="min-h-0 flex-1 select-text overflow-auto px-4 py-3 font-mono text-[12px] leading-[1.55]">
        {!shown.length && <div className="text-ink-200/40">{t('console.empty')}</div>}
        {shown.map((l, i) => (
          <div key={i} className={`whitespace-pre-wrap break-all ${lineClass(l)}`}>{l}</div>
        ))}
        <div ref={bottom} />
      </div>
    </div>
  );
}
