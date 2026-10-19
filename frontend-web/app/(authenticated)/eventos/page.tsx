"use client";

import { ClinicalEventsView } from "@/components/screens/clinical-events-view";
import { useClinicalEventsController } from "@/features/clinical-events/clinical-events-context";
import { usePetsController } from "@/features/pets/pets-context";

export default function EventsPage() {
  return <ClinicalEventsView controller={useClinicalEventsController()} petsController={usePetsController()} />;
}
