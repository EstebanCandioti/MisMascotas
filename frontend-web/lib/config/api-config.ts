const DEFAULT_API_URL = "http://localhost:8080";

function normalizeApiUrl(value: string): string {
  return value.trim().replace(/\/+$/, "");
}

export const API_URL = normalizeApiUrl(
  process.env.NEXT_PUBLIC_API_URL ?? DEFAULT_API_URL,
);
