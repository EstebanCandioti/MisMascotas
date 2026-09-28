import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  type PropsWithChildren,
  useContext,
  useCallback,
  useEffect,
  useState,
} from "react";
import {
  AUTH_TOKEN_KEY,
  getCurrentUser,
  getPetReminders,
  getPets,
  type MascotaResponse,
  type RecordatorioResponse,
} from "../services/api";
import { getAuthToken } from "../services/auth-storage";

export type Pet = {
  id: string;
  name: string;
  species: string;
  breed: string;
  age: string;
  weight: string;
  emoji: string;
  color: string;
  fechaNacimiento?: string | null;
  fechaAproximada?: boolean;
  edadValor?: number | null;
  edadUnidad?: string | null;
  fotoPerfil?: string | null;
  pesoActual?: number | null;
  notas?: string | null;
};

export type Reminder = {
  id: string;
  petId: string;
  title: string;
  type: string;
  pet: string;
  date: string;
  detail: string;
  done: boolean;
  modality: string;
  intervalValue: number | null;
  intervalUnit: string | null;
  weekdays: string | null;
  endDate: string | null;
  confirmedAt: string | null;
  status: string | null;
};
export type ClinicalEvent = {
  id: string;
  petId?: string;
  type: string;
  title: string;
  pet: string;
  detail: string;
  date: string;
  dose?: string;
  reason?: string;
  diagnosis?: string;
  weight?: number;
  observations?: string;
  attachmentUrl?: string;
  attachmentFormat?: string;
};
export type LocalUser = { id: string; name: string; email: string; password: string; esPremium?: boolean };

type AppData = {
  pets: Pet[];
  setPets: (pets: Pet[]) => void;
  refreshPets: () => Promise<Pet[]>;
  reminders: Reminder[];
  setReminders: (reminders: Reminder[]) => void;
  refreshReminders: (petsToLoad: Pet[]) => Promise<void>;
  remindersError: string | null;
  events: ClinicalEvent[];
  setEvents: (events: ClinicalEvent[]) => void;
  users: LocalUser[];
  currentUser: LocalUser | null;
  setCurrentUser: (user: LocalUser | null) => void;
  setUsers: (users: LocalUser[]) => void;
  premium: boolean;
  setPremium: (value: boolean) => void;
  notifications: boolean;
  setNotifications: (value: boolean) => void;
  ready: boolean;
  pendingAuthToken: string | null;
  setPendingAuthToken: (token: string | null) => void;
  pendingCredentials: { email: string; password: string } | null;
  setPendingCredentials: (email: string, password: string) => void;
  completeLogin: (token: string) => Promise<void>;
  resendCode: () => Promise<void>;
  logout: () => Promise<void>;
};

type PersistedData = {
  pets?: Pet[];
  events?: ClinicalEvent[];
  users?: LocalUser[];
  currentUser?: LocalUser | null;
  premium?: boolean;
  notifications?: boolean;
};

const STORAGE_KEY = "mismascotas-demo-data-v1";
const isWeb = typeof window !== "undefined";

const initialPets: Pet[] = [
  { id: "fido", name: "Fido", species: "Perro", breed: "Golden Retriever", age: "3 años", weight: "28,5 kg", emoji: "🐶", color: "#F2D5A0" },
  { id: "luna", name: "Luna", species: "Gato", breed: "Siamés", age: "2 años", weight: "4,1 kg", emoji: "🐱", color: "#D9CDFC" },
  { id: "rex", name: "Rex", species: "Perro", breed: "Bulldog", age: "5 años", weight: "22 kg", emoji: "🐶", color: "#C9E5F3" },
];

const initialEvents: ClinicalEvent[] = [
  { id: "consulta-fido", petId: "fido", type: "Consulta", title: "Control general", pet: "Fido", detail: "Sin hallazgos. Se recomienda control anual.", date: "20 AGO 2026" },
  { id: "vacuna-fido", petId: "fido", type: "Vacuna", title: "Vacuna séxtuple", pet: "Fido", detail: "Aplicada correctamente.", date: "15 MAY 2026" },
];
const initialUsers: LocalUser[] = [{ id: "demo", name: "Usuario Demo", email: "usuario@demo.com", password: "Demo1234" }];

