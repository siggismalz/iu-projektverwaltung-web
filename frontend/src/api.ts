export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

// Cookies bleiben an dieselbe Origin gebunden; der Vite-Proxy leitet /api weiter.
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { credentials: 'same-origin', ...options });
  } catch {
    throw new ApiError('Die Verbindung ist unterbrochen. Prüfe die Verbindung und versuche es erneut.', 0);
  }
  const text = await response.text();
  let body: unknown;
  try { body = text ? JSON.parse(text) : undefined; } catch { body = undefined; }
  if (!response.ok) {
    const supplied = body && typeof body === 'object' && 'message' in body ? String(body.message) : '';
    const fallback = response.status === 401 ? 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.'
      : response.status === 403 ? 'Du hast für diese Aktion keine Berechtigung.'
      : response.status === 404 ? 'Der angeforderte Eintrag wurde nicht gefunden.'
      : response.status >= 500 ? 'Der Server ist gerade nicht erreichbar. Bitte versuche es erneut.'
      : 'Die Aktion konnte nicht gespeichert werden. Bitte prüfe deine Eingaben.';
    throw new ApiError(supplied || fallback, response.status);
  }
  return body as T;
}

// Vor jeder Änderung wird ein aktueller CSRF-Token aus derselben Sitzung geholt.
export async function mutate<T>(path: string, method: 'POST' | 'PUT', data?: unknown, form = false): Promise<T> {
  const csrf = await request<{ token: string; headerName: string }>('/csrf');
  const headers: Record<string, string> = { [csrf.headerName]: csrf.token };
  if (data !== undefined) headers['Content-Type'] = form
    ? 'application/x-www-form-urlencoded;charset=UTF-8' : 'application/json';
  return request<T>(path, {
    method, headers,
    body: data === undefined ? undefined : form ? String(data) : JSON.stringify(data),
  });
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.';
}
