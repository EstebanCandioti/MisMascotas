import { apiRequest } from "@/lib/api/api-client";
import type {
  AuthSession,
  LoginRequest,
  PreAuthResponse,
  RefreshTokenRequest,
  RegisterRequest,
  UserProfile,
  VerifyCodeRequest,
} from "@/features/auth/types";

export function register(request: RegisterRequest): Promise<PreAuthResponse> {
  return apiRequest("/auth/register", { method: "POST", body: request });
}

export function login(request: LoginRequest): Promise<PreAuthResponse> {
  return apiRequest("/auth/login", { method: "POST", body: request });
}

export function verifyCode(
  request: VerifyCodeRequest,
  preAuthToken: string,
): Promise<AuthSession> {
  return apiRequest("/auth/verificar-codigo", {
    method: "POST",
    body: request,
    bearerToken: preAuthToken,
  });
}

export function refreshAuthSession(refreshToken: string): Promise<AuthSession> {
  const request: RefreshTokenRequest = { refreshToken };
  return apiRequest("/auth/refresh", { method: "POST", body: request });
}

export function logoutSession(refreshToken: string): Promise<void> {
  const request: RefreshTokenRequest = { refreshToken };
  return apiRequest("/auth/logout", { method: "POST", body: request });
}

export function getCurrentUser(): Promise<UserProfile> {
  return apiRequest("/usuario/me", { authenticated: true });
}
