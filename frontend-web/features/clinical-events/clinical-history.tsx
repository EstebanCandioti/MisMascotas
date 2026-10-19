"use client";

import { useEffect, useRef, useState } from "react";
import { Activity, Plus } from "lucide-react";

import { Card, EmptyState } from "@/components/layout/app-navigation";
import { ClinicalEventFormDialog } from "@/features/clinical-events/clinical-event-form-dialog";
import { ClinicalEventDetailDialog } from "@/features/clinical-events/clinical-event-detail-dialog";
import { clinicalEventDateLabel, clinicalEventSummary, clinicalEventTypeLabel, sortClinicalEvents } from "@/features/clinical-events/mappers";
import { clinicalEventTypes, type ClinicalEvent, type ClinicalEventRequest, type ClinicalEventType } from "@/features/clinical-events/types";
import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function ClinicalHistory({ petId, petName, controller }: { petId: string; petName: string; controller: ClinicalEventsController }) {
  const [creating, setCreating] = useState(false);
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const [filter, setFilter] = useState<ClinicalEventType | "ALL">("ALL");
  const [filteredEvents, setFilteredEvents] = useState<ClinicalEvent[] | null>(null);
  const [filterLoading, setFilterLoading] = useState(false);
  const [filterError, setFilterError] = useState("");
  const filterRequest = useRef(0);
  const allPetEvents = controller.events.filter((event) => event.mascotaId === petId);
  const petEvents = filter === "ALL" ? allPetEvents : (filteredEvents ?? []);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setFilter("ALL");
      setFilteredEvents(null);
      setFilterError("");
    });
    return () => { active = false; };
  }, [petId]);

  const createEvent = async (request: ClinicalEventRequest) => {
    const created = await controller.createEvent(request);
    if (filter !== "ALL" && created.tipo === filter) setFilteredEvents((current) => sortClinicalEvents([created, ...(current ?? [])]));
    setCreating(false);
  };

  const selectFilter = async (value: ClinicalEventType | "ALL") => {
    setFilter(value);
    setFilterError("");
    if (value === "ALL") {
      setFilteredEvents(null);
      setFilterLoading(false);
      return;
    }
    const requestId = ++filterRequest.current;
    setFilteredEvents([]);
    setFilterLoading(true);
    try {
      const items = await controller.listEventsByPet(petId, value);
      if (requestId === filterRequest.current) setFilteredEvents(items);
    } catch (caught) {
      if (requestId === filterRequest.current) setFilterError(getApiErrorMessage(caught));
    } finally {
      if (requestId === filterRequest.current) setFilterLoading(false);
    }
  };

  const syncFilteredEvent = (changed: ClinicalEvent | null) => {
    if (filter === "ALL" || !selectedEventId) return;
    setFilteredEvents((current) => {
      if (!current) return current;
      if (!changed || changed.tipo !== filter) return current.filter((item) => item.idEvento !== selectedEventId);
      return sortClinicalEvents(current.map((item) => item.idEvento === changed.idEvento ? changed : item));
    });
  };

  return (
    <section className="clinical-history" id="historial-clinico">
      <div className="section-title"><h2>Historial clínico</h2><button onClick={() => setCreating(true)}><Plus /> Registrar evento</button></div>
      {controller.status === "success" && allPetEvents.length > 0 && <div className="clinical-filters" aria-label="Filtrar eventos por tipo"><button className={filter === "ALL" ? "active" : ""} type="button" onClick={() => void selectFilter("ALL")}>Todos</button>{clinicalEventTypes.map((type) => <button key={type} className={filter === type ? "active" : ""} type="button" onClick={() => void selectFilter(type)}>{clinicalEventTypeLabel(type)}</button>)}</div>}
      {(controller.status === "idle" || controller.status === "loading") && <Card className="home-reminder-status">Cargando historial clínico…</Card>}
      {controller.status === "error" && <EmptyState icon={<Activity />} title="No pudimos cargar el historial" description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."} action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>} />}
      {controller.status === "success" && filterLoading && <Card className="home-reminder-status">Consultando filtro en backend…</Card>}
      {controller.status === "success" && filterError && <p className="auth-error reminder-action-error" role="alert">{filterError}</p>}
      {controller.status === "success" && !filterLoading && petEvents.length === 0 && <EmptyState icon={<Activity />} title={filter === "ALL" ? "Sin eventos clínicos" : "Sin eventos de este tipo"} description={filter === "ALL" ? `Todavía no hay eventos registrados para ${petName}.` : "No se encontraron registros para el filtro elegido."} action={filter === "ALL" ? <button className="secondary-action" onClick={() => setCreating(true)}>Registrar evento</button> : undefined} />}
      {controller.status === "success" && !filterLoading && petEvents.length > 0 && <div className="clinical-event-list">{petEvents.map((event) => <Card key={event.idEvento}><span className={`clinical-event-icon ${event.tipo.toLowerCase()}`}><Activity /></span><div><div className="clinical-event-heading"><h3>{clinicalEventTypeLabel(event.tipo)}</h3><time dateTime={event.fecha}>{clinicalEventDateLabel(event.fecha)}</time></div><strong>{clinicalEventSummary(event)}</strong>{event.diagnostico && <p><b>Diagnóstico:</b> {event.diagnostico}</p>}{event.observaciones && <p>{event.observaciones}</p>}<small>Registrado por {event.registradoPorNombre || "un usuario autorizado"}</small><button className="text-action" type="button" onClick={() => setSelectedEventId(event.idEvento)}>Ver detalle y gestionar</button></div></Card>)}</div>}
      {creating && <ClinicalEventFormDialog petId={petId} petName={petName} onClose={() => setCreating(false)} onSave={createEvent} />}
      {selectedEventId && <ClinicalEventDetailDialog eventId={selectedEventId} petId={petId} petName={petName} controller={controller} onClose={() => setSelectedEventId(null)} onChanged={syncFilteredEvent} />}
    </section>
  );
}
