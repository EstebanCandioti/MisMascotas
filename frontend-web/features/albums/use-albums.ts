"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import {
  createAlbum as createAlbumRequest,
  deleteAlbum as deleteAlbumRequest,
  getAlbum as getAlbumRequest,
  listAlbumsByPet,
  updateAlbum as updateAlbumRequest,
} from "@/features/albums/api";
import type { Album, AlbumRequest, AlbumsStatus } from "@/features/albums/types";
import type { PetsController } from "@/features/pets/use-pets";
import { type ApiError, normalizeApiError } from "@/lib/api/api-error";

export type AlbumsController = {
  albums: Album[];
  status: AlbumsStatus;
  error: ApiError | null;
  reload: () => Promise<void>;
  getAlbum: (id: string) => Promise<Album>;
  createAlbum: (request: AlbumRequest) => Promise<Album>;
  updateAlbum: (id: string, request: AlbumRequest) => Promise<Album>;
  deleteAlbum: (id: string) => Promise<void>;
  changePhotoCount: (id: string, delta: number) => void;
};

function sortAlbums(albums: Album[]): Album[] {
  return [...albums].sort((left, right) => right.creadoEn.localeCompare(left.creadoEn));
}

export function useAlbums(enabled: boolean, petsController: PetsController): AlbumsController {
  const { pets, status: petsStatus, error: petsError } = petsController;
  const [albums, setAlbums] = useState<Album[]>([]);
  const [status, setStatus] = useState<AlbumsStatus>("idle");
  const [error, setError] = useState<ApiError | null>(null);
  const enabledRef = useRef(enabled);
  const petsRef = useRef(pets);
  const petIdsKey = pets.map((pet) => pet.idMascota).sort().join(",");

  useEffect(() => {
    petsRef.current = pets;
  }, [pets]);

  const reload = useCallback(async () => {
    if (!enabledRef.current) return;
    if (petsStatus === "error") {
      setError(petsError);
      setStatus("error");
      return;
    }
    if (petsStatus !== "success") {
      setStatus("loading");
      return;
    }
    if (!petIdsKey) {
      setAlbums([]);
      setError(null);
      setStatus("success");
      return;
    }

    setStatus("loading");
    setError(null);
    try {
      const result = await Promise.all(petsRef.current.map((pet) => listAlbumsByPet(pet.idMascota)));
      if (!enabledRef.current) return;
      setAlbums(sortAlbums(result.flat()));
      setStatus("success");
    } catch (caught) {
      if (!enabledRef.current) return;
      setError(normalizeApiError(caught));
      setStatus("error");
    }
  }, [petIdsKey, petsError, petsStatus]);

  useEffect(() => {
    let active = true;
    enabledRef.current = enabled;
    queueMicrotask(() => {
      if (!active) return;
      if (enabled) void reload();
      else {
        setAlbums([]);
        setError(null);
        setStatus("idle");
      }
    });
    return () => { active = false; };
  }, [enabled, reload]);

  const replaceAlbum = useCallback((updated: Album) => {
    setAlbums((current) => sortAlbums(current.map((album) => album.idAlbum === updated.idAlbum ? updated : album)));
    return updated;
  }, []);

  const getAlbum = useCallback(async (id: string) => replaceAlbum(await getAlbumRequest(id)), [replaceAlbum]);

  const createAlbum = useCallback(async (request: AlbumRequest) => {
    const created = await createAlbumRequest(request.mascotaId, request);
    setAlbums((current) => sortAlbums([...current, created]));
    setStatus("success");
    return created;
  }, []);

  const updateAlbum = useCallback(async (id: string, request: AlbumRequest) => (
    replaceAlbum(await updateAlbumRequest(id, request))
  ), [replaceAlbum]);

  const deleteAlbum = useCallback(async (id: string) => {
    await deleteAlbumRequest(id);
    setAlbums((current) => current.filter((album) => album.idAlbum !== id));
  }, []);

  const changePhotoCount = useCallback((id: string, delta: number) => {
    setAlbums((current) => current.map((album) => album.idAlbum === id
      ? { ...album, cantidadFotos: Math.max(0, album.cantidadFotos + delta) }
      : album));
  }, []);

  return { albums, status, error, reload, getAlbum, createAlbum, updateAlbum, deleteAlbum, changePhotoCount };
}
