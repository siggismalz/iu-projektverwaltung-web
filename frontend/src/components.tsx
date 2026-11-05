import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

type IconName =
  'projects' | 'users' | 'logout' | 'plus' | 'arrow' | 'search' | 'check' | 'close' | 'archive';
// Kleine SVG-Symbole liegen direkt im Projekt und benötigen keine zusätzliche Bibliothek.
const paths: Record<IconName, string> = {
  projects: 'M3 7h7l2-3h9v16H3V7Z M3 10h18',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  logout: 'M9 21H3V3h6 M13 17l5-5-5-5 M7 12h12',
  plus: 'M12 5v14 M5 12h14',
  arrow: 'm9 5 7 7-7 7',
  search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  check: 'm5 12 4 4L19 6',
  close: 'm6 6 12 12 M6 18 18 6',
  archive: 'M3 3h18v5H3V3Z M5 8v13h14V8 M9 12h6',
};

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}

export function Brand() {
  return (
    <span className="brand">
      <span className="brand-mark" aria-hidden="true">
        p<span> /</span>
      </span>
      <span>
        projektwerk<span className="brand-sub">PROJEKTVERWALTUNG</span>
      </span>
    </span>
  );
}

// Gemeinsame Meldungen sorgen auf allen Seiten für dieselbe Fehler- und Ladeanzeige.
export function ErrorNotice({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="notice error" role="alert">
      <div>
        <strong>Das hat nicht geklappt.</strong>
        <p>{message}</p>
      </div>
      {onRetry && (
        <button className="button secondary" onClick={onRetry}>
          Erneut versuchen
        </button>
      )}
    </div>
  );
}

export function Loading({ text = 'Daten werden geladen …' }: { text?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {text}
    </div>
  );
}

export function Progress({ done, total }: { done: number; total: number }) {
  // Ohne Aufgaben werden 0 % gezeigt; max=1 hält das HTML-Element trotzdem gültig.
  const percent = total ? Math.round((done / total) * 100) : 0;
  return (
    <div className="progress-group">
      <div className="progress-caption">
        <span>
          {done} / {total} Aufgaben
        </span>
        <strong>{percent} %</strong>
      </div>
      <progress
        value={done}
        max={total || 1}
        aria-label={`${done} von ${total} Aufgaben erledigt`}
      />
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Icon name="projects" size={28} />
      </span>
      <h2>{title}</h2>
      <p>{children}</p>
    </div>
  );
}

// Der native Dialog hält den Tastaturfokus fest und gibt ihn beim Schließen zurück.
export function Modal({
  title,
  subtitle,
  busy,
  onClose,
  children,
}: {
  title: string;
  subtitle?: string;
  busy?: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  useEffect(() => {
    triggerRef.current = document.activeElement as HTMLElement;
    const dialog = dialogRef.current!;
    dialog.showModal();
    // Reacts autoFocus wird bei noch geschlossenem Dialog ausgeführt.
    // Erst nach showModal kann das erste Formularfeld tatsächlich fokussiert werden.
    dialog.querySelector<HTMLElement>('input:not([type="checkbox"]), textarea, select')?.focus();
    return () => {
      dialog.close();
      triggerRef.current?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="dialog-title"
      onCancel={(e) => {
        // Escape darf einen Dialog während des Speicherns nicht schließen.
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <header className="dialog-header">
        <div>
          <h2 id="dialog-title">{title}</h2>
          {subtitle && <p>{subtitle}</p>}
        </div>
        <button
          type="button"
          className="icon-button"
          aria-label="Dialog schließen"
          onClick={onClose}
          disabled={busy}
        >
          <Icon name="close" />
        </button>
      </header>
      {children}
    </dialog>
  );
}

// Alle Dialoge sperren während einer laufenden Speicherung dieselben Schaltflächen.
export function FormActions({
  busy,
  onCancel,
  label = 'Speichern',
}: {
  busy: boolean;
  onCancel: () => void;
  label?: string;
}) {
  return (
    <footer className="form-actions">
      <button type="button" className="button secondary" disabled={busy} onClick={onCancel}>
        Abbrechen
      </button>
      <button type="submit" className="button primary" disabled={busy}>
        {busy ? 'Wird gespeichert …' : label}
      </button>
    </footer>
  );
}
