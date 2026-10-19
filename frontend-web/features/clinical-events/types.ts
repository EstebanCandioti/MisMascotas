export const clinicalEventTypes = [
  "VACUNA",
  "MEDICACION",
  "CONSULTA_VETERINARIA",
  "PESO",
] as const;

export type ClinicalEventType = (typeof clinicalEventTypes)[number];

export type ClinicalEventResponse = {
  idEvento: string;
  mascotaId: string;
  registradoPorId: string;
  registradoPorNombre: string | null;
  tipo: ClinicalEventType;
  fecha: string;
  nombre: string | null;
  dosis: string | null;
  valorPeso: number | null;
  motivo: string | null;
  diagnostico: string | null;
  observaciones: string | null;
  creadoEn: string;
};

export type ClinicalEvent = ClinicalEventResponse & {
  mascotaNombre: string;
};

type ClinicalEventBaseRequest = {
  mascotaId: string;
  fecha: string;
};

export type ClinicalEventRequest =
  | (ClinicalEventBaseRequest & { tipo: "VACUNA"; nombre: string; observaciones?: string })
  | (ClinicalEventBaseRequest & { tipo: "MEDICACION"; nombre: string; dosis: string; observaciones?: string })
  | (ClinicalEventBaseRequest & { tipo: "CONSULTA_VETERINARIA"; motivo: string; diagnostico?: string; observaciones?: string })
  | (ClinicalEventBaseRequest & { tipo: "PESO"; valorPeso: number });

export type ClinicalEventsStatus = "idle" | "loading" | "success" | "error";
