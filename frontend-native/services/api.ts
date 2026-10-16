import {
  clearAuthSession,
  getAuthSession,
  setAuthSession,
  type StoredAuthSession,
} from "./auth-storage";

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_URL ?? "").trim().replace(/\/$/, "");

const REFRESH_MARGIN_MS = 30_000;
let unauthorizedHandler: (() => void) | null = null;
let refreshPromise: Promise<AuthSession> | null = null;

export type PreAuthResponse = {
  token: string;
  tipo: string;
  mensaje: string;
};

export type AuthSession = StoredAuthSession;

export type UsuarioResponse = {
  idUsuario: string;
  nombre: string;
  email: string;
  esPremium: boolean;
};

export type MascotaRequest = {
  nombre: string;
  especie: string;
  raza?: string;
  fechaNacimiento?: string;
  fechaAproximada: boolean;
  edadValor?: number;
  edadUnidad?: string;
  fotoPerfil?: string;
  pesoActual?: number;
  notas?: string;
};

export type MascotaResponse = {
  idMascota: string;
  propietarioId: string;
  nombre: string;
  especie: string;
  raza: string | null;
  fechaNacimiento: string | null;
  fechaAproximada: boolean;
  edadValor: number | null;
  edadUnidad: string | null;
  fotoPerfil: string | null;
  pesoActual: number | null;
  notas: string | null;
  creadoEn: string;
};

export type RecordatorioRequest = {
  mascotaId?: string;
  titulo: string;
  tipo: "PIPETA_ANTIPARASITARIO" | "VISITA_VETERINARIO" | "MEDICACION" | "VACUNA" | "OTRO";
  fechaHoraInicio: string;
  modalidad?: string;
  intervaloValor?: number;
  intervaloUnidad?: string;
  diasSemana?: string;
  fechaFin?: string;
};

export type RecordatorioResponse = {
  idRecordatorio: string;
  mascotaId: string;
  titulo: string;
  tipo: string;
  fechaHoraInicio: string;
  modalidad: string | null;
  intervaloValor: number | null;
  intervaloUnidad: string | null;
  diaSemana: string | null;
  fechaFin: string | null;
  creadoPorId: string;
  creadoEn: string;
  confirmadoPorId: string | null;
  confirmadoEn: string | null;
  estado: string;
};

type ApiErrorBody = {
  codigo?: string;
  message?: string;
  error?: string;
  mensaje?: string;
};

type RequestOptions = RequestInit & {
  authenticated?: boolean;
  retryAfterRefresh?: boolean;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

function requireApiBaseUrl() {
  if (!API_BASE_URL) {
    throw new Error("Falta EXPO_PUBLIC_API_URL. Configurá la URL accesible del backend.");
  }
}

function isAccessTokenNearExpiration(session: AuthSession) {
  const expiration = Date.parse(session.accessTokenExpiraEn);
  return !Number.isFinite(expiration) || expiration <= Date.now() + REFRESH_MARGIN_MS;
}

async function parseError(response: Response) {
  let message = `El servidor respondió con estado ${response.status}`;
  let code: string | undefined;

  try {
    const body = (await response.json()) as ApiErrorBody;
    code = body.codigo;
    message = body.mensaje ?? body.message ?? body.error ?? message;
  } catch {
    // Algunas respuestas HTTP no incluyen un cuerpo JSON.
  }

  return new ApiError(response.status, code, message);
}

async function notifyExpiredSession() {
  await clearAuthSession();
  unauthorizedHandler?.();
}

async function performRefresh(): Promise<AuthSession> {
  const currentSession = await getAuthSession();
  if (!currentSession?.refreshToken) {
    throw new ApiError(401, "NO_AUTENTICADO", "La sesión venció. Iniciá sesión nuevamente.");
  }

  const renewedSession = await request<AuthSession>("/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken: currentSession.refreshToken }),
    authenticated: false,
    retryAfterRefresh: false,
  });
  await setAuthSession(renewedSession);
  return renewedSession;
}

