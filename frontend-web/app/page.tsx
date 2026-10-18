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
import { usePets } from "@/features/pets/use-pets";
import type { AppView } from "@/types/app";

export default function App() {
  const { status, user, logout } = useAuth();
  const [view, setView] = useState<AppView>("home");
  const petsController = usePets(status === "authenticated");
  const userName = user?.nombre ?? "Usuario";
  const userInitials = userName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "US";

  let content: ReactNode;
  switch (view) {
    case "home": content = <HomeView go={setView} userName={userName} controller={petsController} />; break;
    case "pets": content = <PetsView controller={petsController} />; break;
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
        <div className="side-tip"><Database /><b>Datos sincronizados</b><p>Tus mascotas se cargan desde tu cuenta.</p></div>
      </aside>
      <section className="phone-frame">
        <div className="screen-content">{content}</div>
        <BottomNav view={view} go={setView} />
      </section>
      <aside className="desktop-context">
        <div className="context-card"><Database /><h2>Mascotas conectadas</h2><p>Consultá y administrá tus mascotas con información sincronizada.</p></div>
      </aside>
    </main>
  );
}
