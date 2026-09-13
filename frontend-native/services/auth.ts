import { AUTH_API_URL } from "../constants/api";

export type AuthRegisterRequest = {
  nombre: string;
  email: string;
  password: string;
};

export type AuthLoginRequest = {
  email: string;
  password: string;
};

export type AuthVerifyRequest = {
  codigo: string;
};

export type AuthRegisterResponse = {
  token: string;
  tipo: string;
};

export type AuthLoginResponse = {
  token: string;
  tipo: string;
  mensaje: string;
};

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (!AUTH_API_URL) {
    throw new Error("Falta EXPO_PUBLIC_API_BASE_URL. Configurá la URL pública del backend.");
  }

  const response = await fetch(`${AUTH_API_URL}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const isJson = contentType.includes("application/json");
  const payload = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof payload === "string"
      ? payload
      : payload?.mensaje ?? payload?.message ?? payload?.error ?? "Error de autenticación";
    throw new Error(message);
  }

  return payload as T;
}

export async function registerUser(payload: AuthRegisterRequest): Promise<AuthRegisterResponse> {
  return request<AuthRegisterResponse>("/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function loginUser(payload: AuthLoginRequest): Promise<AuthLoginResponse> {
  return request<AuthLoginResponse>("/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function verifyCode(payload: AuthVerifyRequest, tokenPreAuth: string): Promise<AuthRegisterResponse> {
  return request<AuthRegisterResponse>("/verificar-codigo", {
    method: "POST",
    body: JSON.stringify(payload),
    headers: {
      Authorization: `Bearer ${tokenPreAuth}`,
    },
  });
}
