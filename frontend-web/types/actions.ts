export type ActionKind =
  | "pet"
  | "event"
  | "reminder"
  | "edit"
  | "history"
  | "badges"
  | "album"
  | "invite"
  | "manage"
  | "notifications"
  | "security"
  | "legal"
  | "support";

export const actionTitles: Record<ActionKind, string> = {
  pet: "Agregar mascota",
  event: "Registrar evento clínico",
  reminder: "Crear recordatorio",
  edit: "Editar mascota",
  history: "Historial y evolución",
  badges: "Insignias",
  album: "Crear álbum",
  invite: "Invitar persona",
  manage: "Gestionar acceso",
  notifications: "Notificaciones",
  security: "Privacidad y seguridad",
  legal: "Términos y privacidad",
  support: "Ayuda y soporte",
};
