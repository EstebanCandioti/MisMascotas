export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, "");

export const AUTH_API_URL = API_BASE_URL ? `${API_BASE_URL}/auth` : "";
