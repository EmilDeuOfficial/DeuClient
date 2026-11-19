/**
 * Inhalte eines Modpacks (Mods / Resourcepacks / Shader) über Modrinth:
 *  - Installiert: aktivieren/deaktivieren, löschen, Updates prüfen und einspielen
 *  - Hinzufügen: Suche, Kategorien, Loader, MC-Version, Sortierung, Detailansicht mit Versionen
 * Mods werden ausschließlich innerhalb eines Modpacks verwaltet.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ContentType, InstalledContent, Instance, ModrinthCategory, ModrinthHit, ModrinthProject, ModrinthSearchParams, ModrinthVersion } from '../../shared/types';
import { api } from '../api';
import { formatNumber, ModIcon, Modal, Toggle } from './Common';
import { Select } from './Controls';
import { Icon } from './Icons';
import { InstanceIcon } from './InstanceIcon';
import { useApp } from '../store';

/** Kombinierte Ansicht im Modpack: installierte Inhalte + "Hinzufügen" (Modrinth-Suche) */
export function ContentManager({ inst, type }: { inst: Instance; type: ContentType }) {
  const { t } = useApp();
  const [adding, setAdding] = useState(false);
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [version, setVersion] = useState(0);

  const refreshInstalled = useCallback(async () => {
    const items = await api.modrinth.installed(inst.id, type);
    setInstalledIds(new Set(items.map((i) => i.projectId).filter(Boolean) as string[]));
    setVersion((v) => v + 1);
  }, [inst.id, type]);
  useEffect(() => {
    setAdding(false);
    void refreshInstalled();
  }, [refreshInstalled]);

  const label = type === 'mod' ? t('mods.mods') : type === 'shader' ? t('mods.shaders') : t('mods.resourcepacks');
  return (
    <div className="flex flex-col gap-4">
      {type === 'mod' && inst.loader === 'vanilla' && (
        <div className="rounded-xl border border-amber-300/30 bg-amber-400/10 px-4 py-2.5 text-sm text-amber-100">{t('mods.vanillaHint')}</div>
      )}
      {type === 'shader' && <div className="rounded-xl border border-ink-300/30 bg-ink-400/10 px-4 py-2.5 text-sm text-ink-100">{t('mods.shaderHint')}</div>}

      <div className="flex items-center justify-between">
        <div className="text-sm text-ink-200/70">
          {adding ? `${label} ${t('mods.for')} ${inst.name} (${inst.mcVersion}) ${t('pack.addHint')}` : ''}
        </div>
        <button className={adding ? 'btn-ghost' : 'btn-accent'} onClick={() => setAdding((a) => !a)}>
          <Icon name={adding ? 'check' : 'plus'} size={15} /> {adding ? t('pack.doneAdding') : `${label} ${t('pack.add')}`}
        </button>
      </div>

      {adding ? (
        <Discover key={`${inst.id}-${type}`} inst={inst} type={type} installedIds={installedIds} onInstalled={refreshInstalled} />
      ) : (
        <Installed key={`${inst.id}-${type}-${version}`} inst={inst} type={type} onChanged={refreshInstalled} />
      )}
    </div>
  );
}

const TYPES: { type: ContentType; key: 'mods.mods' | 'mods.resourcepacks' | 'mods.shaders' }[] = [
  { type: 'mod', key: 'mods.mods' },
  { type: 'resourcepack', key: 'mods.resourcepacks' },
  { type: 'shader', key: 'mods.shaders' },
];

/* ====================== Entdecken ====================== */

