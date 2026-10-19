"use client";

import { useEffect, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";

import { isReminderConfirmed, reminderDateLabel, reminderTypeLabel } from "@/features/reminders/mappers";
import { ReminderConfirmationButton } from "@/features/reminders/reminder-confirmation-button";
import { ReminderFormDialog } from "@/features/reminders/reminder-form-dialog";
import { reminderStates, type Reminder, type ReminderRequest, type ReminderState } from "@/features/reminders/types";
import type { RemindersController } from "@/features/reminders/use-reminders";
import type { Pet } from "@/features/pets/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

type ReminderDetailDialogProps = {
  reminderId: string;
  pets: Pet[];
  controller: RemindersController;
  onClose: () => void;
};

const stateLabels: Record<ReminderState, string> = {
  ACTIVO: "Activo",
  INACTIVO: "Inactivo",
  COMPLETADO: "Completado",
};

export function ReminderDetailDialog({ reminderId, pets, controller, onClose }: ReminderDetailDialogProps) {
  const [reminder, setReminder] = useState<Reminder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [state, setState] = useState<ReminderState>("ACTIVO");
  const [changingState, setChangingState] = useState(false);
  const { getReminder } = controller;

  useEffect(() => {
    let active = true;
    void getReminder(reminderId)
      .then((loaded) => {
        if (!active) return;
        setReminder(loaded);
        setState(loaded.estado ?? "ACTIVO");
      })
      .catch((caught) => active && setError(getApiErrorMessage(caught)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [getReminder, reminderId]);

  const save = async (request: ReminderRequest) => {
    const updated = await controller.updateReminder(reminderId, request);
    setReminder(updated);
    setEditing(false);
  };

  const changeState = async () => {
    if (!reminder || state === reminder.estado) return;
    setChangingState(true);
    setError("");
    try {
      setReminder(await controller.updateReminderState(reminderId, state));
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setChangingState(false);
    }
  };

  const remove = async () => {
    setDeleting(true);
    setError("");
    try {
      await controller.deleteReminder(reminderId);
      onClose();
    } catch (caught) {
      setError(getApiErrorMessage(caught));
      setDeleting(false);
    }
  };

  if (editing && reminder) {
    return <ReminderFormDialog pets={pets} reminder={reminder} onClose={() => setEditing(false)} onSave={save} />;
  }

  return (
    <div className="modal-backdrop" onMouseDown={() => !deleting && onClose()}>
      <section className="modal detail-modal" role="dialog" aria-modal="true" aria-labelledby="reminder-detail-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><div><span className="eyebrow">DETALLE DEL RECORDATORIO</span><h2 id="reminder-detail-title">{reminder?.titulo ?? "Recordatorio"}</h2></div><button className="icon-btn" type="button" onClick={onClose} disabled={deleting} aria-label="Cerrar">×</button></header>
        {loading && <p className="detail-status"><span className="pet-loader" /> Consultando recordatorio…</p>}
        {!loading && error && !reminder && <div className="detail-status"><p className="auth-error" role="alert">{error}</p><button className="secondary-action" type="button" onClick={onClose}>Cerrar</button></div>}
        {!loading && reminder && (
          <>
            <dl className="detail-list">
              <div><dt>Mascota</dt><dd>{reminder.mascotaNombre}</dd></div>
              <div><dt>Tipo</dt><dd>{reminderTypeLabel(reminder.tipo)}</dd></div>
              <div><dt>Fecha y hora</dt><dd>{reminderDateLabel(reminder.fechaHoraInicio)}</dd></div>
              <div><dt>Modalidad</dt><dd>{reminder.modalidad || "Única"}</dd></div>
              <div><dt>Estado</dt><dd>{reminder.estado ? stateLabels[reminder.estado] : "Sin estado"}</dd></div>
              <div><dt>Confirmación</dt><dd>{isReminderConfirmed(reminder) ? "Confirmado" : "Pendiente"}</dd></div>
            </dl>
            <div className="state-editor">
              <label>Estado<select value={state} onChange={(event) => setState(event.target.value as ReminderState)} disabled={changingState}>{reminderStates.map((value) => <option key={value} value={value}>{stateLabels[value]}</option>)}</select></label>
              <button className="secondary-action" type="button" onClick={() => void changeState()} disabled={changingState || state === reminder.estado}>{changingState ? "Guardando…" : "Cambiar estado"}</button>
            </div>
            <ReminderConfirmationButton reminder={reminder} controller={controller} onChanged={setReminder} />
            {reminder.modalidad.toLowerCase() !== "unica" && <p className="form-hint">La recurrencia todavía no se edita desde Web.</p>}
            {error && <p className="auth-error reminder-action-error" role="alert">{error}</p>}
            <div className="detail-actions">
              <button type="button" onClick={() => setEditing(true)} disabled={reminder.modalidad.toLowerCase() !== "unica"}><Pencil /> Editar</button>
              <button className="danger-action" type="button" onClick={() => setConfirmingDelete(true)}><Trash2 /> Eliminar</button>
            </div>
          </>
        )}
        {confirmingDelete && reminder && <div className="inline-confirm"><p>¿Eliminar “{reminder.titulo}”? Esta acción lo quitará del listado.</p><div className="modal-actions"><button type="button" onClick={() => setConfirmingDelete(false)} disabled={deleting}>Cancelar</button><button className="danger-action" type="button" onClick={() => void remove()} disabled={deleting}>{deleting ? "Eliminando…" : "Confirmar eliminación"}</button></div></div>}
      </section>
    </div>
  );
}
