"use client";

import { useEffect, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";

import { ClinicalEventFormDialog } from "@/features/clinical-events/clinical-event-form-dialog";
import { clinicalEventDateLabel, clinicalEventSummary, clinicalEventTypeLabel } from "@/features/clinical-events/mappers";
import type { ClinicalEvent, ClinicalEventRequest } from "@/features/clinical-events/types";
import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";
import { getApiErrorMessage } from "@/lib/api/api-error";

type ClinicalEventDetailDialogProps = {
  eventId: string;
  petId: string;
  petName: string;
  controller: ClinicalEventsController;
  onClose: () => void;
  onChanged: (event: ClinicalEvent | null) => void;
};

export function ClinicalEventDetailDialog({ eventId, petId, petName, controller, onClose, onChanged }: ClinicalEventDetailDialogProps) {
  const [clinicalEvent, setClinicalEvent] = useState<ClinicalEvent | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const { getEvent } = controller;

  useEffect(() => {
    let active = true;
    void getEvent(eventId)
      .then((loaded) => active && setClinicalEvent(loaded))
      .catch((caught) => active && setError(getApiErrorMessage(caught)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [eventId, getEvent]);

  const save = async (request: ClinicalEventRequest) => {
    const updated = await controller.updateEvent(eventId, request);
    setClinicalEvent(updated);
    onChanged(updated);
    setEditing(false);
  };

  const remove = async () => {
    setDeleting(true);
    setError("");
    try {
      await controller.deleteEvent(eventId);
      onChanged(null);
      onClose();
    } catch (caught) {
      setError(getApiErrorMessage(caught));
      setDeleting(false);
    }
  };

  if (editing && clinicalEvent) {
    return <ClinicalEventFormDialog petId={petId} petName={petName} clinicalEvent={clinicalEvent} onClose={() => setEditing(false)} onSave={save} />;
  }

  return (
    <div className="modal-backdrop" onMouseDown={() => !deleting && onClose()}>
      <section className="modal detail-modal" role="dialog" aria-modal="true" aria-labelledby="clinical-event-detail-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><div><span className="eyebrow">EVENTO CLÍNICO</span><h2 id="clinical-event-detail-title">{clinicalEvent ? clinicalEventTypeLabel(clinicalEvent.tipo) : "Detalle del evento"}</h2></div><button className="icon-btn" type="button" onClick={onClose} disabled={deleting} aria-label="Cerrar">×</button></header>
        {loading && <p className="detail-status"><span className="pet-loader" /> Consultando evento…</p>}
        {!loading && error && !clinicalEvent && <div className="detail-status"><p className="auth-error" role="alert">{error}</p><button className="secondary-action" type="button" onClick={onClose}>Cerrar</button></div>}
        {!loading && clinicalEvent && (
          <>
            <dl className="detail-list">
              <div><dt>Mascota</dt><dd>{clinicalEvent.mascotaNombre}</dd></div>
              <div><dt>Fecha y hora</dt><dd>{clinicalEventDateLabel(clinicalEvent.fecha)}</dd></div>
              <div><dt>Resumen</dt><dd>{clinicalEventSummary(clinicalEvent)}</dd></div>
              {clinicalEvent.nombre && <div><dt>Nombre</dt><dd>{clinicalEvent.nombre}</dd></div>}
              {clinicalEvent.dosis && <div><dt>Dosis</dt><dd>{clinicalEvent.dosis}</dd></div>}
              {clinicalEvent.valorPeso !== null && <div><dt>Peso</dt><dd>{clinicalEvent.valorPeso} kg</dd></div>}
              {clinicalEvent.motivo && <div><dt>Motivo</dt><dd>{clinicalEvent.motivo}</dd></div>}
              {clinicalEvent.diagnostico && <div><dt>Diagnóstico</dt><dd>{clinicalEvent.diagnostico}</dd></div>}
              {clinicalEvent.observaciones && <div><dt>Observaciones</dt><dd>{clinicalEvent.observaciones}</dd></div>}
              <div><dt>Registrado por</dt><dd>{clinicalEvent.registradoPorNombre || "Usuario autorizado"}</dd></div>
            </dl>
            {error && <p className="auth-error reminder-action-error" role="alert">{error}</p>}
            <div className="detail-actions">
              <button type="button" onClick={() => setEditing(true)}><Pencil /> Editar</button>
              <button className="danger-action" type="button" onClick={() => setConfirmingDelete(true)}><Trash2 /> Eliminar</button>
            </div>
          </>
        )}
        {confirmingDelete && clinicalEvent && <div className="inline-confirm"><p>¿Eliminar este evento de {clinicalEventTypeLabel(clinicalEvent.tipo).toLowerCase()}? Se quitará del historial.</p><div className="modal-actions"><button type="button" onClick={() => setConfirmingDelete(false)} disabled={deleting}>Cancelar</button><button className="danger-action" type="button" onClick={() => void remove()} disabled={deleting}>{deleting ? "Eliminando…" : "Confirmar eliminación"}</button></div></div>}
      </section>
    </div>
  );
}
