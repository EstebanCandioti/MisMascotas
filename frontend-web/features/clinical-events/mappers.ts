import type { ClinicalEvent, ClinicalEventResponse, ClinicalEventType } from "@/features/clinical-events/types";
import type { Pet } from "@/features/pets/types";

const typeLabels: Record<ClinicalEventType, string> = {
  VACUNA: "Vacuna",
  MEDICACION: "Medicación",
  CONSULTA_VETERINARIA: "Consulta veterinaria",
  PESO: "Control de peso",
};

export function enrichClinicalEvent(response: ClinicalEventResponse, pets: Pet[]): ClinicalEvent {
  return {
    ...response,
    mascotaNombre: pets.find((pet) => pet.idMascota === response.mascotaId)?.nombre ?? "Mascota",
  };
}

export function sortClinicalEvents(events: ClinicalEvent[]): ClinicalEvent[] {
  return [...events].sort((left, right) => Date.parse(right.fecha) - Date.parse(left.fecha));
}

export function clinicalEventTypeLabel(type: ClinicalEventType): string {
  return typeLabels[type] ?? type;
}

export function clinicalEventDateLabel(instant: string): string {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return "Fecha no disponible";
  return new Intl.DateTimeFormat("es-AR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function clinicalEventSummary(event: ClinicalEvent): string {
  if (event.tipo === "PESO" && event.valorPeso !== null) return `${event.valorPeso} kg`;
  if (event.tipo === "CONSULTA_VETERINARIA") return event.motivo || "Consulta veterinaria";
  if (event.tipo === "MEDICACION") return [event.nombre, event.dosis].filter(Boolean).join(" · ");
  return event.nombre || clinicalEventTypeLabel(event.tipo);
}
