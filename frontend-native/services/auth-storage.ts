import AsyncStorage from "@react-native-async-storage/async-storage";
import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

async function withFallback<T>(secureOperation: () => Promise<T>, fallbackOperation: () => Promise<T>) {
  try {
    return await secureOperation();
  } catch {
    return fallbackOperation();
  }
}

export function getAuthToken(key: string) {
  if (Platform.OS === "web") return AsyncStorage.getItem(key);

  return withFallback(
    () => SecureStore.getItemAsync(key),
    () => AsyncStorage.getItem(key),
  );
}

export function setAuthToken(key: string, token: string) {
  if (Platform.OS === "web") return AsyncStorage.setItem(key, token);

  return withFallback(
    () => SecureStore.setItemAsync(key, token),
    () => AsyncStorage.setItem(key, token),
  );
}

export function deleteAuthToken(key: string) {
  if (Platform.OS === "web") return AsyncStorage.removeItem(key);

  return withFallback(
    () => SecureStore.deleteItemAsync(key),
    () => AsyncStorage.removeItem(key),
  );
}