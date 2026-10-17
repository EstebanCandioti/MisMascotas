import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  type PropsWithChildren,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import {
  getCurrentUser,
  getPet,
  getPets,
  getRemindersByPet,
  login,
  logout as logoutRequest,
  setUnauthorizedHandler,
  type MascotaResponse,
  type RecordatorioResponse,
} from "../services/api";
import { getAuthSession } from "../services/auth-storage";
import { mapearRecordatorioVista } from "../utils/recordatorios";

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
  pet: string;
  date: string;
  detail: string;
  done: boolean;
};

export type ClinicalEvent = { id: string; type: string; title: string; pet: string; detail: string; date: string };
export type AlbumPhoto = { id: string; emoji: string; color: string; title: string; date: string };
export type Album = { id: string; name: string; emoji: string; color: string; photos: AlbumPhoto[] };
export type AuthenticatedUser = { id: string; name: string; email: string; esPremium: boolean };

type AppData = {
  pets: Pet[];
  setPets: (pets: Pet[]) => void;
  petsLoading: boolean;
  petsError: string | null;
  refreshPets: () => Promise<void>;
  refreshPet: (id: string) => Promise<Pet>;
  reminders: Reminder[];
  remindersLoading: boolean;
  remindersError: string | null;
  refreshReminders: () => Promise<void>;
  addCreatedReminder: (reminder: RecordatorioResponse, petName: string) => void;
  events: ClinicalEvent[];
  setEvents: (events: ClinicalEvent[]) => void;
  albums: Album[];
  setAlbums: (albums: Album[]) => void;
  currentUser: AuthenticatedUser | null;
  premium: boolean;
  setPremium: (value: boolean) => void;
  notifications: boolean;
  setNotifications: (value: boolean) => void;
  ready: boolean;
  pendingAuthToken: string | null;
  setPendingAuthToken: (token: string | null) => void;
  pendingCredentials: { email: string; password: string } | null;
  setPendingCredentials: (email: string, password: string) => void;
  completeLogin: () => Promise<void>;
  resendCode: () => Promise<void>;
  logout: () => Promise<void>;
};

type PersistedData = {
  events?: ClinicalEvent[];
  albums?: Album[];
  notifications?: boolean;
};

const STORAGE_KEY = "mismascotas-local-data-v2";
const LEGACY_DEMO_STORAGE_KEY = "mismascotas-demo-data-v1";

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Ocurrió un error inesperado.";
}

function mapUser(profile: Awaited<ReturnType<typeof getCurrentUser>>): AuthenticatedUser {
  return {
    id: profile.idUsuario,
    name: profile.nombre,
    email: profile.email,
    esPremium: profile.esPremium,
  };
}

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

function mapReminderResponse(reminder: RecordatorioResponse, petName: string): Reminder {
  const view = mapearRecordatorioVista(reminder, { id: reminder.mascotaId, nombre: petName });

  return {
    id: view.id,
    petId: view.mascotaId,
    title: view.titulo,
    pet: view.mascotaNombre,
    date: view.fechaCorta,
    detail: `${view.tipoLegible} · ${view.fechaLegible}`,
    done: view.completado,
  };
}

const AppDataContext = createContext<AppData | null>(null);

async function readPersistedData(): Promise<PersistedData | null> {
  try {
    const saved = await AsyncStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as PersistedData) : null;
  } catch {
    return null;
  }
}

async function writePersistedData(data: PersistedData) {
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // Estos datos no críticos pueden continuar únicamente en memoria.
  }
}

