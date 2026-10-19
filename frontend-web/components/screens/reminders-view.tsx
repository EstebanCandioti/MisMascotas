"use client";

import { useState } from "react";
import Link from "next/link";
import { Bell, Plus } from "lucide-react";

import { Card, EmptyState, Header } from "@/components/layout/app-navigation";
import type { PetsController } from "@/features/pets/use-pets";
import { isReminderConfirmed, reminderDateLabel, reminderTypeLabel } from "@/features/reminders/mappers";
import { ReminderConfirmationButton } from "@/features/reminders/reminder-confirmation-button";
import { ReminderDetailDialog } from "@/features/reminders/reminder-detail-dialog";
import { ReminderFormDialog } from "@/features/reminders/reminder-form-dialog";
import type { ReminderRequest, ReminderState } from "@/features/reminders/types";
import type { RemindersController } from "@/features/reminders/use-reminders";
import { getApiErrorMessage } from "@/lib/api/api-error";

const filterOptions: Array<{ value: ReminderState; label: string; emptyTitle: string; emptyDescription: string }> = [
  { value: "ACTIVO", label: "Activos", emptyTitle: "Sin recordatorios activos", emptyDescription: "No hay cuidados activos pendientes." },
  { value: "COMPLETADO", label: "Completados", emptyTitle: "Sin recordatorios completados", emptyDescription: "Los recordatorios completados aparecerán en este historial." },
  { value: "INACTIVO", label: "Inactivos", emptyTitle: "Sin recordatorios inactivos", emptyDescription: "Los recordatorios que desactives aparecerán acá." },
];

export function RemindersView({ controller, petsController }: { controller: RemindersController; petsController: PetsController }) {
  const [creating, setCreating] = useState(false);
  const [selectedReminderId, setSelectedReminderId] = useState<string | null>(null);
  const [filter, setFilter] = useState<ReminderState>("ACTIVO");
  const visibleReminders = controller.reminders.filter((reminder) => reminder.estado === filter);
  const activeFilter = filterOptions.find((option) => option.value === filter) ?? filterOptions[0];

  const createReminder = async (request: ReminderRequest) => {
    await controller.createReminder(request);
    setFilter("ACTIVO");
    setCreating(false);
  };

  return (
    <>
      <Header title="Mis recordatorios" subtitle="Próximos cuidados y controles" action={<button className="icon-btn" onClick={() => setCreating(true)} disabled={petsController.pets.length === 0} aria-label="Crear recordatorio"><Plus /></button>} />
      {(controller.status === "idle" || controller.status === "loading") && <EmptyState icon={<span className="pet-loader" />} title="Cargando recordatorios" description="Estamos consultando los cuidados de tus mascotas." />}
      {controller.status === "error" && <EmptyState icon={<Bell />} title="No pudimos cargar los recordatorios" description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."} action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>} />}
      {controller.status === "success" && petsController.pets.length === 0 && <EmptyState icon={<Bell />} title="Primero agregá una mascota" description="Los recordatorios deben estar asociados a una mascota real." action={<Link className="secondary-action" href="/mascotas">Ir a Mascotas</Link>} />}
      {controller.status === "success" && petsController.pets.length > 0 && <div className="reminder-filters" aria-label="Filtrar recordatorios por estado">{filterOptions.map((option) => <button key={option.value} className={filter === option.value ? "active" : ""} type="button" onClick={() => setFilter(option.value)} aria-pressed={filter === option.value}>{option.label}<span>{controller.reminders.filter((reminder) => reminder.estado === option.value).length}</span></button>)}</div>}
      {controller.status === "success" && petsController.pets.length > 0 && controller.reminders.length === 0 && <EmptyState icon={<Bell />} title="Sin recordatorios" description="Creá un recordatorio único para el próximo cuidado." action={<button className="secondary-action" onClick={() => setCreating(true)}>Crear recordatorio</button>} />}
      {controller.status === "success" && controller.reminders.length > 0 && visibleReminders.length === 0 && <EmptyState icon={<Bell />} title={activeFilter.emptyTitle} description={activeFilter.emptyDescription} />}
      {controller.status === "success" && visibleReminders.length > 0 && (
        <div className="reminders reminder-list">
          {visibleReminders.map((reminder) => {
            const confirmed = isReminderConfirmed(reminder);
            return (
              <Card key={reminder.idRecordatorio} className={confirmed ? "reminder-confirmed" : ""}>
                <Bell />
                <div><h3>{reminder.titulo}</h3><p>{reminder.mascotaNombre} · {reminderTypeLabel(reminder.tipo)}</p><small>{reminderDateLabel(reminder.fechaHoraInicio)}</small><button className="text-action" type="button" onClick={() => setSelectedReminderId(reminder.idRecordatorio)}>Ver detalle y gestionar</button></div>
                <ReminderConfirmationButton reminder={reminder} controller={controller} />
              </Card>
            );
          })}
        </div>
      )}
      {controller.status === "success" && petsController.pets.length > 0 && <button className="fab" onClick={() => setCreating(true)} aria-label="Crear recordatorio"><Plus /></button>}
      {creating && <ReminderFormDialog pets={petsController.pets} onClose={() => setCreating(false)} onSave={createReminder} />}
      {selectedReminderId && <ReminderDetailDialog reminderId={selectedReminderId} pets={petsController.pets} controller={controller} onClose={() => setSelectedReminderId(null)} />}
    </>
  );
}
