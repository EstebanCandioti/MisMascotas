"use client";

import { useParams } from "next/navigation";

import { PetDetailView } from "@/components/screens/pet-detail-view";
import { useClinicalEventsController } from "@/features/clinical-events/clinical-events-context";
import { usePetsController } from "@/features/pets/pets-context";

export default function PetPage() {
  const { id } = useParams<{ id: string }>();
  return <PetDetailView id={id} controller={usePetsController()} clinicalEventsController={useClinicalEventsController()} />;
}
