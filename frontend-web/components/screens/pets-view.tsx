import { ChevronRight, Plus, Search, Users } from "lucide-react";

import { pets } from "@/data/pets";
import { Header, PetAvatar } from "@/components/layout/app-navigation";
import type { PetId } from "@/types/app";
import type { ActionKind } from "@/types/actions";

type PetsViewProps = {
  shared: boolean;
  selectPet: (id: PetId) => void;
  setShared: (value: boolean) => void;
  open: (kind: ActionKind) => void;
};

export function PetsView({ shared, selectPet, setShared, open }: PetsViewProps) {
  const ids: PetId[] = shared ? ["rex"] : ["fido", "luna", "rex"];

  return (
    <>
      <Header
        title="Mascotas"
        subtitle="Tu familia, siempre cerca"
        action={
          <button className="icon-btn" onClick={() => open("pet")} aria-label="Agregar mascota">
            <Search />
          </button>
        }
      />
      <div className="segmented">
        <button className={!shared ? "active" : ""} onClick={() => setShared(false)}>
          Mis mascotas
        </button>
        <button className={shared ? "active" : ""} onClick={() => setShared(true)}>
          Cuidado compartido
        </button>
      </div>
      <div className="pet-list">
        {ids.map((id) => (
          <button className="pet-list-card" key={id} onClick={() => selectPet(id)}>
            <PetAvatar id={id} size="lg" />
            <div>
              <h3>{pets[id].name}</h3>
              <p>{pets[id].breed} · {pets[id].species} · {pets[id].age}</p>
              {shared && <span className="share-chip"><Users /> Perfil: Familia</span>}
            </div>
            <ChevronRight />
          </button>
        ))}
      </div>
      <button className="fab" onClick={() => open("pet")} aria-label="Agregar mascota">
        <Plus />
      </button>
    </>
  );
}
