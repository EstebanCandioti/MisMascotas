export type Pet = {
  idMascota: string;
  propietarioId: string;
  nombre: string;
  especie: string;
  raza: string | null;
  fechaNacimiento: string | null;
  fechaAproximada: boolean;
  edadValor: number | null;
  edadUnidad: string | null;
  fotoPerfil: string | null;
  pesoActual: number | null;
  notas: string | null;
  creadoEn: string;
};

export type PetRequest = {
  nombre: string;
  especie: string;
  raza: string | null;
  fechaNacimiento: string | null;
  fechaAproximada: boolean;
  edadValor: number | null;
  edadUnidad: "dias" | "semanas" | "meses" | "anios" | null;
  fotoPerfil: string | null;
  pesoActual: number | null;
  notas: string | null;
};

export type PetsStatus = "idle" | "loading" | "success" | "error";
