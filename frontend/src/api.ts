// Neben der Meldung bleibt der HTTP-Status erhalten, z. B. für abgelaufene Sitzungen.
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}

// Cookies bleiben an dieselbe Origin gebunden; der Vite-Proxy leitet /api weiter.
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { credentials: 'same-origin', ...options });
  } catch {
    throw new ApiError(
      'Die Verbindung ist unterbrochen. Prüfe die Verbindung und versuche es erneut.',
      0,
    );
  }
  const text = await response.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    body = undefined;
  }
  if (!response.ok) {
    const supplied =
      body && typeof body === 'object' && 'message' in body ? String(body.message) : '';
    // Fachliche Fehlermeldungen des Servers haben Vorrang vor der Standardmeldung.
    throw new ApiError(supplied || statusMessage(response.status), response.status);
  }
  return body as T;
}

// Vor jeder Änderung wird ein aktueller CSRF-Token aus derselben Sitzung geholt.
export async function mutate<T>(
  path: string,
  method: 'POST' | 'PUT',
  data?: unknown,
  form = false,
): Promise<T> {
  const csrf = await request<{ token: string; headerName: string }>('/csrf');
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  let body: string | undefined;
  if (data !== undefined) {
    // Die Anmeldung verwendet Formulardaten, alle anderen Änderungen JSON.
    if (form) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
      body = String(data);
    } else {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(data);
    }
  }
  return request<T>(path, { method, headers, body });
}

function statusMessage(status: number): string {
  if (status === 401) return 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.';
  if (status === 403) return 'Du hast für diese Aktion keine Berechtigung.';
  if (status === 404) return 'Der angeforderte Eintrag wurde nicht gefunden.';
  if (status >= 500) return 'Der Server ist gerade nicht erreichbar. Bitte versuche es erneut.';
  return 'Die Aktion konnte nicht gespeichert werden. Bitte prüfe deine Eingaben.';
}

// Auch unbekannte Fehler liefern eine verständliche Meldung für die Oberfläche.
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.';
}
