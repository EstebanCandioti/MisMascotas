"use client";

import { type FormEvent, useState } from "react";

import type { Pet } from "@/features/pets/types";
import type { Reminder, ReminderRequest, ReminderType } from "@/features/reminders/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

type ReminderFormDialogProps = {
  pets: Pet[];
  reminder?: Reminder;
  onClose: () => void;
  onSave: (request: ReminderRequest) => Promise<void>;
};

const typeOptions: Array<{ value: ReminderType; label: string }> = [
  { value: "PIPETA_ANTIPARASITARIO", label: "Pipeta antiparasitaria" },
  { value: "VISITA_VETERINARIO", label: "Visita veterinaria" },
  { value: "MEDICACION", label: "Medicación" },
  { value: "VACUNA", label: "Vacuna" },
  { value: "OTRO", label: "Otro" },
];

function localToday(): string {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function instantToLocalFields(instant?: string): { date: string; time: string } {
  const value = instant ? new Date(instant) : null;
  if (!value || Number.isNaN(value.getTime())) return { date: "", time: "" };
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  const hour = String(value.getHours()).padStart(2, "0");
  const minute = String(value.getMinutes()).padStart(2, "0");
  return { date: `${value.getFullYear()}-${month}-${day}`, time: `${hour}:${minute}` };
}

function localSelectionToInstant(dateValue: string, timeValue: string): string | null {
  const dateParts = dateValue.split("-").map(Number);
  const timeParts = timeValue.split(":").map(Number);
  if (dateParts.length !== 3 || timeParts.length !== 2 || [...dateParts, ...timeParts].some((part) => !Number.isInteger(part))) return null;

  const [year, month, day] = dateParts;
  const [hour, minute] = timeParts;
  const localDate = new Date(year, month - 1, day, hour, minute, 0, 0);
  if (localDate.getFullYear() !== year || localDate.getMonth() !== month - 1 || localDate.getDate() !== day || localDate.getHours() !== hour || localDate.getMinutes() !== minute) return null;
  return localDate.toISOString();
}

export function ReminderFormDialog({ pets, reminder, onClose, onSave }: ReminderFormDialogProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const editing = Boolean(reminder);
  const initialDateTime = instantToLocalFields(reminder?.fechaHoraInicio);
  const selectedPet = pets.find((pet) => pet.idMascota === reminder?.mascotaId);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const mascotaId = String(values.get("mascotaId") ?? "");
    const titulo = String(values.get("titulo") ?? "").trim();
    const tipo = String(values.get("tipo") ?? "") as ReminderType;
    const instant = localSelectionToInstant(String(values.get("fecha") ?? ""), String(values.get("hora") ?? ""));

    if (!pets.some((pet) => pet.idMascota === mascotaId)) {
      setError("Seleccioná una mascota válida.");
      return;
    }
    if (!titulo) {
      setError("El título es obligatorio.");
      return;
    }
    if (!typeOptions.some((option) => option.value === tipo)) {
      setError("Seleccioná un tipo de recordatorio válido.");
      return;
    }
    if (!instant || (!editing && Date.parse(instant) <= Date.now())) {
      setError(editing ? "Elegí una fecha y hora válidas." : "Elegí una fecha y hora futuras válidas.");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      await onSave({
        mascotaId,
        titulo,
        tipo,
        fechaHoraInicio: instant,
        modalidad: "unica",
        intervaloValor: null,
        intervaloUnidad: null,
        diasSemana: null,
        fechaFin: null,
      });
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={() => !submitting && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="reminder-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><div><span className="eyebrow">RECORDATORIO ÚNICO</span><h2 id="reminder-form-title">{editing ? "Editar recordatorio" : "Nuevo recordatorio"}</h2></div><button className="icon-btn" type="button" onClick={onClose} disabled={submitting} aria-label="Cerrar">×</button></header>
        <form className="modal-form" onSubmit={submit}>
          {editing ? <label>Mascota<input value={selectedPet?.nombre ?? reminder?.mascotaNombre ?? "Mascota"} readOnly aria-readonly="true" /><input name="mascotaId" type="hidden" value={reminder?.mascotaId} /></label> : <label>Mascota<select name="mascotaId" required defaultValue={pets[0]?.idMascota}>{pets.map((pet) => <option key={pet.idMascota} value={pet.idMascota}>{pet.nombre}</option>)}</select></label>}
          <label>Título<input name="titulo" required maxLength={150} defaultValue={reminder?.titulo} placeholder="Ej. Aplicar vacuna anual" /></label>
          <label>Tipo<select name="tipo" required defaultValue={reminder?.tipo ?? "VISITA_VETERINARIO"}>{typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label>Modalidad<input value="Único" readOnly aria-readonly="true" /></label>
          <div className="form-row">
            <label>Fecha<input name="fecha" type="date" min={editing ? undefined : localToday()} defaultValue={initialDateTime.date} required /></label>
            <label>Hora<input name="hora" type="time" defaultValue={initialDateTime.time} required /></label>
          </div>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <div className="modal-actions"><button type="button" onClick={onClose} disabled={submitting}>Cancelar</button><button className="primary" type="submit" disabled={submitting}>{submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear recordatorio"}</button></div>
        </form>
      </section>
    </div>
  );
}
