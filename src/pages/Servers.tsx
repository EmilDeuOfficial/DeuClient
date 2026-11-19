/**
 * Server: eigene Minecraft-Server auf diesem PC erstellen und hosten.
 * Liste links, Detailansicht rechts (Start/Stopp, Adressen inkl. UPnP-Portfreigabe, Spieler, Live-Konsole).
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { NewServerInput, ServerAddresses, ServerConfig, ServerRuntime } from '../../shared/types';
import { api, onEvent } from '../api';
import { Modal, Toggle } from '../components/Common';
import { Checkbox, NumberInput, Select } from '../components/Controls';
import { Icon } from '../components/Icons';
import { useApp } from '../store';

const GAMEMODES = [['survival', 'Überleben'], ['creative', 'Kreativ'], ['adventure', 'Abenteuer'], ['spectator', 'Zuschauer']] as const;
const DIFFICULTIES = [['peaceful', 'Friedlich'], ['easy', 'Einfach'], ['normal', 'Normal'], ['hard', 'Schwer']] as const;

export function Servers() {
  const { toast } = useApp();
  const [servers, setServers] = useState<ServerConfig[]>([]);
  const [runtimes, setRuntimes] = useState<Record<string, ServerRuntime>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [progress, setProgress] = useState('');

  const reload = useCallback(async () => {
    const list = await api.servers.list();
    setServers(list);
    const rts = await api.servers.runtimes();
    setRuntimes(Object.fromEntries(rts.map((r) => [r.id, r])));
    setSelected((s) => (s && list.some((x) => x.id === s) ? s : list[0]?.id ?? null));
  }, []);

  useEffect(() => {
    void reload();
    const offs = [
      onEvent('server:state', (r) => setRuntimes((m) => ({ ...m, [r.id]: r }))),
      onEvent('server:progress', (p) => setProgress(p.text)),
    ];
    return () => offs.forEach((f) => f());
  }, [reload]);

  const current = servers.find((s) => s.id === selected);

  return (
    <div className="mx-auto max-w-6xl pb-28">
      <div className="mb-6 flex items-end justify-between gap-6">
        <div>
          <h2 className="font-display text-3xl font-black text-accent-gradient">Deine Server</h2>
          <p className="mt-1 max-w-2xl text-sm text-ink-200/60">
            Erstelle einen eigenen Minecraft-Server, der direkt auf deinem PC läuft – mit IP für dich, dein Heimnetz und (per Portfreigabe) deine Freunde.
          </p>
        </div>
        <button className="btn-accent shrink-0" onClick={() => setCreating(true)}>
          <Icon name="plus" size={16} /> Server erstellen
        </button>
      </div>

      {!servers.length ? (
        <div className="card flex flex-col items-center gap-4 p-12 text-center">
          <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-accent-400/15 text-accent-300"><Icon name="globe" size={30} /></span>
          <div className="font-display text-xl font-bold">Noch kein Server</div>
          <p className="max-w-md text-sm text-ink-200/60">Mit einem Klick bekommst du einen Paper-Server (Plugins möglich) oder einen Vanilla-Server.</p>
          <button className="btn-accent" onClick={() => setCreating(true)}><Icon name="plus" size={15} /> Ersten Server erstellen</button>
        </div>
      ) : (
        <div className="grid grid-cols-[260px_1fr] gap-5">
          <div className="space-y-2">
            {servers.map((s) => {
              const rt = runtimes[s.id];
              return (
                <button
                  key={s.id}
                  onClick={() => setSelected(s.id)}
                  className={`card card-hover flex w-full items-center gap-3 p-3 text-left ${selected === s.id ? 'border-accent-400/50' : ''}`}
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-ink-500/30 text-accent-200"><Icon name="globe" size={18} /></span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-bold">{s.name}</div>
                    <div className="text-[11px] text-ink-200/55">{s.type === 'paper' ? 'Paper' : 'Vanilla'} {s.mcVersion} · Port {s.port}</div>
                  </div>
                  <StatusDot rt={rt} />
                </button>
              );
            })}
          </div>
          {current && <ServerDetail key={current.id} server={current} rt={runtimes[current.id]} onChanged={reload} />}
        </div>
      )}

      <CreateServer
        open={creating}
        progress={progress}
        onClose={() => setCreating(false)}
        onCreated={async (s) => {
          setCreating(false);
          await reload();
          setSelected(s.id);
          toast(`${s.name} erstellt ✓`, 'success');
        }}
      />
    </div>
  );
}

function StatusDot({ rt }: { rt?: ServerRuntime }) {
  const color = rt?.online ? 'bg-emerald-400' : rt?.running ? 'bg-amber-300' : 'bg-white/20';
  return (
    <span className="relative flex h-2.5 w-2.5">
      {rt?.online && <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60" />}
      <span className={`relative inline-flex h-2.5 w-2.5 rounded-full ${color}`} />
    </span>
  );
}

/* ====================== Detail ====================== */