function Discover({ inst, type, installedIds, onInstalled }: { inst: Instance; type: ContentType; installedIds: Set<string>; onInstalled: () => void }) {
  const { t, toast } = useApp();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [index, setIndex] = useState<ModrinthSearchParams['index']>('relevance');
  const [categories, setCategories] = useState<ModrinthCategory[]>([]);
  const [selectedCats, setSelectedCats] = useState<string[]>([]);
  const [onlyCompatible, setOnlyCompatible] = useState(true);
  const [gameVersion, setGameVersion] = useState(inst?.mcVersion ?? '');
  const [loader, setLoader] = useState(inst?.loader === 'fabric' ? 'fabric' : '');
  const [hits, setHits] = useState<ModrinthHit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [installing, setInstalling] = useState<string | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const req = useRef(0);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(query), 350);
    return () => clearTimeout(id);
  }, [query]);

  useEffect(() => {
    void api.modrinth.categories().then((c) => setCategories(c.filter((x) => x.project_type === type && x.header === 'categories'))).catch(() => undefined);
  }, [type]);

  // "Nur kompatibel" übernimmt Version und Loader der Instanz
  useEffect(() => {
    if (onlyCompatible && inst) {
      setGameVersion(inst.mcVersion);
      setLoader(inst.loader === 'fabric' ? 'fabric' : '');
    }
  }, [onlyCompatible, inst]);

  const load = useCallback(
    async (offset: number) => {
      const my = ++req.current;
      setLoading(true);
      setError('');
      try {
        const res = await api.modrinth.search({
          query: debounced, projectType: type, categories: selectedCats, index, offset, limit: 24,
          gameVersion: gameVersion || undefined, loader: type === 'mod' ? loader || undefined : undefined,
        });
        if (my !== req.current) return;
        setHits((h) => (offset === 0 ? res.hits : [...h, ...res.hits]));
        setTotal(res.total_hits);
      } catch (e) {
        if (my === req.current) setError((e as Error).message);
      } finally {
        if (my === req.current) setLoading(false);
      }
    },
    [debounced, type, selectedCats, index, gameVersion, loader],
  );
  useEffect(() => void load(0), [load]);

  const install = async (projectId: string, title: string, versionId?: string) => {
    if (!inst) return;
    setInstalling(projectId);
    try {
      const r = await api.modrinth.install(inst.id, projectId, type, versionId);
      toast(`${t('mods.installedDeps')}: ${r.installed.join(', ') || title}`, 'success');
      onInstalled();
    } catch (e) {
      toast(`${title}: ${(e as Error).message}`, 'error');
    } finally {
      setInstalling(null);
    }
  };

  return (
    <>
      {/* Filterleiste */}
      <div className="card mb-5 space-y-4 p-4">
        <div className="flex gap-3">
          <div className="relative flex-1">
            <Icon name="search" size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-accent-300/70" />
            <input className="input !pl-10" placeholder={t('mods.search')} value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
          <Select
            className="!w-48"
            value={index as string}
            onChange={(v) => setIndex(v as ModrinthSearchParams['index'])}
            options={(['relevance', 'downloads', 'follows', 'newest', 'updated'] as const).map((k) => ({ value: k as string, label: t(`mods.sort.${k}`) }))}
          />
          <input className="input !w-32" value={gameVersion} disabled={onlyCompatible} placeholder={t('mods.version')} onChange={(e) => setGameVersion(e.target.value)} />
          {type === 'mod' && (
            <Select
              className="!w-44"
              value={loader}
              disabled={onlyCompatible}
              onChange={setLoader}
              options={[
                { value: '', label: `${t('mods.loader')}: ${t('mods.any')}` },
                { value: 'fabric', label: 'Fabric' },
                { value: 'quilt', label: 'Quilt' },
                { value: 'forge', label: 'Forge' },
                { value: 'neoforge', label: 'NeoForge' },
              ]}
            />
          )}
          <label className="flex shrink-0 items-center gap-2 text-xs font-semibold text-ink-100/80">
            <Toggle checked={onlyCompatible} onChange={setOnlyCompatible} /> {inst?.mcVersion}
          </label>
        </div>
        {categories.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {categories.map((c) => {
              const on = selectedCats.includes(c.name);
              return (
                <button key={c.name} onClick={() => setSelectedCats((s) => (on ? s.filter((x) => x !== c.name) : [...s, c.name]))} className={`chip ${on ? 'chip-active' : ''}`}>
                  <span className="h-3.5 w-3.5 [&>svg]:h-3.5 [&>svg]:w-3.5" dangerouslySetInnerHTML={{ __html: sanitizeSvg(c.icon) }} />
                  {c.name.replace(/-/g, ' ')}
                </button>
              );
            })}
          </div>
        )}
      </div>

      {error && <div className="mb-4 rounded-xl border border-red-400/30 bg-red-500/10 p-4 text-sm text-red-200">{error}</div>}

      <div className="grid grid-cols-[repeat(auto-fill,minmax(330px,1fr))] gap-4">
        {hits.map((h, i) => {
          const installed = installedIds.has(h.project_id);
          return (
            <div
              key={h.project_id}
              style={{ animationDelay: `${(i % 24) * 25}ms` }}
              onClick={() => setDetail(h.project_id)}
              className="animate-fade-up card card-hover flex cursor-pointer gap-4 p-4"
            >
              <ModIcon url={h.icon_url} className="h-16 w-16 rounded-xl" iconSize={26} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <div className="truncate font-bold">{h.title}</div>
                  <div className="truncate text-[11px] text-ink-200/50">by {h.author}</div>
                </div>
                <div className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-ink-100/65">{h.description}</div>
                <div className="mt-2 flex items-center gap-3 text-[11px] text-ink-200/60">
                  <span className="flex items-center gap-1"><Icon name="download" size={12} /> {formatNumber(h.downloads)}</span>
                  <span className="flex items-center gap-1"><Icon name="heart" size={12} /> {formatNumber(h.follows)}</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      if (!installed) void install(h.project_id, h.title);
                    }}
                    disabled={installing === h.project_id}
                    className={`ml-auto ${installed ? 'btn-ghost !border-emerald-400/30 !text-emerald-200' : 'btn-accent'} !px-3 !py-1.5 text-xs`}
                  >
                    <Icon name={installed ? 'check' : 'download'} size={13} />
                    {installed ? t('mods.isInstalled') : installing === h.project_id ? t('mods.installing') : t('mods.install')}
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {!loading && !hits.length && !error && <div className="py-16 text-center text-ink-200/50">{t('mods.noResults')}</div>}
      {loading && (
        <div className="flex justify-center py-8">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent-400/20 border-t-accent-400" />
        </div>
      )}
      {!loading && hits.length < total && (
        <button className="btn-ghost mx-auto mt-6" onClick={() => load(hits.length)}>
          {t('mods.more')} ({hits.length}/{formatNumber(total)})
        </button>
      )}

      <ProjectDetail inst={inst} id={detail} type={type} onClose={() => setDetail(null)} installed={detail ? installedIds.has(detail) : false} onInstall={install} installing={installing} />
    </>
  );
}

