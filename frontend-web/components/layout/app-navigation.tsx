import type { PropsWithChildren, ReactNode } from "react";
import { Album, CalendarDays, ChevronLeft, Home, PawPrint, Settings, UserPlus } from "lucide-react";
import type { AppView } from "@/types/app";

export const navigationItems = [
  ["home", "Inicio", Home],
  ["pets", "Mascotas", PawPrint],
  ["albums", "Álbumes", Album],
  ["calendar", "Calendario", CalendarDays],
  ["shared", "Cuidadores", UserPlus],
  ["settings", "Configuración", Settings],
] as const;

export function BottomNav({ view, go }: { view: AppView; go: (view: AppView) => void }) {
  return <nav className="bottom-nav" aria-label="Navegación principal">{navigationItems.map(([id, label, Icon]) => <button key={id} className={view === id ? "active" : ""} onClick={() => go(id)} aria-label={label}><Icon /><span>{label}</span></button>)}</nav>;
}

export function Header({ title, subtitle, onBack, action }: { title: string; subtitle?: string; onBack?: () => void; action?: ReactNode }) {
  return <header className="screen-header">{onBack ? <button className="icon-btn" onClick={onBack} aria-label="Volver"><ChevronLeft /></button> : <span className="brand-mark"><PawPrint /></span>}<div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action && <div className="header-action">{action}</div>}</header>;
}

export function Card({ children, className = "" }: PropsWithChildren<{ className?: string }>) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <Card className="empty-state">
      <span className="empty-state-icon">{icon}</span>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </Card>
  );
}
