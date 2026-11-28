# DeuClient

![Version](https://img.shields.io/badge/version-v1.0-2EFF7A)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20to%2026.3-1C1C1C)
![Platform](https://img.shields.io/badge/platform-Windows%2010%2F11-1C1C1C)
![License](https://img.shields.io/badge/license-proprietary-22C55E)

DeuClient is a Minecraft launcher for Windows with its own Fabric client mod, **DeuCore**. The launcher takes care of the Microsoft login, Java, Fabric and mods. The mod adds a clean client interface, a movable HUD, cosmetics, zoom, a custom crosshair and a Discord call overlay. It is a visual and comfort client: no cheats.

**Full documentation is in the [Wiki](https://github.com/EmilDeuOfficial/DeuClient/wiki).**

## Features

### Launcher

- Microsoft login with several accounts; tokens are encrypted with Windows DPAPI (Electron `safeStorage`). There is no offline login
- Modpacks with their own game folder, memory, icon and Minecraft version: mods, resource packs, shaders, worlds, screenshots and settings per modpack
- Modrinth built in: search, filters, dependencies, updates and `.mrpack` import
- Fabric modpacks come with Sodium, Lithium, FerriteCore, ImmediatelyFast and EntityCulling preinstalled (a deleted one stays deleted)
- World editor: name, game mode, difficulty, cheats and game rules; deleted worlds go to the Windows recycle bin
- "Recommended" page with hand-picked fair mods for more FPS, better visuals and comfort
- Shop with capes and wings, 3D preview, own capes from a pixel editor or an image
- Host your own Paper or Vanilla server on your PC, with live console, player list and optional UPnP port forwarding
- Theme color for launcher and game, German and English interface
- Finds Java or downloads Eclipse Temurin, parallel downloads with checksum verification
- NSIS installer in a dark design, automatic updates from GitHub releases

### DeuCore (Minecraft 1.21 to 26.3)

- Own main menu, loading screen, pause menu, buttons, tooltips and advancement window
- Mod menu on **Right Shift** with favorites, search and categories
- HUD with nine elements (coordinates, FPS, ping, speed, compass, clock, effects, armor, keystrokes), moved with the mouse (**F8**), with grid and snapping
- Six capes and six animated 3D wings ("Mythos-Kollektion"); the wings can replace the elytra
- Zoom with mouse wheel control, custom crosshair with its own editor
- **Discord**: current call, members, mute and deafen in game, text channels, own friends list
- **Scout**: summary card next to another player's inventory when a server lets you open it (for example `/invsee`)
- German and English, following the Minecraft language

| Minecraft | DeuCore |
| --- | --- |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Full |
| 1.21 to 1.21.11 | Full |
| Other versions | Not available; the launcher still starts them with Fabric |

## Install

1. Run `DeuClient-Setup.exe` (from a [release](https://github.com/EmilDeuOfficial/DeuClient/releases), or build it yourself).
2. Start DeuClient, sign in with Microsoft and press **Spielen** / **Play**.

You need Windows 10 or 11 (x64). Java is handled by the launcher.

## Build from source

| Tool | Needed for |
| --- | --- |
| Node.js 20 or newer | Launcher (tested with Node 24) |
| JDK 25 or newer | DeuCore |
| Git | Getting the source |

Everything in one step (all mod versions, then the launcher and installer):

```powershell
git clone https://github.com/EmilDeuOfficial/DeuClient.git
```

```powershell
cd DeuClient
```

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

The installer is written to `release\DeuClient-Setup.exe`.

Launcher only (uses the DeuCore jars already in `resources/mods/` for `npm run start` and `npm run dev`):

```powershell
npm install
```

```powershell
npm run start
```

| Command | Result |
| --- | --- |
| `npm run dev` | Vite dev server with hot reload for the interface |
| `npm run start` | Builds and starts the launcher |
| `npm run typecheck` | TypeScript check for interface and main process |
| `npm run build` | Icons, embedded mods, launcher and installer (all mod versions must be built first) |
| `npm run release` | Publishes the built installer as a GitHub release |

More in the wiki: [Building](https://github.com/EmilDeuOfficial/DeuClient/wiki/Building), [Development](https://github.com/EmilDeuOfficial/DeuClient/wiki/Development), [Releases](https://github.com/EmilDeuOfficial/DeuClient/wiki/Releases-and-Updates).

## Project layout

```text
electron/    Launcher main process (login, game start, modpacks, Modrinth, servers, worlds)
src/         Launcher interface (React, Tailwind)
shared/      Types shared by both
mod/         DeuCore: one source (mc26) for Minecraft 1.21 to 26.3
resources/   Files shipped with the launcher (DeuCore jars, default settings)
tools/       Build and asset scripts (icons, fonts, capes, wings)
img/         Logo and icon sources
installer/   NSIS installer theme
public/      Static files of the interface
```

## License

Proprietary, all rights reserved. The official builds may be used, but the software may not be copied, modified or redistributed without written permission. See [LICENSE](LICENSE).

DeuClient is an unofficial launcher. It is not approved by or associated with Mojang or Microsoft.
