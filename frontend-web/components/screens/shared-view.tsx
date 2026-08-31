import { UserPlus } from "lucide-react";

import { Card, Header } from "@/components/layout/app-navigation";
import type { ActionKind } from "@/types/actions";

type SharedViewProps = { open: (kind: ActionKind) => void; notify: (message: string) => void };

export function SharedView({ open, notify }: SharedViewProps) {
  const invited = [
    ["LG", "Laura García", "Familia", "Fido y Luna"],
    ["CL", "Carlos López", "Cuidador", "Rex"],
  ];

  return (
    <>
      <Header title="Cuidado compartido" subtitle="Personas que cuidan con vos" />
      <section className="section">
        <div className="section-title"><h2>Personas que invité</h2><span className="date-pill">2 activas</span></div>
        {invited.map(([initials, name, role, pets]) => (
          <Card className="person-card" key={name}>
            <span className="initials">{initials}</span>
            <div><h3>{name}</h3><p>Perfil: {role} · {pets}</p><small>Desde mar 2026</small></div>
            <button onClick={() => open("manage")}>Gestionar</button>
          </Card>
        ))}
        <button className="primary wide" onClick={() => open("invite")}><UserPlus /> Invitar persona</button>
      </section>
      <section className="section">
        <div className="section-title"><h2>Me invitaron a su cuidado</h2></div>
        <Card className="person-card">
          <span className="initials coral">MG</span>
          <div><h3>María González</h3><p>Max (Labrador) · Perfil: Familia</p><small>Acceso activo</small></div>
          <button className="danger" onClick={() => notify("Saliste del cuidado compartido de Max")}>Salir</button>
        </Card>
      </section>
    </>
  );
}
