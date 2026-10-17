import type {
  MascotaResponse,
  RecordatorioRequest,
  RecordatorioResponse,
} from "../services/api";

export type TipoRecordatorio =
  | "PIPETA_ANTIPARASITARIO"
  | "VISITA_VETERINARIO"
  | "MEDICACION"
  | "VACUNA"
  | "OTRO";

export type MascotaResumen = {
  id: string;
  nombre: string;
};

export type RecordatorioVista = {
  id: string;
  mascotaId: string;
  mascotaNombre: string;
  titulo: string;
  tipo: string;
  tipoLegible: string;
  fechaHoraInicio: string;
  fechaLegible: string;
  fechaCorta: string;
  detalle: string;
  completado: boolean;
  estado: string;
  raw: RecordatorioResponse;
};

export const tiposRecordatorio: { value: TipoRecordatorio; label: string }[] = [
  { value: "PIPETA_ANTIPARASITARIO", label: "Pipeta / Antiparasitario" },
  { value: "VISITA_VETERINARIO", label: "Visita al veterinario" },
  { value: "MEDICACION", label: "Medicación" },
  { value: "VACUNA", label: "Vacuna" },
  { value: "OTRO", label: "Otro" },
];

export function obtenerNombreTipoRecordatorio(tipo: string | null | undefined): string {
  return tiposRecordatorio.find((item) => item.value === tipo)?.label ?? "Otro";
}

export function mapearRecordatorioVista(
  recordatorio: RecordatorioResponse,
  mascota?: MascotaResponse | MascotaResumen,
): RecordatorioVista {
  const fecha = new Date(recordatorio.fechaHoraInicio);
  const completado = Boolean(
    recordatorio.confirmadoPorId
      || recordatorio.confirmadoEn
      || recordatorio.estado === "COMPLETADO",
  );

  return {
    id: recordatorio.idRecordatorio,
    mascotaId: recordatorio.mascotaId,
    mascotaNombre: mascota?.nombre ?? "Mascota",
    titulo: recordatorio.titulo,
    tipo: recordatorio.tipo,
    tipoLegible: obtenerNombreTipoRecordatorio(recordatorio.tipo),
    fechaHoraInicio: recordatorio.fechaHoraInicio,
    fechaLegible: formatearFechaHora(recordatorio.fechaHoraInicio),
    fechaCorta: formatearFechaCorta(fecha),
    detalle: construirDetalle(recordatorio),
    completado,
    estado: recordatorio.estado,
    raw: recordatorio,
  };
}

export function construirRequestRecordatorioUnico(data: {
  titulo: string;
  tipo: TipoRecordatorio;
  fechaHoraInicio: Date;
}): RecordatorioRequest {
  if (Number.isNaN(data.fechaHoraInicio.getTime())) {
    throw new Error("La fecha y hora seleccionadas no son válidas.");
  }

  return {
    titulo: data.titulo.trim(),
    tipo: data.tipo,
    fechaHoraInicio: data.fechaHoraInicio.toISOString(),
    modalidad: "unica",
  };
}

export function dividirFechaHora(iso: string): { fecha: string; hora: string } {
  const fecha = new Date(iso);

  if (Number.isNaN(fecha.getTime())) {
    return { fecha: "", hora: "" };
  }

  const anio = fecha.getFullYear();
  const mes = String(fecha.getMonth() + 1).padStart(2, "0");
  const dia = String(fecha.getDate()).padStart(2, "0");
  const hora = String(fecha.getHours()).padStart(2, "0");
  const minutos = String(fecha.getMinutes()).padStart(2, "0");

  return { fecha: `${anio}-${mes}-${dia}`, hora: `${hora}:${minutos}` };
}

export function esRecordatorioDeHoy(recordatorio: RecordatorioResponse): boolean {
  const fecha = new Date(recordatorio.fechaHoraInicio);
  const hoy = new Date();

  return !Number.isNaN(fecha.getTime())
    && fecha.getFullYear() === hoy.getFullYear()
    && fecha.getMonth() === hoy.getMonth()
    && fecha.getDate() === hoy.getDate();
}

function formatearFechaHora(iso: string): string {
  const fecha = new Date(iso);

  if (Number.isNaN(fecha.getTime())) {
    return "Fecha no disponible";
  }

  return new Intl.DateTimeFormat("es-AR", {
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  }).format(fecha);
}

function formatearFechaCorta(fecha: Date): string {
  if (Number.isNaN(fecha.getTime())) {
    return "PRÓX.";
  }

  return new Intl.DateTimeFormat("es-AR", {
    day: "2-digit",
    month: "short",
  }).format(fecha).replace(".", "").toUpperCase();
}

function construirDetalle(recordatorio: RecordatorioResponse): string {
  const partes = [obtenerNombreTipoRecordatorio(recordatorio.tipo)];

  if (recordatorio.modalidad && recordatorio.modalidad !== "unica") {
    partes.push("Recurrente");
  }

  if (recordatorio.intervaloValor && recordatorio.intervaloUnidad) {
    partes.push(`Cada ${recordatorio.intervaloValor} ${recordatorio.intervaloUnidad}`);
  }

  return partes.join(" · ");
}
