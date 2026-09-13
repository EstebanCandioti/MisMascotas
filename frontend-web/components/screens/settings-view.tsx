import { Bell, ChevronRight, Crown, Download, FileText, LogOut, Mail, ShieldCheck } from "lucide-react";

import { Card, Header } from "@/components/layout/app-navigation";
import type { ActionKind } from "@/types/actions";

type SettingsViewProps = { logout: () => void; open: (kind: ActionKind) => void; exportData: () => void };

export function SettingsView({ logout, open, exportData }: SettingsViewProps) {
  return (
    <>
      <Header title="Configuración" subtitle="Cuenta y preferencias" />
      <section className="settings-profile">
        <div className="avatar-user large">US</div>
        <div><h2>Usuario Demo</h2><p>usuario@demo.com</p><span><Crown /> Plan Premium</span></div>
      </section>
      <div className="settings-list">
        <Card>
          <button onClick={() => open("notifications")}><Bell /><div><b>Notificaciones</b><span>Recordatorios y novedades</span></div><ChevronRight /></button>
          <button onClick={() => open("security")}><ShieldCheck /><div><b>Privacidad y seguridad</b><span>Contraseña y verificación</span></div><ChevronRight /></button>
          <button onClick={exportData}><Download /><div><b>Exportar mis datos</b><span>Descargá tu información</span></div><ChevronRight /></button>
        </Card>
        <Card>
          <button onClick={() => open("legal")}><FileText /><div><b>Términos y privacidad</b><span>Información legal</span></div><ChevronRight /></button>
          <button onClick={() => open("support")}><Mail /><div><b>Ayuda y soporte</b><span>Estamos para ayudarte</span></div><ChevronRight /></button>
        </Card>
        <button className="logout" onClick={logout}><LogOut /> Cerrar sesión</button>
        <p className="version">Mis Mascotas · Versión 1.0.0</p>
      </div>
    </>
  );
}
