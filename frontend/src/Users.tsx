import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { errorMessage, mutate } from './api';
import { FormActions, Icon, Modal } from './components';
import { roleNames } from './types';
import type { Role, Session, User } from './types';

// Die Kontenliste ist nur für die Administration erreichbar; das Backend prüft die Rechte erneut.
export default function Users({
  users,
  session,
  onSaved,
}: {
  users: User[];
  session: Session;
  onSaved: (message: string) => Promise<void>;
}) {
  const [edit, setEdit] = useState<User | 'new' | null>(null);
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">DEIN ARBEITSBEREICH</span>
          <h1>Benutzerverwaltung</h1>
          <p>Konten und Rollen in deinem Arbeitsbereich verwalten.</p>
        </div>
        <button className="button primary" onClick={() => setEdit('new')}>
          <Icon name="plus" />
          Benutzerkonto anlegen
        </button>
      </div>
      <section className="panel" aria-labelledby="users-heading">
        <div className="panel-heading">
          <div>
            <h2 id="users-heading">
              Dein Team <span className="count-badge">{users.length}</span>
            </h2>
            <p>{session.tenantName}</p>
          </div>
        </div>
        <table className="user-table responsive-table">
          <caption className="sr-only">Benutzerkonten des aktuellen Mandanten</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Benutzername</th>
              <th scope="col">Rolle</th>
              <th scope="col">Aktion</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td data-label="Name">
                  <strong>{user.displayName}</strong>
                  {user.id === session.id && <span className="you-label">Du</span>}
                </td>
                <td data-label="Benutzername">{user.username}</td>
                <td data-label="Rolle">
                  <span className="badge role-badge">{roleNames[user.role]}</span>
                </td>
                <td>
                  <button
                    className="button small secondary"
                    onClick={() => setEdit(user)}
                    aria-label={`Rolle für ${user.displayName} ändern`}
                    disabled={user.id === session.id}
                  >
                    Rolle ändern
                  </button>
                  {user.id === session.id && (
                    <small className="self-role-hint">Eigene Rolle bleibt geschützt.</small>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="roles-note" aria-label="Rollen und Rechte">
        <h2>Die Rollen auf einen Blick</h2>
        <div>
          <p>
            <strong>Administration</strong>
            <span>Konten, Rollen, Projekte und Aufgaben verwalten.</span>
          </p>
          <p>
            <strong>Projektleitung</strong>
            <span>Projekte mit dem Team organisieren und Aufgaben bearbeiten.</span>
          </p>
          <p>
            <strong>Mitarbeitende</strong>
            <span>Aufgaben in zugeordneten Projekten bearbeiten.</span>
          </p>
        </div>
      </section>
      {edit && (
        <UserForm
          user={edit === 'new' ? null : edit}
          onClose={() => setEdit(null)}
          onSaved={async (message) => {
            await onSaved(message);
            setEdit(null);
          }}
        />
      )}
    </>
  );
}
// Bestehende Konten ändern hier nur ihre Rolle; neue Konten benötigen zusätzlich Zugangsdaten.
function UserForm({
  user,
  onClose,
  onSaved,
}: {
  user: User | null;
  onClose: () => void;
  onSaved: (message: string) => Promise<void>;
}) {
  const [username, setUsername] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>(user?.role ?? 'MEMBER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  // Verhindert doppelte Anfragen, bevor React die Schaltflächen deaktiviert hat.
  const lock = useRef(false);
  const errorRef = useRef<HTMLDivElement>(null);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      if (user) {
        await mutate(`/users/${user.id}/role`, 'PUT', { role });
      } else {
        await mutate('/users', 'POST', {
          username: username.trim(),
          displayName: name.trim(),
          password,
          role,
        });
      }
      await onSaved(
        user
          ? `Die Rolle für ${user.displayName} wurde geändert.`
          : 'Das Benutzerkonto wurde angelegt.',
      );
    } catch (err) {
      setError(errorMessage(err));
      setTimeout(() => errorRef.current?.focus(), 0);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return (
    <Modal
      title={user ? 'Benutzerrolle ändern' : 'Benutzerkonto anlegen'}
      subtitle={
        user
          ? `${user.displayName} · ${user.username}`
          : 'Ein neues Konto für deinen Arbeitsbereich.'
      }
      busy={busy}
      onClose={onClose}
    >
      <form onSubmit={save}>
        <div className="form-body">
          {error && (
            <div className="notice error" ref={errorRef} tabIndex={-1} role="alert">
              {error}
            </div>
          )}
          {!user && (
            <>
              <label htmlFor="new-displayName">
                Anzeigename <span className="required">*</span>
              </label>
              <input
                id="new-displayName"
                name="displayName"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                minLength={1}
                maxLength={80}
                disabled={busy}
                autoFocus
                autoComplete="off"
              />
              <label htmlFor="new-username">
                Benutzername <span className="required">*</span>
              </label>
              <input
                id="new-username"
                name="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                minLength={3}
                maxLength={40}
                pattern="[a-zA-Z0-9._-]+"
                title="Mindestens 3 Zeichen: Buchstaben, Ziffern, Punkt, Unterstrich oder Bindestrich."
                disabled={busy}
                autoComplete="off"
                autoCapitalize="none"
              />
              <p className="field-hint">
                Buchstaben, Ziffern, Punkt, Unterstrich und Bindestrich sind erlaubt.
              </p>
              <label htmlFor="new-password">
                Passwort <span className="required">*</span>
              </label>
              <input
                id="new-password"
                name="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                maxLength={72}
                disabled={busy}
                autoComplete="new-password"
              />
              <p className="field-hint">
                8 bis 72 Zeichen. Sonderzeichen können zusätzlichen Platz benötigen. Das Passwort
                kann nach dem Speichern nicht angezeigt werden.
              </p>
            </>
          )}
          <label htmlFor="new-role">Rolle</label>
          <select
            id="new-role"
            name="role"
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            disabled={busy}
            autoFocus={Boolean(user)}
          >
            {Object.entries(roleNames).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <p className="field-hint">
            Die Rolle legt die möglichen Aktionen fest. Die Projektzuordnung steuert den Zugriff auf
            einzelne Projekte.
          </p>
        </div>
        <FormActions
          busy={busy}
          onCancel={onClose}
          label={user ? 'Rolle speichern' : 'Benutzerkonto anlegen'}
        />
      </form>
    </Modal>
  );
}
