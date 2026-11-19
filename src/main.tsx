/** Einstiegspunkt der Oberfläche: "#console" öffnet das Konsolenfenster, sonst den Launcher. */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import ConsoleApp from './ConsoleApp';
import './index.css';

const isConsole = window.location.hash.replace('#', '') === 'console';
document.title = isConsole ? 'DeuClient – Konsole' : 'DeuClient';

createRoot(document.getElementById('root')!).render(<StrictMode>{isConsole ? <ConsoleApp /> : <App />}</StrictMode>);
