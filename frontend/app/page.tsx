"use client";

import { useMemo, useState } from "react";
import {
  Activity,
  Album,
  Bell,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Crown,
  Download,
  FileText,
  HeartPulse,
  Home,
  LogOut,
  Mail,
  Menu,
  PawPrint,
  Pencil,
  Plus,
  Search,
  Settings,
  ShieldCheck,
  Stethoscope,
  Syringe,
  UserPlus,
  Users,
  Weight,
} from "lucide-react";

type View =
  | "home"
  | "pets"
  | "profile"
  | "reminders"
  | "albums"
  | "calendar"
  | "shared"
  | "settings";
type PetId = "fido" | "luna" | "rex";
const pets = {
  fido: {
    name: "Fido",
    species: "Perro",
    breed: "Golden Retriever",
    age: "3 años",
    weight: "28.5 kg",
    emoji: "🐶",
    color: "#f2d5a0",
    days: 3,
  },
  luna: {
    name: "Luna",
    species: "Gato",
    breed: "Siamés",
    age: "2 años",
    weight: "4.1 kg",
    emoji: "🐱",
    color: "#d9cdfc",
    days: 7,
  },
  rex: {
    name: "Rex",
    species: "Perro",
    breed: "Bulldog",
    age: "5 años",
    weight: "22 kg",
    emoji: "🐶",
    color: "#c9e5f3",
    days: 3,
  },
} as const;
const nav = [
  ["home", "Inicio", Home],
  ["pets", "Mascotas", PawPrint],
  ["albums", "Álbumes", Album],
  ["calendar", "Calendario", CalendarDays],
  ["shared", "Cuidadores", UserPlus],
  ["settings", "Configuración", Settings],
] as const;

