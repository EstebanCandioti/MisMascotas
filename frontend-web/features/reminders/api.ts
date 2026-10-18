import type { ReminderRequest, ReminderResponse } from "@/features/reminders/types";
import { apiRequest } from "@/lib/api/api-client";

export function listRemindersByPet(petId: string): Promise<ReminderResponse[]> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/recordatorios`, { authenticated: true });
}

export function createReminder(petId: string, request: ReminderRequest): Promise<ReminderResponse> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/recordatorios`, {
    method: "POST",
    body: request,
    authenticated: true,
  });
}

export function confirmReminder(id: string): Promise<ReminderResponse> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}/confirmacion`, {
    method: "PATCH",
    authenticated: true,
  });
}

export function unconfirmReminder(id: string): Promise<ReminderResponse> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}/confirmacion`, {
    method: "DELETE",
    authenticated: true,
  });
}
