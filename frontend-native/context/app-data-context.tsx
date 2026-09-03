import AsyncStorage from "@react-native-async-storage/async-storage";
import {
  createContext,
  type PropsWithChildren,
  useContext,
  useEffect,
  useState,
} from "react";

export type Pet = {
  id: string;
  name: string;
  species: string;
  breed: string;
  age: string;
  weight: string;
  emoji: string;
  color: string;
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
export type LocalUser = { id: string; name: string; email: string; password: string };

type AppData = {
  pets: Pet[];
  setPets: (pets: Pet[]) => void;
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
};

const STORAGE_KEY = "mismascotas-demo-data-v1";

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

const AppDataContext = createContext<AppData | null>(null);

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

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((saved) => {
        if (!saved) return;

        const data = JSON.parse(saved) as {
          pets?: Pet[];
          reminders?: Reminder[];
          events?: ClinicalEvent[];
          albums?: Album[];
          users?: LocalUser[]; currentUser?: LocalUser | null; premium?: boolean; notifications?: boolean;
        };

        if (data.pets) setPets(data.pets);
        if (data.reminders) setReminders(data.reminders);
        if (data.events) setEvents(data.events);
        if (data.albums) setAlbums(data.albums);
        if (data.users) setUsers(data.users);
        if (data.currentUser) setCurrentUser(data.currentUser);
        if (typeof data.premium === "boolean") setPremium(data.premium);
        if (typeof data.notifications === "boolean") setNotifications(data.notifications);
      })
      .finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (!ready) return;

    AsyncStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ pets, reminders, events, albums, users, currentUser, premium, notifications }),
    );
  }, [pets, reminders, events, albums, users, currentUser, premium, notifications, ready]);

  return (
    <AppDataContext.Provider
      value={{ pets, setPets, reminders, setReminders, events, setEvents, albums, setAlbums, users, setUsers, currentUser, setCurrentUser, premium, setPremium, notifications, setNotifications, ready }}
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
