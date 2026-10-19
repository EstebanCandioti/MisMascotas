"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import {
  createPet as createPetRequest,
  deletePet as deletePetRequest,
  getPet as getPetRequest,
  listPets,
  updatePet as updatePetRequest,
} from "@/features/pets/api";
import type { Pet, PetRequest, PetsStatus } from "@/features/pets/types";
import { type ApiError, normalizeApiError } from "@/lib/api/api-error";

export type PetsController = {
  pets: Pet[];
  status: PetsStatus;
  error: ApiError | null;
  reload: () => Promise<void>;
  loadPet: (id: string) => Promise<Pet>;
  refreshPet: (id: string) => Promise<Pet>;
  createPet: (request: PetRequest) => Promise<Pet>;
  updatePet: (id: string, request: PetRequest) => Promise<Pet>;
  deletePet: (id: string) => Promise<void>;
};

export function usePets(enabled: boolean): PetsController {
  const [pets, setPets] = useState<Pet[]>([]);
  const [status, setStatus] = useState<PetsStatus>("idle");
  const [error, setError] = useState<ApiError | null>(null);
  const enabledRef = useRef(enabled);

  const reload = useCallback(async () => {
    if (!enabledRef.current) return;
    setStatus("loading");
    setError(null);
    try {
      const result = await listPets();
      if (!enabledRef.current) return;
      setPets(result);
      setStatus("success");
    } catch (caught) {
      if (!enabledRef.current) return;
      setError(normalizeApiError(caught));
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    let active = true;
    enabledRef.current = enabled;
    queueMicrotask(() => {
      if (!active) return;
      if (enabled) {
        void reload();
      } else {
        setPets([]);
        setError(null);
        setStatus("idle");
      }
    });
    return () => {
      active = false;
    };
  }, [enabled, reload]);

  const loadPet = useCallback((id: string) => getPetRequest(id), []);

  const refreshPet = useCallback(async (id: string) => {
    const updated = await getPetRequest(id);
    setPets((current) => current.map((pet) => pet.idMascota === id ? updated : pet));
    return updated;
  }, []);

  const createPet = useCallback(async (request: PetRequest) => {
    const created = await createPetRequest(request);
    setPets((current) => [...current, created]);
    setStatus("success");
    return created;
  }, []);

  const updatePet = useCallback(async (id: string, request: PetRequest) => {
    const updated = await updatePetRequest(id, request);
    setPets((current) => current.map((pet) => pet.idMascota === id ? updated : pet));
    return updated;
  }, []);

  const deletePet = useCallback(async (id: string) => {
    await deletePetRequest(id);
    setPets((current) => current.filter((pet) => pet.idMascota !== id));
  }, []);

  return { pets, status, error, reload, loadPet, refreshPet, createPet, updatePet, deletePet };
}
