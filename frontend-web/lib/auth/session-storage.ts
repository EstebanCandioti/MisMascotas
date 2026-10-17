export type StoredAuthSession = {
  accessToken: string;
  refreshToken: string;
  tipo: string;
  accessTokenExpiraEn: string;
};

export type StoredPreAuth = {
  token: string;
  tipo: string;
  mensaje: string;
};

const SESSION_KEY = "mis-mascotas.auth.session";
const PRE_AUTH_KEY = "mis-mascotas.auth.pre-auth";

function getStorage(): Storage | null {
  return typeof window === "undefined" ? null : window.sessionStorage;
}

function readJson<T>(key: string, validate: (value: unknown) => value is T): T | null {
  const storage = getStorage();
  if (!storage) return null;

  try {
    const raw = storage.getItem(key);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (validate(value)) return value;
  } catch {
    // Invalid or unavailable storage is treated as an empty session.
  }

  storage.removeItem(key);
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isSession(value: unknown): value is StoredAuthSession {
  return isRecord(value)
    && typeof value.accessToken === "string"
    && typeof value.refreshToken === "string"
    && typeof value.tipo === "string"
    && typeof value.accessTokenExpiraEn === "string";
}

function isPreAuth(value: unknown): value is StoredPreAuth {
  return isRecord(value)
    && typeof value.token === "string"
    && typeof value.tipo === "string"
    && typeof value.mensaje === "string";
}

export function readSession(): StoredAuthSession | null {
  return readJson(SESSION_KEY, isSession);
}

export function saveSession(session: StoredAuthSession): void {
  const storage = getStorage();
  if (!storage) return;
  storage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  getStorage()?.removeItem(SESSION_KEY);
}

export function readPreAuth(): StoredPreAuth | null {
  return readJson(PRE_AUTH_KEY, isPreAuth);
}

export function savePreAuth(preAuth: StoredPreAuth): void {
  const storage = getStorage();
  if (!storage) return;
  storage.setItem(PRE_AUTH_KEY, JSON.stringify(preAuth));
}

export function clearPreAuth(): void {
  getStorage()?.removeItem(PRE_AUTH_KEY);
}