function mapPetResponse(pet: MascotaResponse): Pet {
  const emoji = pet.especie.toLowerCase().includes("gato") ? "🐱" : "🐶";
  const age = pet.edadValor != null && pet.edadUnidad
    ? `${pet.edadValor} ${pet.edadUnidad.toLowerCase()}`
    : pet.fechaNacimiento ?? "Edad no indicada";

  return {
    id: pet.idMascota,
    name: pet.nombre,
    species: pet.especie,
    breed: pet.raza ?? "Raza no indicada",
    age,
    weight: pet.pesoActual != null ? `${pet.pesoActual} kg` : "Peso no indicado",
    emoji,
    color: emoji === "🐱" ? "#D9CDFC" : "#F2D5A0",
    fechaNacimiento: pet.fechaNacimiento,
    fechaAproximada: pet.fechaAproximada,
    edadValor: pet.edadValor,
    edadUnidad: pet.edadUnidad,
    fotoPerfil: pet.fotoPerfil,
    pesoActual: pet.pesoActual,
    notas: pet.notas,
  };
}

const AppDataContext = createContext<AppData | null>(null);

export function mapReminderResponse(reminder: RecordatorioResponse, pet: Pet): Reminder {
  const startDate = new Date(reminder.fechaHoraInicio);
  const recurrence = reminder.modalidad === "recurrente" && reminder.intervaloValor && reminder.intervaloUnidad
    ? `Cada ${reminder.intervaloValor} ${reminder.intervaloUnidad}`
    : reminder.modalidad === "dias_semana" && reminder.diaSemana
      ? `Días: ${reminder.diaSemana}`
      : "Único";

  return {
    id: reminder.idRecordatorio,
    petId: reminder.mascotaId,
    title: reminder.titulo,
    type: reminder.tipo,
    pet: pet.name,
    date: Number.isNaN(startDate.getTime()) ? reminder.fechaHoraInicio : startDate.toISOString(),
    detail: recurrence,
    done: Boolean(reminder.confirmadoEn) || reminder.estado?.toUpperCase() === "COMPLETADO",
    modality: reminder.modalidad,
    intervalValue: reminder.intervaloValor,
    intervalUnit: reminder.intervaloUnidad,
    weekdays: reminder.diaSemana,
    endDate: reminder.fechaFin,
    confirmedAt: reminder.confirmadoEn,
    status: reminder.estado,
  };
}

async function readPersistedData(): Promise<PersistedData | null> {
  try {
    if (isWeb) {
      const saved = window.localStorage.getItem(STORAGE_KEY);
      return saved ? (JSON.parse(saved) as PersistedData) : null;
    }

    const saved = await AsyncStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as PersistedData) : null;
  } catch {
    return null;
  }
}

async function writePersistedData(data: PersistedData) {
  try {
    if (isWeb) {
      const { currentUser: _currentUser, ...persistedData } = data;
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(persistedData));
      return;
    }

    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // Ignora errores de persistencia para no romper el flujo de la app.
  }
}

