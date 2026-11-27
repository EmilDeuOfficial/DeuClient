/**
 * Modpacks: Übersicht aller Modpacks und Detailansicht eines Modpacks.
 * Mods, Resourcepacks und Shader werden ausschließlich hier - innerhalb eines Modpacks - hinzugefügt.
 */
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { Instance, InstanceStats, ScreenshotInfo, WorldInfo } from '../../shared/types';
import { api } from '../api';
import { Toggle } from '../components/Common';
import { confirmDialog } from '../components/Confirm';
import { ContentManager } from '../components/Content';
import { Icon, type IconName } from '../components/Icons';
import { InstanceIcon } from '../components/InstanceIcon';
import { useApp } from '../store';
import { InstanceEditor } from './Instances';
import { GAME_MODE_KEYS, WorldEditor } from './WorldEditor';

export function formatPlayTime(ms?: number): string {
  if (!ms) return '0 min';
  const min = Math.round(ms / 60000);
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  return `${h} h ${min % 60} min`;
}

export function Modpacks() {
  const { packId, instances } = useApp();
  const inst = packId ? instances.find((i) => i.id === packId) : undefined;
  return inst ? <ModpackDetail inst={inst} /> : <ModpackList />;
}

/* ====================== Übersicht ====================== */

function ModpackList() {
  const { t, instances, selectedInstance, openPack, launch, launching, game, accounts, toast } = useApp();
  const [editing, setEditing] = useState(false);
  const [core, setCore] = useState<string[]>([]);
  useEffect(() => void api.app.deuCore().then((i) => setCore(i?.minecraft ?? [])), []);

  const importPack = async () => {
    try {
      const inst = await api.modrinth.importMrpack();
      if (inst) {
        toast(`${inst.name} ✓`, 'success');
        openPack(inst.id);
      }
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-6 flex items-end justify-between gap-6">
        <div>
          <h2 className="font-display text-3xl font-black text-accent-gradient">{t('pack.title')}</h2>
          <p className="mt-1 text-sm text-ink-200/60">{t('pack.subtitle')}</p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button className="btn-ghost" onClick={importPack}>
            <Icon name="upload" size={16} /> {t('inst.import')}
          </button>
          <button className="btn-accent" onClick={() => setEditing(true)}>
            <Icon name="plus" size={16} /> {t('inst.new')}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-5">
        {instances.map((inst, i) => {
          const selected = inst.id === selectedInstance?.id;
          const hasCore = inst.loader === 'fabric' && core.includes(inst.mcVersion);
          return (
            <div
              key={inst.id}
              style={{ animationDelay: `${i * 40}ms` }}
              onClick={() => openPack(inst.id)}
              className={`card card-hover group relative flex cursor-pointer flex-col overflow-hidden ${selected ? 'border-accent-400/50' : ''}`}
            >
              {/* Banner */}
              <div className="relative h-24 overflow-hidden bg-ink-800">
                {inst.isDefault && <span className="badge absolute left-4 top-3 bg-accent-400/90 text-ink-950">✦ {t('inst.default')}</span>}
              </div>
              <div className="relative -mt-9 flex items-end gap-3 px-5">
                <InstanceIcon icon={inst.icon} size={64} className="rounded-xl ring-2 ring-ink-850" />
                <div className="min-w-0 pb-1">
                  <div className="truncate font-display text-lg font-bold">{inst.name}</div>
                  <div className="flex flex-wrap gap-1.5">
                    <span className="badge bg-white/10 text-ink-100">{inst.mcVersion}</span>
                    <span className={`badge ${inst.loader === 'fabric' ? 'bg-accent-400/15 text-accent-200' : 'bg-white/10 text-ink-100'}`}>{inst.loader}</span>
                    {hasCore && <span className="badge bg-ink-300/20 text-ink-100">DeuCore</span>}
                  </div>
                </div>
              </div>
              <p className="mx-5 mt-3 line-clamp-2 h-8 overflow-hidden text-ellipsis text-xs leading-4 text-ink-200/60">{inst.description ?? `${inst.loader === 'fabric' ? 'Fabric' : 'Vanilla'}-Modpack für Minecraft ${inst.mcVersion}`}</p>
              <div className="mx-5 mt-3 flex items-center gap-4 text-[11px] text-ink-200/55">
                <span className="flex items-center gap-1"><Icon name="clock" size={12} /> {formatPlayTime(inst.playTimeMs)}</span>
                <span className="truncate">{inst.lastPlayed ? new Date(inst.lastPlayed).toLocaleDateString() : t('home.never')}</span>
              </div>
              <div className="mt-4 flex gap-2 border-t border-white/[0.06] p-4" onClick={(e) => e.stopPropagation()}>
                <button
                  className="btn-accent flex-1"
                  disabled={launching || game.running}
                  onClick={() => (accounts.activeUuid ? launch(inst.id) : toast(t('home.loginFirst'), 'error'))}
                >
                  <Icon name="play" size={14} /> {t('inst.play')}
                </button>
                <button className="btn-ghost" onClick={() => openPack(inst.id)}>
                  <Icon name="puzzle" size={15} /> {t('pack.open')}
                </button>
              </div>
            </div>
          );
        })}

        <button
          onClick={() => setEditing(true)}
          className="flex min-h-[260px] flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-white/10 text-ink-200/60 transition hover:border-accent-400/50 hover:bg-accent-400/5 hover:text-accent-200"
        >
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/5">
            <Icon name="plus" size={28} />
          </span>
          <span className="font-semibold">{t('inst.new')}</span>
        </button>
      </div>

      <InstanceEditor open={editing} instance={null} onClose={() => setEditing(false)} />
    </div>
  );
}

/* ====================== Detailansicht ====================== */

type Tab = 'overview' | 'mod' | 'resourcepack' | 'shader' | 'worlds' | 'screenshots' | 'settings';

function ModpackDetail({ inst }: { inst: Instance }) {
  const { t, openPack, launch, launching, game, accounts, toast, updateSettings, selectedInstance } = useApp();
  const [tab, setTab] = useState<Tab>('overview');
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    if (selectedInstance?.id !== inst.id) void updateSettings({ selectedInstance: inst.id });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inst.id]);

  const tabs: { id: Tab; label: string; icon: IconName }[] = [
    { id: 'overview', label: t('pack.overview'), icon: 'home' },
    { id: 'mod', label: t('mods.mods'), icon: 'puzzle' },
    { id: 'resourcepack', label: t('mods.resourcepacks'), icon: 'image' },
    { id: 'shader', label: t('mods.shaders'), icon: 'sun' },
    { id: 'worlds', label: t('pack.worlds'), icon: 'globe' },
    { id: 'screenshots', label: t('pack.screenshots'), icon: 'image' },
    { id: 'settings', label: t('pack.settings'), icon: 'settings' },
  ];

  const play = () => (accounts.activeUuid ? launch(inst.id) : toast(t('home.loginFirst'), 'error'));

  return (
    <div className="mx-auto max-w-6xl">
      <button className="mb-4 flex items-center gap-1.5 text-sm font-semibold text-ink-200/70 transition hover:text-accent-200" onClick={() => openPack(null)}>
        <Icon name="chevronRight" size={14} className="rotate-180" /> {t('pack.back')}
      </button>

      {/* Kopf */}
      <section className="card relative mb-5 overflow-hidden">
        <div className="relative flex items-center gap-6 p-7">
          <InstanceIcon icon={inst.icon} size={96} className="rounded-2xl" />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <h2 className="truncate font-display text-3xl font-black text-accent-gradient">{inst.name}</h2>
              {inst.isDefault && <span className="badge bg-accent-400/90 text-ink-950">✦ {t('inst.default')}</span>}
            </div>
            <p className="mt-1 max-w-2xl text-sm text-ink-100/70">{inst.description ?? `${inst.loader === 'fabric' ? 'Fabric' : 'Vanilla'} · Minecraft ${inst.mcVersion}`}</p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              <span className="badge bg-white/10 text-ink-100">Minecraft {inst.mcVersion}</span>
              <span className="badge bg-accent-400/15 text-accent-200">{inst.loader}</span>
              <span className="badge bg-white/10 text-ink-100"><Icon name="clock" size={11} className="mr-1" />{formatPlayTime(inst.playTimeMs)}</span>
            </div>
          </div>
          <div className="flex shrink-0 flex-col gap-2">
            <button className="btn-accent !px-8 !py-3 text-base" disabled={launching || game.running} onClick={play}>
              <Icon name="play" size={18} /> {t('home.play')}
            </button>
            <div className="flex gap-2">
              <button className="btn-ghost flex-1" onClick={() => api.instances.openFolder(inst.id)} title={t('inst.folder')}>
                <Icon name="folder" size={15} />
              </button>
              <button className="btn-ghost flex-1" onClick={() => setEditing(true)} title={t('inst.edit')}>
                <Icon name="edit" size={15} />
              </button>
            </div>
          </div>
        </div>
        {/* Tabs */}
        <div className="relative flex gap-1 overflow-x-auto border-t border-white/[0.06] px-4">
          {tabs.map((x) => (
            <button
              key={x.id}
              onClick={() => setTab(x.id)}
              className={`flex shrink-0 items-center gap-1.5 border-b-2 px-4 py-3 text-sm font-semibold transition ${
                tab === x.id ? 'border-accent-400 text-accent-200' : 'border-transparent text-ink-200/60 hover:text-white'
              }`}
            >
              <Icon name={x.icon} size={14} /> {x.label}
            </button>
          ))}
        </div>
      </section>

      <div key={tab} className="animate-fade-up">
        {tab === 'overview' && <Overview inst={inst} onTab={setTab} />}
        {(tab === 'mod' || tab === 'resourcepack' || tab === 'shader') && <ContentManager inst={inst} type={tab} />}
        {tab === 'worlds' && <Worlds inst={inst} />}
        {tab === 'screenshots' && <Screenshots inst={inst} />}
        {tab === 'settings' && <PackSettings inst={inst} onEdit={() => setEditing(true)} />}
      </div>

      <InstanceEditor open={editing} instance={inst} onClose={() => setEditing(false)} />
    </div>
  );
}

