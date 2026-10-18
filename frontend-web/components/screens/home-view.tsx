import { Album, Bell, CalendarDays, PawPrint, Users } from "lucide-react";

import { EmptyState } from "@/components/layout/app-navigation";
import type { AppView } from "@/types/app";

type HomeViewProps = {
  go: (view: AppView) => void;
  userName: string;
};

export function HomeView({ go, userName }: HomeViewProps) {
  return (
    <>
      <div className="welcome">
        <div>
          <span className="eyebrow">MIS MASCOTAS</span>
          <h1>
            Hola, {userName} <span>👋</span>
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
        <div><strong>—</strong><span>Mascotas</span></div>
        <div><strong>—</strong><span>Próximos</span></div>
        <div><strong>—</strong><span>Compartido</span></div>
      </div>

      <section className="section">
        <EmptyState
          icon={<PawPrint />}
          title="Sin datos de mascotas"
          description="El resumen se completará cuando el módulo de mascotas consuma la API."
          action={<button className="secondary-action" onClick={() => go("pets")}>Ver mascotas</button>}
        />
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
