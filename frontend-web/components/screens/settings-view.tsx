import { Crown, LogOut, Settings } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";
import type { UserProfile } from "@/features/auth/types";

type SettingsViewProps = { user: UserProfile | null; logout: () => void };

export function SettingsView({ user, logout }: SettingsViewProps) {
  const name = user?.nombre ?? "Usuario";
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "US";

  return (
    <>
      <Header title="Configuración" subtitle="Cuenta y preferencias" />
      <section className="settings-profile">
        <div className="avatar-user large">{initials}</div>
        <div><h2>{name}</h2><p>{user?.email ?? "Cargando perfil…"}</p><span><Crown /> {user?.esPremium ? "Plan Premium" : "Plan gratuito"}</span></div>
      </section>
      <div className="settings-list">
        <EmptyState icon={<Settings />} title="Preferencias pendientes" description="Las opciones de cuenta se habilitarán al conectar sus contratos de backend." />
        <button className="logout" onClick={logout}><LogOut /> Cerrar sesión</button>
      </div>
    </>
  );
}
