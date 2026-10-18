"use client";

import { createContext, type PropsWithChildren, useContext } from "react";

import type { PetsController } from "@/features/pets/use-pets";

const PetsContext = createContext<PetsController | null>(null);

export function PetsProvider({ children, value }: PropsWithChildren<{ value: PetsController }>) {
  return <PetsContext.Provider value={value}>{children}</PetsContext.Provider>;
}

export function usePetsController(): PetsController {
  const context = useContext(PetsContext);
  if (!context) throw new Error("usePetsController debe utilizarse dentro de PetsProvider");
  return context;
}
