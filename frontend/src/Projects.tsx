import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { errorMessage, mutate, request } from './api';
import { EmptyState, ErrorNotice, FormActions, Icon, Loading, Modal, Progress } from './components';
import { statusNames } from './types';
import type { Project, Session, Task, TaskStatus, User } from './types';

type Props = { projects: Project[]; users: User[]; session: Session; route: string; loading: boolean; onSaved: (message: string) => Promise<void> };

export default function Projects({ projects, users, session, route, loading, onSaved }: Props) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('active');
  const [edit, setEdit] = useState<Project | 'new' | null>(null);
  const canManage = session.role === 'ADMIN' || session.role === 'LEAD';
  const projectId = route.startsWith('project/') ? Number(route.split('/')[1]) : null;
  const project = projectId ? projects.find(p => p.id === projectId) : null;
  const active = projects.filter(p => !p.archived);
  const total = active.reduce((n, p) => n + p.totalTasks, 0);
  const done = active.reduce((n, p) => n + p.doneTasks, 0);
  const visible = projects.filter(p => (filter === 'all' || p.archived === (filter === 'archived')) && `${p.name} ${p.description}`.toLocaleLowerCase('de').includes(search.toLocaleLowerCase('de')));
  return <>
    {projectId ? project ? <ProjectDetail project={project} session={session} canManage={canManage} onEdit={() => setEdit(project)} onSaved={onSaved} /> : <><a className="back-link" href="#projects">← Zur Projektübersicht</a>{loading ? <Loading /> : <EmptyState title="Projekt nicht verfügbar">Das Projekt ist nicht vorhanden oder du bist dafür nicht berechtigt.</EmptyState>}</> : <>
      <div className="page-heading"><div><span className="eyebrow">DEIN ARBEITSBEREICH</span><h1>Projekte</h1><p>Projekte, Zuständigkeiten und Bearbeitungsstände im Überblick.</p></div>{canManage && <button className="button primary" onClick={() => setEdit('new')}><Icon name="plus" />Neues Projekt</button>}</div>
      <section className="metrics" aria-label="Überblick über aktive Projekte"><div className="metric"><span>Aktive Projekte</span><strong>{active.length}</strong><small>In deinem Arbeitsbereich</small></div><div className="metric"><span>Offene Aufgaben</span><strong>{total - done}</strong><small>Offen oder in Bearbeitung</small></div><div className="metric"><span>Aufgaben abgeschlossen</span><strong>{total ? Math.round(done / total * 100) : 0}<em> %</em></strong><small>{done} von {total} Aufgaben abgeschlossen</small></div></section>
      <section className="panel" aria-labelledby="project-list-heading"><div className="panel-heading"><div><h2 id="project-list-heading">Deine Projekte <span className="count-badge">{visible.length}</span></h2><p>Öffne ein Projekt, um das Team und seine Aufgaben zu sehen.</p></div><span className="panel-note">Nur für dich freigegebene Projekte</span></div>
        <div className="filters"><div className="search-field"><label className="sr-only" htmlFor="project-search">Projekte durchsuchen</label><Icon name="search" /><input id="project-search" type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Projekt suchen …" /></div><div className="filter-select"><label htmlFor="project-filter">Anzeigen</label><select id="project-filter" value={filter} onChange={e => setFilter(e.target.value)}><option value="active">Aktive Projekte</option><option value="archived">Archivierte Projekte</option><option value="all">Alle Projekte</option></select></div></div>
        {visible.length ? <table className="project-table responsive-table"><caption className="sr-only">Für dich sichtbare Projekte und ihr Fortschritt</caption><thead><tr><th scope="col">Projekt</th><th scope="col">Team</th><th scope="col">Fortschritt</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Aktion</span></th></tr></thead><tbody>{visible.map(p => <tr key={p.id}><td data-label="Projekt"><a className="project-link" href={`#project/${p.id}`}>{p.name}</a><p className="table-description">{p.description || 'Noch keine Beschreibung hinterlegt.'}</p></td><td data-label="Team"><div className="team-size"><Icon name="users" size={16} />{p.memberIds.length} {p.memberIds.length === 1 ? 'Person' : 'Personen'}</div><span className="team-names">{p.memberNames.join(', ') || 'Noch nicht zugeordnet'}</span></td><td data-label="Fortschritt"><Progress done={p.doneTasks} total={p.totalTasks} /></td><td data-label="Status"><span className={`badge ${p.archived ? 'archived' : 'active-status'}`}>{p.archived ? 'Archiviert' : 'Aktiv'}</span></td><td><a className="icon-button open-project" href={`#project/${p.id}`} aria-label={`Projekt ${p.name} öffnen`}><Icon name="arrow" /></a></td></tr>)}</tbody></table> : <EmptyState title={search ? 'Keine passenden Projekte' : filter === 'archived' ? 'Noch keine archivierten Projekte' : 'Hier beginnt dein nächstes Projekt'}>{search ? 'Probiere einen anderen Suchbegriff oder passe den Filter an.' : canManage ? 'Lege ein Projekt an und ordne dein Team zu.' : 'Sobald dir ein Projekt zugeordnet wurde, erscheint es hier.'}</EmptyState>}
      </section>
    </>}
    {edit && <ProjectForm project={edit === 'new' ? null : edit} users={users} session={session} onClose={() => setEdit(null)} onSaved={async message => { await onSaved(message); setEdit(null); }} />}
  </>;
}

