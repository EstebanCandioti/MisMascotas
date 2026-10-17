export type ApiErrorKind =
  | "backend"
  | "http"
  | "network"
  | "session-expired"
  | "unknown";

export type BackendErrorBody = {
  codigo: string;
  mensaje: string;
  timestamp: string;
};

type ApiErrorOptions = {
  kind: ApiErrorKind;
  status?: number;
  code?: string;
  timestamp?: string;
  cause?: unknown;
};

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly code?: string;
  readonly timestamp?: string;

  constructor(message: string, options: ApiErrorOptions) {
    super(message, { cause: options.cause });
    this.name = "ApiError";
    this.kind = options.kind;
    this.status = options.status;
    this.code = options.code;
    this.timestamp = options.timestamp;
  }
}

export function isBackendErrorBody(value: unknown): value is BackendErrorBody {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.codigo === "string"
    && typeof candidate.mensaje === "string"
    && typeof candidate.timestamp === "string";
}

export function getApiErrorMessage(error: unknown): string {
  return normalizeApiError(error).message;
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof Error && error.message) {
    return new ApiError(error.message, { kind: "unknown", cause: error });
  }
  return new ApiError("Ocurrió un error inesperado. Intentá nuevamente.", {
    kind: "unknown",
    cause: error,
  });
}
