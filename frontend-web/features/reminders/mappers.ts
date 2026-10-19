import type { Pet } from "@/features/pets/types";
import type { Reminder, ReminderResponse, ReminderType } from "@/features/reminders/types";

const typeLabels: Record<ReminderType, string> = {
  PIPETA_ANTIPARASITARIO: "Pipeta antiparasitaria",
  VISITA_VETERINARIO: "Visita veterinaria",
  MEDICACION: "Medicación",
  VACUNA: "Vacuna",
  OTRO: "Otro",
};

export function enrichReminder(response: ReminderResponse, pets: Pet[]): Reminder {
  return {
    ...response,
    mascotaNombre: pets.find((pet) => pet.idMascota === response.mascotaId)?.nombre ?? "Mascota",
  };
}

export function sortReminders(reminders: Reminder[]): Reminder[] {
  const now = Date.now();
  return [...reminders].sort((left, right) => {
    const leftTime = Date.parse(left.fechaHoraInicio);
    const rightTime = Date.parse(right.fechaHoraInicio);
    const groupDifference = Number(leftTime < now) - Number(rightTime < now);
    return groupDifference || leftTime - rightTime;
  });
}

export function reminderTypeLabel(type: ReminderType): string {
  return typeLabels[type] ?? type;
}

export function reminderDateLabel(instant: string): string {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return "Fecha no disponible";
  return new Intl.DateTimeFormat("es-AR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function isReminderConfirmed(reminder: Pick<ReminderResponse, "confirmadoEn" | "confirmadoPorId">): boolean {
  return reminder.confirmadoEn !== null || reminder.confirmadoPorId !== null;
}