export function AppDataProvider({ children }: PropsWithChildren) {
  const [pets, setPets] = useState<Pet[]>(initialPets);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [remindersError, setRemindersError] = useState<string | null>(null);
  const [events, setEvents] = useState<ClinicalEvent[]>(initialEvents);
  const [users, setUsers] = useState<LocalUser[]>(initialUsers);
  const [currentUser, setCurrentUser] = useState<LocalUser | null>(null);
  const [premium, setPremium] = useState(false);
  const [notifications, setNotifications] = useState(true);
  const [ready, setReady] = useState(false);
  const [pendingAuthToken, setPendingAuthToken] = useState<string | null>(null);
  const [pendingCredentials, setPendingCredentialsState] = useState<{ email: string; password: string } | null>(null);
  const currentUserId = currentUser?.id;

  const refreshPets = useCallback(async (): Promise<Pet[]> => {
    const remotePets = await getPets();
    const mappedPets = remotePets.map(mapPetResponse);
    setPets(mappedPets);
    return mappedPets;
  }, []);

  const refreshReminders = useCallback(async (petsToLoad: Pet[]) => {
    if (!petsToLoad.length) {
      setReminders([]);
      setRemindersError(null);
      return;
    }

    try {
      const remindersByPet = await Promise.all(
        petsToLoad.map(async (pet) => {
          const results = await getPetReminders(pet.id);
          return results.map((reminder) => mapReminderResponse(reminder, pet));
        }),
      );
      setReminders(remindersByPet.flat().sort((first, second) => Date.parse(first.date) - Date.parse(second.date)));
      setRemindersError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : "No se pudieron cargar los recordatorios.";
      setRemindersError(message);
    }
  }, []);

  useEffect(() => {
    readPersistedData()
      .then((saved) => {
        if (saved?.pets) setPets(saved.pets);
        if (saved?.events) setEvents(saved.events);
        if (saved?.users) setUsers(saved.users);

        if (!isWeb && saved?.currentUser) {
          setCurrentUser(saved.currentUser);
        }

        if (typeof saved?.premium === "boolean") setPremium(saved.premium);
        if (typeof saved?.notifications === "boolean") setNotifications(saved.notifications);
      })
      .finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (!ready || !currentUserId) return;

    let cancelled = false;

    const loadAccountData = async () => {
      const token = await getAuthToken(AUTH_TOKEN_KEY);
      if (!token) return null;
      const remotePets = await refreshPets();
      await refreshReminders(remotePets);
      return getCurrentUser(token);
    };

    loadAccountData()
      .then((profile) => {
        if (!profile || cancelled) return;

        setCurrentUser((user) => user ? {
          ...user,
          id: profile.idUsuario,
          name: profile.nombre,
          email: profile.email,
          esPremium: profile.esPremium,
        } : user);
        setPremium(profile.esPremium);
      })
      .catch(() => {
        // Conserva los datos locales si el servidor no está disponible.
      });

    return () => {
      cancelled = true;
    };
  }, [currentUserId, ready, refreshPets, refreshReminders]);

  const setPendingCredentials = (email: string, password: string) => {
    setPendingCredentialsState({ email, password });
  };

  const completeLogin = async (token: string) => {
    if (!pendingCredentials) return;

    const userFromCredentials = users.find((user) => user.email === pendingCredentials.email);
    const nextUser = userFromCredentials
      ? { ...userFromCredentials, name: userFromCredentials.name || pendingCredentials.email.split("@")[0] }
      : { id: `user-${Date.now()}`, name: pendingCredentials.email.split("@")[0], email: pendingCredentials.email, password: pendingCredentials.password };

    setUsers((existing) => {
      const alreadyExists = existing.some((user) => user.email === pendingCredentials.email);
      return alreadyExists ? existing : [nextUser, ...existing];
    });

    setCurrentUser(nextUser);
    setPendingAuthToken(null);
    setPendingCredentialsState(null);

    if (typeof token === "string" && token.trim()) {
      try {
        const { setAuthToken } = await import("../services/auth-storage");
        await setAuthToken("mismascotas-auth-token-v2", token);

        const profile = await getCurrentUser(token);
        const authenticatedUser = {
          ...nextUser,
          id: profile.idUsuario,
          name: profile.nombre,
          email: profile.email,
          esPremium: profile.esPremium,
        };
        setCurrentUser(authenticatedUser);
        setPremium(profile.esPremium);
      } catch {
        // Conserva el usuario local si no se puede consultar el perfil.
      }
    }
  };

  const resendCode = async () => {
    if (!pendingCredentials) {
      return;
    }

    const { login } = await import("../services/api");
    const response = await login(pendingCredentials.email, pendingCredentials.password);
    setPendingAuthToken(response.token);
  };

  const logout = async () => {
    setCurrentUser(null);
    setReminders([]);
    setRemindersError(null);
    setPendingAuthToken(null);
    setPendingCredentialsState(null);
    try {
      const { deleteAuthToken } = await import("../services/auth-storage");
      await deleteAuthToken("mismascotas-auth-token-v2");
    } catch {
      // no-op
    }
  };

  useEffect(() => {
    if (!ready) return;

    writePersistedData({ pets, events, users, currentUser, premium, notifications });
  }, [pets, events, users, currentUser, premium, notifications, ready]);

  return (
    <AppDataContext.Provider
      value={{ pets, setPets, refreshPets, reminders, setReminders, refreshReminders, remindersError, events, setEvents, users, setUsers, currentUser, setCurrentUser, premium, setPremium, notifications, setNotifications, ready, pendingAuthToken, setPendingAuthToken, pendingCredentials, setPendingCredentials, completeLogin, resendCode, logout }}
    >
      {children}
    </AppDataContext.Provider>
  );
}

export function useAppData() {
  const value = useContext(AppDataContext);

  if (!value) {
    throw new Error("useAppData debe usarse dentro de AppDataProvider");
  }

  return value;
}