function ServerDetail({ server, rt, onChanged }: { server: ServerConfig; rt?: ServerRuntime; onChanged: () => void }) {
  const { toast, instances, launch, accounts, launching, game } = useApp();
  const [log, setLog] = useState<string[]>([]);
  const [cmd, setCmd] = useState('');
  const [addr, setAddr] = useState<ServerAddresses | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);

  const loadAddr = useCallback(() => {
    void api.servers.addresses(server.id).then(setAddr);
  }, [server.id]);
  useEffect(() => {
    void api.servers.log(server.id).then(setLog);
    loadAddr();
    return onEvent('server:log', (e) => {
      if (e.id === server.id) setLog((l) => l.concat(e.lines).slice(-1500));
    });
  }, [server.id, loadAddr]);
  // nur die Konsole mitscrollen (nicht die ganze Seite)
  useEffect(() => {
    const box = bottom.current?.parentElement;
    if (box) box.scrollTop = box.scrollHeight;
  }, [log]);

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    try {
      await fn();
      if (ok) toast(ok, 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setBusy(false);
      onChanged();
      loadAddr();
    }
  };

  /** Beitreten: passendes Modpack (gleiche Version) starten und direkt verbinden */
  const join = () => {
    if (!accounts.activeUuid) return toast('Bitte zuerst anmelden', 'error');
    const match = instances.find((i) => i.mcVersion === server.mcVersion && i.isDefault) ?? instances.find((i) => i.mcVersion === server.mcVersion);
    if (!match) return toast(`Erstelle zuerst ein Modpack mit Minecraft ${server.mcVersion}.`, 'error');
    void launch(match.id, addr?.local ?? `localhost:${server.port}`);
  };

  const copy = (text: string) => {
    void navigator.clipboard.writeText(text);
    toast(`Kopiert: ${text}`, 'success');
  };

  const running = !!rt?.running;
  return (
    <div className="space-y-4">
      {/* Kopf */}
      <section className="card relative overflow-hidden p-5">
        <div className="relative flex items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-ink-700 text-accent-300"><Icon name="globe" size={26} /></span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h3 className="truncate font-display text-2xl font-black">{server.name}</h3>
              <StatusDot rt={rt} />
              <span className="text-xs font-semibold text-ink-200/70">{rt?.online ? 'Online' : running ? 'Startet ...' : 'Gestoppt'}</span>
            </div>
            <div className="mt-1 flex flex-wrap gap-1.5 text-xs">
              <span className="badge bg-white/10 text-ink-100">{server.type === 'paper' ? 'Paper' : 'Vanilla'} {server.mcVersion}</span>
              <span className="badge bg-white/10 text-ink-100">{(server.ramMb / 1024).toFixed(1)} GB RAM</span>
              <span className="badge bg-white/10 text-ink-100">{rt?.players.length ?? 0}/{server.maxPlayers} Spieler</span>
            </div>
          </div>
          <div className="flex shrink-0 gap-2">
            {running ? (
              <button className="btn-danger" disabled={busy} onClick={() => run(() => api.servers.stop(server.id), 'Server gestoppt')}><Icon name="stop" size={13} /> Stoppen</button>
            ) : (
              <button className="btn-accent" disabled={busy} onClick={() => run(() => api.servers.start(server.id))}><Icon name="play" size={14} /> Starten</button>
            )}
            <button className="btn-alt" disabled={!rt?.online || launching || game.running} onClick={join}><Icon name="sparkle" size={14} /> Beitreten</button>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-2 gap-4">
        {/* Adressen */}
        <section className="card space-y-2 p-5">
          <div className="mb-1 font-bold">IP-Adressen</div>
          {addr && (
            <>
              <AddrRow label="Auf diesem PC" value={addr.local} onCopy={copy} />
              {addr.lan && <AddrRow label="Im Heimnetz (LAN)" value={addr.lan} onCopy={copy} />}
              {addr.publicIp ? (
                <AddrRow label="Für Freunde (Internet)" value={addr.publicIp} onCopy={copy} highlight />
              ) : (
                <div className="rounded-xl border border-white/[0.06] bg-ink-950/40 p-3 text-xs text-ink-200/70">
                  Damit Freunde übers Internet joinen können, muss Port <b className="text-accent-200">{server.port}</b> im Router freigegeben sein.
                </div>
              )}
              <div className="flex items-center gap-2 pt-1">
                {addr.upnp ? (
                  <button className="btn-ghost !py-1.5 text-xs" disabled={busy} onClick={() => run(() => api.servers.upnpOff(server.id), 'Portfreigabe entfernt')}>
                    <Icon name="x" size={12} /> Portfreigabe entfernen
                  </button>
                ) : (
                  <button
                    className="btn-ghost !py-1.5 text-xs"
                    disabled={busy}
                    onClick={() => {
                      if (!confirm(`Port ${server.port} jetzt per UPnP in deinem Router freigeben?\n\nDanach ist dein Server aus dem Internet erreichbar. Teile die IP nur mit Leuten, denen du vertraust.`)) return;
                      void run(() => api.servers.upnpOn(server.id), 'Port freigegeben – deine öffentliche IP steht jetzt oben');
                    }}
                  >
                    <Icon name="globe" size={12} /> Port freigeben (UPnP)
                  </button>
                )}
              </div>
            </>
          )}
        </section>

        {/* Spieler + Aktionen */}
        <section className="card flex flex-col p-5">
          <div className="mb-2 font-bold">Spieler online</div>
          <div className="flex-1 space-y-1 text-sm">
            {!rt?.players.length && <div className="text-xs text-ink-200/50">{rt?.online ? 'Noch niemand da.' : 'Server ist nicht online.'}</div>}
            {rt?.players.map((p) => (
              <div key={p} className="flex items-center justify-between rounded-lg bg-ink-950/40 px-3 py-1.5">
                <span>{p}</span>
                <button className="text-[11px] text-accent-300 hover:underline" onClick={() => api.servers.command(server.id, `op ${p}`)}>OP geben</button>
              </div>
            ))}
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <button className="btn-ghost !py-1.5 text-xs" onClick={() => api.servers.openFolder(server.id)}><Icon name="folder" size={12} /> Ordner</button>
            {server.type === 'paper' && <button className="btn-ghost !py-1.5 text-xs" onClick={() => api.servers.openFolder(server.id, 'plugins')}><Icon name="puzzle" size={12} /> Plugins</button>}
            <button className="btn-ghost !py-1.5 text-xs" disabled={running} onClick={() => setEditing(true)}><Icon name="edit" size={12} /> Einstellungen</button>
            <button
              className="btn-danger !py-1.5 text-xs"
              disabled={running}
              onClick={() => confirm(`Server "${server.name}" samt Welt wirklich löschen?`) && run(() => api.servers.remove(server.id), 'Server gelöscht')}
            >
              <Icon name="trash" size={12} /> Löschen
            </button>
          </div>
        </section>
      </div>

      {/* Konsole */}
      <section className="card overflow-hidden">
        <div className="flex items-center justify-between border-b border-white/[0.06] px-4 py-2.5">
          <div className="flex items-center gap-2 text-sm font-bold"><Icon name="terminal" size={14} /> Konsole</div>
        </div>
        <div className="h-64 select-text overflow-auto bg-black/40 px-4 py-3 font-mono text-[12px] leading-[1.5]">
          {!log.length && <div className="text-ink-200/40">Starte den Server, um die Ausgabe zu sehen.</div>}
          {log.map((l, i) => (
            <div key={i} className={`whitespace-pre-wrap break-all ${l.startsWith('[DeuClient]') ? 'text-accent-300' : /WARN/.test(l) ? 'text-amber-200' : /ERROR|Exception/.test(l) ? 'text-red-300' : l.startsWith('>') ? 'text-ink-200' : 'text-ink-100/85'}`}>{l}</div>
          ))}
          <div ref={bottom} />
        </div>
        <form
          className="flex gap-2 border-t border-white/[0.06] p-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (!cmd.trim()) return;
            void api.servers.command(server.id, cmd).catch((err) => toast((err as Error).message, 'error'));
            setCmd('');
          }}
        >
          <input className="input !py-2 font-mono text-xs" placeholder={running ? 'Befehl eingeben, z. B. op DeinName, time set day, say Hallo' : 'Server ist gestoppt'} disabled={!running} value={cmd} onChange={(e) => setCmd(e.target.value)} />
          <button className="btn-accent !py-2" disabled={!running}>Senden</button>
        </form>
      </section>

      <EditServer open={editing} server={server} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); onChanged(); }} />
    </div>
  );
}

