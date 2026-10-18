import { Album, Bell, CalendarDays, PawPrint, Plus, Users } from "lucide-react";

import { EmptyState } from "@/components/layout/app-navigation";
import { PetAvatar } from "@/features/pets/pet-presentation";
import type { PetsController } from "@/features/pets/use-pets";
import { getApiErrorMessage } from "@/lib/api/api-error";
import type { AppView } from "@/types/app";

type HomeViewProps = {
  go: (view: AppView) => void;
  userName: string;
  controller: PetsController;
};

export function HomeView({ go, userName, controller }: HomeViewProps) {
  const initials = userName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "US";

  return (
    <>
      <div className="welcome">
        <div>
          <span className="eyebrow">MIS MASCOTAS</span>
          <h1>Hola, {userName} <span>👋</span></h1>
          <p>Todo el cuidado de tus mascotas, en un solo lugar.</p>
        </div>
        <button className="avatar-user" onClick={() => go("settings")} aria-label="Abrir configuración">{initials}</button>
      </div>

      <div className="quick-summary">
        <div><strong>{controller.status === "success" ? controller.pets.length : "—"}</strong><span>Mascotas</span></div>
        <div><strong>—</strong><span>Próximos</span></div>
        <div><strong>—</strong><span>Compartido</span></div>
      </div>

      <section className="section">
        <div className="section-title"><h2>Mis mascotas</h2>{controller.pets.length > 0 && <button onClick={() => go("pets")}>Administrar</button>}</div>
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
            action={<button className="secondary-action" onClick={() => go("pets")}>Agregar mascota</button>}
          />
        )}
        {controller.status === "success" && controller.pets.length > 0 && (
          <div className="pet-row">
            {controller.pets.map((pet) => (
              <button key={pet.idMascota} onClick={() => go("pets")}>
                <PetAvatar pet={pet} />
                <strong>{pet.nombre}</strong>
                <span>{pet.raza || pet.especie}</span>
              </button>
            ))}
            <button className="add-pet" onClick={() => go("pets")}>
              <Plus /><strong>Agregar</strong><span>Nueva mascota</span>
            </button>
          </div>
        )}

        <div className="integration-links">
          <button onClick={() => go("reminders")}><Bell /><span>Recordatorios</span></button>
          <button onClick={() => go("calendar")}><CalendarDays /><span>Calendario</span></button>
          <button onClick={() => go("albums")}><Album /><span>Álbumes</span></button>
          <button onClick={() => go("shared")}><Users /><span>Cuidadores</span></button>
        </div>
      </section>
    </>
  );
}
