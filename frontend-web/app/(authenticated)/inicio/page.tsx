"use client";

import { HomeView } from "@/components/screens/home-view";
import { useAuth } from "@/features/auth/auth-context";
import { useClinicalEventsController } from "@/features/clinical-events/clinical-events-context";
import { usePetsController } from "@/features/pets/pets-context";
import { useRemindersController } from "@/features/reminders/reminders-context";

export default function HomePage() {
  const { user } = useAuth();
  const controller = usePetsController();
  const remindersController = useRemindersController();
  const clinicalEventsController = useClinicalEventsController();
  return <HomeView userName={user?.nombre ?? "Usuario"} controller={controller} remindersController={remindersController} clinicalEventsController={clinicalEventsController} />;
}
