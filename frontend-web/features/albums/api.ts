import type { Album, AlbumRequest } from "@/features/albums/types";
import { apiRequest } from "@/lib/api/api-client";

export function listAlbumsByPet(petId: string): Promise<Album[]> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/albumes`, { authenticated: true });
}

export function getAlbum(id: string): Promise<Album> {
  return apiRequest(`/albumes/${encodeURIComponent(id)}`, { authenticated: true });
}

export function createAlbum(petId: string, request: AlbumRequest): Promise<Album> {
  return apiRequest(`/mascotas/${encodeURIComponent(petId)}/albumes`, {
    method: "POST",
    body: request,
    authenticated: true,
  });
}

export function updateAlbum(id: string, request: AlbumRequest): Promise<Album> {
  return apiRequest(`/albumes/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: request,
    authenticated: true,
  });
}

export function deleteAlbum(id: string): Promise<void> {
  return apiRequest(`/albumes/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}