export async function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = performRefresh()
      .catch(async (error) => {
        await notifyExpiredSession();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }

  return refreshPromise;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  requireApiBaseUrl();

  const {
    authenticated = true,
    retryAfterRefresh = true,
    ...fetchOptions
  } = options;
  const headers = new Headers(fetchOptions.headers);
  headers.set("Accept", "application/json");
  if (fetchOptions.body != null) headers.set("Content-Type", "application/json");

  if (authenticated && !headers.has("Authorization")) {
    let session = await getAuthSession();
    if (session && isAccessTokenNearExpiration(session)) {
      session = await refreshSession();
    }
    if (session) headers.set("Authorization", `${session.tipo} ${session.accessToken}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...fetchOptions, headers });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Revisá EXPO_PUBLIC_API_URL y que el backend esté iniciado.");
  }

  if (response.status === 401 && authenticated && retryAfterRefresh) {
    try {
      const session = await refreshSession();
      headers.set("Authorization", `${session.tipo} ${session.accessToken}`);
      return request<T>(path, {
        ...fetchOptions,
        headers,
        authenticated: true,
        retryAfterRefresh: false,
      });
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(401, "NO_AUTENTICADO", "La sesión venció. Iniciá sesión nuevamente.");
    }
  }

  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;

  return (await response.json()) as T;
}

export function register(nombre: string, email: string, password: string) {
  return request<PreAuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ nombre, email, password }),
    authenticated: false,
  });
}

export function login(email: string, password: string) {
  return request<PreAuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
    authenticated: false,
  });
}

export async function verifyCode(tokenPreAuth: string, codigo: string) {
  const session = await request<AuthSession>("/auth/verificar-codigo", {
    method: "POST",
    headers: { Authorization: `Bearer ${tokenPreAuth}` },
    body: JSON.stringify({ codigo }),
    authenticated: false,
  });
  await setAuthSession(session);
  return session;
}

export async function logout() {
  const session = await getAuthSession();

  try {
    if (session?.refreshToken) {
      await request<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: session.refreshToken }),
        authenticated: false,
        retryAfterRefresh: false,
      });
    }
  } finally {
    await clearAuthSession();
  }
}

export function getCurrentUser() {
  return request<UsuarioResponse>("/usuario/me");
}

export function getPets() {
  return request<MascotaResponse[]>("/mascotas");
}

export function getPet(id: string) {
  return request<MascotaResponse>(`/mascotas/${id}`);
}

export function createPet(data: MascotaRequest) {
  return request<MascotaResponse>("/mascotas", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function updatePet(id: string, data: MascotaRequest) {
  return request<MascotaResponse>(`/mascotas/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function deletePet(id: string) {
  return request<void>(`/mascotas/${id}`, { method: "DELETE" });
}

export function createReminder(mascotaId: string, data: RecordatorioRequest) {
  return request<RecordatorioResponse>(`/mascotas/${mascotaId}/recordatorios`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function getRemindersByPet(mascotaId: string) {
  return request<RecordatorioResponse[]>(`/mascotas/${mascotaId}/recordatorios`);
}

export function getReminder(id: string) {
  return request<RecordatorioResponse>(`/recordatorios/${id}`);
}

export function updateReminder(id: string, data: RecordatorioRequest) {
  return request<RecordatorioResponse>(`/recordatorios/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export function changeReminderState(id: string, nuevoEstado: string) {
  return request<RecordatorioResponse>(
    `/recordatorios/${id}/estado?nuevoEstado=${encodeURIComponent(nuevoEstado)}`,
    { method: "PATCH" },
  );
}

export function confirmReminder(id: string) {
  return request<RecordatorioResponse>(`/recordatorios/${id}/confirmacion`, { method: "PATCH" });
}

export function unconfirmReminder(id: string) {
  return request<RecordatorioResponse>(`/recordatorios/${id}/confirmacion`, { method: "DELETE" });
}

export function deleteReminder(id: string) {
  return request<void>(`/recordatorios/${id}`, { method: "DELETE" });
}
