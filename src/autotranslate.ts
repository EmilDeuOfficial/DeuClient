/**
 * Uebersetzung fuer Texte, die direkt auf Deutsch im Code stehen (Shop, Server, Mod-Beschreibungen ...).
 * Der deutsche Text dient als Schluessel: bei Sprache Englisch werden passende Texte im DOM
 * (Textknoten, placeholder, title) ersetzt. Ausserdem werden lange Gedankenstriche ueberall durch
 * einen kurzen Bindestrich ersetzt. Fuer Dialoge (confirm) gibt es tx().
 */

const EN: Record<string, string> = {
  // ---- Shop
  'Capes und Flügel für den DeuClient - alles kostenlos. Gestalte dein eigenes Cape oder lade ein Bild hoch.': 'Capes and wings for DeuClient - all free. Design your own cape or upload an image.',
  'Capes & Flügel': 'Capes & Wings',
  'Cape-Editor': 'Cape Editor',
  'Menü': 'Menu',
  'Minimieren': 'Minimize',
  'Maximieren': 'Maximize',
  'Schließen': 'Close',
  'Nicht für': 'Not for',
  'Anprobe': 'Try-on',
  'Deine gespeicherte Anmeldung kann auf diesem PC nicht gelesen werden (der Launcher-Ordner stammt vermutlich von einem anderen Windows-Benutzer oder PC). Der Account wurde entfernt - bitte melde dich neu mit Microsoft an.': 'Your saved login cannot be read on this PC (the launcher folder probably comes from another Windows user or PC). The account was removed - please sign in with Microsoft again.',
  'Keine Verbindung zum Internet oder zum Server. Prüfe deine Internetverbindung (und ob eine Firewall den DeuClient blockiert) und versuche es erneut.': 'No connection to the internet or the server. Check your internet connection (and whether a firewall blocks DeuClient) and try again.',
  'Der Speicherplatz ist voll. Mach auf dem Laufwerk Platz frei und versuche es erneut.': 'The disk is full. Free up space on the drive and try again.',
  'Auf eine Datei konnte nicht zugegriffen werden (gesperrt oder keine Berechtigung). Schließe Minecraft und andere Programme, prüfe deinen Virenscanner und versuche es erneut.': 'A file could not be accessed (locked or no permission). Close Minecraft and other programs, check your antivirus and try again.',
  'Zu viele Anfragen in kurzer Zeit. Warte ein paar Minuten und versuche es dann erneut.': 'Too many requests in a short time. Wait a few minutes and try again.',
  'Die Anmeldung wurde abgelehnt. Melde dich neu mit Microsoft an.': 'The login was rejected. Sign in with Microsoft again.',
  'Der Server hat gerade ein Problem. Versuche es in ein paar Minuten erneut.': 'The server has a problem right now. Try again in a few minutes.',
  'Eine heruntergeladene Datei ist beschädigt. Versuche es erneut - der Launcher lädt sie dann neu.': 'A downloaded file is corrupt. Try again - the launcher will download it again.',
  'Java wurde nicht gefunden oder konnte nicht gestartet werden. Der Launcher lädt Java normalerweise selbst herunter; prüfe sonst in den Einstellungen den Java-Pfad.': 'Java was not found or could not be started. The launcher normally downloads Java itself; otherwise check the Java path in the settings.',
  'Minecraft bekommt nicht genug Arbeitsspeicher. Verringere den Arbeitsspeicher in den Einstellungen oder schließe andere Programme.': 'Minecraft does not get enough memory. Reduce the memory in the settings or close other programs.',
  'Löschen': 'Delete',
  'Cape in bester Qualität importiert ✓': 'Cape imported in best quality ✓',
  'PNG im Cape-Format (64×32 oder HD) wird unverändert übernommen - jedes andere Bild wird in bester Cape-Qualität (HD 1024×512) umgewandelt. Auch per Drag & Drop.': 'PNG in cape format (64×32 or HD) is used unchanged - any other image is converted in best cape quality (HD 1024×512). Drag & drop works too.',
  'HD-Cape: für die Bearbeitung wird es auf 128×64 verkleinert': 'HD cape: it is reduced to 128×64 for editing',
  'Vorne': 'Front',
  'Hinten': 'Back',
  'Bearbeiten': 'Edit',
  'Zurücksetzen': 'Reset',
  'Testen': 'Try',
  'Aktiv': 'Active',
  'Bald kaufbar': 'Available soon',
  'So sieht dein Skin mit dem ausgewählten Cape und den Flügeln aus (Rückansicht). Kaufen kannst du sie, sobald die Cosmetics live gehen.': 'This is how your skin looks with the selected cape and wings (back view). You can buy them as soon as the cosmetics go live.',
  'Probiere die DeuClient-Capes und -Flügel an deinem eigenen Skin aus. Kaufen kannst du sie, sobald sie live gehen. Gestalte außerdem dein eigenes Cape oder lade ein Bild hoch.': 'Try the DeuClient capes and wings on your own skin. You can buy them as soon as they go live. You can also design your own cape or upload an image.',
  'DeuClient-Mods einbauen': 'Include DeuClient mods',
  'DeuCore, HUD, Discord und Scout. Ausschalten für ein reines Mod-Modpack mit eigenen Mods.': 'DeuCore, HUD, Discord and Scout. Turn off for a plain modded modpack with your own mods.',
  'nicht für': 'not for',
  'Kostenlos': 'Free',
  'Anlegen': 'Equip',
  'Angelegt': 'Equipped',
  'Sternennacht': 'Starry Night',
  'Flamme': 'Flame',
  'Eiskristall': 'Ice Crystal',
  'Smaragd': 'Emerald',
  'Galaxie-Flügel': 'Galaxy Wings',
  'Engel': 'Angel',
  'Dämon': 'Demon',
  'Eis': 'Ice',
  'Phönix': 'Phoenix',
  'Schatten': 'Shadow',
  'Cape ablegen': 'Remove cape',
  'Flügel ablegen': 'Remove wings',
  'Werkzeug': 'Tool',
  'Stift': 'Pen',
  'Füllen': 'Fill',
  'Radierer': 'Eraser',
  'Pipette': 'Picker',
  'Farbe': 'Color',
  'Eigene Farbe': 'Custom color',
  'Rand / Rückseite': 'Edge / back',
  'Spiegeln': 'Mirror',
  'Auflösung': 'Resolution',
  'Vorlagen': 'Templates',
  'Leer': 'Empty',
  'Streifen': 'Stripes',
  'Verlauf': 'Gradient',
  'Schach': 'Checkers',
  'Rückgängig': 'Undo',
  'Vorschau': 'Preview',
  'Cape speichern': 'Save cape',
  'Eigene Capes werden in allen Modpacks mit DeuCore angelegt.': 'Custom capes are applied in all modpacks with DeuCore.',
  'Cape hochladen': 'Upload cape',
  'PNG im Cape-Format (64×32 / 128×64) wird direkt übernommen - jedes andere Bild wird automatisch zum Cape umgewandelt. Auch per Drag & Drop.':
    'A PNG in cape format (64×32 / 128×64) is used as is - any other image is converted to a cape automatically. Drag & drop works too.',
  'Bild wählen': 'Choose image',
  'Noch keine eigenen Capes - gestalte eins im Cape-Editor oder lade ein Bild hoch.': 'No custom capes yet - design one in the cape editor or upload an image.',
  'Gespeichert - wird in Modpacks mit DeuCore übernommen': 'Saved - will be applied in modpacks with DeuCore',
  'Cape hochgeladen ✓': 'Cape uploaded ✓',
  'Bild ins Cape-Format umgewandelt - du kannst es jetzt noch anpassen': 'Image converted to cape format - you can still adjust it',
  'Bild konnte nicht gelesen werden': 'The image could not be read',
  // ---- Server
  'Deine Server': 'Your servers',
  'Erstelle einen eigenen Minecraft-Server, der direkt auf deinem PC läuft - mit IP für dich, dein Heimnetz und (per Portfreigabe) deine Freunde.':
    'Create your own Minecraft server that runs right on your PC - with an IP for you, your home network and (with port forwarding) your friends.',
  'Server erstellen': 'Create server',
  'Noch kein Server': 'No server yet',
  'Mit einem Klick bekommst du einen Paper-Server (Plugins möglich) oder einen Vanilla-Server.': 'With one click you get a Paper server (plugins possible) or a Vanilla server.',
  'Ersten Server erstellen': 'Create your first server',
  'Minecraft-Version': 'Minecraft version',
  'Paper (empfohlen)': 'Paper (recommended)',
  'Schnell, Plugins möglich': 'Fast, plugins possible',
  'Original-Server von Mojang': 'Original server from Mojang',
  'Arbeitsspeicher': 'Memory',
  'Spielmodus': 'Game mode',
  'Überleben': 'Survival',
  'Kreativ': 'Creative',
  'Abenteuer': 'Adventure',
  'Zuschauer': 'Spectator',
  'Schwierigkeit': 'Difficulty',
  'Friedlich': 'Peaceful',
  'Einfach': 'Easy',
  'Schwer': 'Hard',
  'Max. Spieler': 'Max. players',
  'Beschreibung (MOTD)': 'Description (MOTD)',
  'Nur echte Accounts': 'Real accounts only',
  'Nur echte Minecraft-Accounts (empfohlen)': 'Real Minecraft accounts only (recommended)',
  'Ich akzeptiere die': 'I accept the',
  '. Ohne Zustimmung startet kein Minecraft-Server.': '. Without consent no Minecraft server will start.',
  'Abbrechen': 'Cancel',
  'Freigeben': 'Forward',
  'Speichern': 'Save',
  'Echte Accounts': 'Real accounts',
  'Server-Einstellungen': 'Server settings',
  'Erstelle Server ...': 'Creating server ...',
  'Erstelle ...': 'Creating ...',
  'Stoppen': 'Stop',
  'Starten': 'Start',
  'Beitreten': 'Join',
  'IP-Adressen': 'IP addresses',
  'Auf diesem PC': 'On this PC',
  'Im Heimnetz (LAN)': 'On the home network (LAN)',
  'Für Freunde (Internet)': 'For friends (internet)',
  'Spieler online': 'Players online',
  'OP geben': 'Give OP',
  'Ordner': 'Folder',
  'Einstellungen': 'Settings',
  'Konsole': 'Console',
  'Starte den Server, um die Ausgabe zu sehen.': 'Start the server to see the output.',
  'Senden': 'Send',
  'Noch niemand da.': 'Nobody here yet.',
  'Server ist nicht online.': 'Server is not online.',
  'Startet ...': 'Starting ...',
  'Gestoppt': 'Stopped',
  'Server gestoppt': 'Server stopped',
  'Server ist gestoppt': 'Server is stopped',
  'Befehl eingeben, z. B. op DeinName, time set day, say Hallo': 'Enter a command, e.g. op YourName, time set day, say Hello',
  'Kopieren': 'Copy',
  'Bitte zuerst anmelden': 'Please sign in first',
  'Portfreigabe entfernt': 'Port forwarding removed',
  'Port freigegeben - deine öffentliche IP steht jetzt oben': 'Port forwarded - your public IP is now shown above',
  'Server gelöscht': 'Server deleted',
  // ---- Modpack und Mod-Beschreibungen
  'Das DeuClient-Modpack mit DeuCore (HUD, Mod-Menü, Capes) sowie den Modulen Discord und Scout.': 'The DeuClient modpack with DeuCore (HUD, mod menu, capes) and the Discord and Scout modules.',
  'Kernmod: Hauptmenü, Ladebildschirm, Mod-Menü (Rechts-Shift), Capes, Flügel und integriertes HUD.': 'Core mod: main menu, loading screen, mod menu (Right Shift), capes, wings and integrated HUD.',
  'Zeigt deinen Discord-Call, Mitglieder, Mute/Deafen und Benachrichtigungen ingame. Menü: Strg + Y': 'Shows your Discord call, members, mute/deafen and notifications in game. Menu: Ctrl + Y',
  'Zeigt beim /invsee sofort Kristalle, Totems, XP-Flaschen und die schwächste Rüstung des Gegners - direkt neben dem Inventar.':
    "Instantly shows crystals, totems, XP bottles and the enemy's weakest armor on /invsee - right next to the inventory.",
};

