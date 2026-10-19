"use client";

import { createContext, type PropsWithChildren, useContext } from "react";

import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";

const ClinicalEventsContext = createContext<ClinicalEventsController | null>(null);

export function ClinicalEventsProvider({ children, value }: PropsWithChildren<{ value: ClinicalEventsController }>) {
  return <ClinicalEventsContext.Provider value={value}>{children}</ClinicalEventsContext.Provider>;
}

export function useClinicalEventsController(): ClinicalEventsController {
  const context = useContext(ClinicalEventsContext);
  if (!context) throw new Error("useClinicalEventsController debe utilizarse dentro de ClinicalEventsProvider");
  return context;
}
