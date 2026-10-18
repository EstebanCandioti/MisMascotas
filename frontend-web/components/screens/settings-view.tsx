import { LogOut, Settings } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

type SettingsViewProps = { logout: () => void };

export function SettingsView({ logout }: SettingsViewProps) {
  return (
    <>
      <Header title="Configuración" subtitle="Cuenta y preferencias" />
      <div className="settings-list">
        <EmptyState icon={<Settings />} title="Preferencias pendientes" description="Las opciones de cuenta se habilitarán al conectar sus contratos de backend." />
        <button className="logout" onClick={logout}><LogOut /> Cerrar sesión</button>
      </div>
    </>
  );
}
