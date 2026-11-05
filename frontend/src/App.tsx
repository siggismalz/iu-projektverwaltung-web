import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage, mutate, request } from './api';
import { Brand, ErrorNotice, Icon, Loading } from './components';
import Login from './Login';
import Projects from './Projects';
import Users from './Users';
import { roleNames } from './types';
import type { Project, Session, User } from './types';

export default function App() {
  const [session, setSession] = useState<Session | null>();
  const [bootError, setBootError] = useState('');
  const [route, setRoute] = useState(location.hash.slice(1) || 'projects');
  const [projects, setProjects] = useState<Project[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [logoutBusy, setLogoutBusy] = useState(false);
  const requestSequence = useRef(0);
  const logoutLock = useRef(false);
  const boot = useCallback(async () => {
    setBootError('');
    try { setSession(await request<Session>('/session')); }
    catch (err) {
      if (err instanceof ApiError && err.status === 401) setSession(null);
      else setBootError(errorMessage(err));
    }
  }, []);
  useEffect(() => { void boot(); }, [boot]);
  useEffect(() => {
    const change = () => { setRoute(location.hash.slice(1) || 'projects'); setError(''); setNotice(''); };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true); setError('');
    try {
      const [newProjects, newUsers] = await Promise.all([request<Project[]>('/projects'), request<User[]>('/users')]);
      if (sequence === requestSequence.current) { setProjects(newProjects); setUsers(newUsers); }
    } catch (err) {
      if (sequence !== requestSequence.current) return;
      if (err instanceof ApiError && err.status === 401) setSession(null);
      else setError(errorMessage(err));
    } finally { if (sequence === requestSequence.current) setLoading(false); }
  }, []);
  useEffect(() => {
    if (session) void load();
    return () => { requestSequence.current++; };
  }, [session, load]);
  useEffect(() => {
    document.title = `${route === 'users' ? 'Benutzerverwaltung' : route.startsWith('project/') ? 'Projektdetails' : 'Projekte'} · Projektwerk`;
  }, [route]);
  async function logout() {
    if (logoutLock.current) return;
    logoutLock.current = true; setLogoutBusy(true);
    try {
      await mutate('/logout', 'POST');
      requestSequence.current++; setSession(null); setProjects([]); setUsers([]); setError(''); setNotice('');
      location.hash = 'projects';
    } catch (err) { setError(errorMessage(err)); }
    finally { logoutLock.current = false; setLogoutBusy(false); }
  }
  if (session === undefined) return <main className="startup"><Brand />{bootError ? <ErrorNotice message={bootError} onRetry={() => void boot()} /> : <Loading text="Arbeitsbereich wird geöffnet …" />}</main>;
  if (!session) return <Login onLogin={s => { location.hash = 'projects'; setSession(s); }} />;
  const isUsers = route === 'users';
  const detailProject = route.startsWith('project/') ? projects.find(project => project.id === Number(route.split('/')[1])) : undefined;
  return <div className="app-shell">
    <a href="#main" className="skip-link" onClick={e => { e.preventDefault(); document.getElementById('main')?.focus(); }}>Zum Inhalt springen</a>
    <aside className="sidebar"><a className="brand-link" href="#projects" aria-label="Projektwerk – zur Projektübersicht"><Brand /></a><div className="workspace-label">ARBEITSBEREICH</div><nav aria-label="Hauptnavigation"><a href="#projects" className={!isUsers ? 'active' : ''} aria-current={!isUsers ? 'page' : undefined}><Icon name="projects" />Projekte<span className="nav-count">{projects.filter(p => !p.archived).length}</span></a>{session.role === 'ADMIN' && <a href="#users" className={isUsers ? 'active' : ''} aria-current={isUsers ? 'page' : undefined}><Icon name="users" />Benutzerverwaltung</a>}</nav><div className="sidebar-bottom"><div className="tenant-label">{session.tenantName}</div><div className="account"><span className="avatar">{session.displayName.split(' ').map(p => p[0]).slice(0, 2).join('')}</span><div><strong>{session.displayName}</strong><small>{roleNames[session.role]}</small></div></div><button className="logout-button" onClick={() => void logout()} disabled={logoutBusy}><Icon name="logout" />{logoutBusy ? 'Abmeldung läuft …' : 'Abmelden'}</button></div></aside>
    <div className="main-column"><header className="topbar"><nav className="breadcrumbs" aria-label="Seitennavigation"><ol><li>Arbeitsbereich</li>{detailProject ? <><li><a href="#projects" aria-label="Zur Projektübersicht">Projekte</a></li><li><span aria-current="page">{detailProject.name}</span></li></> : <li><span aria-current="page">{isUsers ? 'Benutzerverwaltung' : 'Projekte'}</span></li>}</ol></nav><span className="topbar-tenant">{session.tenantName}</span></header>
      <main id="main" tabIndex={-1} className="main-content">
        {notice && <div className="notice success" role="status"><Icon name="check" /><span>{notice}</span><button className="icon-button" aria-label="Hinweis schließen" onClick={() => setNotice('')}><Icon name="close" size={18} /></button></div>}
        {error && <ErrorNotice message={error} onRetry={() => void load()} />}
        {loading && projects.length === 0 ? <Loading /> : isUsers
          ? session.role === 'ADMIN' ? <Users users={users} session={session} onSaved={async message => { setNotice(message); await load(); }} /> : <ErrorNotice message="Die Benutzerverwaltung ist nur für die Administration verfügbar." />
          : <Projects projects={projects} users={users} session={session} route={route} loading={loading} onSaved={async message => { setNotice(message); await load(); }} />}
      </main><footer className="app-footer"><span>Projektwerk</span><span>Projekt- und Aufgabenverwaltung</span></footer>
    </div>
  </div>;
}
