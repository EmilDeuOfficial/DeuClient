/**
 * Eigene Formular-Bausteine im DeuClient-Look (statt der Windows-Standardelemente):
 * Dropdown (Select), Checkbox und Zahlenfeld mit Plus/Minus.
 * Das Dropdown liegt per Portal im <body> und passt Position und Hoehe an das Fenster an,
 * damit nie etwas abgeschnitten wird.
 */
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './Icons';

export interface SelectOption<T extends string = string> {
  value: T;
  label: ReactNode;
  /** zweite, gedaempfte Zeile */
  sub?: ReactNode;
  icon?: ReactNode;
  disabled?: boolean;
}

interface Placement {
  left: number;
  width: number;
  top?: number;
  bottom?: number;
  maxHeight: number;
}

function place(anchor: DOMRect, minWidth: number, needHeight = 200): Placement {
  const margin = 10;
  const gap = 6;
  const below = window.innerHeight - anchor.bottom - gap - margin;
  const above = anchor.top - gap - margin - 40; // Titelleiste
  const openUp = below < needHeight && above > below;
  const width = Math.max(anchor.width, minWidth);
  const left = Math.min(Math.max(margin, anchor.left), Math.max(margin, window.innerWidth - width - margin));
  return openUp
    ? { left, width, bottom: window.innerHeight - anchor.top + gap, maxHeight: Math.max(120, Math.min(360, above)) }
    : { left, width, top: anchor.bottom + gap, maxHeight: Math.max(120, Math.min(360, below)) };
}

export function Select<T extends string>({
  value,
  options,
  onChange,
  disabled,
  className = '',
  placeholder,
  listMinWidth = 0,
  renderTrigger,
}: {
  value: T;
  options: SelectOption<T>[];
  onChange: (v: T) => void;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
  listMinWidth?: number;
  /** eigenes Aussehen des Ausloesers (z. B. auf der Startseite) */
  renderTrigger?: (state: { open: boolean; selected?: SelectOption<T> }) => ReactNode;
}) {
  const btn = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<Placement | null>(null);
  const [active, setActive] = useState(-1);
  const selected = options.find((o) => o.value === value);

  const reposition = useCallback(() => {
    if (btn.current) setPos(place(btn.current.getBoundingClientRect(), listMinWidth));
  }, [listMinWidth]);

  useLayoutEffect(() => {
    if (open) reposition();
  }, [open, reposition]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      const target = e.target as Node;
      if (!btn.current?.contains(target) && !list.current?.contains(target)) setOpen(false);
    };
    window.addEventListener('mousedown', close);
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    return () => {
      window.removeEventListener('mousedown', close);
      window.removeEventListener('resize', reposition);
      window.removeEventListener('scroll', reposition, true);
    };
  }, [open, reposition]);

  // gewaehlten Eintrag in den sichtbaren Bereich holen
  useEffect(() => {
    if (!open || !pos) return;
    const idx = active >= 0 ? active : options.findIndex((o) => o.value === value);
    list.current?.querySelector<HTMLElement>(`[data-i="${idx}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [open, pos, active, options, value]);

  const choose = (o: SelectOption<T>) => {
    if (o.disabled) return;
    onChange(o.value);
    setOpen(false);
    btn.current?.focus();
  };

  const onKey = (e: KeyboardEvent) => {
    if (disabled) return;
    if (!open && (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      setActive(options.findIndex((o) => o.value === value));
      setOpen(true);
      return;
    }
    if (!open) return;
    if (e.key === 'Escape') {
      e.stopPropagation();
      setOpen(false);
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const dir = e.key === 'ArrowDown' ? 1 : -1;
      let i = active;
      do i = (i + dir + options.length) % options.length;
      while (options[i]?.disabled && i !== active);
      setActive(i);
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (options[active]) choose(options[active]);
    }
  };

  return (
    <>
      <button
        ref={btn}
        type="button"
        disabled={disabled}
        onClick={() => {
          setActive(options.findIndex((o) => o.value === value));
          setOpen((o) => !o);
        }}
        onKeyDown={onKey}
        aria-haspopup="listbox"
        aria-expanded={open}
        className={renderTrigger ? className : `input flex cursor-pointer items-center justify-between gap-3 text-left disabled:cursor-not-allowed disabled:opacity-50 ${open ? '!border-accent-400/60 ring-2 ring-accent-400/20' : ''} ${className}`}
      >
        {renderTrigger ? (
          renderTrigger({ open, selected })
        ) : (
          <>
            <span className="flex min-w-0 items-center gap-2.5">
              {selected?.icon}
              <span className="min-w-0 truncate">{selected?.label ?? <span className="text-ink-200/40">{placeholder ?? '-'}</span>}</span>
            </span>
            <Icon name="chevronDown" size={15} strokeWidth={2.5} className={`shrink-0 text-accent-400 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
          </>
        )}
      </button>
      {open &&
        pos &&
        createPortal(
          <div
            ref={list}
            role="listbox"
            className="animate-fade-up fixed z-[80] flex flex-col gap-1.5 overflow-y-auto rounded-xl border border-accent-400/25 bg-ink-850/95 p-2 shadow-card"
            style={{ left: pos.left, width: pos.width, top: pos.top, bottom: pos.bottom, maxHeight: pos.maxHeight }}
          >
            {options.map((o, i) => {
              const isSel = o.value === value;
              return (
                <button
                  key={o.value}
                  type="button"
                  role="option"
                  aria-selected={isSel}
                  data-i={i}
                  disabled={o.disabled}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => choose(o)}
                  className={`flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors disabled:opacity-40 ${
                    isSel ? 'bg-accent-400/15 text-accent-200' : i === active ? 'bg-white/[0.06] text-white' : 'text-ink-100/90'
                  }`}
                >
                  {o.icon}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{o.label}</span>
                    {o.sub && <span className="block truncate text-[11px] text-ink-200/55">{o.sub}</span>}
                  </span>
                  {isSel && <Icon name="check" size={14} strokeWidth={3} className="shrink-0 text-accent-300" />}
                </button>
              );
            })}
          </div>,
          document.body,
        )}
    </>
  );
}

