import type { Photo, PhotoRequest } from "@/features/photos/types";
import { apiRequest } from "@/lib/api/api-client";

export function listPhotosByAlbum(albumId: string): Promise<Photo[]> {
  return apiRequest(`/albumes/${encodeURIComponent(albumId)}/fotos`, { authenticated: true });
}

export function getPhoto(id: string): Promise<Photo> {
  return apiRequest(`/fotos/${encodeURIComponent(id)}`, { authenticated: true });
}

export function createPhoto(albumId: string, request: PhotoRequest): Promise<Photo> {
  return apiRequest(`/albumes/${encodeURIComponent(albumId)}/fotos`, {
    method: "POST",
    body: request,
    authenticated: true,
  });
}

export function deletePhoto(id: string): Promise<void> {
  return apiRequest(`/fotos/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}
