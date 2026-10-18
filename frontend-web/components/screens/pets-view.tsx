"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronRight, Plus } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";
import { PetFormDialog } from "@/features/pets/pet-form-dialog";
import { PetAvatar, petSummary } from "@/features/pets/pet-presentation";
import type { PetRequest } from "@/features/pets/types";
import type { PetsController } from "@/features/pets/use-pets";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function PetsView({ controller }: { controller: PetsController }) {
  const router = useRouter();
  const [creating, setCreating] = useState(false);

  const createPet = async (request: PetRequest) => {
    const created = await controller.createPet(request);
    setCreating(false);
    router.push(`/mascotas/${encodeURIComponent(created.idMascota)}`);
  };

  return (
    <>
      <Header title="Mascotas" subtitle="Tu familia, siempre cerca" action={<button className="icon-btn" onClick={() => setCreating(true)} aria-label="Agregar mascota"><Plus /></button>} />
      {(controller.status === "idle" || controller.status === "loading") && <EmptyState icon={<span className="pet-loader" />} title="Cargando mascotas" description="Estamos buscando la información de tu familia." />}
      {controller.status === "error" && <EmptyState icon={<span>!</span>} title="No pudimos cargar tus mascotas" description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."} action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>} />}
      {controller.status === "success" && controller.pets.length === 0 && <EmptyState icon={<span>🐾</span>} title="Todavía no cargaste mascotas" description="Agregá la primera para empezar a organizar su información." action={<button className="secondary-action" onClick={() => setCreating(true)}>Agregar mascota</button>} />}
      {controller.status === "success" && controller.pets.length > 0 && (
        <div className="pet-list">
          {controller.pets.map((pet) => (
            <Link className="pet-list-card" key={pet.idMascota} href={`/mascotas/${encodeURIComponent(pet.idMascota)}`}>
              <PetAvatar pet={pet} size="lg" />
              <div><h3>{pet.nombre}</h3><p>{petSummary(pet)}</p></div>
              <ChevronRight />
            </Link>
          ))}
        </div>
      )}
      {(controller.status === "success" || controller.status === "error") && <button className="fab" onClick={() => setCreating(true)} aria-label="Agregar mascota"><Plus /></button>}
      {creating && <PetFormDialog onClose={() => setCreating(false)} onSave={createPet} />}
    </>
  );
}