export function Checkbox({ checked, onChange, disabled, className = '' }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; className?: string }) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-md border transition duration-200 disabled:opacity-40 ${
        checked ? 'border-accent-400 bg-accent-500 text-ink-950' : 'border-white/20 bg-ink-950/60 hover:border-accent-400/60'
      } ${className}`}
    >
      {checked && <Icon name="check" size={13} strokeWidth={3.5} />}
    </button>
  );
}

export function NumberInput({
  value,
  onChange,
  min = 0,
  max = 999999,
  step = 1,
  disabled,
  className = '',
}: {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  className?: string;
}) {
  const [text, setText] = useState(String(value));
  useEffect(() => setText(String(value)), [value]);
  const clamp = (n: number) => Math.min(max, Math.max(min, n));
  const commit = (raw: string) => {
    const n = parseInt(raw, 10);
    const v = Number.isFinite(n) ? clamp(n) : value;
    setText(String(v));
    if (v !== value) onChange(v);
  };
  const bump = (dir: 1 | -1) => {
    const v = clamp(value + dir * step);
    setText(String(v));
    if (v !== value) onChange(v);
  };
  return (
    <div className={`relative ${disabled ? 'opacity-50' : ''} ${className}`}>
      <input
        className="input !pr-10"
        inputMode="numeric"
        disabled={disabled}
        value={text}
        onChange={(e) => setText(e.target.value.replace(/[^0-9]/g, ''))}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit(text);
          else if (e.key === 'ArrowUp') {
            e.preventDefault();
            bump(1);
          } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            bump(-1);
          }
        }}
      />
      <div className="absolute right-1.5 top-1/2 flex -translate-y-1/2 flex-col">
        <button type="button" tabIndex={-1} disabled={disabled || value >= max} onClick={() => bump(1)} className="flex h-4 w-6 items-center justify-center rounded text-accent-400 transition hover:bg-accent-400/15 disabled:opacity-30">
          <Icon name="chevronDown" size={12} strokeWidth={3} className="rotate-180" />
        </button>
        <button type="button" tabIndex={-1} disabled={disabled || value <= min} onClick={() => bump(-1)} className="flex h-4 w-6 items-center justify-center rounded text-accent-400 transition hover:bg-accent-400/15 disabled:opacity-30">
          <Icon name="chevronDown" size={12} strokeWidth={3} />
        </button>
      </div>
    </div>
  );
}

/* ---------------- Farbwaehler ---------------- */

function hexToHsv(hex: string): [number, number, number] {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  const n = parseInt(m ? m[1] : '000000', 16);
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
  let h = 0;
  if (d) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h = (h * 60 + 360) % 360;
  }
  return [h, max ? d / max : 0, max];
}

function hsvToHex(h: number, s: number, v: number): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return v - v * s * Math.max(0, Math.min(k, 4 - k, 1));
  };
  const to = (x: number) => Math.round(x * 255).toString(16).padStart(2, '0');
  return `#${to(f(5))}${to(f(3))}${to(f(1))}`;
}