function ProjectForm({ project, users, session, onClose, onSaved }: { project: Project | null; users: User[]; session: Session; onClose: () => void; onSaved: (message: string) => Promise<void> }) {
  const [name, setName] = useState(project?.name ?? '');
  const [description, setDescription] = useState(project?.description ?? '');
  const [members, setMembers] = useState<number[]>(project?.memberIds ?? [session.id]);
  const [archived, setArchived] = useState(project?.archived ?? false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const errorRef = useRef<HTMLDivElement>(null);
  async function save(e: FormEvent) {
    e.preventDefault(); if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      const body = { name: name.trim(), description: description.trim(), memberIds: members };
      await mutate(project ? `/projects/${project.id}` : '/projects', project ? 'PUT' : 'POST', project ? { ...body, archived } : body);
      await onSaved(project ? 'Projekt wurde gespeichert.' : 'Das neue Projekt wurde angelegt.');
    } catch (err) { setError(errorMessage(err)); setTimeout(() => errorRef.current?.focus(), 0); }
    finally { lock.current = false; setBusy(false); }
  }
  return <Modal title={project ? 'Projekt bearbeiten' : 'Neues Projekt'} subtitle="Ziel beschreiben und das passende Team zusammenstellen." busy={busy} onClose={onClose}><form onSubmit={save}>
    <div className="form-body">{error && <div className="notice error" ref={errorRef} tabIndex={-1} role="alert">{error}</div>}
      <label htmlFor="project-name">Projektname <span className="required">*</span></label><input id="project-name" name="name" autoFocus required minLength={3} maxLength={120} value={name} onChange={e => setName(e.target.value)} disabled={busy} placeholder="z. B. Kundenportal modernisieren" />
      <label htmlFor="project-description">Beschreibung</label><textarea id="project-description" name="description" maxLength={2000} rows={3} value={description} onChange={e => setDescription(e.target.value)} disabled={busy} placeholder="Was soll mit diesem Projekt erreicht werden?" />
      <fieldset disabled={busy} className="member-fieldset"><legend>Projektteam</legend><p className="field-hint">Zugeordnete Personen können das Projekt und seine Aufgaben sehen.</p><div className="member-options">{users.map(u => <label className="checkbox-card" key={u.id}><input type="checkbox" checked={members.includes(u.id)} onChange={e => setMembers(previous => e.target.checked ? [...previous, u.id] : previous.filter(id => id !== u.id))} /><span><strong>{u.displayName}</strong><small>{u.username}</small></span></label>)}</div></fieldset>
      {project && <label className="archive-option"><input type="checkbox" checked={archived} onChange={e => setArchived(e.target.checked)} disabled={busy} /><span><strong>Projekt archivieren</strong><small>Archivierte Projekte bleiben sichtbar. Ihre Aufgaben sind schreibgeschützt.</small></span></label>}
      <p className="field-hint">* Pflichtfeld</p>
    </div><FormActions busy={busy} onCancel={onClose} label={project ? 'Änderungen speichern' : 'Projekt anlegen'} />
  </form></Modal>;
}

