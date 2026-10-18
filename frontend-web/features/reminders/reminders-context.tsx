"use client";

import { createContext, type PropsWithChildren, useContext } from "react";

import type { RemindersController } from "@/features/reminders/use-reminders";

const RemindersContext = createContext<RemindersController | null>(null);

export function RemindersProvider({ children, value }: PropsWithChildren<{ value: RemindersController }>) {
  return <RemindersContext.Provider value={value}>{children}</RemindersContext.Provider>;
}

export function useRemindersController(): RemindersController {
  const context = useContext(RemindersContext);
  if (!context) throw new Error("useRemindersController debe utilizarse dentro de RemindersProvider");
  return context;
}
