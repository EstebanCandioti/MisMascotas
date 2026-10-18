"use client";

import { useEffect, useState } from "react";
import { Album, Bell, CalendarDays, PawPrint, Plus, Users } from "lucide-react";
import Link from "next/link";

import { Card, EmptyState } from "@/components/layout/app-navigation";
import { PetAvatar } from "@/features/pets/pet-presentation";
import type { PetsController } from "@/features/pets/use-pets";
import { reminderDateLabel } from "@/features/reminders/mappers";
import type { RemindersController } from "@/features/reminders/use-reminders";
import { getApiErrorMessage } from "@/lib/api/api-error";

type HomeViewProps = {
  userName: string;
  controller: PetsController;
  remindersController: RemindersController;
};

export function HomeView({ userName, controller, remindersController }: HomeViewProps) {
  const [currentTime, setCurrentTime] = useState<number | null>(null);
  const initials = userName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "US";
  const upcomingReminders = currentTime === null
    ? []
    : remindersController.reminders.filter((reminder) => Date.parse(reminder.fechaHoraInicio) >= currentTime);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) setCurrentTime(Date.now());
    });
    const intervalId = window.setInterval(() => setCurrentTime(Date.now()), 60_000);
    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  return (
    <>
      <div className="welcome">
        <div>
          <span className="eyebrow">MIS MASCOTAS</span>
          <h1>Hola, {userName} <span>👋</span></h1>
          <p>Todo el cuidado de tus mascotas, en un solo lugar.</p>
        </div>
        <Link className="avatar-user" href="/perfil" aria-label="Abrir perfil">{initials}</Link>
      </div>

      <div className="quick-summary">
        <div><strong>{controller.status === "success" ? controller.pets.length : "—"}</strong><span>Mascotas</span></div>
        <div><strong>{remindersController.status === "success" && currentTime !== null ? upcomingReminders.length : "—"}</strong><span>Próximos</span></div>
        <div><strong>—</strong><span>Compartido</span></div>
      </div>

      <section className="section">
        <div className="section-title"><h2>Mis mascotas</h2>{controller.pets.length > 0 && <Link href="/mascotas">Administrar</Link>}</div>
        {(controller.status === "idle" || controller.status === "loading") && (
          <EmptyState icon={<span className="pet-loader" />} title="Cargando mascotas" description="Estamos preparando tu información." />
        )}
        {controller.status === "error" && (
          <EmptyState
            icon={<PawPrint />}
            title="No pudimos cargar tus mascotas"
            description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."}
            action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>}
          />
        )}
        {controller.status === "success" && controller.pets.length === 0 && (
          <EmptyState
            icon={<PawPrint />}
            title="Todavía no cargaste mascotas"
            description="Agregá la primera para verla también en tu inicio."
            action={<Link className="secondary-action" href="/mascotas">Agregar mascota</Link>}
          />
        )}
        {controller.status === "success" && controller.pets.length > 0 && (
          <div className="pet-row">
            {controller.pets.map((pet) => (
              <Link key={pet.idMascota} href={`/mascotas/${encodeURIComponent(pet.idMascota)}`}>
                <PetAvatar pet={pet} />
                <strong>{pet.nombre}</strong>
                <span>{pet.raza || pet.especie}</span>
              </Link>
            ))}
            <Link className="add-pet" href="/mascotas">
              <Plus /><strong>Agregar</strong><span>Nueva mascota</span>
            </Link>
          </div>
        )}

        <div className="section-title home-reminders-title"><h2>Próximos recordatorios</h2><Link href="/recordatorios">Ver todos</Link></div>
        {(remindersController.status === "idle" || remindersController.status === "loading") && <Card className="home-reminder-status">Cargando próximos recordatorios…</Card>}
        {remindersController.status === "error" && <Card className="home-reminder-status"><span>No pudimos cargar los recordatorios.</span><button onClick={() => void remindersController.reload()}>Reintentar</button></Card>}
        {remindersController.status === "success" && upcomingReminders.length === 0 && <Card className="home-reminder-status"><span>No hay próximos recordatorios.</span><Link href="/recordatorios">Crear uno</Link></Card>}
        {remindersController.status === "success" && upcomingReminders.length > 0 && (
          <div className="home-reminder-list">
            {upcomingReminders.slice(0, 3).map((reminder) => <Card key={reminder.idRecordatorio}><Bell /><div><strong>{reminder.titulo}</strong><span>{reminder.mascotaNombre} · {reminderDateLabel(reminder.fechaHoraInicio)}</span></div></Card>)}
          </div>
        )}

        <div className="integration-links">
          <Link href="/recordatorios"><Bell /><span>Recordatorios</span></Link>
          <Link href="/eventos"><CalendarDays /><span>Eventos</span></Link>
          <Link href="/albumes"><Album /><span>Álbumes</span></Link>
          <Link href="/cuidadores"><Users /><span>Cuidadores</span></Link>
        </div>
      </section>
    </>
  );
}
