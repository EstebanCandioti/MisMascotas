"use client";

import { type FormEvent, useState } from "react";

import type { Album, AlbumRequest } from "@/features/albums/types";
import type { Pet } from "@/features/pets/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

type AlbumFormDialogProps = {
  pets: Pet[];
  album?: Album;
  initialPetId?: string;
  onClose: () => void;
  onSave: (request: AlbumRequest) => Promise<void>;
};

export function AlbumFormDialog({ pets, album, initialPetId, onClose, onSave }: AlbumFormDialogProps) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const editing = Boolean(album);
  const selectedPetId = album?.mascotaId ?? initialPetId ?? pets[0]?.idMascota ?? "";

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const request: AlbumRequest = {
      nombre: String(values.get("nombre") ?? "").trim(),
      descripcion: String(values.get("descripcion") ?? "").trim() || null,
      mascotaId: album?.mascotaId ?? String(values.get("mascotaId") ?? ""),
    };
    if (!request.nombre || !request.mascotaId) {
      setError("Completá el nombre y seleccioná una mascota.");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      await onSave(request);
    } catch (caught) {
      setError(getApiErrorMessage(caught));
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onMouseDown={() => !submitting && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="album-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div>
            <span className="eyebrow">{editing ? "EDITAR ÁLBUM" : "NUEVO ÁLBUM"}</span>
            <h2 id="album-form-title">{editing ? album?.nombre : "Guardar recuerdos"}</h2>
          </div>
        </header>
        <form className="modal-form" onSubmit={(event) => void submit(event)}>
          <label>Mascota
            <select name="mascotaId" defaultValue={selectedPetId} disabled={editing || submitting} required>
              <option value="" disabled>Seleccioná una mascota</option>
              {pets.map((pet) => <option key={pet.idMascota} value={pet.idMascota}>{pet.nombre}</option>)}
            </select>
          </label>
          {editing && <p className="form-hint">La asociación con la mascota no se puede cambiar.</p>}
          <label>Nombre
            <input name="nombre" defaultValue={album?.nombre ?? ""} maxLength={150} disabled={submitting} required autoFocus />
          </label>
          <label>Descripción
            <textarea name="descripcion" defaultValue={album?.descripcion ?? ""} rows={4} disabled={submitting} />
          </label>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <div className="modal-actions">
            <button type="button" onClick={onClose} disabled={submitting}>Cancelar</button>
            <button type="submit" disabled={submitting}>{submitting ? "Guardando…" : editing ? "Guardar cambios" : "Crear álbum"}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
