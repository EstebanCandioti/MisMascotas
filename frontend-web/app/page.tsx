"use client";

import { type ReactNode, useState } from "react";
import { Database, PawPrint } from "lucide-react";

import { AuthScreen } from "@/components/auth/auth-screen";
import { BottomNav, navigationItems } from "@/components/layout/app-navigation";
import { AlbumsView } from "@/components/screens/albums-view";
import { CalendarView } from "@/components/screens/calendar-view";
import { HomeView } from "@/components/screens/home-view";
import { PetsView } from "@/components/screens/pets-view";
import { RemindersView } from "@/components/screens/reminders-view";
import { SettingsView } from "@/components/screens/settings-view";
import { SharedView } from "@/components/screens/shared-view";
import { useAuth } from "@/features/auth/auth-context";
import type { AppView } from "@/types/app";

export default function App() {
  const { status, user, logout } = useAuth();
  const [view, setView] = useState<AppView>("home");
  const userName = user?.nombre ?? "Usuario";
  const userInitials = userName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "US";

  let content: ReactNode;
  switch (view) {
    case "home": content = <HomeView go={setView} userName={userName} />; break;
    case "pets": content = <PetsView />; break;
    case "reminders": content = <RemindersView />; break;
    case "albums": content = <AlbumsView />; break;
    case "calendar": content = <CalendarView />; break;
    case "shared": content = <SharedView />; break;
    case "settings": content = <SettingsView user={user} logout={() => void logout()} />; break;
  }

  if (status !== "authenticated") return <AuthScreen />;

  return (
    <main className="app-shell">
      <aside className="desktop-sidebar">
        <div className="side-logo"><PawPrint /> <b>Mis Mascotas</b></div>
        <div className="side-user"><span>{userInitials}</span><div><b>{userName}</b><small>{user?.esPremium ? "Plan Premium" : "Plan gratuito"}</small></div></div>
        <nav aria-label="Navegación principal">
          {navigationItems.map(([id, label, Icon]) => <button key={id} className={view === id ? "active" : ""} onClick={() => setView(id)}><Icon />{label}</button>)}
        </nav>
        <div className="side-tip"><Database /><b>Integración en curso</b><p>Los módulos de dominio todavía no cargan datos.</p></div>
      </aside>
      <section className="phone-frame">
        <div className="screen-content">{content}</div>
        <BottomNav view={view} go={setView} />
      </section>
      <aside className="desktop-context">
        <div className="context-card"><Database /><h2>Preparado para integrar</h2><p>Auth usa el backend real. Los demás módulos muestran estados vacíos hasta conectar sus endpoints.</p></div>
      </aside>
    </main>
  );
}
