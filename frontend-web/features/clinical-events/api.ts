import type { ClinicalEventRequest, ClinicalEventResponse } from "@/features/clinical-events/types";
import { apiRequest } from "@/lib/api/api-client";

export function listClinicalEventsByPet(petId: string): Promise<ClinicalEventResponse[]> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/eventos-clinicos`, { authenticated: true });
}

export function createClinicalEvent(petId: string, request: ClinicalEventRequest): Promise<ClinicalEventResponse> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/eventos-clinicos`, {
    method: "POST",
    body: request,
    authenticated: true,
  });
}
