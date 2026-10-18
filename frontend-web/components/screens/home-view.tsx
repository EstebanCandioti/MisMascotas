import { Album, Bell, CalendarDays, PawPrint, Plus, Users } from "lucide-react";
import Link from "next/link";

import { EmptyState } from "@/components/layout/app-navigation";
import { PetAvatar } from "@/features/pets/pet-presentation";
import type { PetsController } from "@/features/pets/use-pets";
import { getApiErrorMessage } from "@/lib/api/api-error";

type HomeViewProps = {
  userName: string;
  controller: PetsController;
};

export function HomeView({ userName, controller }: HomeViewProps) {
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
        <Link className="avatar-user" href="/perfil" aria-label="Abrir perfil">{initials}</Link>
      </div>

      <div className="quick-summary">
        <div><strong>{controller.status === "success" ? controller.pets.length : "—"}</strong><span>Mascotas</span></div>
        <div><strong>—</strong><span>Próximos</span></div>
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
