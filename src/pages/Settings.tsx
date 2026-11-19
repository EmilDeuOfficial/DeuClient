/** Einstellungen: RAM, Java, JVM-Argumente, Auflösung, Verhalten nach dem Start, Sprache, Accounts. */
import { useEffect, useState, type ReactNode } from 'react';
import type { JavaInstall, SystemInfo } from '../../shared/types';
import { api } from '../api';
import { Toggle } from '../components/Common';
import { NumberInput } from '../components/Controls';
import { Icon, type IconName } from '../components/Icons';
import { SkinHead } from '../components/SkinHead';
import { useApp } from '../store';

function Section({ icon, title, children }: { icon: IconName; title: string; children: ReactNode }) {
  return (
    <section className="card animate-fade-up p-6">
      <h3 className="mb-5 flex items-center gap-2.5 font-display text-lg font-bold">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-400/15 text-accent-300">
          <Icon name={icon} size={17} />
        </span>
        {title}
      </h3>
      <div className="space-y-5">{children}</div>
    </section>
  );
}

const JVM_PRESETS = {
  default: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200',
  performance:
    '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1',
  lowmem: '-XX:+UseSerialGC -XX:TieredStopAtLevel=1',
} as const;

export function Settings() {
  const { t, settings, updateSettings, accounts, setAccounts, toast, update } = useApp();
  const [sys, setSys] = useState<SystemInfo | null>(null);
  const [javas, setJavas] = useState<JavaInstall[] | null>(null);
  const [downloading, setDownloading] = useState<number | null>(null);
  const [core, setCore] = useState<{ version: string; minecraft: string[] } | null>(null);

  useEffect(() => {
    void api.app.systemInfo().then(setSys);
    void api.java.list().then(setJavas);
    void api.app.divineCore().then(setCore);
  }, []);

  if (!settings) return null;
  const maxRam = Math.max(2048, (sys?.totalMemMb ?? 8192) - 1024);
  const ramPct = ((settings.ramMb - 1024) / (maxRam - 1024)) * 100;

  const downloadJava = async (major: number) => {
    setDownloading(major);
    try {
      const j = await api.java.download(major);
      toast(`Java ${j.version} ✓`, 'success');
      setJavas(await api.java.list());
    } catch (e) {
      toast((e as Error).message, 'error');
    } finally {
      setDownloading(null);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6 pb-28">
      <h2 className="font-display text-3xl font-black text-accent-gradient">{t('set.title')}</h2>

      <Section icon="cpu" title={t('set.game')}>
        <div>
          <div className="mb-3 flex items-end justify-between">
            <label className="label !mb-0">{t('set.ram')}</label>
            <span className="font-mono text-lg font-bold text-accent-300">{(settings.ramMb / 1024).toFixed(1)} GB</span>
          </div>
          <input
            type="range"
            className="deu-range w-full"
            min={1024}
            max={maxRam}
            step={256}
            value={settings.ramMb}
            style={{ ['--fill' as string]: `${ramPct}%` }}
            onChange={(e) => updateSettings({ ramMb: +e.target.value })}
          />
          <div className="mt-1.5 flex justify-between text-[11px] text-ink-200/45">
            <span>1 GB</span>
            <span>{sys ? `${(sys.totalMemMb / 1024).toFixed(0)} GB System` : ''}</span>
          </div>
        </div>

        <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-3">
          <div>
            <label className="label">{t('set.resolution')}</label>
            <NumberInput min={320} max={7680} step={10} value={settings.width} disabled={settings.fullscreen} onChange={(v) => updateSettings({ width: v })} />
          </div>
          <div>
            <NumberInput min={240} max={4320} step={10} value={settings.height} disabled={settings.fullscreen} onChange={(v) => updateSettings({ height: v })} />
          </div>
          <label className="flex items-center gap-2 pb-2.5 text-sm font-semibold">
            <Toggle checked={settings.fullscreen} onChange={(v) => updateSettings({ fullscreen: v })} /> {t('set.fullscreen')}
          </label>
        </div>

        <div>
          <label className="label">{t('set.jvm')}</label>
          <textarea className="input min-h-[70px] resize-y font-mono text-xs" value={settings.jvmArgs} onChange={(e) => updateSettings({ jvmArgs: e.target.value })} spellCheck={false} />
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-ink-200/60">{t('set.preset')}:</span>
            {(['default', 'performance', 'lowmem'] as const).map((k) => (
              <button key={k} className={`chip ${settings.jvmArgs === JVM_PRESETS[k] ? 'chip-active' : ''}`} onClick={() => updateSettings({ jvmArgs: JVM_PRESETS[k] })}>
                {t(`set.preset.${k}` as const)}
              </button>
            ))}
          </div>
        </div>
      </Section>

      <Section icon="coffee" title={t('set.java')}>
        <p className="-mt-2 text-sm text-ink-200/60">{t('set.javaHint')}</p>
        <div className="grid grid-cols-2 gap-2">
          <button onClick={() => updateSettings({ javaPath: '' })} className={`rounded-xl border px-4 py-3 text-left transition ${!settings.javaPath ? 'border-accent-400/70 bg-accent-400/10' : 'border-white/10 hover:border-white/25'}`}>
            <div className="font-bold">{t('set.javaAuto')}</div>
            <div className="text-xs text-ink-200/60">Java 8 / 17 / 21 / 25</div>
          </button>
          <button
            onClick={async () => {
              const p = await api.java.pick();
              if (p) void updateSettings({ javaPath: p });
            }}
            className={`rounded-xl border px-4 py-3 text-left transition ${settings.javaPath ? 'border-accent-400/70 bg-accent-400/10' : 'border-white/10 hover:border-white/25'}`}
          >
            <div className="font-bold">{t('set.javaManual')}</div>
            <div className="truncate font-mono text-xs text-ink-200/60">{settings.javaPath || `${t('set.javaBrowse')}...`}</div>
          </button>
        </div>
        <div>
          <label className="label">{t('set.javaFound')}</label>
          <div className="max-h-48 space-y-1.5 overflow-y-auto">
            {javas === null && <div className="text-sm text-ink-200/50">{t('common.loading')}</div>}
            {javas?.map((j) => (
              <button
                key={j.path}
                onClick={() => updateSettings({ javaPath: j.path })}
                className={`flex w-full items-center gap-3 rounded-lg border px-3 py-2 text-left text-xs transition ${settings.javaPath === j.path ? 'border-accent-400/60 bg-accent-400/10' : 'border-white/[0.06] bg-ink-950/40 hover:border-white/20'}`}
              >
                <span className="badge bg-ink-400/30 text-ink-100">Java {j.major}</span>
                <span className="font-mono text-ink-100/80">{j.version}</span>
                <span className="text-ink-200/45">{j.source}</span>
                <span className="ml-auto truncate font-mono text-[10px] text-ink-200/35">{j.path}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-ink-200/60">{t('set.javaDownload')}:</span>
          {[8, 17, 21, 25].map((m) => (
            <button key={m} className="btn-ghost !px-3 !py-1.5 text-xs" disabled={downloading !== null} onClick={() => downloadJava(m)}>
              <Icon name="download" size={12} className={downloading === m ? 'animate-bounce' : ''} /> Java {m}
            </button>
          ))}
        </div>
      </Section>

      <Section icon="monitor" title={t('set.afterLaunch')}>
        <div className="grid grid-cols-3 gap-2">
          {(['keep', 'minimize', 'close'] as const).map((k) => (
            <button
              key={k}
              onClick={() => updateSettings({ afterLaunch: k })}
              className={`rounded-xl border px-4 py-3 text-sm font-bold transition ${settings.afterLaunch === k ? 'border-accent-400/70 bg-accent-400/10 text-accent-100' : 'border-white/10 text-ink-100/70 hover:border-white/25'}`}
            >
              {t(k === 'keep' ? 'set.keep' : k === 'minimize' ? 'set.minimize' : 'set.close')}
            </button>
          ))}
        </div>
        <label className="flex items-center justify-between text-sm font-semibold">
          {t('set.console')} <Toggle checked={settings.showConsole} onChange={(v) => updateSettings({ showConsole: v })} />
        </label>
        <div>
          <div className="mb-2 flex justify-between">
            <label className="label !mb-0">{t('set.parallel')}</label>
            <span className="font-mono text-sm font-bold text-accent-300">{settings.parallelDownloads}</span>
          </div>
          <input
            type="range"
            className="deu-range w-full"
            min={2}
            max={32}
            value={settings.parallelDownloads}
            style={{ ['--fill' as string]: `${((settings.parallelDownloads - 2) / 30) * 100}%` }}
            onChange={(e) => updateSettings({ parallelDownloads: +e.target.value })}
          />
        </div>
      </Section>

      <Section icon="globe" title={t('set.language')}>
        <div className="grid grid-cols-2 gap-2">
          {([['de', '🇩🇪 Deutsch'], ['en', '🇬🇧 English']] as const).map(([code, label]) => (
            <button
              key={code}
              onClick={() => updateSettings({ language: code })}
              className={`rounded-xl border px-4 py-3 text-sm font-bold transition ${settings.language === code ? 'border-accent-400/70 bg-accent-400/10 text-accent-100' : 'border-white/10 text-ink-100/70 hover:border-white/25'}`}
            >
              {label}
            </button>
          ))}
        </div>
      </Section>

      <Section icon="settings" title={t('set.general')}>
        <label className="flex items-center justify-between text-sm font-semibold">
          {t('set.startup')} <Toggle checked={settings.launchOnStartup} onChange={(v) => updateSettings({ launchOnStartup: v })} />
        </label>
        <label className="flex items-center justify-between text-sm font-semibold">
          {t('set.animations')} <Toggle checked={settings.animations} onChange={(v) => updateSettings({ animations: v })} />
        </label>
        <label className="flex items-center justify-between text-sm font-semibold">
          {t('set.particles')} <Toggle checked={settings.particles} onChange={(v) => updateSettings({ particles: v })} />
        </label>
        <div>
          <label className="label">{t('set.priority')}</label>
          <div className="grid grid-cols-2 gap-2">
            {(['normal', 'high'] as const).map((k) => (
              <button
                key={k}
                onClick={() => updateSettings({ gamePriority: k })}
                className={`rounded-xl border px-4 py-3 text-sm font-bold transition ${settings.gamePriority === k ? 'border-accent-400/70 bg-accent-400/10 text-accent-100' : 'border-white/10 text-ink-100/70 hover:border-white/25'}`}
              >
                {t(k === 'normal' ? 'set.priority.normal' : 'set.priority.high')}
              </button>
            ))}
          </div>
        </div>
      </Section>

      <Section icon="folder" title={t('set.maintenance')}>
        <div className="flex flex-wrap gap-2">
          <button className="btn-ghost" onClick={() => api.console.openLogsFolder()}>
            <Icon name="folder" size={15} /> {t('set.openLogs')}
          </button>
          <button
            className="btn-ghost"
            onClick={async () => {
              const mb = await api.app.clearCache();
              toast(`${t('set.cacheCleared')}: ${mb}`, 'success');
            }}
          >
            <Icon name="trash" size={15} /> {t('set.clearCache')}
          </button>
        </div>
      </Section>

      <Section icon="users" title={t('set.accounts')}>
        {accounts.accounts.length === 0 && <div className="text-sm text-ink-200/50">{t('account.none')}</div>}
        {accounts.accounts.map((a) => (
          <div key={a.uuid} className="flex items-center gap-3 rounded-xl border border-white/[0.06] bg-ink-950/40 px-4 py-3">
            <SkinHead url={a.skinUrl} size={36} />
            <div className="min-w-0 flex-1">
              <div className="font-bold">{a.name}</div>
              <div className="truncate font-mono text-[11px] text-ink-200/45">{a.uuid}</div>
            </div>
            {a.uuid === accounts.activeUuid ? (
              <span className="badge bg-accent-400/20 text-accent-200">Aktiv</span>
            ) : (
              <button className="btn-ghost !py-1.5 text-xs" onClick={async () => setAccounts(await api.accounts.select(a.uuid))}>{t('inst.select')}</button>
            )}
            <button className="btn-danger !py-1.5 text-xs" onClick={async () => setAccounts(await api.accounts.remove(a.uuid))}>
              <Icon name="logout" size={13} /> {t('account.remove')}
            </button>
          </div>
        ))}
        <button
          className="btn-accent"
          onClick={async () => {
            try {
              setAccounts(await api.accounts.add());
            } catch (e) {
              toast((e as Error).message, 'error');
            }
          }}
        >
          <Icon name="plus" size={15} /> {t('account.add')}
        </button>
      </Section>

      <Section icon="sparkle" title={t('set.about')}>
        <div className="grid grid-cols-2 gap-3 text-sm">
          <Info label="DeuClient" value={`v${sys?.appVersion ?? '1.0.0'}`} />
          <Info label="DeuCore" value={core ? `v${core.version} · MC ${core.minecraft.join(', ')}` : '–'} />
          <Info label="System" value={sys?.platform ?? '–'} />
          <Info label="Daten" value={sys?.dataDir ?? '–'} mono />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <button className="btn-ghost" disabled={update.state === 'checking' || update.state === 'downloading'} onClick={() => void api.update.check()}>
            <Icon name="refresh" size={15} className={update.state === 'checking' ? 'animate-spin' : ''} /> {t('update.check')}
          </button>
          <span className="text-sm text-ink-200/70">
            {update.state === 'checking' && t('update.checking')}
            {update.state === 'none' && (update.error ?? t('update.none'))}
            {update.state === 'downloading' && `${t('update.downloading')} ${update.version ?? ''} (${update.percent ?? 0}%)`}
            {update.state === 'ready' && t('update.ready')}
            {update.state === 'error' && <span className="text-red-300">{update.error}</span>}
          </span>
          {update.state === 'ready' && (
            <button className="btn-accent" onClick={() => void api.update.install()}>{t('update.install')} {update.version}</button>
          )}
        </div>
        <button className="btn-ghost" onClick={() => api.app.openDataFolder()}>
          <Icon name="folder" size={15} /> {t('set.dataFolder')}
        </button>
      </Section>
    </div>
  );
}

function Info({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-xl border border-white/[0.06] bg-ink-950/40 px-4 py-3">
      <div className="text-[10px] font-bold uppercase tracking-wider text-ink-200/50">{label}</div>
      <div className={`mt-0.5 select-text truncate ${mono ? 'font-mono text-xs' : 'font-semibold'}`}>{value}</div>
    </div>
  );
}