type Rule = [RegExp, (m: RegExpExecArray) => string];
const RULES: Rule[] = [
  [/^Meine Capes \((\d+)\)$/, (m) => `My Capes (${m[1]})`],
  [/^Die Anmeldung für (.+) ist abgelaufen\. Bitte füge den Account im Account-Menü neu hinzu\.$/, (m) => `The login for ${m[1]} has expired. Please add the account again in the account menu.`],
  [/^Download fehlgeschlagen: (.+?)\. (.*)$/, (m) => `Download failed: ${m[1]}. ${tx(m[2])}`],
  // Zeilen der Konsole
  [/^\[DeuClient\] FEHLER: ([\s\S]*)$/, (m) => `[DeuClient] ERROR: ${lookup(m[1]) ?? m[1]}`],
  [/^\[DeuClient\] Technische Details: ([\s\S]*)$/, (m) => `[DeuClient] Technical details: ${m[1]}`],
  [/^\[DeuClient\] Fehler beim Start: ([\s\S]*)$/, (m) => `[DeuClient] Error on start: ${lookup(m[1]) ?? m[1]}`],
  [/^\[DeuClient\] Minecraft wurde beendet \(Code (.*)\)$/, (m) => `[DeuClient] Minecraft exited (code ${m[1]})`],
  [/^\[DeuClient\] Spieler: (.*?)( · verbinde mit (.*))?$/, (m) => `[DeuClient] Player: ${m[1]}${m[3] ? ` · connecting to ${m[3]}` : ''}`],
  [/^\[DeuClient\] DeuCore aktuell \((.*)\)$/, (m) => `[DeuClient] DeuCore up to date (${m[1]})`],
  [/^\[DeuClient\] DeuCore aktualisiert: (.*)$/, (m) => `[DeuClient] DeuCore updated: ${m[1]}`],
  [/^\[DeuClient\] Modpack ohne DeuClient-Mods$/, () => '[DeuClient] Modpack without DeuClient mods'],
  [/^Angelegt in (\d+) Modpacks?( ✓)?$/, (m) => `Equipped in ${m[1]} modpack${m[1] === '1' ? '' : 's'} ✓`],
  [/^"(.*)" gespeichert$/, (m) => `"${m[1]}" saved`],
  [/^(.*) erstellt ✓$/, (m) => `${m[1]} created ✓`],
  [/^Kopiert: (.*)$/, (m) => `Copied: ${m[1]}`],
  [/^Erstelle zuerst ein Modpack mit Minecraft (.*)\.$/, (m) => `First create a modpack with Minecraft ${m[1]}.`],
  [/^(.*) - gehostet mit dem DeuClient$/, (m) => `${m[1]} - hosted with DeuClient`],
  [/^Fabric-Modpack für Minecraft (.*)$/, (m) => `Fabric modpack for Minecraft ${m[1]}`],
  [/^Vanilla-Modpack für Minecraft (.*)$/, (m) => `Vanilla modpack for Minecraft ${m[1]}`],
  [/^Textur (\d+)×(\d+) \(inkl\. Elytra\)$/, (m) => `Texture ${m[1]}×${m[2]} (incl. elytra)`],
  [/^Vorderseite des Capes \((\d+) × (\d+) Pixel\) - Klicken und ziehen zum Malen$/, (m) => `Front of the cape (${m[1]} × ${m[2]} pixels) - click and drag to paint`],
  [/^"(.*)" löschen\?$/, (m) => `Delete "${m[1]}"?`],
  [/^Server "(.*)" samt Welt wirklich löschen\?$/, (m) => `Really delete server "${m[1]}" including its world?`],
  [/^Port (\d+) jetzt per UPnP in deinem Router freigeben\?\n\nDanach ist dein Server aus dem Internet erreichbar\. Teile die IP nur mit Leuten, denen du vertraust\.$/, (m) =>
    `Forward port ${m[1]} in your router via UPnP now?\n\nAfterwards your server is reachable from the internet. Only share the IP with people you trust.`],
];

