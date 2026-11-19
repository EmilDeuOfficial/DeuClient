/** Dialog zum Erstellen und Bearbeiten eines Modpacks (Name, Icon, Loader, Minecraft-Version). */
import { useEffect, useMemo, useState } from 'react';
import type { Instance, LoaderType, McVersion } from '../../shared/types';
import { api } from '../api';
import { Modal, Toggle } from '../components/Common';
import { Select } from '../components/Controls';
import { Icon } from '../components/Icons';
import { ICON_KEYS, InstanceIcon } from '../components/InstanceIcon';
import { useApp } from '../store';

export function InstanceEditor({ open, instance, onClose }: { open: boolean; instance: Instance | null; onClose: () => void }) {
  const { t, toast } = useApp();
  const [name, setName] = useState('');
  const [icon, setIcon] = useState('grass');
  const [mcVersion, setMcVersion] = useState('26.3');
  const [loader, setLoader] = useState<LoaderType>('fabric');
  const [snapshots, setSnapshots] = useState(false);
  const [versions, setVersions] = useState<McVersion[]>([]);
  const [fabricVersions, setFabricVersions] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(instance?.name ?? '');
    setIcon(instance?.icon ?? 'grass');
    setMcVersion(instance?.mcVersion ?? '26.3');
    setLoader(instance?.loader ?? 'fabric');
  }, [open, instance]);

  useEffect(() => {
    if (!open) return;
    void api.versions.list(snapshots).then(setVersions).catch(() => setVersions([]));
    void api.versions.fabric().then((v) => setFabricVersions(new Set(v))).catch(() => undefined);
  }, [open, snapshots]);

  // Bei Fabric nur Versionen anbieten, die Fabric unterstützt
  const options = useMemo(
    () => (loader === 'fabric' && fabricVersions.size ? versions.filter((v) => fabricVersions.has(v.id)) : versions),
    [versions, fabricVersions, loader],
  );

  const save = async () => {
    setSaving(true);
    try {
      if (instance) await api.instances.update(instance.id, { name, icon, mcVersion, loader });
      else await api.instances.create({ name, icon, mcVersion, loader });
      toast(`${name || 'Instanz'} ✓`, 'success');
      onClose();
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={instance ? t('inst.edit') : t('inst.new')}>
      <div className="space-y-5 p-6">
        <div>
          <label className="label">{t('inst.name')}</label>
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Mein Survival" autoFocus />
        </div>
        <div>
          <label className="label">{t('inst.icon')}</label>
          <div className="flex flex-wrap gap-2">
            {ICON_KEYS.map((k) => (
              <button
                key={k}
                onClick={() => setIcon(k)}
                className={`rounded-xl border-2 p-1.5 transition ${icon === k ? 'border-accent-400 bg-accent-400/10 shadow-accent' : 'border-transparent hover:border-white/20'}`}
              >
                <InstanceIcon icon={k} size={36} />
              </button>
            ))}
          </div>
        </div>
        <div>
          <label className="label">{t('inst.loader')}</label>
          <div className="grid grid-cols-2 gap-2">
            {(['fabric', 'vanilla'] as const).map((l) => (
              <button
                key={l}
                disabled={!!instance?.isDefault}
                onClick={() => setLoader(l)}
                className={`rounded-xl border px-4 py-3 text-left transition disabled:opacity-50 ${loader === l ? 'border-accent-400/70 bg-accent-400/10' : 'border-white/10 hover:border-white/25'}`}
              >
                <div className="font-bold capitalize">{l}</div>
                <div className="text-xs text-ink-200/60">{l === 'fabric' ? 'Mods + DeuCore' : 'Reines Minecraft'}</div>
              </button>
            ))}
          </div>
        </div>
        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="label !mb-0">{t('inst.version')}</label>
            <label className="flex items-center gap-2 text-xs text-ink-200/70">
              {t('inst.snapshots')} <Toggle checked={snapshots} onChange={setSnapshots} />
            </label>
          </div>
          <Select
            value={mcVersion}
            onChange={setMcVersion}
            disabled={!!instance?.isDefault}
            options={[
              ...(options.some((v) => v.id === mcVersion) ? [] : [{ value: mcVersion, label: mcVersion }]),
              ...options.map((v) => ({ value: v.id, label: v.id, sub: v.type === 'snapshot' ? 'Snapshot' : undefined })),
            ]}
          />
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button className="btn-ghost" onClick={onClose}>{t('inst.cancel')}</button>
          <button className="btn-accent" onClick={save} disabled={saving || !name.trim()}>
            <Icon name="check" size={15} /> {instance ? t('inst.save') : t('inst.create')}
          </button>
        </div>
      </div>
    </Modal>
  );
}