/** Kategorie-Icons von Modrinth sind SVG-Strings – Script/Handler entfernen, bevor sie eingefügt werden */
function sanitizeSvg(svg: string): string {
  if (!svg.trim().startsWith('<svg')) return '';
  return svg.replace(/<script[\s\S]*?<\/script>/gi, '').replace(/\son\w+="[^"]*"/gi, '').replace(/(href|xlink:href)="(?!#)[^"]*"/gi, '');
}

/* ====================== Detailansicht ====================== */

function ProjectDetail({ inst, id, type, onClose, installed, onInstall, installing }: {
  inst: Instance; id: string | null; type: ContentType; onClose: () => void; installed: boolean;
  onInstall: (projectId: string, title: string, versionId?: string) => Promise<void>; installing: string | null;
}) {
  const { t } = useApp();
  const [project, setProject] = useState<ModrinthProject | null>(null);
  const [versions, setVersions] = useState<ModrinthVersion[]>([]);
  const [tab, setTab] = useState<'desc' | 'versions'>('desc');

  useEffect(() => {
    setProject(null);
    setVersions([]);
    setTab('desc');
    if (!id || !inst) return;
    void api.modrinth.project(id).then(setProject);
    const loaders = type === 'mod' ? [inst.loader === 'fabric' ? 'fabric' : 'minecraft'] : type === 'resourcepack' ? ['minecraft'] : undefined;
    void api.modrinth.versions(id, loaders, [inst.mcVersion]).then(setVersions).catch(() => undefined);
  }, [id, inst, type]);

  const html = useMemo(() => (project ? renderMarkdown(project.body) : ''), [project]);

  return (
    <Modal open={!!id} onClose={onClose} width={860}>
      {!project ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-accent-400/20 border-t-accent-400" />
        </div>
      ) : (
        <div>
          <div className="relative overflow-hidden border-b border-white/[0.07] p-6">
            {project.gallery?.[0] && <img src={project.gallery.find((g) => g.featured)?.url ?? project.gallery[0].url} alt="" className="absolute inset-0 h-full w-full object-cover opacity-20" />}
            <div className="absolute inset-0 bg-ink-850/85" />
            <div className="relative flex items-start gap-5">
              <ModIcon url={project.icon_url} className="h-24 w-24 rounded-2xl" iconSize={34} />
              <div className="min-w-0 flex-1">
                <h3 className="font-display text-2xl font-black text-accent-gradient">{project.title}</h3>
                <p className="mt-1 text-sm text-ink-100/75">{project.description}</p>
                <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-ink-200/70">
                  <span className="badge bg-white/10"><Icon name="download" size={11} className="mr-1" />{formatNumber(project.downloads)}</span>
                  <span className="badge bg-white/10"><Icon name="heart" size={11} className="mr-1" />{formatNumber(project.followers)}</span>
                  {project.categories.map((c) => <span key={c} className="badge bg-ink-400/20 text-ink-100">{c}</span>)}
                </div>
              </div>
              <div className="flex shrink-0 flex-col gap-2">
                <button className={installed ? 'btn-ghost !text-emerald-200' : 'btn-accent'} disabled={installed || installing === project.id || !versions.length} onClick={() => onInstall(project.id, project.title)}>
                  <Icon name={installed ? 'check' : 'download'} size={15} />
                  {installed ? t('mods.isInstalled') : installing === project.id ? t('mods.installing') : t('mods.install')}
                </button>
                <button className="btn-ghost text-xs" onClick={() => api.app.openExternal(`https://modrinth.com/${project.project_type}/${project.slug}`)}>
                  <Icon name="external" size={13} /> {t('mods.website')}
                </button>
                <button className="btn-ghost text-xs" onClick={onClose}>{t('common.close')}</button>
              </div>
            </div>
          </div>
          <div className="flex gap-1 border-b border-white/[0.07] px-6">
            {(['desc', 'versions'] as const).map((k) => (
              <button key={k} onClick={() => setTab(k)} className={`border-b-2 px-4 py-3 text-sm font-semibold transition ${tab === k ? 'border-accent-400 text-accent-200' : 'border-transparent text-ink-200/60 hover:text-white'}`}>
                {k === 'desc' ? t('mods.description') : `${t('mods.versions')} (${versions.length})`}
              </button>
            ))}
          </div>
          {tab === 'desc' ? (
            <div className="md select-text p-6 text-sm" dangerouslySetInnerHTML={{ __html: html }} />
          ) : (
            <div className="divide-y divide-white/[0.05] p-3">
              {!versions.length && <div className="p-6 text-center text-sm text-ink-200/50">{t('mods.noResults')}</div>}
              {versions.slice(0, 50).map((v) => (
                <div key={v.id} className="flex items-center gap-3 rounded-lg px-3 py-2.5 hover:bg-white/[0.03]">
                  <span className={`badge ${v.version_type === 'release' ? 'bg-emerald-400/15 text-emerald-200' : v.version_type === 'beta' ? 'bg-amber-300/15 text-amber-200' : 'bg-red-400/15 text-red-200'}`}>{v.version_type}</span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-semibold">{v.name}</div>
                    <div className="truncate text-[11px] text-ink-200/50">{v.loaders.join(', ')} · {v.game_versions.slice(-4).join(', ')} · {new Date(v.date_published).toLocaleDateString()}</div>
                  </div>
                  <button className="btn-ghost !px-3 !py-1.5 text-xs" disabled={installing === project.id} onClick={() => onInstall(project.id, project.title, v.id)}>
                    <Icon name="download" size={12} /> {t('mods.install')}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

/**
 * Sehr einfacher, sicherer Markdown-Renderer für Modrinth-Beschreibungen.
 * HTML wird komplett escaped; nur Überschriften, Fett/Kursiv, Links, Bilder (https), Listen und Code werden umgesetzt.
 */
function renderMarkdown(md: string): string {
  // <img src="…"> aus HTML-Beschreibungen in Markdown-Bilder umwandeln, restliches HTML entfernen
  let src = md
    .replace(/<img[^>]*src="(https:[^"]+)"[^>]*>/gi, '![]($1)')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/?[a-z][^>]*>/gi, '');
  src = src.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const inline = (s: string) =>
    s
      .replace(/!\[[^\]]*\]\((https:[^)\s]+)[^)]*\)/g, '<img src="$1" loading="lazy" />')
      .replace(/\[([^\]]+)\]\((https:[^)\s]+)[^)]*\)/g, '<a href="$2" target="_blank">$1</a>')
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/__([^_]+)__/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>');
  const out: string[] = [];
  let inList = false;
  for (const raw of src.split('\n')) {
    const line = raw.trimEnd();
    const li = /^\s*[-*+]\s+(.*)/.exec(line);
    if (li) {
      if (!inList) out.push('<ul>');
      inList = true;
      out.push(`<li>${inline(li[1])}</li>`);
      continue;
    }
    if (inList) {
      out.push('</ul>');
      inList = false;
    }
    const h = /^(#{1,6})\s+(.*)/.exec(line);
    if (h) out.push(`<h${Math.min(3, h[1].length)}>${inline(h[2])}</h${Math.min(3, h[1].length)}>`);
    else if (line.trim() && !/^[-=_*]{3,}$/.test(line.trim())) out.push(`<p>${inline(line)}</p>`);
  }
  if (inList) out.push('</ul>');
  return out.join('\n');
}

/* ====================== Installiert ====================== */

function Installed({ inst, type, onChanged }: { inst: Instance; type: ContentType; onChanged: () => void }) {
  const { t, toast } = useApp();
  const [items, setItems] = useState<InstalledContent[]>([]);
  const [checking, setChecking] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (inst) setItems(await api.modrinth.installed(inst.id, type));
  }, [inst, type]);
  useEffect(() => void reload(), [reload]);

  const check = async () => {
    if (!inst) return;
    setChecking(true);
    try {
      const res = await api.modrinth.checkUpdates(inst.id, type);
      setItems(res);
      const n = res.filter((r) => r.update).length;
      toast(n ? `${n} Update(s)` : '✓ Alles aktuell', n ? 'info' : 'success');
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setChecking(false);
    }
  };

  const doUpdate = async (it: InstalledContent) => {
    if (!inst || !it.update || !it.projectId) return;
    setBusy(it.fileName);
    try {
      await api.modrinth.update(inst.id, type, it.fileName, it.projectId, it.update.versionId);
      toast(`${it.title} → ${it.update.versionNumber}`, 'success');
      await reload();
      onChanged();
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setBusy(null);
    }
  };

  const act = async (fn: () => Promise<void>) => {
    try {
      await fn();
      await reload();
      onChanged();
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  };

  const updates = items.filter((i) => i.update);

  return (
    <div className="card overflow-hidden">
      <div className="flex items-center justify-between border-b border-white/[0.07] px-5 py-3.5">
        <div className="text-sm font-semibold text-ink-100/80">{items.length} {t(TYPES.find((x) => x.type === type)!.key)}</div>
        <div className="flex gap-2">
          <button className="btn-ghost !py-1.5 text-xs" onClick={() => inst && api.instances.openFolder(inst.id, type === 'mod' ? 'mods' : type === 'shader' ? 'shaderpacks' : 'resourcepacks')}>
            <Icon name="folder" size={13} /> {t('mods.openFolder')}
          </button>
          {updates.length > 1 && (
            <button className="btn-alt !py-1.5 text-xs" onClick={async () => { for (const u of updates) await doUpdate(u); }}>
              <Icon name="refresh" size={13} /> {t('mods.updateAll')}
            </button>
          )}
          <button className="btn-accent !py-1.5 text-xs" onClick={check} disabled={checking}>
            <Icon name="refresh" size={13} className={checking ? 'animate-spin' : ''} /> {t('mods.checkUpdates')}
          </button>
        </div>
      </div>
      {!items.length && <div className="py-14 text-center text-sm text-ink-200/50">{t('mods.empty')}</div>}
      <div className="divide-y divide-white/[0.05]">
        {items.map((it) => (
          <div key={it.fileName} className={`flex items-center gap-4 px-5 py-3 transition hover:bg-white/[0.02] ${!it.enabled ? 'opacity-50' : ''}`}>
            {it.managed ? (
              <InstanceIcon icon="divine" size={40} />
            ) : (
              <ModIcon url={it.iconUrl} className="h-10 w-10 rounded-lg" iconSize={18} />
            )}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="truncate font-semibold">{it.title}</span>
                {it.managed && <span className="badge bg-accent-400/20 text-accent-200">{t('mods.managed')}</span>}
                {it.bundled && <span className="badge bg-ink-300/25 text-ink-100">{t('mods.module')}</span>}
                {it.update && <span className="badge bg-emerald-400/20 text-emerald-200">→ {it.update.versionNumber}</span>}
              </div>
              <div className="truncate font-mono text-[11px] text-ink-200/45">
                {it.description ? `${it.description.slice(0, 90)} · ` : ''}{it.versionNumber ? `${it.versionNumber} · ` : ''}{it.fileName} · {(it.size / 1024 / 1024).toFixed(2)} MB
              </div>
            </div>
            {it.update && (
              <button className="btn-alt !px-3 !py-1.5 text-xs" disabled={busy === it.fileName} onClick={() => doUpdate(it)}>
                <Icon name="download" size={12} /> {t('mods.update')}
              </button>
            )}
            <Toggle checked={it.enabled} disabled={it.managed} onChange={() => act(() => api.modrinth.toggle(inst!.id, type, it.fileName))} />
            <button className="btn-icon hover:!border-red-400/50 hover:!text-red-300" onClick={() => { if ((it.managed || it.bundled) && !confirm(t('mods.confirmManaged'))) return; void act(() => api.modrinth.remove(inst!.id, type, it.fileName)); }}>
              <Icon name="trash" size={15} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
