import { UserPlus } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

export function SharedView() {
  return (
    <>
      <Header title="Cuidado compartido" subtitle="Personas que cuidan con vos" />
      <EmptyState icon={<UserPlus />} title="Sin accesos compartidos" description="Las invitaciones y permisos se mostrarán cuando se integre este módulo." />
    </>
  );
}
