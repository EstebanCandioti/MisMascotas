"use client";

import { PetsView } from "@/components/screens/pets-view";
import { usePetsController } from "@/features/pets/pets-context";

export default function PetsPage() {
  return <PetsView controller={usePetsController()} />;
}
