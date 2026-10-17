import type { StoredAuthSession, StoredPreAuth } from "@/lib/auth/session-storage";

export type AuthSession = StoredAuthSession;
export type PreAuthResponse = StoredPreAuth;

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = LoginRequest & {
  nombre: string;
};

export type VerifyCodeRequest = {
  codigo: string;
};

export type RefreshTokenRequest = {
  refreshToken: string;
};

export type UserProfile = {
  idUsuario: string;
  nombre: string;
  email: string;
  esPremium: boolean;
};

export type AuthStatus =
  | "loading"
  | "anonymous"
  | "awaiting-code"
  | "authenticated";
