import { Bell } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

export function RemindersView() {
  return (
    <>
      <Header title="Mis recordatorios" subtitle="Próximos cuidados y controles" />
      <EmptyState icon={<Bell />} title="Sin recordatorios" description="Los recordatorios aparecerán aquí cuando se integre su API." />
    </>
  );
}
