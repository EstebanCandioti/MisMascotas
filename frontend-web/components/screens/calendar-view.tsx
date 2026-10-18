import { CalendarDays } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

export function CalendarView() {
  return (
    <>
      <Header title="Calendario" subtitle="Cuidados y eventos clínicos" />
      <EmptyState icon={<CalendarDays />} title="Sin eventos" description="El calendario se completará con recordatorios y eventos obtenidos desde la API." />
    </>
  );
}
