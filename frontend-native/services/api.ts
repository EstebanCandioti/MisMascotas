import { Platform } from "react-native";
import { deleteAuthToken, getAuthToken } from "./auth-storage";

const defaultBaseUrl = Platform.OS === "android"
  ? "http://10.0.2.2:8080"
  : "http://localhost:8080";

const API_BASE_URL = (process.env.EXPO_PUBLIC_API_URL ?? defaultBaseUrl).replace(/\/$/, "");
export const AUTH_TOKEN_KEY = "mismascotas-auth-token-v2";

const PUBLIC_PATHS = ["/auth/register", "/auth/login", "/auth/verificar-codigo"];
let unauthorizedHandler: (() => void) | null = null;

export type AuthTokenResponse = {
  token: string;
  tipo: string;
};

export type LoginResponse = AuthTokenResponse & {
  mensaje: string;
};

export type UsuarioResponse = {
  idUsuario: string;
  nombre: string;
  email: string;
  esPremium: boolean;
  activo: boolean;
  creadoEn: string;
};

type ApiErrorBody = {
  codigo?: string;
  message?: string;
  error?: string;
  mensaje?: string;
};

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string | undefined, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");

  if (!PUBLIC_PATHS.includes(path) && !headers.has("Authorization")) {
    const token = await getAuthToken(AUTH_TOKEN_KEY);
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    });
  } catch {
    throw new Error("No se pudo conectar con el servidor. Revisá la URL de la API y que el backend esté iniciado.");
  }

  if (!response.ok) {
    let message = "Ocurrió un error inesperado";
    let code: string | undefined;

    try {
      const body = (await response.json()) as ApiErrorBody;
      code = body.codigo;
      message = body.mensaje ?? body.message ?? body.error ?? message;
    } catch {
      // La respuesta puede no tener un cuerpo JSON.
    }

    if (response.status === 401) {
      await deleteAuthToken(AUTH_TOKEN_KEY);
      unauthorizedHandler?.();
    }

    throw new ApiError(response.status, code, message);
  }

  return (await response.json()) as T;
}

export function register(nombre: string, email: string, password: string) {
  return request<AuthTokenResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ nombre, email, password }),
  });
}

export function login(email: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function verifyCode(tokenPreAuth: string, codigo: string) {
  return request<AuthTokenResponse>("/auth/verificar-codigo", {
    method: "POST",
    headers: { Authorization: `Bearer ${tokenPreAuth}` },
    body: JSON.stringify({ codigo }),
  });
}

export function getCurrentUser(token: string) {
  return request<UsuarioResponse>("/usuario/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
}
