export type Photo = {
  idFoto: string;
  albumId: string;
  urlArchivo: string;
  formato: string | null;
  subidaPorId: string | null;
  creadoEn: string;
};

export type PhotoRequest = {
  urlArchivo: string;
  formato: string | null;
};
