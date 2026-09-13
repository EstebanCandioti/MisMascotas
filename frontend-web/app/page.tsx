"use client";

import { useMemo, useState } from "react";
import { Bell, PawPrint, ShieldCheck } from "lucide-react";

import { AuthScreen } from "@/components/auth/auth-screen";
import { ActionDialog } from "@/components/dialogs/action-dialog";
import { BottomNav, navigationItems } from "@/components/layout/app-navigation";
import { AlbumsView } from "@/components/screens/albums-view";
import { CalendarView } from "@/components/screens/calendar-view";
import { HomeView } from "@/components/screens/home-view";
import { PetsView } from "@/components/screens/pets-view";
import { ProfileView } from "@/components/screens/profile-view";
import { RemindersView } from "@/components/screens/reminders-view";
import { SettingsView } from "@/components/screens/settings-view";
import { SharedView } from "@/components/screens/shared-view";
import { pets } from "@/data/pets";
import { downloadJson } from "@/services/export-service";
import type { AppView, PetId } from "@/types/app";
import type { ActionKind } from "@/types/actions";

export default function App() {
  const [logged, setLogged] = useState(false);
  const [view, setView] = useState<AppView>("home");
  const [pet, setPet] = useState<PetId>("fido");
  const [shared, setShared] = useState(false);
  const [action, setAction] = useState<ActionKind | null>(null);
  const [toast, setToast] = useState("");

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };
  const selectPet = (id: PetId) => {
    setPet(id);
    setView("profile");
  };
  const exportData = () => {
    downloadJson("mis-mascotas-datos.json", {
      usuario: "Usuario Demo",
      mascotas: Object.values(pets),
      exportado: new Date().toISOString(),
    });
    notify("Tus datos se descargaron correctamente");
  };

  const content = useMemo(() => {
    switch (view) {
      case "home": return <HomeView go={setView} selectPet={selectPet} />;
      case "pets": return <PetsView shared={shared} setShared={setShared} selectPet={selectPet} open={setAction} />;
      case "profile": return <ProfileView id={pet} back={() => setView("pets")} go={setView} open={setAction} />;
      case "reminders": return <RemindersView open={setAction} />;
      case "albums": return <AlbumsView open={setAction} />;
      case "calendar": return <CalendarView />;
      case "shared": return <SharedView open={setAction} notify={notify} />;
      case "settings": return <SettingsView logout={() => setLogged(false)} open={setAction} exportData={exportData} />;
    }
  }, [pet, shared, view]);

  if (!logged) return <AuthScreen onLogin={() => setLogged(true)} />;

  return (
    <main className="app-shell">
      <aside className="desktop-sidebar">
        <div className="side-logo"><PawPrint /> <b>Mis Mascotas</b></div>
        <div className="side-user"><span>US</span><div><b>Usuario Demo</b><small>Plan Premium</small></div></div>
        <nav aria-label="Navegación principal">
          {navigationItems.map(([id, label, Icon]) => <button key={id} className={view === id ? "active" : ""} onClick={() => setView(id)}><Icon />{label}</button>)}
        </nav>
        <div className="side-tip"><Bell /><b>Todo bajo control</b><p>Tenés 3 recordatorios pendientes.</p></div>
      </aside>
      <section className="phone-frame">
        <div className="statusbar"><span>9:41</span><div><span>●●●</span><span>⌁</span><span>▰</span></div></div>
        <div className="screen-content">{content}</div>
        <BottomNav view={view} go={setView} />
      </section>
      <aside className="desktop-context">
        <div className="context-card"><ShieldCheck /><h2>Panel demostrativo</h2><p>Las acciones funcionan con datos de ejemplo, sin conexión al backend.</p></div>
      </aside>
      {action && <ActionDialog kind={action} pet={pet} close={() => setAction(null)} done={(message) => { setAction(null); notify(message); }} />}
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </main>
  );
}
