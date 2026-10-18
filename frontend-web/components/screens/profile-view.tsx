import { Crown, Mail, UserRound } from "lucide-react";

import { Card, Header } from "@/components/layout/app-navigation";
import type { UserProfile } from "@/features/auth/types";

export function ProfileView({ user }: { user: UserProfile | null }) {
  const name = user?.nombre ?? "Usuario";
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "US";

  return (
    <>
      <Header title="Perfil" subtitle="Tu información personal" />
      <section className="settings-profile profile-page-heading">
        <div className="avatar-user large">{initials}</div>
        <div><h2>{name}</h2><p>{user?.email ?? "Cargando perfil…"}</p><span><Crown /> {user?.esPremium ? "Plan Premium" : "Plan gratuito"}</span></div>
      </section>
      <div className="profile-info-grid">
        <Card><UserRound /><div><span>Nombre</span><strong>{name}</strong></div></Card>
        <Card><Mail /><div><span>Correo electrónico</span><strong>{user?.email ?? "No disponible"}</strong></div></Card>
      </div>
    </>
  );
}
