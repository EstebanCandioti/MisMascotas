"use client";

import { type FormEvent, useState } from "react";

import type { Pet, PetRequest } from "@/features/pets/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

type AgeMode = "birth-date" | "approximate-age" | "not-provided";

type PetFormDialogProps = {
  pet?: Pet;
  onClose: () => void;
  onSave: (request: PetRequest) => Promise<void>;
};

function localDateToday(): string {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function optionalText(value: FormDataEntryValue | null): string | null {
  const normalized = String(value ?? "").trim();
  return normalized || null;
}

export function PetFormDialog({ pet, onClose, onSave }: PetFormDialogProps) {
  const today = localDateToday();
  const [ageMode, setAgeMode] = useState<AgeMode>(
    pet?.fechaNacimiento
      ? "birth-date"
      : pet?.edadValor !== null && pet?.edadValor !== undefined
        ? "approximate-age"
        : "not-provided",
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const editing = Boolean(pet);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const nombre = String(values.get("nombre") ?? "").trim();
    const especie = String(values.get("especie") ?? "").trim();
    const fechaNacimiento = ageMode === "birth-date"
      ? optionalText(values.get("fechaNacimiento"))
      : null;
    const rawAge = ageMode === "approximate-age"
      ? String(values.get("edadValor") ?? "").trim()
      : "";
    const edadValor = rawAge ? Number(rawAge) : null;
    const rawWeight = String(values.get("pesoActual") ?? "").trim();
    const pesoActual = rawWeight ? Number(rawWeight) : null;

    if (!nombre || !especie) {
      setError("Nombre y especie son obligatorios.");
      return;
    }
    if (fechaNacimiento && fechaNacimiento > today) {
      setError("La fecha de nacimiento no puede ser futura.");
      return;
    }
    if (ageMode === "approximate-age" && (edadValor === null || !Number.isInteger(edadValor) || edadValor < 0)) {
      setError("Ingresá una edad aproximada válida.");
      return;
    }
    if (pesoActual !== null && (!Number.isFinite(pesoActual) || pesoActual <= 0 || pesoActual > 9999.99)) {
      setError("El peso debe ser mayor a 0 y menor o igual a 9999,99 kg.");
      return;
    }

    const request: PetRequest = {
      nombre,
      especie,
      raza: optionalText(values.get("raza")),
      fechaNacimiento,
      fechaAproximada: ageMode === "approximate-age"
        || (ageMode === "birth-date" && values.get("fechaAproximada") === "on"),
      edadValor: ageMode === "approximate-age" ? edadValor : null,
      edadUnidad: ageMode === "approximate-age"
        ? String(values.get("edadUnidad")) as PetRequest["edadUnidad"]
        : null,
      fotoPerfil: optionalText(values.get("fotoPerfil")),
      pesoActual,
      notas: optionalText(values.get("notas")),
    };

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
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="pet-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div><span className="eyebrow">MIS MASCOTAS</span><h2 id="pet-form-title">{editing ? "Editar mascota" : "Agregar mascota"}</h2></div>
          <button className="icon-btn" type="button" onClick={onClose} disabled={submitting} aria-label="Cerrar">×</button>
        </header>
        <form className="modal-form" onSubmit={submit}>
          <label>Nombre
            <input name="nombre" required maxLength={100} defaultValue={pet?.nombre ?? ""} placeholder="Nombre de la mascota" />
          </label>
          <div className="form-row">
            <label>Especie
              <input name="especie" required maxLength={50} defaultValue={pet?.especie ?? ""} placeholder="Ej. Perro" />
            </label>
            <label>Raza
              <input name="raza" maxLength={100} defaultValue={pet?.raza ?? ""} placeholder="Opcional" />
            </label>
          </div>

          <fieldset className="pet-age-fieldset">
            <legend>Edad</legend>
            <div className="segmented pet-age-tabs">
              <button type="button" className={ageMode === "birth-date" ? "active" : ""} onClick={() => setAgeMode("birth-date")}>Fecha de nacimiento</button>
              <button type="button" className={ageMode === "approximate-age" ? "active" : ""} onClick={() => setAgeMode("approximate-age")}>Edad aproximada</button>
              <button type="button" className={ageMode === "not-provided" ? "active" : ""} onClick={() => setAgeMode("not-provided")}>No informar</button>
            </div>
            {ageMode === "birth-date" ? (
              <>
                <label>Fecha de nacimiento
                  <input name="fechaNacimiento" type="date" max={today} required defaultValue={pet?.fechaNacimiento ?? ""} />
                </label>
                <label className="pet-checkbox-row">
                  <input name="fechaAproximada" type="checkbox" defaultChecked={pet?.fechaAproximada ?? false} />
                  La fecha es aproximada
                </label>
              </>
            ) : ageMode === "approximate-age" ? (
              <div className="form-row">
                <label>Edad
                  <input name="edadValor" type="number" min={0} step={1} required defaultValue={pet?.edadValor ?? ""} />
                </label>
                <label>Unidad
                  <select name="edadUnidad" required defaultValue={pet?.edadUnidad ?? "anios"}>
                    <option value="dias">Días</option>
                    <option value="semanas">Semanas</option>
                    <option value="meses">Meses</option>
                    <option value="anios">Años</option>
                  </select>
                </label>
              </div>
            ) : <p className="form-hint">Podés completar este dato más adelante.</p>}
          </fieldset>

          <label>Peso actual (kg)
            <input name="pesoActual" type="number" min="0.01" max="9999.99" step="0.01" defaultValue={pet?.pesoActual ?? ""} placeholder="Opcional" />
          </label>
          <label>Foto de perfil (URL)
            <input name="fotoPerfil" type="url" maxLength={255} defaultValue={pet?.fotoPerfil ?? ""} placeholder="https://…" />
          </label>
          <label>Notas
            <textarea name="notas" maxLength={255} defaultValue={pet?.notas ?? ""} placeholder="Información útil sobre tu mascota" />
          </label>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <div className="modal-actions">
            <button type="button" onClick={onClose} disabled={submitting}>Cancelar</button>
            <button className="primary" type="submit" disabled={submitting}>{submitting ? "Guardando…" : "Guardar"}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
