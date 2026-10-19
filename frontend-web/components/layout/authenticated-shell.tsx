"use client";

import { type PropsWithChildren, useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Database, PawPrint } from "lucide-react";

import { AuthScreen } from "@/components/auth/auth-screen";
import { BottomNav, navigationItems } from "@/components/layout/app-navigation";
import { useAuth } from "@/features/auth/auth-context";
import { ClinicalEventsProvider } from "@/features/clinical-events/clinical-events-context";
import { useClinicalEvents } from "@/features/clinical-events/use-clinical-events";
import { PetsProvider } from "@/features/pets/pets-context";
import { usePets } from "@/features/pets/use-pets";
import { RemindersProvider } from "@/features/reminders/reminders-context";
import { useReminders } from "@/features/reminders/use-reminders";

export function AuthenticatedShell({ children }: PropsWithChildren) {
  const { status, user } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const petsController = usePets(status === "authenticated");
  const remindersController = useReminders(status === "authenticated", petsController);
  const clinicalEventsController = useClinicalEvents(status === "authenticated", petsController);
  const userName = user?.nombre ?? "Usuario";
  const userInitials = userName.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("") || "US";

  useEffect(() => {
    if (status !== "loading" && status !== "authenticated") router.replace("/login");
  }, [router, status]);

  if (status !== "authenticated") return <AuthScreen />;

  return (
    <PetsProvider value={petsController}>
      <RemindersProvider value={remindersController}>
        <ClinicalEventsProvider value={clinicalEventsController}>
          <main className="app-shell">
            <aside className="desktop-sidebar">
              <Link className="side-logo" href="/inicio"><PawPrint /> <b>Mis Mascotas</b></Link>
              <Link className="side-user" href="/perfil"><span>{userInitials}</span><div><b>{userName}</b><small>{user?.esPremium ? "Plan Premium" : "Plan gratuito"}</small></div></Link>
              <nav aria-label="Navegación principal">
                {navigationItems.map(({ href, label, Icon }) => {
                  const active = pathname === href || pathname.startsWith(`${href}/`);
                  return <Link key={href} className={active ? "active" : ""} href={href} aria-current={active ? "page" : undefined}><Icon />{label}</Link>;
                })}
              </nav>
              <div className="side-tip"><Database /><b>Datos sincronizados</b><p>Mascotas, recordatorios e historiales se cargan desde tu cuenta.</p></div>
            </aside>
            <section className="app-content">{children}</section>
            <BottomNav />
          </main>
        </ClinicalEventsProvider>
      </RemindersProvider>
    </PetsProvider>
  );
}
