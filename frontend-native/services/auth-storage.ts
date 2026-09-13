import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";

async function withFallback<T>(secureOperation: () => Promise<T>, fallbackOperation: () => Promise<T>) {
  try {
    return await secureOperation();
  } catch {
    return fallbackOperation();
  }
}

export function getAuthToken(key: string) {
  return withFallback(
    () => SecureStore.getItemAsync(key),
    () => AsyncStorage.getItem(key),
  );
}

export function setAuthToken(key: string, token: string) {
  return withFallback(
    () => SecureStore.setItemAsync(key, token),
    () => AsyncStorage.setItem(key, token),
  );
}

export function deleteAuthToken(key: string) {
  return withFallback(
    () => SecureStore.deleteItemAsync(key),
    () => AsyncStorage.removeItem(key),
  );
}