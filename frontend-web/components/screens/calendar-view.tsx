"use client";

import { useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Syringe } from "lucide-react";

import { Card, Header } from "@/components/layout/app-navigation";

const events = [
  { day: 28, date: "28 AGO", time: "09:00", pet: "Luna", title: "Pipeta antiparasitaria", type: "Medicación" },
  { day: 30, date: "30 AGO", time: "10:30", pet: "Fido", title: "Vacuna antirrábica", type: "Vacunación" },
  { day: 31, date: "31 AGO", time: "18:00", pet: "Rex", title: "Control de peso", type: "Seguimiento" },
  { day: 3, date: "03 SEP", time: "11:00", pet: "Rex", title: "Control anual", type: "Consulta" },
  { day: 5, date: "05 SEP", time: "20:00", pet: "Fido", title: "Comprimido antigarrapatas", type: "Medicación" },
];

const colors: Record<string, string> = { Fido: "fido-event", Luna: "luna-event", Rex: "rex-event" };
const days = Array.from({ length: 35 }, (_, index) => (index < 3 ? "" : String(index - 2)));
const weekDays = [24, 25, 26, 27, 28, 29, 30];

export function CalendarView() {
  const [mode, setMode] = useState<"Mes" | "Semana" | "Lista">("Mes");
  const [filter, setFilter] = useState<"Todas" | "Fido" | "Luna" | "Rex">("Todas");
  const [selectedDay, setSelectedDay] = useState(30);
  const visible = events.filter((event) => filter === "Todas" || event.pet === filter);
  const dayEvents = visible.filter((event) => event.day === selectedDay && event.date.includes("AGO"));

  return (
    <>
      <Header title="Calendario" subtitle="Agosto 2026" />
      <div className="segmented calendar-tabs">
        {(["Mes", "Semana", "Lista"] as const).map((item) => (
          <button key={item} className={mode === item ? "active" : ""} onClick={() => setMode(item)}>{item}</button>
        ))}
      </div>
      <div className="filter-pills">
        {(["Todas", "Fido", "Luna", "Rex"] as const).map((item) => (
          <button key={item} className={`${filter === item ? "active" : ""} ${item.toLowerCase()}-filter`} onClick={() => setFilter(item)}>{item}</button>
        ))}
      </div>
      {mode === "Mes" && (
        <Card className="calendar-card">
          <div className="calendar-month"><button><ChevronLeft /></button><strong>Agosto 2026</strong><button><ChevronRight /></button></div>
          <div className="weekdays">{"DLMMJVS".split("").map((day, index) => <span key={index}>{day}</span>)}</div>
          <div className="days">
            {days.map((day, index) => {
              const number = Number(day);
              const dots = visible.filter((event) => event.day === number && event.date.includes("AGO"));
              return (
                <button key={index} className={number === selectedDay ? "selected-day" : day === "27" ? "today" : ""} onClick={() => day && setSelectedDay(number)}>
                  <span>{day}</span>
                  {dots.length > 0 && <i>{dots.map((event, dotIndex) => <b key={dotIndex} className={colors[event.pet]} />)}</i>}
                </button>
              );
            })}
          </div>
        </Card>
      )}
      {mode === "Semana" && (
        <Card className="week-card">
          <div className="week-heading"><button><ChevronLeft /></button><strong>24–30 de agosto</strong><button><ChevronRight /></button></div>
          <div className="week-grid">
            {weekDays.map((day) => (
              <button key={day} className={day === selectedDay ? "active" : ""} onClick={() => setSelectedDay(day)}>
                <small>{["LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"][weekDays.indexOf(day)]}</small>
                <b>{day}</b>
                <span>{visible.filter((event) => event.day === day && event.date.includes("AGO")).map((event, index) => <i key={index} className={colors[event.pet]} />)}</span>
              </button>
            ))}
          </div>
        </Card>
      )}
      {mode === "Lista" ? (
        <section className="calendar-list">
          <div className="list-summary"><b>Próximas tareas</b><span>{visible.length} pendientes · ordenadas por fecha</span></div>
          {visible.map((event, index) => (
            <Card className="list-event" key={`${event.date}-${event.pet}`}>
              <div className="list-order">{index + 1}</div><div className={`event-pet-dot ${colors[event.pet]}`} />
              <div><strong>{event.title}</strong><span>{event.pet} · {event.type}</span></div>
              <time><b>{event.date}</b><small>{event.time}</small></time>
            </Card>
          ))}
        </section>
      ) : (
        <section className="section">
          <div className="section-title"><h2>Eventos del día {selectedDay}</h2></div>
          {dayEvents.length ? dayEvents.map((event) => (
            <Card className="event-card" key={event.title}>
              <span className={colors[event.pet]}><Syringe /></span>
              <div><b>{event.title}</b><small>{event.pet} · {event.time} · {event.type}</small></div><ChevronRight />
            </Card>
          )) : <div className="empty-day"><CalendarDays /><b>Sin eventos</b><span>No hay tareas médicas para este día.</span></div>}
        </section>
      )}
    </>
  );
}