function PetAvatar({
  id,
  size = "md",
}: {
  id: PetId;
  size?: "sm" | "md" | "lg";
}) {
  const p = pets[id];
  return (
    <div
      className={`pet-avatar ${size}`}
      style={{ background: p.color }}
      aria-label={`${p.name}, ${p.species}`}
    >
      {p.emoji}
    </div>
  );
}
function BottomNav({ view, go }: { view: View; go: (v: View) => void }) {
  return (
    <nav className="bottom-nav" aria-label="Navegación principal">
      {nav.map(([id, label, Icon]) => (
        <button
          key={id}
          className={view === id ? "active" : ""}
          onClick={() => go(id)}
          aria-label={label}
        >
          <Icon />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}
function Header({
  title,
  subtitle,
  onBack,
  action,
}: {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  action?: React.ReactNode;
}) {
  return (
    <header className="screen-header">
      {onBack ? (
        <button className="icon-btn" onClick={onBack} aria-label="Volver">
          <ChevronLeft />
        </button>
      ) : (
        <span className="brand-mark">
          <PawPrint />
        </span>
      )}
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action && <div className="header-action">{action}</div>}
    </header>
  );
}
function Card({
  children,
  className = "",
}: React.PropsWithChildren<{ className?: string }>) {
  return <section className={`card ${className}`}>{children}</section>;
}

type ActionKind =
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
const actionTitles: Record<ActionKind, string> = {
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
function ActionDialog({
  kind,
  pet,
  close,
  done,
}: {
  kind: ActionKind;
  pet: PetId;
  close: () => void;
  done: (message: string) => void;
}) {
  const p = pets[pet];
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    done(`${actionTitles[kind]} guardado correctamente`);
  };
  return (
    <div className="modal-backdrop" onMouseDown={close}>
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header>
          <div>
            <span className="eyebrow">MIS MASCOTAS</span>
            <h2 id="modal-title">{actionTitles[kind]}</h2>
          </div>
          <button className="icon-btn" onClick={close} aria-label="Cerrar">
            ×
          </button>
        </header>
        {kind === "history" ? (
          <div className="timeline">
            <div>
              <span />
              <p>
                <b>Consulta veterinaria</b>
                <small>20 ago 2026 · Control general sin hallazgos.</small>
              </p>
            </div>
            <div>
              <span />
              <p>
                <b>Peso actualizado</b>
                <small>15 ago 2026 · {p.weight}</small>
              </p>
            </div>
            <div>
              <span />
              <p>
                <b>Vacuna quíntuple</b>
                <small>4 mar 2026 · Aplicación completa.</small>
              </p>
            </div>
            <button
              className="secondary-action"
              onClick={() => done("Historial exportado")}
            >
              Exportar historial
            </button>
          </div>
        ) : kind === "badges" ? (
          <div className="badge-gallery">
            <span>
              💀<b>Destructor</b>
              <small>12 juguetes</small>
            </span>
            <span>
              🍖<b>Glotón</b>
              <small>30 comidas</small>
            </span>
            <span>
              🏆<b>Puntual</b>
              <small>10 cuidados</small>
            </span>
          </div>
        ) : kind === "legal" ? (
          <div className="legal-copy">
            <p>
              Mis Mascotas protege la información de tus animales y permite
              exportarla libremente. El acceso compartido siempre puede ser
              revocado por el responsable.
            </p>
            <p>Versión académica demostrativa · PP2.</p>
          </div>
        ) : (
          <form className="modal-form" onSubmit={submit}>
            {(kind === "pet" || kind === "edit") && (
              <>
                <label>
                  Nombre
                  <input
                    required
                    defaultValue={kind === "edit" ? p.name : ""}
                    placeholder="Nombre de la mascota"
                  />
                </label>
                <div className="form-row">
                  <label>
                    Especie
                    <select
                      defaultValue={kind === "edit" ? p.species : "Perro"}
                    >
                      <option>Perro</option>
                      <option>Gato</option>
                      <option>Ave</option>
                      <option>Conejo</option>
                      <option>Otra</option>
                    </select>
                  </label>
                  <label>
                    Raza
                    <input
                      defaultValue={kind === "edit" ? p.breed : ""}
                      placeholder="Raza"
                    />
                  </label>
                </div>
                <label>
                  Fecha de nacimiento
                  <input type="date" defaultValue="2023-06-12" />
                </label>
              </>
            )}
            {kind === "event" && (
              <>
                <label>
                  Tipo de evento
                  <select>
                    <option>Consulta veterinaria</option>
                    <option>Vacunación</option>
                    <option>Medicación</option>
                    <option>Estudio</option>
                    <option>Otro</option>
                  </select>
                </label>
                <label>
                  Fecha
                  <input type="date" required defaultValue="2026-08-27" />
                </label>
                <label>
                  Descripción
                  <textarea
                    required
                    placeholder="Diagnóstico, indicaciones y observaciones"
                  />
                </label>
                <label>
                  Peso registrado (kg)
                  <input
                    type="number"
                    step="0.1"
                    defaultValue={p.weight.replace(" kg", "")}
                  />
                </label>
              </>
            )}
            {kind === "reminder" && (
              <>
                <label>
                  Título
                  <input required placeholder="Ej. Vacuna antirrábica" />
                </label>
                <div className="form-row">
                  <label>
                    Fecha
                    <input type="date" required defaultValue="2026-08-30" />
                  </label>
                  <label>
                    Hora
                    <input type="time" defaultValue="10:30" />
                  </label>
                </div>
                <label>
                  Repetición
                  <select>
                    <option>Una vez</option>
                    <option>Mensual</option>
                    <option>Cada 3 meses</option>
                    <option>Anual</option>
                  </select>
                </label>
              </>
            )}
            {kind === "album" && (
              <>
                <label>
                  Nombre del álbum
                  <input required placeholder="Ej. Vacaciones 2026" />
                </label>
                <label>
                  Descripción
                  <textarea placeholder="Contá de qué se trata" />
                </label>
                <label>
                  Mascota
                  <select>
                    <option>Fido</option>
                    <option>Luna</option>
                    <option>Rex</option>
                  </select>
                </label>
              </>
            )}
            {kind === "invite" && (
              <>
                <label>
                  Correo electrónico
                  <input
                    required
                    type="email"
                    placeholder="persona@correo.com"
                  />
                </label>
                <label>
                  Perfil
                  <select>
                    <option>Familia</option>
                    <option>Cuidador</option>
                  </select>
                </label>
                <label>
                  Mascotas
                  <select>
                    <option>Todas</option>
                    <option>Fido</option>
                    <option>Luna</option>
                    <option>Rex</option>
                  </select>
                </label>
              </>
            )}
            {kind === "manage" && (
              <>
                <label>
                  Perfil de acceso
                  <select>
                    <option>Familia</option>
                    <option>Cuidador</option>
                  </select>
                </label>
                <label>
                  Mascotas autorizadas
                  <select>
                    <option>Fido y Luna</option>
                    <option>Fido</option>
                    <option>Luna</option>
                    <option>Rex</option>
                  </select>
                </label>
                <label className="switch-row">
                  <span>Permitir registrar eventos</span>
                  <input type="checkbox" defaultChecked />
                </label>
              </>
            )}
            {kind === "notifications" && (
              <>
                <label className="switch-row">
                  <span>Recordatorios de salud</span>
                  <input type="checkbox" defaultChecked />
                </label>
                <label className="switch-row">
                  <span>Actividad de cuidadores</span>
                  <input type="checkbox" defaultChecked />
                </label>
                <label className="switch-row">
                  <span>Novedades del producto</span>
                  <input type="checkbox" />
                </label>
              </>
            )}
            {kind === "security" && (
              <>
                <label>
                  Contraseña actual
                  <input type="password" required />
                </label>
                <label>
                  Nueva contraseña
                  <input type="password" required minLength={8} />
                </label>
                <label>
                  Repetir contraseña
                  <input type="password" required minLength={8} />
                </label>
              </>
            )}
            {kind === "support" && (
              <>
                <label>
                  Asunto
                  <select>
                    <option>Problema técnico</option>
                    <option>Consulta sobre mi cuenta</option>
                    <option>Sugerencia</option>
                  </select>
                </label>
                <label>
                  Mensaje
                  <textarea required placeholder="¿Cómo podemos ayudarte?" />
                </label>
              </>
            )}
            {!(["history", "badges", "legal"] as string[]).includes(kind) && (
              <div className="modal-actions">
                <button type="button" onClick={close}>
                  Cancelar
                </button>
                <button className="primary" type="submit">
                  Guardar
                </button>
              </div>
            )}
          </form>
        )}
      </section>
    </div>
  );
}

function HomeView({
  go,
  selectPet,
}: {
  go: (v: View) => void;
  selectPet: (id: PetId) => void;
}) {
  const [completed, setCompleted] = useState<string[]>([]);
  const toggle = (id: string) =>
    setCompleted((items) =>
      items.includes(id) ? items.filter((item) => item !== id) : [...items, id],
    );
  const TaskItem = ({
    id,
    title,
    detail,
    time,
  }: {
    id: string;
    title: string;
    detail: string;
    time: string;
  }) => (
    <div className={`task ${completed.includes(id) ? "completed" : ""}`}>
      <button
        onClick={() => toggle(id)}
        aria-label={
          completed.includes(id) ? "Marcar pendiente" : "Marcar completado"
        }
      >
        {completed.includes(id) ? "✓" : <span />}
      </button>
      <div>
        <strong>{title}</strong>
        <small>{detail}</small>
      </div>
      <span className="time">{time}</span>
    </div>
  );
  return (
    <>
      <div className="welcome">
        <div>
          <span className="eyebrow">Jueves, 27 de agosto</span>
          <h1>
            Hola, Usuario <span>👋</span>
          </h1>
          <p>Todo el cuidado de tus mascotas, en un solo lugar.</p>
        </div>
        <button
          className="avatar-user"
          onClick={() => go("settings")}
          aria-label="Abrir configuración"
        >
          US
        </button>
      </div>
      <div className="quick-summary">
        <div>
          <strong>3</strong>
          <span>Mascotas</span>
        </div>
        <div>
          <strong>2</strong>
          <span>Próximos</span>
        </div>
        <div>
          <strong>1</strong>
          <span>Compartido</span>
        </div>
      </div>
      <section className="section">
        <div className="section-title">
          <h2>Hoy</h2>
          <span className="date-pill">{3 - completed.length} pendientes</span>
        </div>
        <Card className="today-card">
          <div className="today-pet">
            <PetAvatar id="fido" size="sm" />
            <div>
              <strong>Fido</strong>
              <span>Salud preventiva</span>
            </div>
          </div>
          <TaskItem
            id="selamectina"
            title="Selamectina 18 mg"
            detail="Aplicar pipeta antiparasitaria"
            time="20:00"
          />
          <TaskItem
            id="comprimido"
            title="Comprimido antigarrapatas"
            detail="Con la comida"
            time="21:00"
          />
        </Card>
        <Card className="today-card">
          <div className="today-pet">
            <PetAvatar id="luna" size="sm" />
            <div>
              <strong>Luna</strong>
              <span>Cuidado diario</span>
            </div>
          </div>
          <TaskItem
            id="pipeta"
            title="Pipeta antiparasitaria"
            detail="Recordatorio mensual"
            time="Hoy"
          />
        </Card>
      </section>
      <section className="section">
        <div className="section-title">
          <h2>Actividad reciente</h2>
          <button onClick={() => go("calendar")}>
            Ver todo <ChevronRight />
          </button>
        </div>
        <div className="activity-list">
          <div>
            <span className="activity-icon purple">
              <Syringe />
            </span>
            <p>
              <b>Laura</b> marcó que le dio Amoxicilina a Fido
              <small>Hace 25 min</small>
            </p>
          </div>
          <div>
            <span className="activity-icon blue">
              <Stethoscope />
            </span>
            <p>
              Registraste una consulta de Luna<small>Ayer, 18:40</small>
            </p>
          </div>
        </div>
      </section>
      <section className="section">
        <div className="section-title">
          <h2>Próximos recordatorios</h2>
          <button onClick={() => go("reminders")}>
            Ver todos <ChevronRight />
          </button>
        </div>
        <div className="reminder-strip">
          <div>
            <Syringe />
            <p>
              <b>Vacuna antirrábica</b>
              <span>Fido · En 3 días</span>
            </p>
            <strong>30 AGO</strong>
          </div>
          <div>
            <HeartPulse />
            <p>
              <b>Control anual</b>
              <span>Rex · En 7 días</span>
            </p>
            <strong>03 SEP</strong>
          </div>
        </div>
      </section>
      <section className="section">
        <div className="section-title">
          <h2>Mis mascotas</h2>
          <button onClick={() => go("pets")}>
            Administrar <ChevronRight />
          </button>
        </div>
        <div className="pet-row">
          {(Object.keys(pets) as PetId[]).map((id) => (
            <button key={id} onClick={() => selectPet(id)}>
              <PetAvatar id={id} />
              <strong>{pets[id].name}</strong>
              <span>{pets[id].breed}</span>
            </button>
          ))}
          <button className="add-pet" onClick={() => go("pets")}>
            <Plus />
            <strong>Agregar</strong>
            <span>Nueva mascota</span>
          </button>
        </div>
      </section>
    </>
  );
}

function PetsView({
  shared,
  selectPet,
  setShared,
  open,
}: {
  shared: boolean;
  selectPet: (id: PetId) => void;
  setShared: (v: boolean) => void;
  open: (kind: ActionKind) => void;
}) {
  const ids: PetId[] = shared ? ["rex"] : ["fido", "luna", "rex"];
  return (
    <>
      <Header
        title="Mascotas"
        subtitle="Tu familia, siempre cerca"
        action={
          <button
            className="icon-btn"
            onClick={() => open("pet")}
            aria-label="Agregar mascota"
          >
            <Search />
          </button>
        }
      />
      <div className="segmented">
        <button
          className={!shared ? "active" : ""}
          onClick={() => setShared(false)}
        >
          Mis mascotas
        </button>
        <button
          className={shared ? "active" : ""}
          onClick={() => setShared(true)}
        >
          Cuidado compartido
        </button>
      </div>
      <div className="pet-list">
        {ids.map((id) => (
          <button
            className="pet-list-card"
            key={id}
            onClick={() => selectPet(id)}
          >
            <PetAvatar id={id} size="lg" />
            <div>
              <h3>{pets[id].name}</h3>
              <p>
                {pets[id].breed} · {pets[id].species} · {pets[id].age}
              </p>
              {shared && (
                <span className="share-chip">
                  <Users /> Perfil: Familia
                </span>
              )}
            </div>
            <ChevronRight />
          </button>
        ))}
      </div>
      <button
        className="fab"
        onClick={() => open("pet")}
        aria-label="Agregar mascota"
      >
        <Plus />
      </button>
    </>
  );
}

function ProfileView({
  id,
  back,
  go,
  open,
}: {
  id: PetId;
  back: () => void;
  go: (v: View) => void;
  open: (kind: ActionKind) => void;
}) {
  const p = pets[id];
  return (
    <>
      <Header
        title={p.name}
        onBack={back}
        action={
          <button
            className="icon-btn"
            onClick={() => open("edit")}
            aria-label="Editar mascota"
          >
            <Menu />
          </button>
        }
      />
      <div className="profile-hero">
        <PetAvatar id={id} size="lg" />
        <h2>{p.name}</h2>
        <p>
          {p.breed} · {p.species} · {p.age}
        </p>
      </div>
      <div className="action-grid">
        <button onClick={() => open("event")}>
          <Stethoscope />
          <span>Registrar evento clínico</span>
        </button>
        <button onClick={() => open("reminder")}>
          <Bell />
          <span>Crear recordatorio</span>
        </button>
        <button onClick={() => open("history")}>
          <Activity />
          <span>Historial y evolución</span>
        </button>
        <button onClick={() => open("edit")}>
          <Pencil />
          <span>Editar mascota</span>
        </button>
      </div>
      <Card className="metric-card">
        <div>
          <span>Peso actual</span>
          <strong>{p.weight}</strong>
          <small>Actualizado hace 5 días</small>
        </div>
        <Weight />
      </Card>
      <Card className="next-card">
        <div>
          <span>Próximo recordatorio</span>
          <strong>Vacuna antirrábica</strong>
          <small>En {p.days} días</small>
        </div>
        <Syringe />
      </Card>
      <Card>
        <div className="card-heading">
          <span>
            <Crown /> Insignias
          </span>
          <button onClick={() => open("badges")}>Ver todas</button>
        </div>
        <div className="badges">
          <span>Destructor 💀</span>
          <span>Glotón 🍖</span>
        </div>
      </Card>
      <Card>
        <div className="card-heading">
          <span>
            <Album /> Álbumes
          </span>
          <button onClick={() => go("albums")}>Ver todos</button>
        </div>
        <div className="album-preview">
          <div className="photo">{p.emoji}</div>
          <div>
            <b>{id === "luna" ? "Luna en su casa" : "Verano 2026"}</b>
            <small>8 fotos</small>
          </div>
        </div>
      </Card>
    </>
  );
}

function RemindersView({ open }: { open: (kind: ActionKind) => void }) {
  return (
    <>
      <Header
        title="Mis recordatorios"
        subtitle="Próximos cuidados y controles"
      />
      <div className="filter-pills">
        <button className="active">Todos</button>
        <button>Hoy</button>
        <button>Esta semana</button>
      </div>
      <div className="group-title">
        <PetAvatar id="fido" size="sm" />
        <div>
          <b>Fido</b>
          <span>2 recordatorios</span>
        </div>
      </div>
      <div className="reminders">
        {[
          ["Pipeta antiparasitaria", "Cada 3 meses", "15 JUN", "Activo"],
          ["Vacuna antirrábica", "Todos los años", "30 AGO", "Próximo"],
        ].map((x) => (
          <Card key={x[0]}>
            <Syringe />
            <div>
              <h3>{x[0]}</h3>
              <p>{x[1]}</p>
              <small>Próximo: {x[2]}</small>
            </div>
            <span className="status">{x[3]}</span>
          </Card>
        ))}
      </div>
      <div className="group-title">
        <PetAvatar id="luna" size="sm" />
        <div>
          <b>Luna</b>
          <span>1 recordatorio</span>
        </div>
      </div>
      <div className="reminders">
        <Card>
          <HeartPulse />
          <div>
            <h3>Control anual</h3>
            <p>Una vez al año</p>
            <small>Próximo: 22 JUN</small>
          </div>
          <span className="status">Activo</span>
        </Card>
      </div>
      <button
        className="fab"
        onClick={() => open("reminder")}
        aria-label="Crear recordatorio"
      >
        <Plus />
      </button>
    </>
  );
}
function AlbumsView({ open }: { open: (kind: ActionKind) => void }) {
  const [selected, setSelected] = useState<number | null>(null);
  const albums = [
    {
      name: "Verano 2026",
      description: "Los mejores momentos de verano",
      pet: "Fido",
      emoji: "🐶",
      photos: [
        "https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1507146426996-ef05306b995a?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1558788353-f76d92427f16?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1477884213360-7e9d7dcc1e48?auto=format&fit=crop&w=700&q=80",
      ],
    },
    {
      name: "Cachorrito",
      description: "De cuando era bebé",
      pet: "Fido",
      emoji: "🐾",
      photos: [
        "https://images.unsplash.com/photo-1518717758536-85ae29035b6d?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=700&q=80",
      ],
    },
    {
      name: "Luna en su casa",
      description: "Siestas y aventuras",
      pet: "Luna",
      emoji: "🐱",
      photos: [
        "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=700&q=80",
        "https://images.unsplash.com/photo-1495360010541-f48722b34f7d?auto=format&fit=crop&w=700&q=80",
      ],
    },
  ];
  if (selected !== null) {
    const album = albums[selected];
    return (
      <>
        <Header
          title={album.name}
          subtitle={`${album.pet} · ${album.photos.length} fotos`}
          onBack={() => setSelected(null)}
          action={
            <button className="icon-btn" aria-label="Agregar foto">
              <Plus />
            </button>
          }
        />
        <p className="album-detail-copy">{album.description}</p>
        <div className="photo-grid">
          {album.photos.map((src, index) => (
            <button
              key={src}
              className={index === 0 ? "featured" : ""}
              aria-label={`Ver foto ${index + 1}`}
            >
              <img src={src} alt={`${album.name}, foto ${index + 1}`} />
              <span>{index + 1}</span>
            </button>
          ))}
        </div>
      </>
    );
  }
  return (
    <>
      <Header
        title="Álbumes"
        subtitle="Tus recuerdos favoritos"
        action={
          <button
            className="icon-btn"
            onClick={() => open("album")}
            aria-label="Crear álbum"
          >
            <Plus />
          </button>
        }
      />
      <div className="album-list">
        {albums.map((a, index) => (
          <button
            className="album-card-button"
            key={a.name}
            onClick={() => setSelected(index)}
          >
            <Card>
              <div className="album-art">
                <img src={a.photos[0]} alt="" />
              </div>
              <div>
                <h3>{a.name}</h3>
                <p>{a.description}</p>
                <span>{a.pet}</span>
              </div>
              <small>{a.photos.length} fotos</small>
              <ChevronRight />
            </Card>
          </button>
        ))}
      </div>
    </>
  );
}
function CalendarView() {
  const [mode, setMode] = useState<"Mes" | "Semana" | "Lista">("Mes");
  const [filter, setFilter] = useState<"Todas" | "Fido" | "Luna" | "Rex">(
    "Todas",
  );
  const [selectedDay, setSelectedDay] = useState(30);
  const days = Array.from({ length: 35 }, (_, i) =>
    i < 3 ? "" : String(i - 2),
  );
  const events = [
    {
      day: 28,
      date: "28 AGO",
      time: "09:00",
      pet: "Luna",
      title: "Pipeta antiparasitaria",
      type: "Medicación",
    },
    {
      day: 30,
      date: "30 AGO",
      time: "10:30",
      pet: "Fido",
      title: "Vacuna antirrábica",
      type: "Vacunación",
    },
    {
      day: 31,
      date: "31 AGO",
      time: "18:00",
      pet: "Rex",
      title: "Control de peso",
      type: "Seguimiento",
    },
    {
      day: 3,
      date: "03 SEP",
      time: "11:00",
      pet: "Rex",
      title: "Control anual",
      type: "Consulta",
    },
    {
      day: 5,
      date: "05 SEP",
      time: "20:00",
      pet: "Fido",
      title: "Comprimido antigarrapatas",
      type: "Medicación",
    },
  ];
  const colors: Record<string, string> = {
    Fido: "fido-event",
    Luna: "luna-event",
    Rex: "rex-event",
  };
  const visible = events.filter((e) => filter === "Todas" || e.pet === filter);
  const dayEvents = visible.filter(
    (e) => e.day === selectedDay && e.date.includes("AGO"),
  );
  const weekDays = [24, 25, 26, 27, 28, 29, 30];
  return (
    <>
      <Header title="Calendario" subtitle="Agosto 2026" />
      <div className="segmented calendar-tabs">
        {(["Mes", "Semana", "Lista"] as const).map((item) => (
          <button
            key={item}
            className={mode === item ? "active" : ""}
            onClick={() => setMode(item)}
          >
            {item}
          </button>
        ))}
      </div>
      <div className="filter-pills">
        {(["Todas", "Fido", "Luna", "Rex"] as const).map((item) => (
          <button
            key={item}
            className={`${filter === item ? "active" : ""} ${item.toLowerCase()}-filter`}
            onClick={() => setFilter(item)}
          >
            {item}
          </button>
        ))}
      </div>
      {mode === "Mes" && (
        <Card className="calendar-card">
          <div className="calendar-month">
            <button>
              <ChevronLeft />
            </button>
            <strong>Agosto 2026</strong>
            <button>
              <ChevronRight />
            </button>
          </div>
          <div className="weekdays">
            {"DLMMJVS".split("").map((d, i) => (
              <span key={i}>{d}</span>
            ))}
          </div>
          <div className="days">
            {days.map((d, i) => {
              const num = Number(d);
              const dots = visible.filter(
                (e) => e.day === num && e.date.includes("AGO"),
              );
              return (
                <button
                  key={i}
                  className={
                    num === selectedDay
                      ? "selected-day"
                      : d === "27"
                        ? "today"
                        : ""
                  }
                  onClick={() => d && setSelectedDay(num)}
                >
                  <span>{d}</span>
                  {dots.length > 0 && (
                    <i>
                      {dots.map((e, index) => (
                        <b key={index} className={colors[e.pet]} />
                      ))}
                    </i>
                  )}
                </button>
              );
            })}
          </div>
        </Card>
      )}
      {mode === "Semana" && (
        <Card className="week-card">
          <div className="week-heading">
            <button>
              <ChevronLeft />
            </button>
            <strong>24–30 de agosto</strong>
            <button>
              <ChevronRight />
            </button>
          </div>
          <div className="week-grid">
            {weekDays.map((day) => (
              <button
                key={day}
                className={day === selectedDay ? "active" : ""}
                onClick={() => setSelectedDay(day)}
              >
                <small>
                  {
                    ["LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"][
                      weekDays.indexOf(day)
                    ]
                  }
                </small>
                <b>{day}</b>
                <span>
                  {visible
                    .filter((e) => e.day === day && e.date.includes("AGO"))
                    .map((e, i) => (
                      <i key={i} className={colors[e.pet]} />
                    ))}
                </span>
              </button>
            ))}
          </div>
        </Card>
      )}
      {mode === "Lista" ? (
        <section className="calendar-list">
          <div className="list-summary">
            <b>Próximas tareas</b>
            <span>{visible.length} pendientes · ordenadas por fecha</span>
          </div>
          {visible.map((e, index) => (
            <Card className="list-event" key={`${e.date}-${e.pet}`}>
              <div className="list-order">{index + 1}</div>
              <div className={`event-pet-dot ${colors[e.pet]}`} />
              <div>
                <strong>{e.title}</strong>
                <span>
                  {e.pet} · {e.type}
                </span>
              </div>
              <time>
                <b>{e.date}</b>
                <small>{e.time}</small>
              </time>
            </Card>
          ))}
        </section>
      ) : (
        <section className="section">
          <div className="section-title">
            <h2>Eventos del día {selectedDay}</h2>
          </div>
          {dayEvents.length ? (
            dayEvents.map((e) => (
              <Card className="event-card" key={e.title}>
                <span className={colors[e.pet]}>
                  <Syringe />
                </span>
                <div>
                  <b>{e.title}</b>
                  <small>
                    {e.pet} · {e.time} · {e.type}
                  </small>
                </div>
                <ChevronRight />
              </Card>
            ))
          ) : (
            <div className="empty-day">
              <CalendarDays />
              <b>Sin eventos</b>
              <span>No hay tareas médicas para este día.</span>
            </div>
          )}
        </section>
      )}
    </>
  );
}
function SharedView({
  open,
  notify,
}: {
  open: (kind: ActionKind) => void;
  notify: (message: string) => void;
}) {
  return (
    <>
      <Header
        title="Cuidado compartido"
        subtitle="Personas que cuidan con vos"
      />
      <section className="section">
        <div className="section-title">
          <h2>Personas que invité</h2>
          <span className="date-pill">2 activas</span>
        </div>
        {[
          ["LG", "Laura García", "Familia", "Fido y Luna"],
          ["CL", "Carlos López", "Cuidador", "Rex"],
        ].map((x) => (
          <Card className="person-card" key={x[1]}>
            <span className="initials">{x[0]}</span>
            <div>
              <h3>{x[1]}</h3>
              <p>
                Perfil: {x[2]} · {x[3]}
              </p>
              <small>Desde mar 2026</small>
            </div>
            <button onClick={() => open("manage")}>Gestionar</button>
          </Card>
        ))}
        <button className="primary wide" onClick={() => open("invite")}>
          <UserPlus /> Invitar persona
        </button>
      </section>
      <section className="section">
        <div className="section-title">
          <h2>Me invitaron a su cuidado</h2>
        </div>
        <Card className="person-card">
          <span className="initials coral">MG</span>
          <div>
            <h3>María González</h3>
            <p>Max (Labrador) · Perfil: Familia</p>
            <small>Acceso activo</small>
          </div>
          <button
            className="danger"
            onClick={() => notify("Saliste del cuidado compartido de Max")}
          >
            Salir
          </button>
        </Card>
      </section>
    </>
  );
}
function SettingsView({
  logout,
  open,
  exportData,
}: {
  logout: () => void;
  open: (kind: ActionKind) => void;
  exportData: () => void;
}) {
  return (
    <>
      <Header title="Configuración" subtitle="Cuenta y preferencias" />
      <section className="settings-profile">
        <div className="avatar-user large">US</div>
        <div>
          <h2>Usuario Demo</h2>
          <p>usuario@demo.com</p>
          <span>
            <Crown /> Plan Premium
          </span>
        </div>
      </section>
      <div className="settings-list">
        <Card>
          <button onClick={() => open("notifications")}>
            <Bell />
            <div>
              <b>Notificaciones</b>
              <span>Recordatorios y novedades</span>
            </div>
            <ChevronRight />
          </button>
          <button onClick={() => open("security")}>
            <ShieldCheck />
            <div>
              <b>Privacidad y seguridad</b>
              <span>Contraseña y verificación</span>
            </div>
            <ChevronRight />
          </button>
          <button onClick={exportData}>
            <Download />
            <div>
              <b>Exportar mis datos</b>
              <span>Descargá tu información</span>
            </div>
            <ChevronRight />
          </button>
        </Card>
        <Card>
          <button onClick={() => open("legal")}>
            <FileText />
            <div>
              <b>Términos y privacidad</b>
              <span>Información legal</span>
            </div>
            <ChevronRight />
          </button>
          <button onClick={() => open("support")}>
            <Mail />
            <div>
              <b>Ayuda y soporte</b>
              <span>Estamos para ayudarte</span>
            </div>
            <ChevronRight />
          </button>
        </Card>
        <button className="logout" onClick={logout}>
          <LogOut /> Cerrar sesión
        </button>
        <p className="version">Mis Mascotas · Versión 1.0.0</p>
      </div>
    </>
  );
}

function Auth({ onLogin }: { onLogin: () => void }) {
  const [register, setRegister] = useState(false);
  const [showPass, setShowPass] = useState(false);
  return (
    <main className="auth-page">
      <section className="auth-brand">
        <div className="logo-big">
          <PawPrint />
        </div>
        <span>Tu familia, siempre cuidada</span>
        <h1>Mis Mascotas</h1>
        <p>
          Organizá la salud, los cuidados y los recuerdos de quienes hacen tu
          hogar más feliz.
        </p>
        <div className="auth-pets">
          <span>🐶</span>
          <span>🐱</span>
          <span>🐰</span>
          <span>🐦</span>
        </div>
      </section>
      <section className="auth-card">
        <div className="mobile-logo">
          <PawPrint />
          <b>Mis Mascotas</b>
        </div>
        <span className="eyebrow">
          {register ? "CREÁ TU CUENTA" : "BIENVENIDO DE NUEVO"}
        </span>
        <h2>{register ? "Empecemos" : "Iniciar sesión"}</h2>
        <p>
          {register
            ? "Completá tus datos para cuidar mejor."
            : "Ingresá para ver cómo están tus mascotas."}
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            onLogin();
          }}
        >
          {register && (
            <div className="form-row">
              <label>
                Nombre
                <input required placeholder="Usuario" />
              </label>
              <label>
                Apellido
                <input required placeholder="Demo" />
              </label>
            </div>
          )}
          <label>
            Correo electrónico
            <div className="input-icon">
              <Mail />
              <input required type="email" defaultValue="usuario@demo.com" />
            </div>
          </label>
          <label>
            Contraseña
            <div className="input-icon">
              <ShieldCheck />
              <input
                required
                type={showPass ? "text" : "password"}
                defaultValue="Demo1234"
              />
              <button type="button" onClick={() => setShowPass(!showPass)}>
                {showPass ? "Ocultar" : "Ver"}
              </button>
            </div>
          </label>
          {register && (
            <label>
              Repetir contraseña
              <input required type="password" placeholder="••••••••" />
            </label>
          )}
          <button className="primary" type="submit">
            {register ? "Crear cuenta" : "Ingresar"}
            <ChevronRight />
          </button>
        </form>
        <div className="auth-switch">
          {register ? "¿Ya tenés cuenta?" : "¿No tenés cuenta?"}{" "}
          <button onClick={() => setRegister(!register)}>
            {register ? "Iniciar sesión" : "Registrate gratis"}
          </button>
        </div>
        <small className="terms">
          Al continuar, aceptás los Términos y la Política de Privacidad.
        </small>
      </section>
    </main>
  );
}

export default function App() {
  const [logged, setLogged] = useState(false);
  const [view, setView] = useState<View>("home");
  const [pet, setPet] = useState<PetId>("fido");
  const [shared, setShared] = useState(false);
  const [action, setAction] = useState<ActionKind | null>(null);
  const [toast, setToast] = useState("");
  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };
  const exportData = () => {
    const payload = {
      usuario: "Usuario Demo",
      mascotas: Object.values(pets),
      exportado: new Date().toISOString(),
    };
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(payload, null, 2)], {
        type: "application/json",
      }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = "mis-mascotas-datos.json";
    link.click();
    URL.revokeObjectURL(url);
    notify("Tus datos se descargaron correctamente");
  };
  const content = useMemo(() => {
    const selectPet = (id: PetId) => {
      setPet(id);
      setView("profile");
    };
    switch (view) {
      case "home":
        return <HomeView go={setView} selectPet={selectPet} />;
      case "pets":
        return (
          <PetsView
            shared={shared}
            selectPet={selectPet}
            setShared={setShared}
            open={setAction}
          />
        );
      case "profile":
        return (
          <ProfileView
            id={pet}
            back={() => setView("pets")}
            go={setView}
            open={setAction}
          />
        );
      case "reminders":
        return <RemindersView open={setAction} />;
      case "albums":
        return <AlbumsView open={setAction} />;
      case "calendar":
        return <CalendarView />;
      case "shared":
        return <SharedView open={setAction} notify={notify} />;
      case "settings":
        return (
          <SettingsView
            logout={() => setLogged(false)}
            open={setAction}
            exportData={exportData}
          />
        );
    }
  }, [view, pet, shared]);
  if (!logged) return <Auth onLogin={() => setLogged(true)} />;
  return (
    <main className="app-shell">
      {action && (
        <ActionDialog
          kind={action}
          pet={pet}
          close={() => setAction(null)}
          done={(message) => {
            setAction(null);
            notify(message);
          }}
        />
      )}
      {toast && (
        <div className="toast">
          <span>✓</span>
          {toast}
        </div>
      )}
      <aside className="desktop-sidebar">
        <div className="side-logo">
          <PawPrint />
          <b>Mis Mascotas</b>
        </div>
        <div className="side-user">
          <span>US</span>
          <div>
            <b>Usuario</b>
            <small>Plan Premium</small>
          </div>
        </div>
        <nav>
          {nav.map(([id, label, Icon]) => (
            <button
              key={id}
              className={view === id ? "active" : ""}
              onClick={() => setView(id)}
            >
              <Icon />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <div className="side-tip">
          <Crown />
          <b>Cuidado sin límites</b>
          <p>Tu plan Premium está activo.</p>
        </div>
      </aside>
      <section className="phone-frame">
        <div className="statusbar">
          <span>9:41</span>
          <div>
            <span>●●●</span>
            <span>▰</span>
          </div>
        </div>
        <div className="screen-content">{content}</div>
        <BottomNav view={view} go={setView} />
      </section>
      <aside className="desktop-context">
        <div className="context-card">
          <span className="eyebrow">PRÓXIMO EVENTO</span>
          <div className="big-date">
            <b>30</b>
            <span>
              AGO
              <br />
              2026
            </span>
          </div>
          <h3>Vacuna antirrábica</h3>
          <p>Fido · 10:30</p>
          <button onClick={() => setView("calendar")}>
            Ver calendario <ChevronRight />
          </button>
        </div>
        <div className="context-card compact">
          <div className="context-title">
            <b>Tu familia</b>
            <span>3 mascotas</span>
          </div>
          <div className="mini-pets">
            {(["fido", "luna", "rex"] as PetId[]).map((id) => (
              <button
                key={id}
                onClick={() => {
                  setPet(id);
                  setView("profile");
                }}
              >
                <PetAvatar id={id} size="sm" />
                <span>{pets[id].name}</span>
              </button>
            ))}
          </div>
        </div>
      </aside>
    </main>
  );
}
