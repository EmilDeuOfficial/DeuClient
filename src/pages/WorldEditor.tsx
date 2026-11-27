/** Dialog zum Bearbeiten einer Welt (Name, Spielmodus, Schwierigkeit, Cheats, Spielregeln). */
import { useEffect, useState } from 'react';
import type { WorldDifficulty, WorldInfo, WorldRule, WorldSettings } from '../../shared/types';
import { api } from '../api';
import { Modal, Toggle } from '../components/Common';
import { Select } from '../components/Controls';
import { Icon } from '../components/Icons';
import type { TKey } from '../i18n';
import { useApp } from '../store';

export const GAME_MODE_KEYS: TKey[] = ['world.survival', 'world.creative', 'world.adventure', 'world.spectator'];
const DIFFICULTIES: WorldDifficulty[] = ['peaceful', 'easy', 'normal', 'hard'];

function Row({ label, hint, checked, onChange }: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-3 rounded-xl border border-white/10 px-3.5 py-2.5">
      <div className="min-w-0">
        <div className="text-sm font-semibold">{label}</div>
        {hint && <div className="text-[11px] text-ink-200/55">{hint}</div>}
      </div>
      <Toggle checked={checked} onChange={onChange} />
    </label>
  );
}

export function WorldEditor({ instanceId, world, onClose, onSaved }: { instanceId: string; world: WorldInfo | null; onClose: () => void; onSaved: () => void }) {
  const { t, toast } = useApp();
  const [value, setValue] = useState<WorldSettings | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setValue(null);
    if (!world) return;
    let current = true;
    api.instances
      .worldSettings(instanceId, world.folder)
      .then((s) => current && setValue(s))
      .catch((e) => {
        toast((e as Error).message, 'error');
        onClose();
      });
    return () => {
      current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instanceId, world?.folder]);

  const set = (patch: Partial<WorldSettings>) => setValue((v) => (v ? { ...v, ...patch } : v));

  const save = async () => {
    if (!world || !value) return;
    setSaving(true);
    try {
      await api.instances.saveWorld(instanceId, world.folder, value);
      toast(`${value.name} ✓`, 'success');
      onSaved();
      onClose();
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={!!world} onClose={onClose} title={t('world.edit')}>
      {value && (
        <div className="space-y-5 p-6">
          <div>
            <label className="label">{t('world.name')}</label>
            <input className="input" value={value.name} maxLength={64} onChange={(e) => set({ name: e.target.value })} autoFocus />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">{t('world.gameMode')}</label>
              <Select
                value={String(value.gameMode)}
                onChange={(v) => set({ gameMode: Number(v) })}
                options={GAME_MODE_KEYS.map((k, i) => ({ value: String(i), label: t(k) }))}
              />
            </div>
            <div>
              <label className="label">{t('world.difficulty')}</label>
              <Select
                value={value.difficulty}
                onChange={(difficulty) => set({ difficulty })}
                options={DIFFICULTIES.map((d) => ({ value: d, label: t(`world.${d}`) }))}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Row label={t('world.cheats')} checked={value.allowCommands} onChange={(allowCommands) => set({ allowCommands })} />
            <Row label={t('world.hardcore')} checked={value.hardcore} onChange={(hardcore) => set({ hardcore })} />
            <Row label={t('world.locked')} checked={value.difficultyLocked} onChange={(difficultyLocked) => set({ difficultyLocked })} />
          </div>
          {Object.keys(value.rules).length > 0 && (
            <div>
              <label className="label">{t('world.rules')}</label>
              <div className="grid grid-cols-2 gap-2">
                {(Object.keys(value.rules) as WorldRule[]).map((rule) => (
                  <Row
                    key={rule}
                    label={t(`world.rule.${rule}`)}
                    checked={!!value.rules[rule]}
                    onChange={(on) => set({ rules: { ...value.rules, [rule]: on } })}
                  />
                ))}
              </div>
            </div>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <button className="btn-ghost" onClick={onClose}>{t('inst.cancel')}</button>
            <button className="btn-accent" onClick={save} disabled={saving || !value.name.trim()}>
              <Icon name="check" size={15} /> {t('inst.save')}
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}
