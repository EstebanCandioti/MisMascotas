"use client";

import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  getCurrentUser,
  login as loginRequest,
  logoutSession,
  refreshAuthSession,
  register as registerRequest,
  verifyCode as verifyCodeRequest,
} from "@/features/auth/api";
import type {
  AuthSession,
  AuthStatus,
  LoginRequest,
  PreAuthResponse,
  RegisterRequest,
  UserProfile,
} from "@/features/auth/types";
import { configureApiAuth } from "@/lib/api/api-client";
import { ApiError } from "@/lib/api/api-error";
import {
  clearPreAuth,
  clearSession,
  readPreAuth,
  readSession,
  savePreAuth,
  saveSession,
} from "@/lib/auth/session-storage";

type AuthContextValue = {
  status: AuthStatus;
  user: UserProfile | null;
  preAuthMessage: string;
  notice: string;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  verifyCode: (code: string) => Promise<void>;
  cancelVerification: () => void;
  logout: () => Promise<void>;
  reloadProfile: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserProfile | null>(null);
  const [preAuth, setPreAuth] = useState<PreAuthResponse | null>(null);
  const [notice, setNotice] = useState("");

  const expireSession = useCallback(() => {
    clearSession();
    clearPreAuth();
    setUser(null);
    setPreAuth(null);
    setNotice("Tu sesión venció. Iniciá sesión nuevamente.");
    setStatus("anonymous");
  }, []);

  const applyRefreshedSession = useCallback(async (refreshToken: string) => {
    const session = await refreshAuthSession(refreshToken);

    // El logout puede ejecutarse mientras el backend está rotando el token.
    // Si la sesión original ya no es la vigente, no resucitarla con la respuesta tardía.
    if (readSession()?.refreshToken !== refreshToken) {
      try {
        await logoutSession(session.refreshToken);
      } catch {
        // La limpieza local ya ocurrió; la revocación remota es best effort.
      }
      throw new ApiError("La sesión fue cerrada.", { kind: "session-expired" });
    }

    saveSession(session);
    setStatus("authenticated");
    return session;
  }, []);

  const loadProfile = useCallback(async () => {
    const profile = await getCurrentUser();
    setUser(profile);
  }, []);

  useEffect(() => configureApiAuth({
    getSession: readSession,
    refreshSession: applyRefreshedSession,
    onSessionExpired: expireSession,
  }), [applyRefreshedSession, expireSession]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      const session = readSession();
      if (session) {
        setStatus("authenticated");
        void loadProfile().catch(() => {
          // The API layer handles an expired session. Network errors leave the
          // valid local session available so the user can retry later.
        });
        return;
      }

      const pending = readPreAuth();
      if (pending) {
        setPreAuth(pending);
        setStatus("awaiting-code");
        return;
      }

      setStatus("anonymous");
    });

    return () => {
      active = false;
    };
  }, [loadProfile]);

  const beginVerification = useCallback((response: PreAuthResponse) => {
    clearSession();
    savePreAuth(response);
    setNotice("");
    setPreAuth(response);
    setStatus("awaiting-code");
  }, []);

  const login = useCallback(async (request: LoginRequest) => {
    beginVerification(await loginRequest(request));
  }, [beginVerification]);

  const register = useCallback(async (request: RegisterRequest) => {
    beginVerification(await registerRequest(request));
  }, [beginVerification]);

  const verifyCode = useCallback(async (code: string) => {
    if (!preAuth) throw new Error("No hay una verificación pendiente.");
    const session: AuthSession = await verifyCodeRequest({ codigo: code }, preAuth.token);
    saveSession(session);
    clearPreAuth();
    setPreAuth(null);
    setNotice("");
    setStatus("authenticated");
    await loadProfile();
  }, [loadProfile, preAuth]);

  const cancelVerification = useCallback(() => {
    clearPreAuth();
    setPreAuth(null);
    setNotice("");
    setStatus("anonymous");
  }, []);

  const logout = useCallback(async () => {
    const session = readSession();
    clearSession();
    clearPreAuth();
    setUser(null);
    setPreAuth(null);
    setNotice("");
    setStatus("anonymous");

    if (session?.refreshToken) {
      try {
        await logoutSession(session.refreshToken);
      } catch {
        // Local logout is authoritative. A remote failure must never leave the
        // user trapped in an authenticated UI.
      }
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    preAuthMessage: preAuth?.mensaje ?? "",
    notice,
    login,
    register,
    verifyCode,
    cancelVerification,
    logout,
    reloadProfile: loadProfile,
  }), [
    cancelVerification,
    loadProfile,
    login,
    logout,
    notice,
    preAuth?.mensaje,
    register,
    status,
    user,
    verifyCode,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth debe utilizarse dentro de AuthProvider");
  return context;
}
