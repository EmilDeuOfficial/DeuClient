/**
 * Preload-Script: stellt der Oberfläche eine schmale, sichere API (window.divine) bereit.
 * Die Oberfläche hat keinen Node-Zugriff – alles läuft über diese IPC-Brücke.
 */
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('divine', {
  /** Ruft einen IPC-Handler im Main-Prozess auf und entpackt { ok, data | error } */
  invoke: async (channel: string, ...args: unknown[]) => {
    const res = (await ipcRenderer.invoke(channel, ...args)) as { ok: boolean; data?: unknown; error?: string };
    if (!res.ok) throw new Error(res.error);
    return res.data;
  },
  /** Abonniert ein Ereignis aus dem Main-Prozess; gibt eine Abmeldefunktion zurück */
  on: (channel: string, cb: (payload: unknown) => void) => {
    const listener = (_e: unknown, payload: unknown) => cb(payload);
    ipcRenderer.on(channel, listener);
    return () => ipcRenderer.removeListener(channel, listener);
  },
});
