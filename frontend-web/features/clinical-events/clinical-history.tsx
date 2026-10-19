"use client";

import { useState } from "react";
import { Activity, Plus } from "lucide-react";

import { Card, EmptyState } from "@/components/layout/app-navigation";
import { ClinicalEventFormDialog } from "@/features/clinical-events/clinical-event-form-dialog";
import { clinicalEventDateLabel, clinicalEventSummary, clinicalEventTypeLabel } from "@/features/clinical-events/mappers";
import type { ClinicalEventRequest } from "@/features/clinical-events/types";
import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function ClinicalHistory({ petId, petName, controller }: { petId: string; petName: string; controller: ClinicalEventsController }) {
  const [creating, setCreating] = useState(false);
  const petEvents = controller.events.filter((event) => event.mascotaId === petId);

  const createEvent = async (request: ClinicalEventRequest) => {
    await controller.createEvent(request);
    setCreating(false);
  };

  return (
    <section className="clinical-history" id="historial-clinico">
      <div className="section-title"><h2>Historial clínico</h2><button onClick={() => setCreating(true)}><Plus /> Registrar evento</button></div>
      {(controller.status === "idle" || controller.status === "loading") && <Card className="home-reminder-status">Cargando historial clínico…</Card>}
      {controller.status === "error" && <EmptyState icon={<Activity />} title="No pudimos cargar el historial" description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."} action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>} />}
      {controller.status === "success" && petEvents.length === 0 && <EmptyState icon={<Activity />} title="Sin eventos clínicos" description={`Todavía no hay eventos registrados para ${petName}.`} action={<button className="secondary-action" onClick={() => setCreating(true)}>Registrar evento</button>} />}
      {controller.status === "success" && petEvents.length > 0 && <div className="clinical-event-list">{petEvents.map((event) => <Card key={event.idEvento}><span className={`clinical-event-icon ${event.tipo.toLowerCase()}`}><Activity /></span><div><div className="clinical-event-heading"><h3>{clinicalEventTypeLabel(event.tipo)}</h3><time dateTime={event.fecha}>{clinicalEventDateLabel(event.fecha)}</time></div><strong>{clinicalEventSummary(event)}</strong>{event.diagnostico && <p><b>Diagnóstico:</b> {event.diagnostico}</p>}{event.observaciones && <p>{event.observaciones}</p>}<small>Registrado por {event.registradoPorNombre || "un usuario autorizado"}</small></div></Card>)}</div>}
      {creating && <ClinicalEventFormDialog petId={petId} petName={petName} onClose={() => setCreating(false)} onSave={createEvent} />}
    </section>
  );
}
