"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import {
  confirmReminder as confirmReminderRequest,
  createReminder as createReminderRequest,
  listRemindersByPet,
  unconfirmReminder as unconfirmReminderRequest,
} from "@/features/reminders/api";
import { enrichReminder, sortReminders } from "@/features/reminders/mappers";
import type { Reminder, ReminderRequest, RemindersStatus } from "@/features/reminders/types";
import type { PetsController } from "@/features/pets/use-pets";
import { type ApiError, normalizeApiError } from "@/lib/api/api-error";

export type RemindersController = {
  reminders: Reminder[];
  status: RemindersStatus;
  error: ApiError | null;
  reload: () => Promise<void>;
  createReminder: (request: ReminderRequest) => Promise<Reminder>;
  confirmReminder: (id: string) => Promise<Reminder>;
  unconfirmReminder: (id: string) => Promise<Reminder>;
};

export function useReminders(enabled: boolean, petsController: PetsController): RemindersController {
  const { pets, status: petsStatus, error: petsError } = petsController;
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [status, setStatus] = useState<RemindersStatus>("idle");
  const [error, setError] = useState<ApiError | null>(null);
  const enabledRef = useRef(enabled);

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

    setStatus("loading");
    setError(null);
    try {
      const responses = await Promise.all(pets.map((pet) => listRemindersByPet(pet.idMascota)));
      if (!enabledRef.current) return;
      setReminders(sortReminders(responses.flat().map((item) => enrichReminder(item, pets))));
      setStatus("success");
    } catch (caught) {
      if (!enabledRef.current) return;
      setError(normalizeApiError(caught));
      setStatus("error");
    }
  }, [enabled, pets, petsError, petsStatus]);

  useEffect(() => {
    let active = true;
    enabledRef.current = enabled;
    queueMicrotask(() => {
      if (!active) return;
      if (enabled) void reload();
      else {
        setReminders([]);
        setError(null);
        setStatus("idle");
      }
    });
    return () => { active = false; };
  }, [enabled, reload]);

  const createReminder = useCallback(async (request: ReminderRequest) => {
    const response = await createReminderRequest(request.mascotaId, request);
    const created = enrichReminder(response, pets);
    setReminders((current) => sortReminders([...current, created]));
    setStatus("success");
    return created;
  }, [pets]);

  const replaceReminder = useCallback((response: Awaited<ReturnType<typeof confirmReminderRequest>>) => {
    const updated = enrichReminder(response, pets);
    setReminders((current) => sortReminders(current.map((item) => item.idRecordatorio === updated.idRecordatorio ? updated : item)));
    return updated;
  }, [pets]);

  const confirmReminder = useCallback(async (id: string) => replaceReminder(await confirmReminderRequest(id)), [replaceReminder]);
  const unconfirmReminder = useCallback(async (id: string) => replaceReminder(await unconfirmReminderRequest(id)), [replaceReminder]);

  return { reminders, status, error, reload, createReminder, confirmReminder, unconfirmReminder };
}