function AddrRow({ label, value, onCopy, highlight }: { label: string; value: string; onCopy: (v: string) => void; highlight?: boolean }) {
  return (
    <div className={`flex items-center gap-3 rounded-xl border px-3 py-2 ${highlight ? 'border-accent-400/40 bg-accent-400/10' : 'border-white/[0.06] bg-ink-950/40'}`}>
      <div className="min-w-0 flex-1">
        <div className="text-[10px] font-bold uppercase tracking-wider text-ink-200/55">{label}</div>
        <div className="select-text truncate font-mono text-sm font-bold">{value}</div>
      </div>
      <button className="btn-icon !h-8 !w-8" onClick={() => onCopy(value)} title="Kopieren"><Icon name="copy" size={14} /></button>
    </div>
  );
}

/* ====================== Erstellen / Bearbeiten ====================== */

function useVersions(type: 'paper' | 'vanilla', open: boolean) {
  const [versions, setVersions] = useState<string[]>([]);
  useEffect(() => {
    if (!open) return;
    const p = type === 'paper' ? api.servers.paperVersions() : api.versions.list(false).then((v) => v.map((x) => x.id));
    void p.then(setVersions).catch(() => setVersions([]));
  }, [type, open]);
  return versions;
}

function CreateServer({ open, progress, onClose, onCreated }: { open: boolean; progress: string; onClose: () => void; onCreated: (s: ServerConfig) => void }) {
  const { toast } = useApp();
  const [form, setForm] = useState<NewServerInput>({
    name: 'Mein Server', type: 'paper', mcVersion: '26.3', ramMb: 3072, maxPlayers: 10,
    gamemode: 'survival', difficulty: 'normal', motd: '', onlineMode: true, pvp: true, acceptEula: false,
  });
  const [busy, setBusy] = useState(false);
  const [totalMem, setTotalMem] = useState(8192);
  const versions = useVersions(form.type, open);
  useEffect(() => {
    void api.app.systemInfo().then((s) => setTotalMem(s.totalMemMb));
  }, []);
  const set = <K extends keyof NewServerInput>(k: K, v: NewServerInput[K]) => setForm((f) => ({ ...f, [k]: v }));
  const maxRam = Math.max(2048, Math.min(16384, totalMem - 2048));

  const create = async () => {
    setBusy(true);
    try {
      onCreated(await api.servers.create(form));
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={() => !busy && onClose()} title="Server erstellen" width={620}>
      <div className="space-y-4 p-6">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label">Name</label>
            <input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} />
          </div>
          <div>
            <label className="label">Minecraft-Version</label>
            <Select value={form.mcVersion} onChange={(v) => set('mcVersion', v)}
              options={[...(versions.includes(form.mcVersion) ? [] : [form.mcVersion]), ...versions].map((v) => ({ value: v, label: v }))} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2">
          {(['paper', 'vanilla'] as const).map((t) => (
            <button key={t} onClick={() => set('type', t)} className={`rounded-xl border px-4 py-3 text-left transition ${form.type === t ? 'border-accent-400/70 bg-accent-400/10' : 'border-white/10 hover:border-white/25'}`}>
              <div className="font-bold">{t === 'paper' ? 'Paper (empfohlen)' : 'Vanilla'}</div>
              <div className="text-xs text-ink-200/60">{t === 'paper' ? 'Schnell, Plugins möglich' : 'Original-Server von Mojang'}</div>
            </button>
          ))}
        </div>
        <div>
          <div className="mb-2 flex justify-between"><label className="label !mb-0">Arbeitsspeicher</label><span className="font-mono text-sm font-bold text-accent-300">{(form.ramMb / 1024).toFixed(1)} GB</span></div>
          <input type="range" className="deu-range w-full" min={1024} max={maxRam} step={512} value={form.ramMb}
            style={{ ['--fill' as string]: `${((form.ramMb - 1024) / (maxRam - 1024)) * 100}%` }} onChange={(e) => set('ramMb', +e.target.value)} />
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="label">Spielmodus</label>
            <Select value={form.gamemode} onChange={(v) => set('gamemode', v as NewServerInput['gamemode'])} options={GAMEMODES.map(([v, l]) => ({ value: v, label: l }))} />
          </div>
          <div>
            <label className="label">Schwierigkeit</label>
            <Select value={form.difficulty} onChange={(v) => set('difficulty', v as NewServerInput['difficulty'])} options={DIFFICULTIES.map(([v, l]) => ({ value: v, label: l }))} />
          </div>
          <div>
            <label className="label">Max. Spieler</label>
            <NumberInput min={1} max={200} value={form.maxPlayers} onChange={(v) => set('maxPlayers', v)} />
          </div>
        </div>
        <div>
          <label className="label">Beschreibung (MOTD)</label>
          <input className="input" placeholder={`${form.name} – gehostet mit dem DeuClient`} value={form.motd} onChange={(e) => set('motd', e.target.value)} />
        </div>
        <div className="grid grid-cols-2 gap-3 text-sm">
          <label className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-ink-950/40 px-3 py-2">PvP <Toggle checked={form.pvp} onChange={(v) => set('pvp', v)} /></label>
          <label className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-ink-950/40 px-3 py-2" title="Nur echte Minecraft-Accounts (empfohlen)">
            Nur echte Accounts <Toggle checked={form.onlineMode} onChange={(v) => set('onlineMode', v)} />
          </label>
        </div>
        <label className="flex items-start gap-3 rounded-xl border border-accent-400/25 bg-accent-400/5 p-3 text-sm">
          <Checkbox className="mt-0.5" checked={form.acceptEula} onChange={(v) => set('acceptEula', v)} />
          <span>
            Ich akzeptiere die{' '}
            <button className="text-accent-300 underline" onClick={(e) => { e.preventDefault(); void api.app.openExternal('https://aka.ms/MinecraftEULA'); }}>Minecraft-EULA</button>
            . Ohne Zustimmung startet kein Minecraft-Server.
          </span>
        </label>
        {busy && <div className="text-xs text-accent-200">{progress || 'Erstelle Server ...'}</div>}
        <div className="flex justify-end gap-2">
          <button className="btn-ghost" disabled={busy} onClick={onClose}>Abbrechen</button>
          <button className="btn-accent" disabled={busy || !form.acceptEula || !form.name.trim()} onClick={create}>
            <Icon name="check" size={15} /> {busy ? 'Erstelle ...' : 'Server erstellen'}
          </button>
        </div>
      </div>
    </Modal>
  );
}

