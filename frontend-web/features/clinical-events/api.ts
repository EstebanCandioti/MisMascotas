import type { ClinicalEventRequest, ClinicalEventResponse } from "@/features/clinical-events/types";
import { apiRequest } from "@/lib/api/api-client";

export function listClinicalEventsByPet(petId: string, type?: string): Promise<ClinicalEventResponse[]> {
  const query = type ? `?tipo=${encodeURIComponent(type)}` : "";
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/eventos-clinicos${query}`, { authenticated: true });
}

export function getClinicalEvent(id: string): Promise<ClinicalEventResponse> {
  return apiRequest(`/eventos-clinicos/${encodeURIComponent(id)}`, { authenticated: true });
}

export function updateClinicalEvent(id: string, request: ClinicalEventRequest): Promise<ClinicalEventResponse> {
  return apiRequest(`/eventos-clinicos/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: request,
    authenticated: true,
  });
}

export function deleteClinicalEvent(id: string): Promise<void> {
  return apiRequest(`/eventos-clinicos/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}

export function createClinicalEvent(petId: string, request: ClinicalEventRequest): Promise<ClinicalEventResponse> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/eventos-clinicos`, {
    method: "POST",
    body: request,
    authenticated: true,
  });
}
