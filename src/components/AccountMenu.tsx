/** Account-Anzeige oben rechts: Skin-Kopf + Spielername, Dropdown zum Wechseln/Hinzufügen/Abmelden. */
import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { useApp } from '../store';
import { Icon } from './Icons';
import { SkinHead } from './SkinHead';

export function AccountMenu() {
  const { accounts, setAccounts, t, toast } = useApp();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const active = accounts.accounts.find((a) => a.uuid === accounts.activeUuid);

  // Klick außerhalb schließt das Menü
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const login = async () => {
    setBusy(true);
    try {
      const s = await api.accounts.add();
      setAccounts(s);
      const acc = s.accounts.find((a) => a.uuid === s.activeUuid);
      toast(`✦ ${acc?.name ?? ''}`, 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setBusy(false);
      setOpen(false);
    }
  };

  if (!active) {
    return (
      <button onClick={login} disabled={busy} className="btn-accent">
        <svg width="16" height="16" viewBox="0 0 23 23">
          <path fill="#f35325" d="M1 1h10v10H1z" />
          <path fill="#81bc06" d="M12 1h10v10H12z" />
          <path fill="#05a6f0" d="M1 12h10v10H1z" />
          <path fill="#ffba08" d="M12 12h10v10H12z" />
        </svg>
        {busy ? t('account.loggingIn') : t('account.login')}
      </button>
    );
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-3 rounded-2xl border border-white/10 bg-ink-850/70 py-1.5 pl-2.5 pr-3.5 transition hover:border-accent-400/40"
      >
        <SkinHead url={active.skinUrl} size={24} />
        <div className="text-left leading-tight">
          <div className="text-sm font-bold text-white">{active.name}</div>
          <div className="text-[10px] font-semibold uppercase tracking-wider text-emerald-300/80">Microsoft</div>
        </div>
        <Icon name="chevronDown" size={14} className={`text-accent-300 transition ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="animate-fade-up absolute right-0 top-[calc(100%+8px)] z-50 w-64 overflow-hidden rounded-2xl border border-white/10 bg-ink-850/95 p-2 shadow-card">
          <div className="px-2 pb-1.5 pt-1 text-[10px] font-bold uppercase tracking-widest text-ink-200/50">{t('account.switch')}</div>
          {accounts.accounts.map((a) => (
            <div key={a.uuid} className={`group flex items-center gap-2.5 rounded-xl px-2 py-2 transition ${a.uuid === active.uuid ? 'bg-accent-400/10' : 'hover:bg-white/5'}`}>
              <button className="flex flex-1 items-center gap-2.5 text-left" onClick={async () => { setAccounts(await api.accounts.select(a.uuid)); setOpen(false); }}>
                <SkinHead url={a.skinUrl} size={24} />
                <span className="text-sm font-semibold">{a.name}</span>
                {a.uuid === active.uuid && <Icon name="check" size={14} className="text-accent-300" />}
              </button>
              <button
                title={t('account.remove')}
                className="rounded-lg p-1 text-ink-200/40 opacity-0 transition hover:bg-red-500/20 hover:text-red-300 group-hover:opacity-100"
                onClick={async () => setAccounts(await api.accounts.remove(a.uuid))}
              >
                <Icon name="logout" size={14} />
              </button>
            </div>
          ))}
          <div className="my-1.5 h-px bg-white/10" />
          <button onClick={login} disabled={busy} className="flex w-full items-center gap-2 rounded-xl px-2 py-2 text-sm font-semibold text-accent-200 transition hover:bg-accent-400/10">
            <Icon name="plus" size={16} /> {busy ? t('account.loggingIn') : t('account.add')}
          </button>
        </div>
      )}
    </div>
  );
}