let lang: 'de' | 'en' = 'de';
const original = new Map<Node, string>();
const originalAttr = new Map<Element, Map<string, string>>();
const ATTRS = ['placeholder', 'title', 'aria-label'];

function lookup(text: string): string | null {
  if (EN[text]) return EN[text];
  for (const [re, fn] of RULES) {
    const m = re.exec(text);
    if (m) return fn(m);
  }
  return null;
}

/** Lange Gedankenstriche entfernen, bei Englisch zusaetzlich uebersetzen */
function convert(text: string): string {
  const flat = text.replace(/[–—]/g, '-');
  if (lang !== 'en') return flat;
  return lookup(flat) ?? flat;
}

export function tx(de: string): string {
  return convert(de);
}

/** Einzelne Zeile uebersetzen, ohne den globalen Uebersetzer zu aktivieren (Konsolenfenster) */
export function translateLine(de: string, language: 'de' | 'en'): string {
  return language === 'en' ? lookup(de) ?? de : de;
}

function processText(n: Text) {
  const v = n.nodeValue ?? '';
  const trimmed = v.trim();
  if (!trimmed) return;
  const out = convert(trimmed);
  if (out === trimmed) return;
  original.set(n, v);
  n.nodeValue = v.replace(trimmed, out);
}

function processElement(el: Element) {
  for (const a of ATTRS) {
    const v = el.getAttribute(a);
    if (!v) continue;
    const out = convert(v);
    if (out === v) continue;
    let map = originalAttr.get(el);
    if (!map) originalAttr.set(el, (map = new Map()));
    map.set(a, v);
    el.setAttribute(a, out);
  }
}

function walk(root: Node) {
  if (root.nodeType === Node.TEXT_NODE) return processText(root as Text);
  if (root.nodeType !== Node.ELEMENT_NODE) return;
  const el = root as Element;
  if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') return;
  processElement(el);
  for (const child of Array.from(el.childNodes)) walk(child);
}

let observer: MutationObserver | null = null;

export function setTranslateLanguage(next: 'de' | 'en') {
  // beim Wechsel: bereits uebersetzte Texte auf Deutsch zuruecksetzen und neu verarbeiten
  if (next !== lang) {
    for (const [n, v] of original) if (n.isConnected) n.nodeValue = v;
    for (const [el, map] of originalAttr) if (el.isConnected) for (const [a, v] of map) el.setAttribute(a, v);
    original.clear();
    originalAttr.clear();
  }
  lang = next;
  if (!observer) {
    observer = new MutationObserver((records) => {
      for (const r of records) {
        if (r.type === 'characterData') processText(r.target as Text);
        else if (r.type === 'attributes') processElement(r.target as Element);
        else r.addedNodes.forEach((n) => walk(n));
      }
    });
    observer.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  }
  walk(document.body);
}
