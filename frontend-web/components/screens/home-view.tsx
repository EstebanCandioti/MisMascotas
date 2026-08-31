"use client";

import { useState } from "react";
import { ChevronRight, HeartPulse, Plus, Stethoscope, Syringe } from "lucide-react";

import { pets } from "@/data/pets";
import { Card, PetAvatar } from "@/components/layout/app-navigation";
import type { AppView, PetId } from "@/types/app";

type HomeViewProps = {
  go: (view: AppView) => void;
  selectPet: (id: PetId) => void;
};

export function HomeView({ go, selectPet }: HomeViewProps) {
  const [completed, setCompleted] = useState<string[]>([]);

  const toggle = (id: string) => {
    setCompleted((items) =>
      items.includes(id) ? items.filter((item) => item !== id) : [...items, id],
    );
  };

  const TaskItem = ({
    id,
    title,
    detail,
    time,
  }: {
    id: string;
    title: string;
    detail: string;
    time: string;
  }) => (
    <div className={`task ${completed.includes(id) ? "completed" : ""}`}>
      <button
        onClick={() => toggle(id)}
        aria-label={completed.includes(id) ? "Marcar pendiente" : "Marcar completado"}
      >
        {completed.includes(id) ? "✓" : <span />}
      </button>
      <div>
        <strong>{title}</strong>
        <small>{detail}</small>
      </div>
      <span className="time">{time}</span>
    </div>
  );

  return (
    <>
      <div className="welcome">
        <div>
          <span className="eyebrow">Jueves, 27 de agosto</span>
          <h1>
            Hola, Usuario <span>👋</span>
          </h1>
          <p>Todo el cuidado de tus mascotas, en un solo lugar.</p>
        </div>
        <button
          className="avatar-user"
          onClick={() => go("settings")}
          aria-label="Abrir configuración"
        >
          US
        </button>
      </div>

      <div className="quick-summary">
        <div><strong>3</strong><span>Mascotas</span></div>
        <div><strong>2</strong><span>Próximos</span></div>
        <div><strong>1</strong><span>Compartido</span></div>
      </div>

      <section className="section">
        <div className="section-title">
          <h2>Hoy</h2>
          <span className="date-pill">{3 - completed.length} pendientes</span>
        </div>
        <Card className="today-card">
          <div className="today-pet">
            <PetAvatar id="fido" size="sm" />
            <div><strong>Fido</strong><span>Salud preventiva</span></div>
          </div>
          <TaskItem id="selamectina" title="Selamectina 18 mg" detail="Aplicar pipeta antiparasitaria" time="20:00" />
          <TaskItem id="comprimido" title="Comprimido antigarrapatas" detail="Con la comida" time="21:00" />
        </Card>
        <Card className="today-card">
          <div className="today-pet">
            <PetAvatar id="luna" size="sm" />
            <div><strong>Luna</strong><span>Cuidado diario</span></div>
          </div>
          <TaskItem id="pipeta" title="Pipeta antiparasitaria" detail="Recordatorio mensual" time="Hoy" />
        </Card>
      </section>

      <section className="section">
        <div className="section-title">
          <h2>Actividad reciente</h2>
          <button onClick={() => go("calendar")}>Ver todo <ChevronRight /></button>
        </div>
        <div className="activity-list">
          <div>
            <span className="activity-icon purple"><Syringe /></span>
            <p><b>Laura</b> marcó que le dio Amoxicilina a Fido<small>Hace 25 min</small></p>
          </div>
          <div>
            <span className="activity-icon blue"><Stethoscope /></span>
            <p>Registraste una consulta de Luna<small>Ayer, 18:40</small></p>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="section-title">
          <h2>Próximos recordatorios</h2>
          <button onClick={() => go("reminders")}>Ver todos <ChevronRight /></button>
        </div>
        <div className="reminder-strip">
          <div>
            <Syringe />
            <p><b>Vacuna antirrábica</b><span>Fido · En 3 días</span></p>
            <strong>30 AGO</strong>
          </div>
          <div>
            <HeartPulse />
            <p><b>Control anual</b><span>Rex · En 7 días</span></p>
            <strong>03 SEP</strong>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="section-title">
          <h2>Mis mascotas</h2>
          <button onClick={() => go("pets")}>Administrar <ChevronRight /></button>
        </div>
        <div className="pet-row">
          {(Object.keys(pets) as PetId[]).map((id) => (
            <button key={id} onClick={() => selectPet(id)}>
              <PetAvatar id={id} />
              <strong>{pets[id].name}</strong>
              <span>{pets[id].breed}</span>
            </button>
          ))}
          <button className="add-pet" onClick={() => go("pets")}>
            <Plus />
            <strong>Agregar</strong>
            <span>Nueva mascota</span>
          </button>
        </div>
      </section>
    </>
  );
}
