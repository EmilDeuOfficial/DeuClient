/** Wiederverwendbare UI-Bausteine: Fortschrittsbalken, Modal, Toasts, Schalter. */
import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import type { ProgressEvent } from '../../shared/types';
import { api } from '../api';
import { useApp } from '../store';
import { Icon } from './Icons';

/* ---------------- Fortschrittsbalken ---------------- */

export function ProgressBar({ value, className = '' }: { value: number; className?: string }) {
  const indeterminate = value < 0;
  return (
    <div className={`h-2 overflow-hidden rounded-full bg-white/[0.07] ${className}`}>
      <div
        className={`h-full rounded-full bg-accent-500 transition-[width] duration-300 ${
          indeterminate ? 'w-1/3 animate-[indeterminate_1.2s_ease-in-out_infinite]' : ''
        }`}
        style={indeterminate ? undefined : { width: `${Math.max(2, Math.min(100, value * 100))}%` }}
      />
    </div>
  );
}

/** Schwebende Leiste unten: Fortschritt beim Spielstart oder bei Downloads */
export function LaunchBar() {
  const { progress, download, t, game } = useApp();
  const ev: ProgressEvent | null = progress ?? download;
  if (!ev) return null;
  const error = ev.stage === 'error';
  const done = ev.stage === 'running' || ev.stage === 'done';
  return (
    <div className="animate-fade-up pointer-events-auto absolute inset-x-0 bottom-0 z-40">
      <div className={`border-t bg-ink-850 px-8 py-4 ${error ? 'border-red-400/40' : done ? 'border-accent-400/30' : 'border-white/10'}`}>
        <div className="mb-2.5 flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${error ? 'bg-red-500/20 text-red-300' : done ? 'bg-emerald-500/20 text-emerald-300' : 'bg-accent-400/15 text-accent-300'}`}>
              <Icon name={error ? 'x' : done ? 'check' : 'download'} size={15} />
            </span>
            <div className="min-w-0">
              <div className={`truncate text-sm font-bold ${error ? 'text-red-200' : 'text-white'}`}>{ev.text}</div>
              {ev.detail && <div className="truncate font-mono text-[11px] text-ink-200/55">{ev.detail}</div>}
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            {(progress || game.running) && (
              <button className="btn-ghost !px-2.5 !py-1 text-xs" onClick={() => api.console.open()}>
                <Icon name="terminal" size={13} /> {t('launch.console')}
              </button>
            )}
            {ev.percent >= 0 && <span className="font-mono text-sm font-bold text-accent-300">{Math.round(ev.percent * 100)}%</span>}
          </div>
        </div>
        {!error && <ProgressBar value={done ? 1 : ev.percent} />}
      </div>
    </div>
  );
}

/* ---------------- Modal ---------------- */

export function Modal({ open, onClose, title, children, width = 560 }: { open: boolean; onClose: () => void; title?: ReactNode; children: ReactNode; width?: number }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    if (open) window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  // Portal: Modals liegen direkt im <body>, damit animierte Eltern-Elemente (transform) sie nicht einengen
  return createPortal(
    <div className="fixed inset-0 top-10 z-[60] flex items-center justify-center bg-ink-950/70 p-6" onMouseDown={onClose}>
      <div
        className="animate-fade-up card flex max-h-full w-full flex-col overflow-hidden border-accent-400/20"
        style={{ maxWidth: width }}
        onMouseDown={(e) => e.stopPropagation()}
      >
        {title && (
          <div className="flex items-center justify-between border-b border-white/[0.07] px-6 py-4">
            <div className="font-display text-lg font-bold text-accent-gradient">{title}</div>
            <button className="btn-icon !h-8 !w-8" onClick={onClose}>
              <Icon name="x" size={15} />
            </button>
          </div>
        )}
        <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
      </div>
    </div>,
    document.body,
  );
}

/* ---------------- Mod-Icon mit Platzhalter ---------------- */

/** Mod-/Projekt-Icon; fehlt es oder laedt nicht, erscheint ein Platzhalter */
export function ModIcon({ url, className = 'h-12 w-12 rounded-xl', iconSize = 20 }: { url?: string | null; className?: string; iconSize?: number }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [url]);
  if (!url || failed) {
    return (
      <div className={`flex shrink-0 items-center justify-center border border-white/[0.06] bg-ink-700 text-ink-200/70 ${className}`}>
        <Icon name="package" size={iconSize} />
      </div>
    );
  }
  return <img src={url} alt="" className={`shrink-0 bg-ink-700 object-cover ${className}`} onError={() => setFailed(true)} draggable={false} />;
}

/* ---------------- Toasts ---------------- */

export function Toasts() {
  const { toasts } = useApp();
  return (
    <div className="pointer-events-none fixed right-5 top-14 z-[90] flex w-[340px] flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`animate-fade-up pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 text-sm shadow-card ${
            t.kind === 'error'
              ? 'border-red-400/40 bg-red-950/80 text-red-100'
              : t.kind === 'success'
                ? 'border-accent-400/40 bg-ink-850/90 text-accent-100'
                : 'border-white/10 bg-ink-850/90 text-ink-100'
          }`}
        >
          <Icon name={t.kind === 'error' ? 'x' : t.kind === 'success' ? 'check' : 'sparkle'} size={16} className="mt-0.5 shrink-0" />
          <span className="break-words">{t.text}</span>
        </div>
      ))}
    </div>
  );
}

/* ---------------- Schalter ---------------- */

export function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors duration-300 disabled:opacity-40 ${
        checked ? 'bg-accent-500' : 'bg-white/10'
      }`}
    >
      <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all duration-300 ${checked ? 'left-[22px]' : 'left-0.5'}`} />
    </button>
  );
}

export function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}
