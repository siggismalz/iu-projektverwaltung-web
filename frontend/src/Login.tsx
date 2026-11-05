import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { errorMessage, mutate, request } from './api';
import { Brand, Icon } from './components';
import type { Session } from './types';

export default function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const errorRef = useRef<HTMLDivElement>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      await mutate('/login', 'POST', new URLSearchParams({ username, password }), true);
      // Spring rotiert den CSRF-Token nach erfolgreicher Anmeldung.
      await request('/csrf');
      onLogin(await request<Session>('/session'));
    } catch (err) {
      setError(errorMessage(err));
      setTimeout(() => errorRef.current?.focus(), 0);
    } finally { lock.current = false; setBusy(false); }
  }
  return <main className="login-screen">
    <section className="login-intro" aria-label="Projektwerk"><Brand /><div className="intro-copy"><span className="eyebrow">PROJEKT- UND AUFGABENVERWALTUNG</span><h1>Projekte und Aufgaben<br /><span>gemeinsam verwalten.</span></h1><p>Plane Projekte, ordne Aufgaben zu und verfolge den Bearbeitungsstand deines Teams.</p><div className="intro-note"><Icon name="check" /><span>Projektleitung, Mitarbeitende und Administration in einem Arbeitsbereich.</span></div></div><p className="intro-footer">Projekte · Aufgaben · Zuständigkeiten</p></section>
    <section className="login-area"><div className="login-card"><span className="eyebrow">DEIN ARBEITSBEREICH</span><h2>Willkommen zurück</h2><p className="muted">Melde dich mit deinem Benutzerkonto an.</p>
      <form onSubmit={submit}>
        {error && <div className="notice error" ref={errorRef} tabIndex={-1} role="alert">{error}</div>}
        <label htmlFor="username">Benutzername</label><input id="username" name="username" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" autoCapitalize="none" required maxLength={80} disabled={busy} autoFocus />
        <label htmlFor="password">Passwort</label><input id="password" name="password" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required disabled={busy} />
        <button className="button primary login-submit" disabled={busy}>{busy ? 'Anmeldung läuft …' : 'Anmelden'}<Icon name="arrow" size={18} /></button>
      </form>
      <details className="demo-info"><summary>Demozugänge ausprobieren</summary><p>Fiktive Konten · Passwort: <code>Demo2026!</code></p><div className="demo-buttons">{['admin', 'leitung', 'mitarbeiter', 'fremd'].map(name => <button key={name} className="button small secondary" disabled={busy} onClick={() => { setUsername(name); setPassword('Demo2026!'); }}>{name}</button>)}</div><p>„fremd“ gehört zu einem anderen Mandanten.</p></details>
    </div><p className="login-footnote">Projektwerk · Projekt- und Aufgabenverwaltung</p></section>
  </main>;
}
