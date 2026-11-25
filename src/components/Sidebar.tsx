/** Seitenleiste mit Navigation: Home, Instanzen, Mods, Einstellungen. */
import { useApp, type Page } from '../store';
import { api } from '../api';
import { APP_VERSION } from '../version';
import { Icon, type IconName } from './Icons';

const ITEMS: { page: Page; icon: IconName; key: 'nav.home' | 'nav.modpacks' | 'nav.servers' | 'nav.shop' | 'nav.extras' | 'nav.settings' }[] = [
  { page: 'home', icon: 'home', key: 'nav.home' },
  { page: 'modpacks', icon: 'package', key: 'nav.modpacks' },
  { page: 'servers', icon: 'globe', key: 'nav.servers' },
  { page: 'shop', icon: 'bag', key: 'nav.shop' },
  { page: 'extras', icon: 'bolt', key: 'nav.extras' },
  { page: 'settings', icon: 'settings', key: 'nav.settings' },
];

export function Sidebar() {
  const { page, setPage, t, game, update } = useApp();
  return (
    <nav className="flex w-[220px] shrink-0 flex-col border-r border-white/[0.06] bg-ink-950/55 px-3 py-5">
      <div className="mb-6 px-3">
        <div className="font-display text-[11px] font-bold uppercase tracking-[0.35em] text-ink-200/50">Menü</div>
      </div>
      <ul className="flex flex-col gap-1.5">
        {ITEMS.map((item) => {
          const active = page === item.page;
          return (
            <li key={item.page}>
              <button
                onClick={() => setPage(item.page)}
                className={`group relative flex w-full items-center gap-3 overflow-hidden rounded-xl px-3.5 py-3 text-sm font-semibold transition-all duration-300 ${
                  active
                    ? 'bg-white/[0.07] text-white'
                    : 'text-ink-100/70 hover:bg-white/[0.05] hover:text-white'
                }`}
              >
                {/* gruene Markierung links */}
                <span
                  className={`absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full bg-accent-400 transition-all duration-300 ${
                    active ? 'opacity-100' : 'scale-y-0 opacity-0'
                  }`}
                />
                <Icon
                  name={item.icon}
                  size={19}
                  className={`transition-transform duration-300 ${active ? 'text-accent-300' : 'group-hover:scale-110 group-hover:text-accent-200'}`}
                />
                {t(item.key)}
              </button>
            </li>
          );
        })}
      </ul>

      <div className="mt-auto space-y-3 px-1">
        {game.running && (
          <button onClick={() => api.console.open()} className="flex w-full items-center gap-2 rounded-xl border border-emerald-400/30 bg-emerald-500/10 px-3 py-2.5 text-xs font-semibold text-emerald-200 transition hover:bg-emerald-500/20">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400" />
            </span>
            Minecraft läuft · {t('launch.console')}
          </button>
        )}
        {update.state === 'downloading' && (
          <div className="rounded-xl border border-white/10 px-3 py-2.5 text-xs text-ink-100/80">
            {t('update.downloading')} {update.percent ?? 0}%
          </div>
        )}
        {update.state === 'ready' && (
          <button onClick={() => void api.update.install()} className="btn-accent w-full !py-2 text-xs">
            {t('update.install')} {update.version}
          </button>
        )}
        <div className="text-center text-[10px] tracking-wider text-ink-200/35">DeuClient v{APP_VERSION}</div>
      </div>
    </nav>
  );
}
