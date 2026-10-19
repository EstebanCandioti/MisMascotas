"use client";

import { type FormEvent, useState } from "react";

import type { ClinicalEventRequest, ClinicalEventType } from "@/features/clinical-events/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

type ClinicalEventFormDialogProps = {
  petId: string;
  petName: string;
  onClose: () => void;
  onSave: (request: ClinicalEventRequest) => Promise<void>;
};

const typeOptions: Array<{ value: ClinicalEventType; label: string }> = [
  { value: "VACUNA", label: "Vacuna" },
  { value: "MEDICACION", label: "Medicación" },
  { value: "CONSULTA_VETERINARIA", label: "Consulta veterinaria" },
  { value: "PESO", label: "Control de peso" },
];

function localDateTimeToInstant(dateValue: string, timeValue: string): string | null {
  const dateParts = dateValue.split("-").map(Number);
  const timeParts = timeValue.split(":").map(Number);
  if (dateParts.length !== 3 || timeParts.length !== 2 || [...dateParts, ...timeParts].some((part) => !Number.isInteger(part))) return null;
  const [year, month, day] = dateParts;
  const [hour, minute] = timeParts;
  const value = new Date(year, month - 1, day, hour, minute, 0, 0);
  if (value.getFullYear() !== year || value.getMonth() !== month - 1 || value.getDate() !== day || value.getHours() !== hour || value.getMinutes() !== minute) return null;
  return value.toISOString();
}

function argentinaToday(): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Argentina/Buenos_Aires",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function optionalText(values: FormData, name: string): string | undefined {
  const value = String(values.get(name) ?? "").trim();
  return value || undefined;
}

export function ClinicalEventFormDialog({ petId, petName, onClose, onSave }: ClinicalEventFormDialogProps) {
  const [type, setType] = useState<ClinicalEventType>("VACUNA");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const today = argentinaToday();

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const instant = localDateTimeToInstant(String(values.get("fecha") ?? ""), String(values.get("hora") ?? ""));
    const endOfTodayArgentina = Date.parse(`${today}T23:59:59.999-03:00`);
    if (!instant || Date.parse(instant) > endOfTodayArgentina) {
      setError("Elegí una fecha y hora válidas que no sean posteriores al día actual.");
      return;
    }

    const observations = optionalText(values, "observaciones");
    let request: ClinicalEventRequest;
    if (type === "VACUNA") {
      const nombre = optionalText(values, "nombre");
      if (!nombre) { setError("El nombre de la vacuna es obligatorio."); return; }
      request = { mascotaId: petId, tipo: type, fecha: instant, nombre, ...(observations ? { observaciones: observations } : {}) };
    } else if (type === "MEDICACION") {
      const nombre = optionalText(values, "nombre");
      const dosis = optionalText(values, "dosis");
      if (!nombre || !dosis) { setError("El nombre y la dosis son obligatorios."); return; }
      request = { mascotaId: petId, tipo: type, fecha: instant, nombre, dosis, ...(observations ? { observaciones: observations } : {}) };
    } else if (type === "CONSULTA_VETERINARIA") {
      const motivo = optionalText(values, "motivo");
      if (!motivo) { setError("El motivo de la consulta es obligatorio."); return; }
      const diagnostico = optionalText(values, "diagnostico");
      request = { mascotaId: petId, tipo: type, fecha: instant, motivo, ...(diagnostico ? { diagnostico } : {}), ...(observations ? { observaciones: observations } : {}) };
    } else {
      const rawWeight = String(values.get("valorPeso") ?? "").trim();
      const valorPeso = Number(rawWeight);
      if (!rawWeight || !Number.isFinite(valorPeso) || valorPeso < 0.01 || valorPeso > 300) {
        setError("El peso debe estar entre 0,01 y 300 kg.");
        return;
      }
      request = { mascotaId: petId, tipo: type, fecha: instant, valorPeso };
    }

    setSubmitting(true);
    setError("");
    try {
      await onSave(request);
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={() => !submitting && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="clinical-event-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><div><span className="eyebrow">HISTORIAL DE {petName}</span><h2 id="clinical-event-form-title">Registrar evento clínico</h2></div><button className="icon-btn" type="button" onClick={onClose} disabled={submitting} aria-label="Cerrar">×</button></header>
        <form className="modal-form" onSubmit={submit}>
          <label>Tipo<select name="tipo" value={type} onChange={(event) => setType(event.target.value as ClinicalEventType)}>{typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <div className="form-row"><label>Fecha<input name="fecha" type="date" max={today} required /></label><label>Hora<input name="hora" type="time" required /></label></div>
          {type === "VACUNA" && <label>Nombre de la vacuna<input name="nombre" required maxLength={255} placeholder="Ej. Antirrábica" /></label>}
          {type === "MEDICACION" && <><label>Nombre de la medicación<input name="nombre" required maxLength={255} placeholder="Ej. Antibiótico" /></label><label>Dosis<input name="dosis" required maxLength={100} placeholder="Ej. 1 comprimido cada 12 horas" /></label></>}
          {type === "CONSULTA_VETERINARIA" && <><label>Motivo<input name="motivo" required maxLength={255} placeholder="Motivo de la consulta" /></label><label>Diagnóstico<input name="diagnostico" maxLength={255} placeholder="Opcional" /></label></>}
          {type === "PESO" && <label>Peso (kg)<input name="valorPeso" type="number" min="0.01" max="300" step="0.01" required placeholder="Ej. 12,50" /></label>}
          {type !== "PESO" && <label>Observaciones<textarea name="observaciones" maxLength={255} placeholder="Opcional" /></label>}
          <p className="form-hint">La carga de adjuntos todavía no está disponible.</p>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <div className="modal-actions"><button type="button" onClick={onClose} disabled={submitting}>Cancelar</button><button className="primary" type="submit" disabled={submitting}>{submitting ? "Guardando…" : "Registrar evento"}</button></div>
        </form>
      </section>
    </div>
  );
}