function ProjectDetail({ project, session, canManage, onEdit, onSaved }: { project: Project; session: Session; canManage: boolean; onEdit: () => void; onSaved: (message: string) => Promise<void> }) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [edit, setEdit] = useState<Task | 'new' | null>(null);
  const [status, setStatus] = useState('all');
  const sequence = useRef(0);
  const loadTasks = useCallback(async () => {
    const current = ++sequence.current;
    setLoading(true); setError('');
    try { const data = await request<Task[]>(`/projects/${project.id}/tasks`); if (current === sequence.current) setTasks(data); }
    catch (err) { if (current === sequence.current) setError(errorMessage(err)); }
    finally { if (current === sequence.current) setLoading(false); }
  }, [project.id]);
  useEffect(() => { setTasks([]); setEdit(null); setStatus('all'); void loadTasks(); return () => { sequence.current++; }; }, [loadTasks]);
  const visible = tasks.filter(task => status === 'all' || task.status === status);
  return <><a className="back-link" href="#projects">← Alle Projekte</a><div className="page-heading detail-heading"><div><div className="detail-kicker"><span className={`badge ${project.archived ? 'archived' : 'active-status'}`}>{project.archived ? 'Archiviert' : 'Aktiv'}</span><span className="eyebrow">PROJEKTÜBERSICHT</span></div><h1>{project.name}</h1><p className="project-description">{project.description || 'Für dieses Projekt wurde noch keine Beschreibung hinterlegt.'}</p></div>{canManage && <button className="button secondary" onClick={onEdit}>Projekt bearbeiten</button>}</div>
    {project.archived && <div className="notice neutral"><Icon name="archive" /><span>Dieses Projekt ist archiviert. Aufgaben können angesehen, aber nicht verändert werden.</span></div>}
    <section className="detail-summary" aria-label="Team und Fortschritt"><div className="panel summary-team"><span className="eyebrow">ZUGEORDNETE PERSONEN</span><h2>Das Projektteam</h2><div className="member-chips">{project.memberNames.length ? project.memberNames.map((name, i) => <span className="member-chip" key={`${project.memberIds[i]}-${name}`}><span className="mini-avatar" aria-hidden="true">{name.split(' ').map(p => p[0]).slice(0, 2).join('')}</span>{name}</span>) : <p className="muted">Noch keine Personen zugeordnet.</p>}</div></div><div className="panel summary-progress"><span className="eyebrow">ERLEDIGTE AUFGABEN</span><h2>Projektfortschritt</h2><Progress done={project.doneTasks} total={project.totalTasks} /></div></section>
    <section className="panel" aria-labelledby="task-heading"><div className="panel-heading"><div><h2 id="task-heading">Aufgaben <span className="count-badge">{tasks.length}</span></h2><p>Aufgabenstatus und Verantwortliche für dieses Projekt.</p></div>{!project.archived && <button className="button primary" onClick={() => setEdit('new')}><Icon name="plus" />Neue Aufgabe</button>}</div><div className="task-toolbar"><label htmlFor="task-filter">Aufgabenstatus</label><select id="task-filter" value={status} onChange={e => setStatus(e.target.value)}><option value="all">Alle Aufgaben</option>{Object.entries(statusNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><span className="muted">Angemeldet als {session.displayName}</span></div>
      {error ? <ErrorNotice message={error} onRetry={() => void loadTasks()} /> : loading ? <Loading text="Aufgaben werden geladen …" /> : visible.length ? <div className="task-list">{visible.map(task => <article className="task-card" key={task.id}><div className={`task-status-icon status-${task.status.toLowerCase()}`} aria-hidden="true">{task.status === 'DONE' ? <Icon name="check" size={18} /> : <span />}</div><div className="task-copy"><h3>{task.title}</h3>{task.description && <p>{task.description}</p>}<span className="task-assignee"><Icon name="users" size={15} />{task.assigneeName || 'Noch nicht zugewiesen'}</span></div><span className={`badge status-${task.status.toLowerCase()}`}>{statusNames[task.status]}</span>{!project.archived && <button className="button small secondary" onClick={() => setEdit(task)} aria-label={`Aufgabe ${task.title} bearbeiten`}>Bearbeiten</button>}</article>)}</div> : <EmptyState title={status === 'all' ? 'Noch keine Aufgaben' : 'Keine Aufgaben mit diesem Status'}>{status === 'all' ? project.archived ? 'Für dieses archivierte Projekt wurden keine Aufgaben angelegt.' : 'Erstelle die erste Aufgabe und bring dein Projekt in Bewegung.' : 'Wähle einen anderen Status, um weitere Aufgaben zu sehen.'}</EmptyState>}
    </section>{edit && <TaskForm task={edit === 'new' ? null : edit} project={project} onClose={() => setEdit(null)} onSaved={async message => { await Promise.all([loadTasks(), onSaved(message)]); setEdit(null); }} />}
  </>;
}

function TaskForm({ task, project, onClose, onSaved }: { task: Task | null; project: Project; onClose: () => void; onSaved: (message: string) => Promise<void> }) {
  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'OPEN');
  const [assignee, setAssignee] = useState(task?.assigneeId?.toString() ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const errorRef = useRef<HTMLDivElement>(null);
  async function save(e: FormEvent) {
    e.preventDefault(); if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      await mutate(task ? `/tasks/${task.id}` : `/projects/${project.id}/tasks`, task ? 'PUT' : 'POST', { title: title.trim(), description: description.trim(), status, assigneeId: assignee ? Number(assignee) : null });
      await onSaved(task ? 'Aufgabe wurde gespeichert.' : 'Die neue Aufgabe wurde angelegt.');
    } catch (err) { setError(errorMessage(err)); setTimeout(() => errorRef.current?.focus(), 0); }
    finally { lock.current = false; setBusy(false); }
  }
  return <Modal title={task ? 'Aufgabe bearbeiten' : 'Neue Aufgabe'} subtitle={project.name} busy={busy} onClose={onClose}><form onSubmit={save}><div className="form-body">{error && <div className="notice error" ref={errorRef} tabIndex={-1} role="alert">{error}</div>}
    <label htmlFor="task-title">Aufgabentitel <span className="required">*</span></label><input id="task-title" name="title" autoFocus value={title} onChange={e => setTitle(e.target.value)} required minLength={3} maxLength={160} disabled={busy} placeholder="z. B. Anforderungen abstimmen" />
    <label htmlFor="task-description">Beschreibung</label><textarea id="task-description" name="description" value={description} onChange={e => setDescription(e.target.value)} rows={4} maxLength={2000} disabled={busy} />
    <div className="form-grid"><div><label htmlFor="task-status">Status</label><select id="task-status" name="status" value={status} onChange={e => setStatus(e.target.value as TaskStatus)} disabled={busy}>{Object.entries(statusNames).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div><div><label htmlFor="task-assignee">Zuständige Person</label><select id="task-assignee" name="assigneeId" value={assignee} onChange={e => setAssignee(e.target.value)} disabled={busy}><option value="">Noch nicht zuweisen</option>{project.memberIds.map((id, i) => <option key={id} value={id}>{project.memberNames[i]}</option>)}</select></div></div><p className="field-hint">Zuständige Personen werden aus dem Projektteam gewählt. * Pflichtfeld</p>
  </div><FormActions busy={busy} onCancel={onClose} label={task ? 'Änderungen speichern' : 'Aufgabe anlegen'} /></form></Modal>;
}
