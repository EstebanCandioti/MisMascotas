export type Album = {
  idAlbum: string;
  nombre: string;
  descripcion: string | null;
  mascotaId: string;
  creadoPorId: string | null;
  creadoEn: string;
  cantidadFotos: number;
};

export type AlbumRequest = {
  nombre: string;
  descripcion: string | null;
  mascotaId: string;
};

export type AlbumsStatus = "idle" | "loading" | "success" | "error";
