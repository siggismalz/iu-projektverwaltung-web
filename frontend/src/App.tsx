import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, errorMessage, mutate, request } from './api';
import { Brand, ErrorNotice, Icon, Loading } from './components';
import Login from './Login';
import Projects from './Projects';
import Users from './Users';
import { roleNames } from './types';
import type { Project, Session, User } from './types';

// Verwaltet die Sitzung und zeigt die über den URL-Hash ausgewählte Seite an.
export default function App() {
  // undefined: Sitzung wird geprüft; null: nicht angemeldet; Session: angemeldet.
  const [session, setSession] = useState<Session | null>();
  const [bootError, setBootError] = useState('');
  const [route, setRoute] = useState(location.hash.slice(1) || 'projects');
  const [projects, setProjects] = useState<Project[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [logoutBusy, setLogoutBusy] = useState(false);
  // Nur die neueste Ladeanfrage darf den Zustand verändern, auch nach einer Abmeldung.
  const requestSequence = useRef(0);
  // Die Ref sperrt sofort; ein React-Zustand wird erst beim nächsten Rendern wirksam.
  const logoutLock = useRef(false);

  const boot = useCallback(async () => {
    setBootError('');
    try {
      setSession(await request<Session>('/session'));
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) setSession(null);
      else setBootError(errorMessage(err));
    }
  }, []);
  useEffect(() => {
    void boot();
  }, [boot]);

  // Die kleine Anwendung benötigt nur drei Ansichten und keinen zusätzlichen Router.
  useEffect(() => {
    const change = () => {
      setRoute(location.hash.slice(1) || 'projects');
      setError('');
      setNotice('');
    };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError('');
    try {
      const [newProjects, newUsers] = await Promise.all([
        request<Project[]>('/projects'),
        request<User[]>('/users'),
      ]);
      if (sequence === requestSequence.current) {
        setProjects(newProjects);
        setUsers(newUsers);
      }
    } catch (err) {
      if (sequence !== requestSequence.current) return;
      if (err instanceof ApiError && err.status === 401) setSession(null);
      else setError(errorMessage(err));
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    if (session) void load();
    return () => {
      requestSequence.current++;
    };
  }, [session, load]);
  useEffect(() => {
    let title = 'Projekte';
    if (route === 'users') title = 'Benutzerverwaltung';
    else if (route.startsWith('project/')) title = 'Projektdetails';
    document.title = `${title} · Projektwerk`;
  }, [route]);

  async function logout() {
    if (logoutLock.current) return;
    logoutLock.current = true;
    setLogoutBusy(true);
    try {
      await mutate('/logout', 'POST');
      requestSequence.current++;
      setSession(null);
      setProjects([]);
      setUsers([]);
      setError('');
      setNotice('');
      location.hash = 'projects';
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      logoutLock.current = false;
      setLogoutBusy(false);
    }
  }
  if (session === undefined)
    return (
      <main className="startup">
        <Brand />
        {bootError ? (
          <ErrorNotice message={bootError} onRetry={() => void boot()} />
        ) : (
          <Loading text="Arbeitsbereich wird geöffnet …" />
        )}
      </main>
    );
  if (!session)
    return (
      <Login
        onLogin={(s) => {
          location.hash = 'projects';
          setSession(s);
        }}
      />
    );
  const isUsers = route === 'users';
  const detailProject = route.startsWith('project/')
    ? projects.find((project) => project.id === Number(route.split('/')[1]))
    : undefined;

  async function handleSaved(message: string) {
    setNotice(message);
    await load();
  }

  // Die Seitenauswahl bleibt vom gemeinsamen Rahmen mit Navigation getrennt.
  let content;
  if (loading && projects.length === 0) {
    content = <Loading />;
  } else if (isUsers && session.role !== 'ADMIN') {
    content = (
      <ErrorNotice message="Die Benutzerverwaltung ist nur für die Administration verfügbar." />
    );
  } else if (isUsers) {
    content = <Users users={users} session={session} onSaved={handleSaved} />;
  } else {
    content = (
      <Projects
        projects={projects}
        users={users}
        session={session}
        route={route}
        loading={loading}
        onSaved={handleSaved}
      />
    );
  }

  return (
    <div className="app-shell">
      <a
        href="#main"
        className="skip-link"
        onClick={(e) => {
          e.preventDefault();
          document.getElementById('main')?.focus();
        }}
      >
        Zum Inhalt springen
      </a>
      <aside className="sidebar">
        <a className="brand-link" href="#projects" aria-label="Projektwerk – zur Projektübersicht">
          <Brand />
        </a>
        <div className="workspace-label">ARBEITSBEREICH</div>
        <nav aria-label="Hauptnavigation">
          <a
            href="#projects"
            className={!isUsers ? 'active' : ''}
            aria-current={!isUsers ? 'page' : undefined}
          >
            <Icon name="projects" />
            Projekte<span className="nav-count">{projects.filter((p) => !p.archived).length}</span>
          </a>
          {session.role === 'ADMIN' && (
            <a
              href="#users"
              className={isUsers ? 'active' : ''}
              aria-current={isUsers ? 'page' : undefined}
            >
              <Icon name="users" />
              Benutzerverwaltung
            </a>
          )}
        </nav>
        <div className="sidebar-bottom">
          <div className="tenant-label">{session.tenantName}</div>
          <div className="account">
            <span className="avatar">
              {session.displayName
                .split(' ')
                .map((p) => p[0])
                .slice(0, 2)
                .join('')}
            </span>
            <div>
              <strong>{session.displayName}</strong>
              <small>{roleNames[session.role]}</small>
            </div>
          </div>
          <button className="logout-button" onClick={() => void logout()} disabled={logoutBusy}>
            <Icon name="logout" />
            {logoutBusy ? 'Abmeldung läuft …' : 'Abmelden'}
          </button>
        </div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <nav className="breadcrumbs" aria-label="Seitennavigation">
            <ol>
              <li>Arbeitsbereich</li>
              {detailProject ? (
                <>
                  <li>
                    <a href="#projects" aria-label="Zur Projektübersicht">
                      Projekte
                    </a>
                  </li>
                  <li>
                    <span aria-current="page">{detailProject.name}</span>
                  </li>
                </>
              ) : (
                <li>
                  <span aria-current="page">{isUsers ? 'Benutzerverwaltung' : 'Projekte'}</span>
                </li>
              )}
            </ol>
          </nav>
          <span className="topbar-tenant">{session.tenantName}</span>
        </header>
        <main id="main" tabIndex={-1} className="main-content">
          {notice && (
            <div className="notice success" role="status">
              <Icon name="check" />
              <span>{notice}</span>
              <button
                className="icon-button"
                aria-label="Hinweis schließen"
                onClick={() => setNotice('')}
              >
                <Icon name="close" size={18} />
              </button>
            </div>
          )}
          {error && <ErrorNotice message={error} onRetry={() => void load()} />}
          {content}
        </main>
        <footer className="app-footer">
          <span>Projektwerk</span>
          <span>Projekt- und Aufgabenverwaltung</span>
        </footer>
      </div>
    </div>
  );
}
