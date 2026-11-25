/**
 * Extras: handverlesene, faire Mods (Performance, Optik, Komfort),
 * die mit einem Klick in ein Fabric-Modpack installiert werden. Bewusst keine Cheats.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ModrinthProject } from '../../shared/types';
import { api } from '../api';
import { formatNumber, ModIcon } from '../components/Common';
import { Select } from '../components/Controls';
import { InstanceIcon } from '../components/InstanceIcon';
import { Icon, type IconName } from '../components/Icons';
import { useApp } from '../store';
import type { TKey } from '../i18n';

const CATEGORIES: { id: string; title: TKey; desc: TKey; icon: IconName; color: string; mods: string[] }[] = [
  { id: 'perf', title: 'extras.performance', desc: 'extras.performanceDesc', icon: 'cpu', color: 'from-emerald-400/25', mods: ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'entityculling', 'moreculling', 'badoptimizations', 'modernfix', 'packet-fixer', 'c2me-fabric'] },
  { id: 'visual', title: 'extras.visual', desc: 'extras.visualDesc', icon: 'sun', color: 'from-accent-400/25', mods: ['iris', 'continuity', '3dskinlayers', 'not-enough-animations', 'modelfix', 'chat-heads'] },
  { id: 'comfort', title: 'extras.comfort', desc: 'extras.comfortDesc', icon: 'heart', color: 'from-ink-300/30', mods: ['modmenu', 'xaeros-minimap', 'xaeros-world-map', 'zoomify', 'appleskin', 'jade', 'mouse-tweaks', 'shulkerboxtooltip', 'rei', 'controlling', 'reeses-sodium-options', 'sodium-extra', 'simple-voice-chat', 'full-brightness-toggle'] },
];

export function Extras() {
  const { t, instances, selectedInstance, toast } = useApp();
  const fabricPacks = instances.filter((i) => i.loader === 'fabric');
  const [targetId, setTargetId] = useState<string>('');
  const target = fabricPacks.find((i) => i.id === targetId) ?? fabricPacks.find((i) => i.id === selectedInstance?.id) ?? fabricPacks[0];
  const [projects, setProjects] = useState<Record<string, ModrinthProject>>({});
  const [installed, setInstalled] = useState<Set<string>>(new Set());
  const [available, setAvailable] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState<string | null>(null);

  const allSlugs = useMemo(() => CATEGORIES.flatMap((c) => c.mods), []);

  // Projektinfos (Icon, Name, Beschreibung) einmalig laden
  useEffect(() => {
    void api.modrinth.projects(allSlugs).then((list) => {
      const map: Record<string, ModrinthProject> = {};
      for (const p of list) map[p.slug] = p;
      setProjects(map);
    }).catch(() => undefined);
  }, [allSlugs]);

  const refreshInstalled = useCallback(async () => {
    if (!target) return;
    const items = await api.modrinth.installed(target.id, 'mod');
    setInstalled(new Set(items.map((i) => i.projectId).filter(Boolean) as string[]));
  }, [target]);

  // Installierte Mods + Verfügbarkeit für die Version des Ziel-Modpacks
  useEffect(() => {
    if (!target) return;
    void refreshInstalled();
    setAvailable({});
    for (const slug of allSlugs) {
      void api.modrinth.versions(slug, ['fabric'], [target.mcVersion])
        .then((v) => setAvailable((a) => ({ ...a, [slug]: v.length > 0 })))
        .catch(() => setAvailable((a) => ({ ...a, [slug]: false })));
    }
  }, [target, allSlugs, refreshInstalled]);

  const install = async (slugs: string[]) => {
    if (!target) return;
    for (const slug of slugs) {
      const p = projects[slug];
      if (!p || installed.has(p.id) || available[slug] === false) continue;
      setBusy(slug);
      try {
        await api.modrinth.install(target.id, p.id, 'mod');
        toast(`${p.title} → ${target.name}`, 'success');
      } catch (e) {
        toast(`${p.title}: ${(e as Error).message}`, 'error');
      }
    }
    setBusy(null);
    await refreshInstalled();
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex items-end justify-between gap-6">
        <div>
          <h2 className="font-display text-3xl font-black text-accent-gradient">{t('extras.title')}</h2>
          <p className="mt-1 max-w-2xl text-sm text-ink-200/60">{t('extras.subtitle')}</p>
        </div>
        {target && (
          <label className="flex shrink-0 items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-ink-200/60">{t('extras.target')}</span>
            <Select className="!w-60" value={target.id} onChange={setTargetId} options={fabricPacks.map((i) => ({ value: i.id, label: i.name, sub: i.mcVersion, icon: <InstanceIcon icon={i.icon} size={26} /> }))} />
          </label>
        )}
      </div>

      {!target && <div className="card p-8 text-center text-ink-200/60">{t('extras.needsFabric')}</div>}

      {target && CATEGORIES.map((cat) => {
        const missing = cat.mods.filter((s) => projects[s] && !installed.has(projects[s].id) && available[s] !== false);
        return (
          <section key={cat.id} className="card overflow-hidden">
            <div className={`flex items-center justify-between bg-white/[0.02] px-6 py-4`}>
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-black/30 text-accent-200">
                  <Icon name={cat.icon} size={19} />
                </span>
                <div>
                  <div className="font-display text-lg font-bold">{t(cat.title)}</div>
                  <div className="text-xs text-ink-100/70">{t(cat.desc)}</div>
                </div>
              </div>
              <button className="btn-accent" disabled={!missing.length || busy !== null} onClick={() => install(missing)}>
                <Icon name={missing.length ? 'download' : 'check'} size={15} /> {missing.length ? `${t('extras.installAll')} (${missing.length})` : t('extras.allInstalled')}
              </button>
            </div>
            <div className="grid grid-cols-2 gap-px bg-white/[0.04] xl:grid-cols-3">
              {cat.mods.map((slug) => {
                const p = projects[slug];
                const isInstalled = p ? installed.has(p.id) : false;
                const avail = available[slug];
                return (
                  <div key={slug} className="flex items-center gap-3 bg-ink-850/85 p-4">
                    {p ? <ModIcon url={p.icon_url} className="h-12 w-12 rounded-xl" /> : <div className="h-12 w-12 animate-pulse rounded-xl bg-ink-700" />}
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-bold">{p?.title ?? slug}</div>
                      <div className="line-clamp-2 text-[11px] text-ink-200/60">{p?.description ?? '…'}</div>
                      {p && <div className="mt-0.5 text-[10px] text-ink-200/40"><Icon name="download" size={10} className="mr-0.5 inline" />{formatNumber(p.downloads)}</div>}
                    </div>
                    {isInstalled ? (
                      <span className="badge bg-emerald-400/15 text-emerald-200"><Icon name="check" size={11} className="mr-1" />{t('mods.isInstalled')}</span>
                    ) : avail === false ? (
                      <span className="badge bg-white/5 text-ink-200/50">nicht für {target.mcVersion}</span>
                    ) : (
                      <button className="btn-ghost !px-3 !py-1.5 text-xs" disabled={!p || busy !== null} onClick={() => install([slug])}>
                        <Icon name="download" size={12} className={busy === slug ? 'animate-bounce' : ''} /> {busy === slug ? t('mods.installing') : t('mods.install')}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
