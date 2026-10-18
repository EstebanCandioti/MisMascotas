"use client";

import { RemindersView } from "@/components/screens/reminders-view";
import { usePetsController } from "@/features/pets/pets-context";
import { useRemindersController } from "@/features/reminders/reminders-context";

export default function RemindersPage() {
  return <RemindersView controller={useRemindersController()} petsController={usePetsController()} />;
}
