"use client";

import type { PropsWithChildren, ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Album, Bell, CalendarDays, ChevronLeft, Home, PawPrint, Settings, UserRound } from "lucide-react";

export const navigationItems = [
  { href: "/inicio", label: "Inicio", Icon: Home },
  { href: "/mascotas", label: "Mascotas", Icon: PawPrint },
  { href: "/recordatorios", label: "Recordatorios", Icon: Bell },
  { href: "/eventos", label: "Eventos", Icon: CalendarDays },
  { href: "/albumes", label: "Álbumes", Icon: Album },
  { href: "/perfil", label: "Perfil", Icon: UserRound },
  { href: "/configuracion", label: "Configuración", Icon: Settings },
] as const;

const bottomNavigationItems = navigationItems.filter(({ href }) =>
  ["/inicio", "/mascotas", "/perfil", "/configuracion"].includes(href),
);

export function BottomNav() {
  const pathname = usePathname();
  return <nav className="bottom-nav" aria-label="Navegación principal">{bottomNavigationItems.map(({ href, label, Icon }) => {
    const active = pathname === href || pathname.startsWith(`${href}/`);
    return <Link key={href} className={active ? "active" : ""} href={href} aria-label={label} aria-current={active ? "page" : undefined}><Icon /><span>{label}</span></Link>;
  })}</nav>;
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