export function AppDataProvider({ children }: PropsWithChildren) {
  const [pets, setPets] = useState<Pet[]>([]);
  const [petsLoading, setPetsLoading] = useState(false);
  const [petsError, setPetsError] = useState<string | null>(null);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [remindersLoading, setRemindersLoading] = useState(false);
  const [remindersError, setRemindersError] = useState<string | null>(null);
  const [events, setEvents] = useState<ClinicalEvent[]>([]);
  const [albums, setAlbums] = useState<Album[]>([]);
  const [currentUser, setCurrentUser] = useState<AuthenticatedUser | null>(null);
  const [premium, setPremium] = useState(false);
  const [notifications, setNotifications] = useState(true);
  const [ready, setReady] = useState(false);
  const [pendingAuthToken, setPendingAuthToken] = useState<string | null>(null);
  const [pendingCredentials, setPendingCredentialsState] = useState<{ email: string; password: string } | null>(null);

  const clearAuthenticatedData = useCallback(() => {
    setCurrentUser(null);
    setPets([]);
    setReminders([]);
    setPremium(false);
    setPendingAuthToken(null);
    setPendingCredentialsState(null);
  }, []);

  const loadRemindersForPets = useCallback(async (petList: Pet[]) => {
    setRemindersLoading(true);
    setRemindersError(null);
    try {
      const responses = await Promise.all(
        petList.map(async (pet) => ({
          pet,
          reminders: await getRemindersByPet(pet.id),
        })),
      );
      setReminders(responses.flatMap(({ pet, reminders: values }) => (
        values.map((reminder) => mapReminderResponse(reminder, pet.name))
      )));
    } catch (error) {
      setRemindersError(errorMessage(error));
      throw error;
    } finally {
      setRemindersLoading(false);
    }
  }, []);

  const refreshPets = useCallback(async () => {
    setPetsLoading(true);
    setPetsError(null);
    let mappedPets: Pet[];
    try {
      mappedPets = (await getPets()).map(mapPetResponse);
      setPets(mappedPets);
    } catch (error) {
      setPetsError(errorMessage(error));
      throw error;
    } finally {
      setPetsLoading(false);
    }

    await loadRemindersForPets(mappedPets).catch(() => undefined);
  }, [loadRemindersForPets]);

  const refreshPet = useCallback(async (id: string) => {
    const mappedPet = mapPetResponse(await getPet(id));
    setPets((current) => {
      const exists = current.some((pet) => pet.id === id);
      return exists
        ? current.map((pet) => pet.id === id ? mappedPet : pet)
        : [...current, mappedPet];
    });
    return mappedPet;
  }, []);

  const refreshReminders = useCallback(async () => {
    await loadRemindersForPets(pets);
  }, [loadRemindersForPets, pets]);

  const addCreatedReminder = useCallback((reminder: RecordatorioResponse, petName: string) => {
    const mappedReminder = mapReminderResponse(reminder, petName);
    setReminders((current) => [
      mappedReminder,
      ...current.filter((item) => item.id !== mappedReminder.id),
    ]);
    setRemindersError(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearAuthenticatedData);
    return () => setUnauthorizedHandler(null);
  }, [clearAuthenticatedData]);

  useEffect(() => {
    let cancelled = false;

    async function restore() {
      await AsyncStorage.removeItem(LEGACY_DEMO_STORAGE_KEY).catch(() => undefined);
      const saved = await readPersistedData();
      if (saved?.events) setEvents(saved.events);
      if (saved?.albums) setAlbums(saved.albums);
      if (typeof saved?.notifications === "boolean") setNotifications(saved.notifications);

      const session = await getAuthSession();
      if (!session) return;

      try {
        const profile = await getCurrentUser();
        if (cancelled) return;
        const user = mapUser(profile);
        setCurrentUser(user);
        setPremium(user.esPremium);
        await refreshPets().catch(() => undefined);
      } catch {
        if (!cancelled) clearAuthenticatedData();
      }
    }

    restore().finally(() => {
      if (!cancelled) setReady(true);
    });

    return () => {
      cancelled = true;
    };
  }, [clearAuthenticatedData, refreshPets]);

  useEffect(() => {
    if (!ready) return;
    void writePersistedData({ events, albums, notifications });
  }, [events, albums, notifications, ready]);

  const setPendingCredentials = (email: string, password: string) => {
    setPendingCredentialsState({ email, password });
  };

  const completeLogin = async () => {
    const profile = await getCurrentUser();
    const user = mapUser(profile);
    setCurrentUser(user);
    setPremium(user.esPremium);
    setPendingAuthToken(null);
    setPendingCredentialsState(null);
    await refreshPets().catch(() => undefined);
  };

  const resendCode = async () => {
    if (!pendingCredentials) {
      throw new Error("La verificación venció. Volvé a iniciar sesión.");
    }

    const response = await login(pendingCredentials.email, pendingCredentials.password);
    setPendingAuthToken(response.token);
  };

  const logout = async () => {
    try {
      await logoutRequest();
    } finally {
      clearAuthenticatedData();
    }
  };

  return (
    <AppDataContext.Provider
      value={{
        pets,
        setPets,
        petsLoading,
        petsError,
        refreshPets,
        refreshPet,
        reminders,
        remindersLoading,
        remindersError,
        refreshReminders,
        addCreatedReminder,
        events,
        setEvents,
        albums,
        setAlbums,
        currentUser,
        premium,
        setPremium,
        notifications,
        setNotifications,
        ready,
        pendingAuthToken,
        setPendingAuthToken,
        pendingCredentials,
        setPendingCredentials,
        completeLogin,
        resendCode,
        logout,
      }}
    >
      {children}
    </AppDataContext.Provider>
  );
}

export function useAppData() {
  const value = useContext(AppDataContext);
  if (!value) throw new Error("useAppData debe usarse dentro de AppDataProvider");
  return value;
}
