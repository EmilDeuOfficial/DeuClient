# DeuClient

![Version](https://img.shields.io/badge/version-v1.4.0-22C55E)
![Platform](https://img.shields.io/badge/platform-Windows%2010%2F11-1C1C1C)
![Language](https://img.shields.io/badge/language-TypeScript-1C1C1C)
![License](https://img.shields.io/badge/license-proprietary-22C55E)

DeuClient is a Minecraft launcher for Windows with its own Fabric client mod. It handles the Microsoft login, Fabric, Java and mods.

## Features

- Official Microsoft login (Microsoft, Xbox, XSTS, Minecraft), several accounts can be saved and switched, tokens are encrypted with Electron `safeStorage` (Windows DPAPI). There is no offline login
- Dark design with a green accent, defined in `tailwind.config.js` and `src/index.css`
- Splash screen, frameless window with its own title bar, sidebar (Home, Modpacks, Servers, Shop, Extras, Settings), blurred background with particles
- Home dashboard: play button with modpack selection, statistics (play time, modpacks, mods) and recently played modpacks
- Modpacks with an overview, mods, resourcepacks, shaders, worlds, screenshots and settings per modpack (own RAM, name, icon, version)
- Mods are added per modpack through Modrinth (search, filters, detail page, dependencies, updates), `.mrpack` import
- Default modpack on Minecraft 26.3 (Fabric) with DeuCore (includes the HUD) and the modules Discord and Scout, Fabric API is installed automatically
- New modpacks start with the settings from `resources/default-profile/` (options.txt and mod configs)
- Bundled mods can be deleted per modpack and restored from the modpack overview
- Extras: hand-picked fair mods (more FPS, better visuals, comfort), one-click install, no cheats
- Own start implementation with parallel downloads and SHA1 check, Java is detected or downloaded (Temurin), play time tracking, console window
- Shop: capes and wings with preview, pixel editor for own capes (10x16 front), PNG upload (64x32 / 128x64) or any image converted to a cape
- Server hosting on your own PC: Paper or Vanilla, start and stop, live console, player list, UPnP port forwarding on request
- Setup installer (NSIS) with selectable install folder, desktop and Start menu shortcuts
- German and English user interface

### DeuCore (Fabric client mod in `mod/`, internal name DivineCore)

- Visual client only, no cheats
- Green and black design, main menu and loading screen with the DeuClient logo, mod menu on Right Shift (favorites, search, categories)
- Client features: F3 name, logo next to the name, zoom, crosshair
- Capes and wings
- Available for Minecraft 26.3 (Java 25) and 1.21.4 (Java 21)

## Requirements

| Dependency | Purpose |
| --- | --- |
| Windows 10 or 11 (x64) | Operating system |
| Node.js 20 or newer | Build only (tested with Node 24) |
| JDK 25 or newer | Build of the DeuCore mods only (26.3 needs Java 25, 1.21.4 is built with it too) |
| Azure app registration | Optional, own client ID for the Microsoft login |

## Usage

### Installer

1. Run `DeuClient-Setup.exe`. Choose the install folder, the installer creates a desktop shortcut and a Start menu entry.
2. Start DeuClient, sign in with Microsoft and press "Spielen".

### Build it yourself

1. Everything at once (mods, plugin and launcher):

   ```powershell
   .\tools\build.ps1
   ```

2. Launcher only (output in `release\`):

   ```bash
   npm install
   npm run build
   ```

3. Mods only:

   ```powershell
   cd mod\mc26.3;   .\gradlew.bat build
   cd ..\mc1.21.4;  .\gradlew.bat build
   ```

### Development

```bash
npm run start       # builds and starts the launcher locally
npm run dev         # Vite dev server with hot reload for the interface
npm run typecheck   # TypeScript check for interface and main process
```

### Azure client ID

Without an own client ID the launcher uses the public Microsoft Live client, like many open-source launchers. For an own launcher create an Azure app:

1. In the [Azure portal](https://portal.azure.com) open Microsoft Entra ID, App registrations, New registration.
2. Name `DeuClient`, account type "Personal Microsoft accounts only".
3. Redirect URI, platform "Public client/native": `https://login.microsoftonline.com/common/oauth2/nativeclient`
4. Under Authentication turn on "Allow public client flows".
5. Copy the application (client) ID and set it in [`electron/auth/config.ts`](electron/auth/config.ts) or start with the environment variable `DEUCLIENT_AZURE_CLIENT_ID`.
6. New Azure apps must be approved by Mojang for the Minecraft API: [Minecraft Java Edition API access](https://aka.ms/mce-reviewappid). Until then the login reports that the app is not approved yet.
7. Build the launcher again.

### Test mode

For automatic tests, developers only:

- `DEUCLIENT_DATA_DIR` uses another data folder
- `DEUCLIENT_DRY_RUN=1` prepares a launch without starting Java
- `DEUCLIENT_SCREENSHOT=<file.png>` saves a screenshot of the interface and exits
- Mod: JVM argument `-Ddivinecore.demo=true` opens menus one after another and saves screenshots

## File Structure

```text
DeuClient/
├── .gitignore
├── LICENSE
├── README.md
├── package.json            Scripts, dependencies and installer configuration
├── package-lock.json
├── index.html              Entry of the interface
├── vite.config.mts         Vite build (PostCSS, strict content security policy)
├── tailwind.config.js      Theme: colors (ink, accent), shadows, animations
├── tsconfig.json           Interface (renderer)
├── img/
│   ├── emblem.svg          Emblem, source of all icons
│   ├── logo.png            DeuClient logo (in-game loading screen and main menu)
│   ├── wordmark.svg
│   ├── installer-header.svg
│   └── installer-sidebar.svg
├── tools/
│   ├── build.ps1                Builds mods, plugin and launcher in one step
│   ├── generate-icons.cjs       Renders icon, ICO and installer images from img/
│   ├── generate-mod-assets.cjs  Renders the in-game logo, emblem and icons from img/
│   ├── generate-cosmetics.cjs   Generates cape and wing textures
│   ├── generate-ingame-ui.cjs   Generates in-game UI assets
│   └── embed-mod.cjs            Copies the built mods into resources/mods
├── public/                 Static files of the interface (icon, shop previews)
├── resources/
│   ├── mods/               Embedded mods per Minecraft version and manifest.json
│   ├── bundled-mods/       Modules Discord and Scout (26.3)
│   ├── default-profile/    Default options.txt and mod configs for new modpacks
├── shared/
│   └── types.ts            Types shared by main process and interface
├── electron/               Main process
│   ├── tsconfig.json       Main process and preload
│   ├── main.ts             Window, IPC, console window
│   ├── preload.ts          Safe bridge to the interface (window.divine)
│   ├── auth/               Microsoft, Xbox, XSTS and Minecraft login, account storage
│   ├── game/               Versions, Fabric, Java, launch, instances, mod sync
│   ├── modrinth.ts         Modrinth API v2 (search, install, updates, .mrpack)
│   ├── servers.ts          Local server hosting
│   ├── upnp.ts             Port forwarding
│   ├── capes.ts            Cape and wings selection, own capes
│   ├── settings.ts
│   └── util/               Downloads (parallel, SHA1), paths, storage
├── src/                    Interface (React)
│   ├── main.tsx            Entry
│   ├── App.tsx             Layout and pages
│   ├── ConsoleApp.tsx      Console window
│   ├── api.ts              Typed bridge to the preload API
│   ├── store.tsx           State
│   ├── i18n.ts             German and English texts
│   ├── index.css           Base style and components
│   ├── components/         Splash, title bar, sidebar, background, shared widgets
│   └── pages/              Home, Modpacks, Instances, Servers, Shop, Extras, Settings
└── mod/                    DeuCore Fabric mod (internal name DivineCore)
    ├── mc26.3/             Minecraft 26.3 (unobfuscated, Mojang names, Java 25)
    └── mc1.21.4/           Minecraft 1.21.4 (Yarn mappings, Java 21)
```

Building also creates `dist/`, `dist-electron/` and `release/` (not in the repository). The installer is `release\DeuClient-Setup.exe`.

Everything the launcher stores is under `%APPDATA%\.deuclient`:

- `instances/`: modpacks, each with its own game folder
- `shared/`: versions, libraries and assets shared by all modpacks
- `runtime/`: Java versions downloaded by the launcher
- `settings.json`: settings
- `accounts.json`: accounts (tokens encrypted)

An existing `%APPDATA%\.divinelauncher` folder from the earlier Divine Launcher is moved to `.deuclient` on the first start.

## Releases and automatic updates

The installer is published as a GitHub release. Installed launchers check for a new release after the start (and every few hours), download it in the background and install it when the launcher is closed, or right away with "Restart" in the sidebar or in the settings.

To publish a new version:

1. Raise `version` in `package.json`.
2. `npm run build` creates `release\DeuClient-Setup.exe` and `release\latest.yml`.
3. `npm run release` creates the GitHub release `v<version>` with these files (needs the GitHub CLI, logged in).

Updates only work in the installed version, not in development.

## Changelog

### v1.4.0

- Launcher renamed from Divine Launcher to DeuClient
- New design: black with a green accent
- Repository restructured: launcher at the root, `img/` for branding, `tools/` for build scripts
- Shop: HD capes, no more Divine/DeuClient capes, wings fit completely, own color picker, no hover movement
- Extras: More Culling, BadOptimizations, ModernFix, Packet Fixer, C2ME, Model Gap Fix, Chat Heads, REI, Controlling, Reese's Sodium Options, Sodium Extra, Simple Voice Chat, Xaero's World Map, Full Brightness Toggle
- Settings: start with Windows, animations and particles on/off, game priority, JVM profiles, clear cache and logs
- DeuCore: GUI scale 2.5 (default) in the mod menu; new modpacks start with music at 0% and without the welcome screen
- Automatic updates from GitHub releases; the license is now proprietary (all rights reserved)
- Navigation: "Extras" is now "Recommended", new icons for Shop and Recommended
- Launcher design is flatter: no gradients, glow effects, background scene or decorative animations
- DivineBridge server plugin removed completely
- Launcher: own dropdowns, checkboxes and number fields instead of the Windows controls, vector modpack icons, nothing is cut off at small window sizes
- The mod is now called DeuCore and includes the HUD; Spotify is removed, modules are called Discord and Scout
- Repository tidied: build script in `tools/`, TypeScript config of the main process in `electron/`, PostCSS inlined into the Vite config, bundled mods in `resources/`
- In-game design (loading screen, main menu, buttons, menus) is now green and black with the DeuClient logo instead of the Mojang Studios screen
- Server address removed from the launcher and the mod (no pinned server, no join button)
- The console window closes when Minecraft exits normally and stays open after a crash
- Setup installer is now `DeuClient-Setup.exe` in `release\`
- Data folder is now `%APPDATA%\.deuclient`, the old folder is migrated automatically
- Environment variables for tests renamed from `DIVINE_*` to `DEUCLIENT_*`

## License

Proprietary, all rights reserved. The launcher may be used, but not copied, modified or redistributed. See the [LICENSE](LICENSE) file.

## About

DeuClient is a Minecraft launcher for Windows with its own Fabric client mod, written in TypeScript with Electron and React.

Topics: `minecraft`, `minecraft-launcher`, `fabric`, `electron`, `react`, `typescript`, `windows`
