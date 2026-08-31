import {
  Activity,
  Album,
  Bell,
  Crown,
  Menu,
  Pencil,
  Stethoscope,
  Syringe,
  Weight,
} from "lucide-react";

import { pets } from "@/data/pets";
import { Card, Header, PetAvatar } from "@/components/layout/app-navigation";
import type { AppView, PetId } from "@/types/app";
import type { ActionKind } from "@/types/actions";

type ProfileViewProps = {
  id: PetId;
  back: () => void;
  go: (view: AppView) => void;
  open: (kind: ActionKind) => void;
};

export function ProfileView({ id, back, go, open }: ProfileViewProps) {
  const pet = pets[id];

  return (
    <>
      <Header
        title={pet.name}
        onBack={back}
        action={
          <button className="icon-btn" onClick={() => open("edit")} aria-label="Editar mascota">
            <Menu />
          </button>
        }
      />
      <div className="profile-hero">
        <PetAvatar id={id} size="lg" />
        <h2>{pet.name}</h2>
        <p>{pet.breed} · {pet.species} · {pet.age}</p>
      </div>
      <div className="action-grid">
        <button onClick={() => open("event")}><Stethoscope /><span>Registrar evento clínico</span></button>
        <button onClick={() => open("reminder")}><Bell /><span>Crear recordatorio</span></button>
        <button onClick={() => open("history")}><Activity /><span>Historial y evolución</span></button>
        <button onClick={() => open("edit")}><Pencil /><span>Editar mascota</span></button>
      </div>
      <Card className="metric-card">
        <div><span>Peso actual</span><strong>{pet.weight}</strong><small>Actualizado hace 5 días</small></div>
        <Weight />
      </Card>
      <Card className="next-card">
        <div><span>Próximo recordatorio</span><strong>Vacuna antirrábica</strong><small>En {pet.days} días</small></div>
        <Syringe />
      </Card>
      <Card>
        <div className="card-heading">
          <span><Crown /> Insignias</span>
          <button onClick={() => open("badges")}>Ver todas</button>
        </div>
        <div className="badges"><span>Destructor 💀</span><span>Glotón 🍖</span></div>
      </Card>
      <Card>
        <div className="card-heading">
          <span><Album /> Álbumes</span>
          <button onClick={() => go("albums")}>Ver todos</button>
        </div>
        <div className="album-preview">
          <div className="photo">{pet.emoji}</div>
          <div>
            <b>{id === "luna" ? "Luna en su casa" : "Verano 2026"}</b>
            <small>8 fotos</small>
          </div>
        </div>
      </Card>
    </>
  );
}