/** Eigener Farbwaehler (statt dem Windows-Dialog): Farbfeld, Farbton-Regler und Hex-Eingabe */
export function ColorInput({ value, onChange, className = '', compact }: { value: string; onChange: (hex: string) => void; className?: string; compact?: boolean }) {
  const btn = useRef<HTMLButtonElement>(null);
  const pop = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<Placement | null>(null);
  const [hsv, setHsv] = useState<[number, number, number]>(() => hexToHsv(value));
  const [hexText, setHexText] = useState(value);

  // von aussen geaenderten Wert uebernehmen (nur wenn er nicht aus dem Waehler selbst stammt)
  useEffect(() => {
    if (hsvToHex(hsv[0], hsv[1], hsv[2]).toLowerCase() !== value.toLowerCase()) setHsv(hexToHsv(value));
    setHexText(value);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const reposition = useCallback(() => {
    if (btn.current) setPos(place(btn.current.getBoundingClientRect(), 248, 270));
  }, []);
  useLayoutEffect(() => {
    if (open) reposition();
  }, [open, reposition]);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      const t = e.target as Node;
      if (!btn.current?.contains(t) && !pop.current?.contains(t)) setOpen(false);
    };
    const esc = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('mousedown', close);
    window.addEventListener('keydown', esc);
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    return () => {
      window.removeEventListener('mousedown', close);
      window.removeEventListener('keydown', esc);
      window.removeEventListener('resize', reposition);
      window.removeEventListener('scroll', reposition, true);
    };
  }, [open, reposition]);

  const apply = (next: [number, number, number]) => {
    setHsv(next);
    onChange(hsvToHex(next[0], next[1], next[2]));
  };

  const drag = (el: HTMLElement, e: React.PointerEvent, fn: (x: number, y: number) => void) => {
    const rect = el.getBoundingClientRect();
    const move = (ev: { clientX: number; clientY: number }) => fn(Math.min(1, Math.max(0, (ev.clientX - rect.left) / rect.width)), Math.min(1, Math.max(0, (ev.clientY - rect.top) / rect.height)));
    move(e);
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  return (
    <>
      <button
        ref={btn}
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={compact ? `h-8 w-8 shrink-0 rounded-lg border border-white/20 transition hover:border-accent-400/60 ${open ? 'ring-2 ring-accent-400/30' : ''} ${className}` : `input flex cursor-pointer items-center gap-3 text-left ${open ? '!border-accent-400/60 ring-2 ring-accent-400/20' : ''} ${className}`}
        style={compact ? { background: value } : undefined}
        aria-label="Farbe waehlen"
      >
        {!compact && (
          <>
            <span className="h-5 w-5 shrink-0 rounded-md border border-white/20" style={{ background: value }} />
            <span className="flex-1 font-mono text-xs uppercase">{value}</span>
            <Icon name="chevronDown" size={15} strokeWidth={2.5} className={`shrink-0 text-accent-400 transition-transform ${open ? 'rotate-180' : ''}`} />
          </>
        )}
      </button>
      {open &&
        pos &&
        createPortal(
          <div
            ref={pop}
            className="animate-fade-up fixed z-[80] space-y-3 rounded-xl border border-accent-400/25 bg-ink-850/95 p-3 shadow-card"
            style={{ left: pos.left, width: 248, top: pos.top, bottom: pos.bottom }}
          >
            <div
              className="relative h-36 w-full cursor-crosshair rounded-lg"
              style={{ background: `linear-gradient(to top, #000, transparent), linear-gradient(to right, #fff, hsl(${hsv[0]}, 100%, 50%))` }}
              onPointerDown={(e) => drag(e.currentTarget, e, (x, y) => apply([hsv[0], x, 1 - y]))}
            >
              <span className="pointer-events-none absolute h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,.5)]" style={{ left: `${hsv[1] * 100}%`, top: `${(1 - hsv[2]) * 100}%`, background: value }} />
            </div>
            <div
              className="relative h-3.5 w-full cursor-pointer rounded-full"
              style={{ background: 'linear-gradient(to right, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00)' }}
              onPointerDown={(e) => drag(e.currentTarget, e, (x) => apply([x * 359.9, hsv[1], hsv[2]]))}
            >
              <span className="pointer-events-none absolute top-1/2 h-5 w-5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white shadow-[0_0_0_1px_rgba(0,0,0,.5)]" style={{ left: `${(hsv[0] / 360) * 100}%`, background: `hsl(${hsv[0]}, 100%, 50%)` }} />
            </div>
            <div className="flex items-center gap-2">
              <span className="h-9 w-9 shrink-0 rounded-lg border border-white/20" style={{ background: value }} />
              <input
                className="input !py-1.5 font-mono text-xs uppercase"
                maxLength={7}
                value={hexText}
                onChange={(e) => {
                  const t = e.target.value.startsWith('#') ? e.target.value : `#${e.target.value}`;
                  setHexText(t);
                  if (/^#[0-9a-f]{6}$/i.test(t)) {
                    setHsv(hexToHsv(t));
                    onChange(t.toLowerCase());
                  }
                }}
                spellCheck={false}
              />
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
