import { Album } from "lucide-react";

import { EmptyState, Header } from "@/components/layout/app-navigation";

export function AlbumsView() {
  return (
    <>
      <Header title="Álbumes" subtitle="Tus recuerdos favoritos" />
      <EmptyState icon={<Album />} title="Sin álbumes" description="Los álbumes y sus fotos aparecerán cuando el módulo se conecte al backend." />
    </>
  );
}
