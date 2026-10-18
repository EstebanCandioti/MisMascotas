import type { Pet, PetRequest } from "@/features/pets/types";
import { apiRequest } from "@/lib/api/api-client";

export function listPets(): Promise<Pet[]> {
  return apiRequest("/mascotas", { authenticated: true });
}

export function getPet(id: string): Promise<Pet> {
  return apiRequest(`/mascotas/${encodeURIComponent(id)}`, { authenticated: true });
}

export function createPet(request: PetRequest): Promise<Pet> {
  return apiRequest("/mascotas", {
    method: "POST",
    body: request,
    authenticated: true,
  });
}

export function updatePet(id: string, request: PetRequest): Promise<Pet> {
  return apiRequest(`/mascotas/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: request,
    authenticated: true,
  });
}

export function deletePet(id: string): Promise<void> {
  return apiRequest(`/mascotas/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}
