import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  type PropsWithChildren,
  useContext,
  useEffect,
  useState,
} from "react";
import { AUTH_TOKEN_KEY, getCurrentUser, getPets, type MascotaResponse } from "../services/api";
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
  title: string;
  pet: string;
  date: string;
  detail: string;
  done: boolean;
};
export type ClinicalEvent = { id: string; type: string; title: string; pet: string; detail: string; date: string };
export type AlbumPhoto = { id: string; emoji: string; color: string; title: string; date: string };
export type Album = { id: string; name: string; emoji: string; color: string; photos: AlbumPhoto[] };
export type LocalUser = { id: string; name: string; email: string; password: string; esPremium?: boolean };

type AppData = {
  pets: Pet[];
  setPets: (pets: Pet[]) => void;
  refreshPets: () => Promise<void>;
  reminders: Reminder[];
  setReminders: (reminders: Reminder[]) => void;
  events: ClinicalEvent[];
  setEvents: (events: ClinicalEvent[]) => void;
  albums: Album[];
  setAlbums: (albums: Album[]) => void;
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
  reminders?: Reminder[];
  events?: ClinicalEvent[];
  albums?: Album[];
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

const initialReminders: Reminder[] = [
  { id: "vacuna", title: "Vacuna antirrábica", pet: "Fido", date: "30 AGO", detail: "En 3 días", done: false },
  { id: "control", title: "Control anual", pet: "Rex", date: "03 SEP", detail: "En 7 días", done: false },
  { id: "pipeta", title: "Pipeta antiparasitaria", pet: "Luna", date: "27 AGO", detail: "Completado hoy", done: true },
];
const initialEvents: ClinicalEvent[] = [
  { id: "consulta-fido", type: "Consulta", title: "Control general", pet: "Fido", detail: "Sin hallazgos. Se recomienda control anual.", date: "20 AGO 2026" },
  { id: "vacuna-fido", type: "Vacuna", title: "Vacuna séxtuple", pet: "Fido", detail: "Aplicada correctamente.", date: "15 MAY 2026" },
];
const initialAlbums: Album[] = [
  { id: "fido", name: "Momentos de Fido", emoji: "🐶", color: "#F2D5A0", photos: [{ id: "1", emoji: "🐶", color: "#F2D5A0", title: "Paseo en la plaza", date: "20 AGO" }, { id: "2", emoji: "🌳", color: "#D6EBC7", title: "Tarde al aire libre", date: "14 AGO" }] },
  { id: "luna", name: "Luna en casa", emoji: "🐱", color: "#D9CDFC", photos: [{ id: "3", emoji: "🐱", color: "#D9CDFC", title: "Siesta", date: "08 AGO" }] },
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
  const [reminders, setReminders] = useState<Reminder[]>(initialReminders);
  const [events, setEvents] = useState<ClinicalEvent[]>(initialEvents);
  const [albums, setAlbums] = useState<Album[]>(initialAlbums);
  const [users, setUsers] = useState<LocalUser[]>(initialUsers);
  const [currentUser, setCurrentUser] = useState<LocalUser | null>(null);
  const [premium, setPremium] = useState(false);
  const [notifications, setNotifications] = useState(true);
  const [ready, setReady] = useState(false);
  const [pendingAuthToken, setPendingAuthToken] = useState<string | null>(null);
  const [pendingCredentials, setPendingCredentialsState] = useState<{ email: string; password: string } | null>(null);

  const refreshPets = async () => {
    try {
      const remotePets = await getPets();
      setPets(remotePets.map(mapPetResponse));
    } catch {
      // Conserva las mascotas locales si el backend no está disponible.
    }
  };

  useEffect(() => {
    readPersistedData()
      .then((saved) => {
        if (saved?.pets) setPets(saved.pets);
        if (saved?.reminders) setReminders(saved.reminders);
        if (saved?.events) setEvents(saved.events);
        if (saved?.albums) setAlbums(saved.albums);
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
    if (!ready || !currentUser) return;

    let cancelled = false;

    getAuthToken(AUTH_TOKEN_KEY)
      .then(async (token) => {
        if (!token) return null;
        await refreshPets();
        return getCurrentUser(token);
      })
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
  }, [ready, currentUser?.id]);

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

    writePersistedData({ pets, reminders, events, albums, users, currentUser, premium, notifications });
  }, [pets, reminders, events, albums, users, currentUser, premium, notifications, ready]);

  return (
    <AppDataContext.Provider
      value={{ pets, setPets, refreshPets, reminders, setReminders, events, setEvents, albums, setAlbums, users, setUsers, currentUser, setCurrentUser, premium, setPremium, notifications, setNotifications, ready, pendingAuthToken, setPendingAuthToken, pendingCredentials, setPendingCredentials, completeLogin, resendCode, logout }}
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
