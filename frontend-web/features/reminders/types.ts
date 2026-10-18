export const reminderTypes = [
  "PIPETA_ANTIPARASITARIO",
  "VISITA_VETERINARIO",
  "MEDICACION",
  "VACUNA",
  "OTRO",
] as const;

export type ReminderType = (typeof reminderTypes)[number];

export type ReminderResponse = {
  idRecordatorio: string;
  mascotaId: string;
  titulo: string;
  tipo: ReminderType;
  fechaHoraInicio: string;
  modalidad: string;
  intervaloValor: number | null;
  intervaloUnidad: string | null;
  diaSemana: string | null;
  fechaFin: string | null;
  creadoPorId: string;
  creadoEn: string;
  confirmadoPorId: string | null;
  confirmadoEn: string | null;
  estado: string | null;
};

export type Reminder = ReminderResponse & {
  mascotaNombre: string;
};

export type ReminderRequest = {
  mascotaId: string;
  titulo: string;
  tipo: ReminderType;
  fechaHoraInicio: string;
  modalidad: "unica";
  intervaloValor: null;
  intervaloUnidad: null;
  diasSemana: null;
  fechaFin: null;
};

export type RemindersStatus = "idle" | "loading" | "success" | "error";
