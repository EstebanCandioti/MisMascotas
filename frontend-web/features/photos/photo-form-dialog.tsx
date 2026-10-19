"use client";

import { type FormEvent, useState } from "react";

import type { PhotoRequest } from "@/features/photos/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function PhotoFormDialog({ onClose, onSave }: { onClose: () => void; onSave: (request: PhotoRequest) => Promise<void> }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const request: PhotoRequest = {
      urlArchivo: String(values.get("urlArchivo") ?? "").trim(),
      formato: String(values.get("formato") ?? "").trim() || null,
    };
    if (!request.urlArchivo) {
      setError("Ingresá la URL de la imagen.");
      return;
    }
    try {
      new URL(request.urlArchivo);
    } catch {
      setError("Ingresá una URL válida, por ejemplo https://sitio.com/foto.jpg.");
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
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="photo-form-title" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div><span className="eyebrow">AGREGAR FOTO</span><h2 id="photo-form-title">Registrar imagen por URL</h2></div>
        </header>
        <p className="storage-notice">El backend actual guarda una URL y sus metadatos. No sube archivos ni ofrece almacenamiento propio.</p>
        <form className="modal-form" onSubmit={(event) => void submit(event)}>
          <label>URL de la imagen
            <input name="urlArchivo" type="url" placeholder="https://sitio.com/foto.jpg" disabled={submitting} required autoFocus />
          </label>
          <label>Formato (opcional)
            <input name="formato" placeholder="jpg, png, webp…" maxLength={20} disabled={submitting} />
          </label>
          {error && <p className="auth-error" role="alert">{error}</p>}
          <div className="modal-actions">
            <button type="button" onClick={onClose} disabled={submitting}>Cancelar</button>
            <button type="submit" disabled={submitting}>{submitting ? "Agregando…" : "Agregar foto"}</button>
          </div>
        </form>
      </section>
    </div>
  );
}