function EditServer({ open, server, onClose, onSaved }: { open: boolean; server: ServerConfig; onClose: () => void; onSaved: () => void }) {
  const { toast } = useApp();
  const [form, setForm] = useState(server);
  useEffect(() => {
    setForm(server);
  }, [server, open]);
  const set = <K extends keyof ServerConfig>(k: K, v: ServerConfig[K]) => setForm((f) => ({ ...f, [k]: v }));
  const save = async () => {
    try {
      await api.servers.update(server.id, form);
      onSaved();
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };
  const ramMax = useMemo(() => 16384, []);
  return (
    <Modal open={open} onClose={onClose} title="Server-Einstellungen" width={560}>
      <div className="space-y-4 p-6">
        <div className="grid grid-cols-2 gap-3">
          <div><label className="label">Name</label><input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} /></div>
          <div><label className="label">Port</label><NumberInput min={1024} max={65535} value={form.port} onChange={(v) => set('port', v)} /></div>
        </div>
        <div>
          <div className="mb-2 flex justify-between"><label className="label !mb-0">Arbeitsspeicher</label><span className="font-mono text-sm font-bold text-accent-300">{(form.ramMb / 1024).toFixed(1)} GB</span></div>
          <input type="range" className="deu-range w-full" min={1024} max={ramMax} step={512} value={form.ramMb}
            style={{ ['--fill' as string]: `${((form.ramMb - 1024) / (ramMax - 1024)) * 100}%` }} onChange={(e) => set('ramMb', +e.target.value)} />
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div><label className="label">Spielmodus</label><Select value={form.gamemode} onChange={(v) => set('gamemode', v as ServerConfig['gamemode'])} options={GAMEMODES.map(([v, l]) => ({ value: v, label: l }))} /></div>
          <div><label className="label">Schwierigkeit</label><Select value={form.difficulty} onChange={(v) => set('difficulty', v as ServerConfig['difficulty'])} options={DIFFICULTIES.map(([v, l]) => ({ value: v, label: l }))} /></div>
          <div><label className="label">Max. Spieler</label><NumberInput min={1} max={200} value={form.maxPlayers} onChange={(v) => set('maxPlayers', v)} /></div>
        </div>
        <div><label className="label">Beschreibung (MOTD)</label><input className="input" value={form.motd} onChange={(e) => set('motd', e.target.value)} /></div>
        <div className="grid grid-cols-3 gap-3 text-sm">
          <label className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-ink-950/40 px-3 py-2">PvP <Toggle checked={form.pvp} onChange={(v) => set('pvp', v)} /></label>
          <label className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-ink-950/40 px-3 py-2">Whitelist <Toggle checked={form.whitelist} onChange={(v) => set('whitelist', v)} /></label>
          <label className="flex items-center justify-between rounded-xl border border-white/[0.06] bg-ink-950/40 px-3 py-2">Echte Accounts <Toggle checked={form.onlineMode} onChange={(v) => set('onlineMode', v)} /></label>
        </div>
        <div className="flex justify-end gap-2">
          <button className="btn-ghost" onClick={onClose}>Abbrechen</button>
          <button className="btn-accent" onClick={save}><Icon name="check" size={15} /> Speichern</button>
        </div>
      </div>
    </Modal>
  );
}
