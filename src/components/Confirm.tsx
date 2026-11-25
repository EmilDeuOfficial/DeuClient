/** Bestaetigungsfenster im DeuClient-Stil (ersetzt das graue Windows-Fenster von window.confirm). */
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useApp } from '../store';
import { Icon } from './Icons';

interface ConfirmOpts {
  title?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** rote Darstellung fuer Loeschen und andere nicht umkehrbare Aktionen */
  danger?: boolean;
}

interface Pending extends ConfirmOpts {
  message: string;
  resolve: (ok: boolean) => void;
}

let show: ((p: Pending) => void) | null = null;

/** Zeigt das Fenster und liefert true (bestaetigt) oder false (abgebrochen) */
export function confirmDialog(message: string, opts: ConfirmOpts = {}): Promise<boolean> {
  return new Promise((resolve) => {
    if (!show) {
      resolve(window.confirm(message)); // Notbehelf, falls die Oberflaeche noch nicht steht
      return;
    }
    show({ message, resolve, ...opts });
  });
}

export function ConfirmHost() {
  const { t } = useApp();
  const [queue, setQueue] = useState<Pending[]>([]);

  useEffect(() => {
    show = (p) => setQueue((q) => [...q, p]);
    return () => {
      show = null;
    };
  }, []);

  const cur = queue[0];
  const close = (ok: boolean) => {
    if (!cur) return;
    cur.resolve(ok);
    setQueue((q) => q.slice(1));
  };

  useEffect(() => {
    if (!cur) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cur]);

  if (!cur) return null;
  return createPortal(
    <div className="fixed inset-0 top-10 z-[80] flex items-center justify-center bg-ink-950/70 p-6" onMouseDown={() => close(false)}>
      <div className="animate-fade-up card w-full max-w-[420px] border-accent-400/20 p-6" role="alertdialog" onMouseDown={(e) => e.stopPropagation()}>
        <div className="flex items-start gap-4">
          <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${cur.danger ? 'bg-red-500/15 text-red-300' : 'bg-accent-400/15 text-accent-300'}`}>
            <Icon name={cur.danger ? 'trash' : 'sparkle'} size={18} />
          </span>
          <div className="min-w-0">
            {cur.title && <div className="font-display text-base font-bold text-white">{cur.title}</div>}
            <div className={`whitespace-pre-line break-words text-sm leading-relaxed text-ink-200/80 ${cur.title ? 'mt-1.5' : 'pt-2'}`}>{cur.message}</div>
          </div>
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <button className="btn-ghost" onClick={() => close(false)}>
            {cur.cancelLabel ?? t('inst.cancel')}
          </button>
          <button autoFocus className={cur.danger ? 'btn-danger' : 'btn-accent'} onClick={() => close(true)}>
            {cur.confirmLabel ?? 'OK'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
