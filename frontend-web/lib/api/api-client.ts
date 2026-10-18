import { ApiError, isBackendErrorBody } from "@/lib/api/api-error";
import type { StoredAuthSession } from "@/lib/auth/session-storage";
import { API_URL } from "@/lib/config/api-config";

type AuthBridge = {
  getSession: () => StoredAuthSession | null;
  refreshSession: (refreshToken: string) => Promise<StoredAuthSession>;
  onSessionExpired: () => void;
};

type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  authenticated?: boolean;
  bearerToken?: string;
};

const REFRESH_WINDOW_MS = 30_000;

let authBridge: AuthBridge | null = null;
let refreshInFlight: Promise<StoredAuthSession> | null = null;

export function configureApiAuth(bridge: AuthBridge): () => void {
  authBridge = bridge;
  return () => {
    if (authBridge === bridge) authBridge = null;
  };
}

function buildUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_URL}${normalizedPath}`;
}

function expiresSoon(expiresAt: string): boolean {
  const expiry = Date.parse(expiresAt);
  return !Number.isFinite(expiry) || expiry - Date.now() <= REFRESH_WINDOW_MS;
}

async function refreshSession(): Promise<StoredAuthSession> {
  if (refreshInFlight) return refreshInFlight;
  if (!authBridge) {
    throw new ApiError("La sesión no está disponible.", { kind: "session-expired" });
  }

  const session = authBridge.getSession();
  if (!session?.refreshToken) {
    authBridge.onSessionExpired();
    throw new ApiError("Tu sesión venció. Iniciá sesión nuevamente.", {
      kind: "session-expired",
    });
  }

  refreshInFlight = authBridge.refreshSession(session.refreshToken)
    .catch((cause: unknown) => {
      // Un logout puede limpiar la sesión mientras el refresh está en vuelo.
      // En ese caso no debemos convertir una salida voluntaria en “sesión vencida”.
      if (authBridge?.getSession()) authBridge.onSessionExpired();
      throw new ApiError("Tu sesión venció. Iniciá sesión nuevamente.", {
        kind: "session-expired",
        cause,
      });
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

async function resolveAccessToken(): Promise<string> {
  if (!authBridge) {
    throw new ApiError("La sesión no está disponible.", { kind: "session-expired" });
  }

  let session = authBridge.getSession();
  if (!session) {
    authBridge.onSessionExpired();
    throw new ApiError("Tu sesión venció. Iniciá sesión nuevamente.", {
      kind: "session-expired",
    });
  }

  if (expiresSoon(session.accessTokenExpiraEn)) {
    session = await refreshSession();
  }

  return session.accessToken;
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.status === 205) return undefined;
  const text = await response.text();
  if (!text) return undefined;

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function responseError(response: Response, body: unknown): ApiError {
  if (isBackendErrorBody(body)) {
    return new ApiError(body.mensaje, {
      kind: "backend",
      status: response.status,
      code: body.codigo,
      timestamp: body.timestamp,
    });
  }

  return new ApiError(`La API respondió con estado ${response.status}.`, {
    kind: "http",
    status: response.status,
  });
}

async function execute<T>(
  path: string,
  options: ApiRequestOptions,
  alreadyRetried: boolean,
): Promise<T> {
  const headers = new Headers(options.headers);
  let accessTokenUsed: string | null = null;
  headers.set("Accept", "application/json");

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.bearerToken) {
    headers.set("Authorization", `Bearer ${options.bearerToken}`);
  } else if (options.authenticated) {
    accessTokenUsed = await resolveAccessToken();
    headers.set("Authorization", `Bearer ${accessTokenUsed}`);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path), {
      ...options,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch (cause) {
    throw new ApiError("No se pudo conectar con el backend. Verificá que esté iniciado.", {
      kind: "network",
      cause,
    });
  }

  if (response.status === 401 && options.authenticated && !alreadyRetried) {
    const currentAccessToken = authBridge?.getSession()?.accessToken;
    if (!accessTokenUsed || !currentAccessToken || currentAccessToken === accessTokenUsed) {
      await refreshSession();
    }
    return execute<T>(path, options, true);
  }

  const body = await parseBody(response);
  if (!response.ok) {
    if (response.status === 401 && options.authenticated) {
      authBridge?.onSessionExpired();
      throw new ApiError("Tu sesión venció. Iniciá sesión nuevamente.", {
        kind: "session-expired",
        status: response.status,
      });
    }
    throw responseError(response, body);
  }

  return body as T;
}

export function apiRequest<T = void>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  return execute<T>(path, options, false);
}
