import type { ReminderRequest, ReminderResponse, ReminderState } from "@/features/reminders/types";
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

export function getReminder(id: string): Promise<ReminderResponse> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}`, { authenticated: true });
}

export function updateReminder(id: string, request: ReminderRequest): Promise<ReminderResponse> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: request,
    authenticated: true,
  });
}

export function updateReminderState(id: string, state: ReminderState): Promise<ReminderResponse> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}/estado?nuevoEstado=${encodeURIComponent(state)}`, {
    method: "PATCH",
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

export function deleteReminder(id: string): Promise<void> {
  return apiRequest(`/recordatorios/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}
