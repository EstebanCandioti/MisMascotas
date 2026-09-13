import { HeartPulse, Plus, Syringe } from "lucide-react";

import { Card, Header, PetAvatar } from "@/components/layout/app-navigation";
import type { ActionKind } from "@/types/actions";

export function RemindersView({ open }: { open: (kind: ActionKind) => void }) {
  return (
    <>
      <Header title="Mis recordatorios" subtitle="Próximos cuidados y controles" />
      <div className="filter-pills">
        <button className="active">Todos</button>
        <button>Hoy</button>
        <button>Esta semana</button>
      </div>
      <div className="group-title">
        <PetAvatar id="fido" size="sm" />
        <div><b>Fido</b><span>2 recordatorios</span></div>
      </div>
      <div className="reminders">
        {[
          ["Pipeta antiparasitaria", "Cada 3 meses", "15 JUN", "Activo"],
          ["Vacuna antirrábica", "Todos los años", "30 AGO", "Próximo"],
        ].map(([title, frequency, date, status]) => (
          <Card key={title}>
            <Syringe />
            <div><h3>{title}</h3><p>{frequency}</p><small>Próximo: {date}</small></div>
            <span className="status">{status}</span>
          </Card>
        ))}
      </div>
      <div className="group-title">
        <PetAvatar id="luna" size="sm" />
        <div><b>Luna</b><span>1 recordatorio</span></div>
      </div>
      <div className="reminders">
        <Card>
          <HeartPulse />
          <div><h3>Control anual</h3><p>Una vez al año</p><small>Próximo: 22 JUN</small></div>
          <span className="status">Activo</span>
        </Card>
      </div>
      <button className="fab" onClick={() => open("reminder")} aria-label="Crear recordatorio"><Plus /></button>
    </>
  );
}
