"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { createClinicalEvent as createClinicalEventRequest, listClinicalEventsByPet } from "@/features/clinical-events/api";
import { enrichClinicalEvent, sortClinicalEvents } from "@/features/clinical-events/mappers";
import type { ClinicalEvent, ClinicalEventRequest, ClinicalEventsStatus } from "@/features/clinical-events/types";
import type { PetsController } from "@/features/pets/use-pets";
import { type ApiError, normalizeApiError } from "@/lib/api/api-error";

export type ClinicalEventsController = {
  events: ClinicalEvent[];
  status: ClinicalEventsStatus;
  error: ApiError | null;
  reload: () => Promise<void>;
  createEvent: (request: ClinicalEventRequest) => Promise<ClinicalEvent>;
};

export function useClinicalEvents(enabled: boolean, petsController: PetsController): ClinicalEventsController {
  const { pets, status: petsStatus, error: petsError, refreshPet } = petsController;
  const [events, setEvents] = useState<ClinicalEvent[]>([]);
  const [status, setStatus] = useState<ClinicalEventsStatus>("idle");
  const [error, setError] = useState<ApiError | null>(null);
  const enabledRef = useRef(enabled);
  const petsRef = useRef(pets);
  const petIdsKey = pets.map((pet) => pet.idMascota).sort().join(",");

  useEffect(() => {
    petsRef.current = pets;
  }, [pets]);

  const reload = useCallback(async () => {
    if (!enabled) return;
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
      setEvents([]);
      setError(null);
      setStatus("success");
      return;
    }

    setStatus("loading");
    setError(null);
    try {
      const currentPets = petsRef.current;
      const responses = await Promise.all(currentPets.map((pet) => listClinicalEventsByPet(pet.idMascota)));
      if (!enabledRef.current) return;
      setEvents(sortClinicalEvents(responses.flat().map((event) => enrichClinicalEvent(event, currentPets))));
      setStatus("success");
    } catch (caught) {
      if (!enabledRef.current) return;
      setError(normalizeApiError(caught));
      setStatus("error");
    }
  }, [enabled, petIdsKey, petsError, petsStatus]);

  useEffect(() => {
    let active = true;
    enabledRef.current = enabled;
    queueMicrotask(() => {
      if (!active) return;
      if (enabled) void reload();
      else {
        setEvents([]);
        setError(null);
        setStatus("idle");
      }
    });
    return () => { active = false; };
  }, [enabled, reload]);

  const createEvent = useCallback(async (request: ClinicalEventRequest) => {
    const response = await createClinicalEventRequest(request.mascotaId, request);
    const created = enrichClinicalEvent(response, petsRef.current);
    setEvents((current) => sortClinicalEvents([...current, created]));
    setStatus("success");
    if (request.tipo === "PESO") void refreshPet(request.mascotaId).catch(() => undefined);
    return created;
  }, [refreshPet]);

  return { events, status, error, reload, createEvent };
}
