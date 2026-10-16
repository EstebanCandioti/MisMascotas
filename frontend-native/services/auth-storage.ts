import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

export const AUTH_SESSION_KEY = "mismascotas-auth-session-v1";
const LEGACY_AUTH_TOKEN_KEY = "mismascotas-auth-token-v2";

export type StoredAuthSession = {
  accessToken: string;
  refreshToken: string;
  tipo: string;
  accessTokenExpiraEn: string;
};

async function withFallback<T>(secureOperation: () => Promise<T>, fallbackOperation: () => Promise<T>) {
  try {
    return await secureOperation();
  } catch {
    return fallbackOperation();
  }
}

async function getItem(key: string) {
  if (Platform.OS === "web") return AsyncStorage.getItem(key);

  return withFallback(
    () => SecureStore.getItemAsync(key),
    () => AsyncStorage.getItem(key),
  );
}

async function setItem(key: string, value: string) {
  if (Platform.OS === "web") return AsyncStorage.setItem(key, value);

  return withFallback(
    () => SecureStore.setItemAsync(key, value),
    () => AsyncStorage.setItem(key, value),
  );
}

async function deleteItem(key: string) {
  if (Platform.OS === "web") return AsyncStorage.removeItem(key);

  return withFallback(
    () => SecureStore.deleteItemAsync(key),
    () => AsyncStorage.removeItem(key),
  );
}

function isStoredAuthSession(value: unknown): value is StoredAuthSession {
  if (!value || typeof value !== "object") return false;

  const session = value as Partial<StoredAuthSession>;
  return Boolean(
    session.accessToken &&
    session.refreshToken &&
    session.tipo &&
    session.accessTokenExpiraEn,
  );
}

export async function getAuthSession(): Promise<StoredAuthSession | null> {
  const serialized = await getItem(AUTH_SESSION_KEY);
  if (!serialized) {
    await deleteItem(LEGACY_AUTH_TOKEN_KEY);
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(serialized);
    if (isStoredAuthSession(parsed)) return parsed;
  } catch {
    // Una sesión corrupta se trata como una sesión vencida.
  }

  await clearAuthSession();
  return null;
}

export async function setAuthSession(session: StoredAuthSession) {
  await setItem(AUTH_SESSION_KEY, JSON.stringify(session));
  await deleteItem(LEGACY_AUTH_TOKEN_KEY);
}

export async function clearAuthSession() {
  await Promise.all([
    deleteItem(AUTH_SESSION_KEY),
    deleteItem(LEGACY_AUTH_TOKEN_KEY),
  ]);
}
