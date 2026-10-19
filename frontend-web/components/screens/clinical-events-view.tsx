"use client";

import Link from "next/link";
import { Activity, ChevronRight } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";
import { PetAvatar } from "@/features/pets/pet-presentation";
import type { PetsController } from "@/features/pets/use-pets";
import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function ClinicalEventsView({ controller, petsController }: { controller: ClinicalEventsController; petsController: PetsController }) {
  return (
    <>
      <Header title="Eventos clínicos" subtitle="Historial de salud por mascota" />
      {(controller.status === "idle" || controller.status === "loading") && <EmptyState icon={<span className="pet-loader" />} title="Cargando historiales" description="Estamos consultando la información clínica." />}
      {controller.status === "error" && <EmptyState icon={<Activity />} title="No pudimos cargar los eventos" description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."} action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>} />}
      {controller.status === "success" && petsController.pets.length === 0 && <EmptyState icon={<Activity />} title="Primero agregá una mascota" description="Los eventos clínicos deben pertenecer a una mascota real." action={<Link className="secondary-action" href="/mascotas">Ir a Mascotas</Link>} />}
      {controller.status === "success" && petsController.pets.length > 0 && <div className="pet-list clinical-pet-list">{petsController.pets.map((pet) => { const count = controller.events.filter((event) => event.mascotaId === pet.idMascota).length; return <Link className="pet-list-card" key={pet.idMascota} href={`/mascotas/${encodeURIComponent(pet.idMascota)}#historial-clinico`}><PetAvatar pet={pet} size="lg" /><div><h3>{pet.nombre}</h3><p>{count === 1 ? "1 evento clínico" : `${count} eventos clínicos`}</p></div><ChevronRight /></Link>; })}</div>}
    </>
  );
}