function StatCard({ icon, label, value, onClick }: { icon: IconName; label: string; value: ReactNode; onClick?: () => void }) {
  return (
    <button onClick={onClick} disabled={!onClick} className="card card-hover flex items-center gap-3 p-4 text-left disabled:cursor-default">
      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-400/15 text-accent-300">
        <Icon name={icon} size={18} />
      </span>
      <div>
        <div className="font-display text-xl font-black">{value}</div>
        <div className="text-[11px] font-semibold uppercase tracking-wider text-ink-200/55">{label}</div>
      </div>
    </button>
  );
}

function Overview({ inst, onTab }: { inst: Instance; onTab: (t: Tab) => void }) {
  const { t } = useApp();
  const [stats, setStats] = useState<InstanceStats | null>(null);
  const [managed, setManaged] = useState<Awaited<ReturnType<typeof api.instances.managedMods>>>([]);
  useEffect(() => {
    void api.instances.stats(inst.id).then(setStats);
    void api.instances.managedMods(inst.id).then(setManaged);
  }, [inst.id]);

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-3 gap-4 xl:grid-cols-6">
        <StatCard icon="puzzle" label={t('mods.mods')} value={stats?.mods ?? '-'} onClick={() => onTab('mod')} />
        <StatCard icon="image" label={t('mods.resourcepacks')} value={stats?.resourcepacks ?? '-'} onClick={() => onTab('resourcepack')} />
        <StatCard icon="sun" label={t('mods.shaders')} value={stats?.shaders ?? '-'} onClick={() => onTab('shader')} />
        <StatCard icon="globe" label={t('pack.worlds')} value={stats?.worlds ?? '-'} onClick={() => onTab('worlds')} />
        <StatCard icon="image" label={t('pack.screenshots')} value={stats?.screenshots ?? '-'} onClick={() => onTab('screenshots')} />
        <StatCard icon="package" label={t('pack.size')} value={stats ? `${stats.sizeMb} MB` : '-'} />
      </div>

      {managed.length > 0 && (
        <section className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-4">
            <div>
              <div className="font-display font-bold text-accent-200">{t('pack.deuMods')}</div>
              <div className="text-xs text-ink-200/60">{t('pack.deuModsHint')}</div>
            </div>
            <span className="badge shrink-0 bg-accent-400/15 text-accent-200">Rechts-Shift</span>
          </div>
          <div className="grid grid-cols-2 gap-px bg-white/[0.04]">
            {managed.map((m) => (
              <div key={m.file} className="flex items-start gap-3 bg-ink-850/80 p-4">
                <span className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${m.core ? 'bg-accent-400/20 text-accent-300' : 'bg-ink-400/25 text-ink-100'}`}>
                  <Icon name={m.core ? 'sparkle' : 'star'} size={16} />
                </span>
                <div className="min-w-0">
                  <div className="text-sm font-bold">{m.name} <span className="font-mono text-[10px] text-ink-200/45">v{m.version}</span>{m.removed && <span className="badge ml-2 bg-red-400/15 text-red-200">{t('pack.removed')}</span>}</div>
                  <div className="line-clamp-2 text-xs text-ink-200/60">{m.description ?? 'Kernmod: Hauptmenü, Ladebildschirm, Mod-Menü (Rechts-Shift), Capes, Flügel und integriertes HUD.'}</div>
                </div>
                {m.removed && (
                  <button className="btn-ghost ml-auto !px-3 !py-1.5 text-xs" onClick={() => void api.instances.restoreManaged(inst.id, m.file).then(() => api.instances.managedMods(inst.id).then(setManaged))}>
                    <Icon name="refresh" size={13} /> {t('pack.restore')}
                  </button>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="card flex items-center justify-between gap-4 p-5">
        <div>
          <div className="font-bold">{t('mods.mods')} {t('pack.add')}</div>
          <div className="text-xs text-ink-200/60">{t('pack.subtitle')}</div>
        </div>
        <div className="flex gap-2">
          <button className="btn-accent" onClick={() => onTab('mod')}><Icon name="plus" size={15} /> {t('mods.mods')}</button>
          <button className="btn-ghost" onClick={() => onTab('resourcepack')}><Icon name="plus" size={15} /> {t('mods.resourcepacks')}</button>
          <button className="btn-ghost" onClick={() => onTab('shader')}><Icon name="plus" size={15} /> {t('mods.shaders')}</button>
        </div>
      </section>
    </div>
  );
}

function Worlds({ inst }: { inst: Instance }) {
  const { t, game, toast } = useApp();
  const [worlds, setWorlds] = useState<WorldInfo[] | null>(null);
  const [editing, setEditing] = useState<WorldInfo | null>(null);
  const reload = useCallback(() => void api.instances.worlds(inst.id).then(setWorlds), [inst.id]);
  useEffect(reload, [reload, game.running]);
  // solange dieses Modpack läuft, gehören die Welten dem Spiel
  const busy = game.running && game.instanceId === inst.id;

  const remove = async (w: WorldInfo) => {
    const message = t('world.confirmDelete').replace('{name}', w.name);
    if (!(await confirmDialog(message, { danger: true, confirmLabel: t('inst.delete') }))) return;
    try {
      await api.instances.deleteWorld(inst.id, w.folder);
      toast(`${w.name} ✓`, 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    }
    reload();
  };

  return (
    <div className="card overflow-hidden">
      <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-3.5">
        <div className="text-sm font-semibold text-ink-100/80">{worlds?.length ?? 0} {t('pack.worlds')}</div>
        <button className="btn-ghost !py-1.5 text-xs" onClick={() => api.instances.openFolder(inst.id, 'saves')}>
          <Icon name="folder" size={13} /> {t('mods.openFolder')}
        </button>
      </div>
      {busy && <div className="border-b border-white/[0.06] px-5 py-2.5 text-xs text-amber-200/80">{t('world.busy')}</div>}
      {worlds && !worlds.length && <div className="py-14 text-center text-sm text-ink-200/50">{t('pack.noWorlds')}</div>}
      <div className="divide-y divide-white/[0.05]">
        {worlds?.map((w) => (
          <div key={w.folder} className="flex items-center gap-4 px-5 py-3">
            {w.icon ? <img src={w.icon} alt="" className="h-12 w-12 rounded-lg" style={{ imageRendering: 'pixelated' }} /> : <InstanceIcon icon="grass" size={48} />}
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold">{w.name}</div>
              <div className="text-xs text-ink-200/50">
                {new Date(w.lastPlayed).toLocaleString()} · {w.sizeMb} MB
                {w.hardcore ? ` · ${t('world.hardcore')}` : w.gameMode !== undefined && GAME_MODE_KEYS[w.gameMode] ? ` · ${t(GAME_MODE_KEYS[w.gameMode])}` : ''}
              </div>
            </div>
            <div className="flex shrink-0 gap-2">
              <button className="btn-ghost !py-1.5 text-xs" disabled={busy} onClick={() => setEditing(w)}>
                <Icon name="edit" size={13} /> {t('world.editShort')}
              </button>
              <button className="btn-icon !h-8 !w-8" title={t('mods.openFolder')} onClick={() => api.instances.openFolder(inst.id, `saves/${w.folder}`)}>
                <Icon name="folder" size={14} />
              </button>
              <button className="btn-icon !h-8 !w-8 hover:!text-red-300" title={t('inst.delete')} disabled={busy} onClick={() => void remove(w)}>
                <Icon name="trash" size={14} />
              </button>
            </div>
          </div>
        ))}
      </div>
      <WorldEditor instanceId={inst.id} world={editing} onClose={() => setEditing(null)} onSaved={reload} />
    </div>
  );
}

function Screenshots({ inst }: { inst: Instance }) {
  const { t } = useApp();
  const [shots, setShots] = useState<ScreenshotInfo[] | null>(null);
  useEffect(() => void api.instances.screenshots(inst.id).then(setShots), [inst.id]);
  return (
    <div>
      <div className="mb-4 flex justify-end">
        <button className="btn-ghost !py-1.5 text-xs" onClick={() => api.instances.openFolder(inst.id, 'screenshots')}>
          <Icon name="folder" size={13} /> {t('mods.openFolder')}
        </button>
      </div>
      {shots && !shots.length && <div className="card py-14 text-center text-sm text-ink-200/50">{t('pack.noScreenshots')}</div>}
      <div className="grid grid-cols-3 gap-4">
        {shots?.map((s) => (
          <button key={s.file} onClick={() => api.instances.openScreenshot(inst.id, s.file)} className="card card-hover overflow-hidden text-left">
            <img src={s.dataUrl} alt="" className="aspect-video w-full object-cover" />
            <div className="truncate px-3 py-2 text-[11px] text-ink-200/60">{new Date(s.time).toLocaleString()}</div>
          </button>
        ))}
      </div>
    </div>
  );
}

function PackSettings({ inst, onEdit }: { inst: Instance; onEdit: () => void }) {
  const { t, settings, toast, openPack } = useApp();
  const [ram, setRam] = useState(inst.ramMb ?? settings?.ramMb ?? 4096);
  const [total, setTotal] = useState(8192);
  useEffect(() => void api.app.systemInfo().then((s) => setTotal(s.totalMemMb)), []);
  const maxRam = Math.max(2048, total - 1024);

  const saveRam = useCallback(async (value: number | undefined) => {
    await api.instances.update(inst.id, { ramMb: value });
  }, [inst.id]);

  const remove = async () => {
    if (!(await confirmDialog(t('inst.confirmDelete'), { danger: true, confirmLabel: t('inst.delete') }))) return;
    try {
      await api.instances.remove(inst.id);
      openPack(null);
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  return (
    <div className="space-y-5">
      <section className="card space-y-4 p-6">
        <div className="flex items-center justify-between">
          <div className="font-bold">{t('pack.ram')}</div>
          <label className="flex items-center gap-2 text-xs font-semibold text-ink-100/80">
            {t('pack.ramGlobal')} <Toggle checked={!inst.ramMb} onChange={(v) => void saveRam(v ? undefined : ram)} />
          </label>
        </div>
        <div className={inst.ramMb ? '' : 'pointer-events-none opacity-40'}>
          <div className="mb-2 text-right font-mono text-lg font-bold text-accent-300">{(ram / 1024).toFixed(1)} GB</div>
          <input
            type="range"
            className="deu-range w-full"
            min={1024}
            max={maxRam}
            step={256}
            value={ram}
            style={{ ['--fill' as string]: `${((ram - 1024) / (maxRam - 1024)) * 100}%` }}
            onChange={(e) => setRam(+e.target.value)}
            onMouseUp={() => void saveRam(ram)}
          />
        </div>
      </section>

      <section className="card flex items-center justify-between gap-4 p-6">
        <div>
          <div className="font-bold">{t('inst.edit')}</div>
          <div className="text-xs text-ink-200/60">{t('pack.versionWarn')}</div>
        </div>
        <button className="btn-ghost" onClick={onEdit}><Icon name="edit" size={15} /> {t('inst.edit')}</button>
      </section>

      {!inst.isDefault && (
        <section className="card flex items-center justify-between gap-4 border-red-400/20 p-6">
          <div>
            <div className="font-bold text-red-200">{t('pack.danger')}</div>
            <div className="text-xs text-ink-200/60">{t('inst.confirmDelete')}</div>
          </div>
          <button className="btn-danger" onClick={remove}><Icon name="trash" size={15} /> {t('inst.delete')}</button>
        </section>
      )}
    </div>
  );
}
