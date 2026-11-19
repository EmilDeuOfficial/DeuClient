/** Eigene Titelleiste für das rahmenlose Fenster: Logo, Schriftzug, Minimieren/Maximieren/Schließen. */
import emblem from '../assets/emblem.png';
import { api } from '../api';
import { Icon } from './Icons';

export function TitleBar({ title = 'DeuClient', subtitle }: { title?: string; subtitle?: string }) {
  return (
    <header className="drag relative z-50 flex h-10 shrink-0 items-center justify-between border-b border-white/[0.06] bg-ink-950/70 pl-3">
      <div className="flex items-center gap-2.5">
        <img src={emblem} alt="" className="h-6 w-6" draggable={false} />
        <span className="font-display text-[13px] font-bold tracking-[0.18em] text-accent-gradient">{title.toUpperCase()}</span>
        {subtitle && <span className="text-xs text-ink-200/50">· {subtitle}</span>}
      </div>
      <div className="no-drag flex h-full">
        <button
          onClick={() => api.window.minimize()}
          className="flex h-full w-12 items-center justify-center text-ink-200/70 transition hover:bg-white/10 hover:text-white"
          title="Minimieren"
        >
          <Icon name="minus" size={16} />
        </button>
        <button
          onClick={() => api.window.maximize()}
          className="flex h-full w-12 items-center justify-center text-ink-200/70 transition hover:bg-white/10 hover:text-white"
          title="Maximieren"
        >
          <Icon name="square" size={13} />
        </button>
        <button
          onClick={() => api.window.close()}
          className="flex h-full w-12 items-center justify-center text-ink-200/70 transition hover:bg-red-600 hover:text-white"
          title="Schließen"
        >
          <Icon name="x" size={16} />
        </button>
      </div>
    </header>
  );
}
