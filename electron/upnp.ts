/**
 * Minimale UPnP-IGD-Implementierung (ohne Zusatzpakete), um einen Port im Router freizugeben,
 * damit Freunde über die öffentliche IP auf den eigenen Server kommen.
 *
 *   1. SSDP-Suche (Multicast 239.255.255.250:1900) nach dem Internet-Gateway
 *   2. Gerätebeschreibung laden → Control-URL von WANIPConnection / WANPPPConnection
 *   3. SOAP: AddPortMapping / DeletePortMapping / GetExternalIPAddress
 *
 * Wird nur ausgeführt, wenn der Nutzer im Launcher ausdrücklich "Port freigeben" drückt.
 */
import dgram from 'node:dgram';
import os from 'node:os';

interface Gateway {
  controlUrl: string;
  serviceType: string;
}

let cached: Gateway | null = null;

/**
 * Lokale IPv4-Adresse der Netzwerkkarte, über die dieser PC ins Internet geht.
 * Ein UDP-"connect" legt nur die Route fest - es werden dabei keine Daten gesendet.
 * So landet man nicht bei virtuellen Adaptern (VirtualBox, Hyper-V, VPN …).
 */
export function lanAddress(): Promise<string | null> {
  return new Promise((resolve) => {
    const socket = dgram.createSocket('udp4');
    const fallback = () => {
      for (const list of Object.values(os.networkInterfaces())) {
        for (const a of list ?? []) {
          if (a.family === 'IPv4' && !a.internal && /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(a.address)) return a.address;
        }
      }
      return null;
    };
    socket.on('error', () => {
      socket.close();
      resolve(fallback());
    });
    socket.connect(53, '1.1.1.1', () => {
      const addr = socket.address().address;
      socket.close();
      resolve(addr && addr !== '0.0.0.0' ? addr : fallback());
    });
  });
}

function ssdpSearch(timeoutMs = 3000): Promise<string> {
  return new Promise((resolve, reject) => {
    const socket = dgram.createSocket('udp4');
    const msg = Buffer.from(
      'M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: "ssdp:discover"\r\nMX: 2\r\n' +
        'ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n',
    );
    const timer = setTimeout(() => {
      socket.close();
      reject(new Error('Kein UPnP-Router gefunden (UPnP im Router evtl. deaktiviert).'));
    }, timeoutMs);
    socket.on('message', (data) => {
      const location = /LOCATION:\s*(\S+)/i.exec(data.toString())?.[1];
      if (location) {
        clearTimeout(timer);
        socket.close();
        resolve(location);
      }
    });
    socket.on('error', (e) => {
      clearTimeout(timer);
      socket.close();
      reject(e);
    });
    socket.send(msg, 1900, '239.255.255.250');
  });
}

async function findGateway(): Promise<Gateway> {
  if (cached) return cached;
  const location = await ssdpSearch();
  const xml = await (await fetch(location)).text();
  // passenden Dienst suchen (WANIPConnection bevorzugt)
  const services = [...xml.matchAll(/<service>([\s\S]*?)<\/service>/g)].map((m) => m[1]);
  for (const type of ['WANIPConnection:2', 'WANIPConnection:1', 'WANPPPConnection:1']) {
    const svc = services.find((s) => s.includes(`urn:schemas-upnp-org:service:${type}`));
    if (!svc) continue;
    const control = /<controlURL>([^<]+)<\/controlURL>/.exec(svc)?.[1];
    if (!control) continue;
    cached = { controlUrl: new URL(control, location).toString(), serviceType: `urn:schemas-upnp-org:service:${type}` };
    return cached;
  }
  throw new Error('Router unterstützt keine Portfreigabe per UPnP.');
}

async function soap(action: string, args: Record<string, string | number>): Promise<string> {
  const gw = await findGateway();
  const body =
    `<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">` +
    `<s:Body><u:${action} xmlns:u="${gw.serviceType}">` +
    Object.entries(args).map(([k, v]) => `<${k}>${v}</${k}>`).join('') +
    `</u:${action}></s:Body></s:Envelope>`;
  const res = await fetch(gw.controlUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'text/xml; charset="utf-8"', SOAPAction: `"${gw.serviceType}#${action}"` },
    body,
  });
  const text = await res.text();
  if (!res.ok) {
    const err = /<errorDescription>([^<]+)<\/errorDescription>/.exec(text)?.[1];
    throw new Error(`Router lehnt ${action} ab${err ? `: ${err}` : ''}`);
  }
  return text;
}

/** Öffnet den TCP-Port im Router und liefert die öffentliche IP */
export async function openPort(port: number, description: string): Promise<string> {
  const lan = await lanAddress();
  if (!lan) throw new Error('Keine lokale Netzwerkadresse gefunden.');
  await soap('AddPortMapping', {
    NewRemoteHost: '',
    NewExternalPort: port,
    NewProtocol: 'TCP',
    NewInternalPort: port,
    NewInternalClient: lan,
    NewEnabled: 1,
    NewPortMappingDescription: description,
    NewLeaseDuration: 0,
  });
  return externalIp();
}

export async function closePort(port: number): Promise<void> {
  await soap('DeletePortMapping', { NewRemoteHost: '', NewExternalPort: port, NewProtocol: 'TCP' });
}

export async function externalIp(): Promise<string> {
  const xml = await soap('GetExternalIPAddress', {});
  const ip = /<NewExternalIPAddress>([^<]+)<\/NewExternalIPAddress>/.exec(xml)?.[1];
  if (!ip) throw new Error('Öffentliche IP nicht ermittelbar.');
  return ip;
}
