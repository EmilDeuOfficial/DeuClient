/**
 * Startseite: großer SPIELEN-Button mit Instanzauswahl, Statistik und Schnellzugriff.
 */
import { useEffect, useState } from 'react';
import emblem from '../assets/emblem.png';
import { api } from '../api';
import { Icon } from '../components/Icons';
import { Select } from '../components/Controls';
import { InstanceIcon } from '../components/InstanceIcon';
import { useApp } from '../store';
import { formatPlayTime } from './Modpacks';

const NEW_PACK = '__new__';

export function Home() {
  const { t, accounts, instances, selectedInstance, updateSettings, launch, launching, game, toast, openPack } = useApp();
  const [packMods, setPackMods] = useState<number | null>(null);
  useEffect(() => {
    if (selectedInstance) void api.instances.stats(selectedInstance.id).then((s) => setPackMods(s.mods));
  }, [selectedInstance]);
  const totalPlay = instances.reduce((sum, i) => sum + (i.playTimeMs ?? 0), 0);
  const recent = [...instances].sort((a, b) => (b.lastPlayed ?? 0) - (a.lastPlayed ?? 0)).slice(0, 3);
  const active = accounts.accounts.find((a) => a.uuid === accounts.activeUuid);
  const busy = launching || game.running;

  const play = () => {
    if (!active) return toast(t('home.loginFirst'), 'error');
    if (!selectedInstance) return;
    void launch(selectedInstance.id);
  };

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      {/* ---------- Hero ---------- */}
      <section className="card relative overflow-hidden px-10 py-10">
        <div className="absolute right-12 top-1/2 hidden -translate-y-1/2 min-[1120px]:block">
          <img src={emblem} alt="" className="h-44 w-44 " draggable={false} />
        </div>

        <div className="relative max-w-xl">
          <div className="text-xs font-bold uppercase tracking-[0.4em] text-ink-200/60">
            {active ? `${t('home.welcome')}, ${active.name}` : t('home.welcomeGuest')}
          </div>
          <h1 className="mt-2 font-display text-6xl font-black leading-none tracking-wide text-accent-gradient">
            DeuClient
          </h1>

          <div className="mt-9 flex items-stretch gap-3">
            {/* SPIELEN */}
            <button
              onClick={play}
              disabled={busy || !selectedInstance}
              className="group relative flex h-[72px] min-w-[260px] items-center justify-center gap-3 rounded-2xl bg-accent-500 hover:bg-accent-400 px-10 font-display text-2xl font-black tracking-[0.25em] text-ink-950 transition duration-300 hover:brightness-110 disabled:cursor-not-allowed disabled:opacity-70"
            >
              <Icon name="play" size={26} className="transition" />
              {game.running ? t('home.playing') : launching ? t('home.launching') : t('home.play')}
            </button>

            {/* Instanzauswahl */}
            <Select
              value={selectedInstance?.id ?? ''}
              listMinWidth={288}
              onChange={(id) => {
                if (id === NEW_PACK) openPack(null);
                else void updateSettings({ selectedInstance: id });
              }}
              options={[
                ...instances.map((i) => ({ value: i.id, label: i.name, sub: `${i.mcVersion} · ${i.loader}`, icon: <InstanceIcon icon={i.icon} size={30} /> })),
                { value: NEW_PACK, label: t('inst.new'), icon: <Icon name="plus" size={16} className="mx-1.5 text-accent-300" /> },
              ]}
              renderTrigger={() => (
                <span className="flex h-full min-w-[210px] items-center gap-3 rounded-2xl border border-white/10 bg-ink-950/60 px-4 text-left transition hover:border-accent-400/40">
                  {selectedInstance && <InstanceIcon icon={selectedInstance.icon} size={36} />}
                  <span className="min-w-0 flex-1">
                    <span className="block text-[10px] font-bold uppercase tracking-widest text-ink-200/50">{t('home.instance')}</span>
                    <span className="block truncate text-sm font-bold">{selectedInstance?.name ?? '-'}</span>
                    <span className="block text-[11px] text-ink-200/60">
                      {selectedInstance?.mcVersion} · {selectedInstance?.loader === 'fabric' ? 'Fabric' : 'Vanilla'}
                    </span>
                  </span>
                  <Icon name="chevronDown" size={16} strokeWidth={2.5} className="text-accent-400" />
                </span>
              )}
            />
          </div>
        </div>
      </section>

      {/* ---------- Statistik ---------- */}
      <div className="grid grid-cols-3 gap-4">
        <MiniStat icon="clock" label={t('home.stats.playtime')} value={formatPlayTime(totalPlay)} />
        <MiniStat icon="package" label={t('home.stats.packs')} value={String(instances.length)} onClick={() => openPack(null)} />
        <MiniStat icon="puzzle" label={t('home.stats.mods')} value={packMods === null ? '-' : String(packMods)} onClick={() => selectedInstance && openPack(selectedInstance.id)} />
      </div>

      <div className="grid grid-cols-1 gap-6 min-[1120px]:grid-cols-2">
        <div className="card p-5">
          <div className="mb-3 text-[10px] font-bold uppercase tracking-[0.3em] text-ink-200/60">{t('home.quick')}</div>
          <div className="space-y-2">
            {recent.map((i) => (
              <button key={i.id} onClick={() => openPack(i.id)} className="group flex w-full items-center gap-3 rounded-xl border border-transparent p-2 text-left transition hover:border-accent-400/30 hover:bg-white/[0.04]">
                <InstanceIcon icon={i.icon} size={40} />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-bold">{i.name}</div>
                  <div className="text-[11px] text-ink-200/55">{i.mcVersion} · {formatPlayTime(i.playTimeMs)}</div>
                </div>
                <Icon name="chevronRight" size={16} className="text-ink-200/40 transition group-hover:translate-x-0.5 group-hover:text-accent-300" />
              </button>
            ))}
          </div>
        </div>
        <div className="card p-5">
          <div className="mb-3 text-[10px] font-bold uppercase tracking-[0.3em] text-ink-200/60">{t('home.news')}</div>
          <ul className="space-y-3 text-sm">
            {(['home.feature1', 'home.feature2', 'home.feature3'] as const).map((k) => (
              <li key={k} className="flex items-start gap-2.5">
                <Icon name="sparkle" size={14} className="mt-0.5 shrink-0 text-accent-400" />
                <span className="text-ink-100/80">{t(k)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-accent-400/25 bg-accent-400/10 px-3 py-2 text-xs font-semibold text-accent-100">
            <kbd className="rounded-md border border-accent-400/40 bg-black/30 px-1.5 py-0.5 font-mono text-[10px]">⇧ R-Shift</kbd>
            {t('home.deuMenu')}
          </div>
        </div>
      </div>
    </div>
  );
}

function MiniStat({ icon, label, value, onClick }: { icon: 'clock' | 'package' | 'puzzle'; label: string; value: string; onClick?: () => void }) {
  return (
    <button onClick={onClick} disabled={!onClick} className="card card-hover flex items-center gap-3 px-4 py-3.5 text-left disabled:cursor-default disabled:hover:translate-y-0">
      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-400/15 text-accent-300">
        <Icon name={icon} size={18} />
      </span>
      <div className="min-w-0">
        <div className="truncate font-display text-lg font-black">{value}</div>
        <div className="truncate text-[10px] font-semibold uppercase tracking-wider text-ink-200/55">{label}</div>
      </div>
    </button>
  );
}
