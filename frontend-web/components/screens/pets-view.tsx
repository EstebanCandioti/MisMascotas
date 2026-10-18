import { PawPrint } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

export function PetsView() {
  return (
    <>
      <Header title="Mascotas" subtitle="Tu familia, siempre cerca" />
      <EmptyState icon={<PawPrint />} title="Sin mascotas cargadas" description="Este listado queda listo para recibir los datos del endpoint de mascotas." />
    </>
  );
}
