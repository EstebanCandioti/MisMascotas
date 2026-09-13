import AsyncStorage from "@react-native-async-storage/async-storage";
import { createContext, PropsWithChildren, useContext, useEffect, useState } from "react";

export type Pet = { id: string; name: string; species: string; breed: string; age: string; weight: string; emoji: string; color: string };
export type Reminder = { id: string; title: string; pet: string; date: string; detail: string; done: boolean };
type AppData = { pets: Pet[]; setPets: (pets: Pet[]) => void; reminders: Reminder[]; setReminders: (reminders: Reminder[]) => void; ready: boolean };

const DATA_KEY = "mismascotas-demo-data-v1";
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
const Context = createContext<AppData | null>(null);

export function AppDataProvider({ children }: PropsWithChildren) {
  const [pets, setPets] = useState(initialPets);
  const [reminders, setReminders] = useState(initialReminders);
  const [ready, setReady] = useState(false);
  useEffect(() => { AsyncStorage.getItem(DATA_KEY).then((saved) => { if (saved) { const data = JSON.parse(saved) as { pets?: Pet[]; reminders?: Reminder[] }; if (data.pets) setPets(data.pets); if (data.reminders) setReminders(data.reminders); } }).finally(() => setReady(true)); }, []);
  useEffect(() => { if (ready) AsyncStorage.setItem(DATA_KEY, JSON.stringify({ pets, reminders })); }, [pets, reminders, ready]);
  return <Context.Provider value={{ pets, setPets, reminders, setReminders, ready }}>{children}</Context.Provider>;
}
export function useAppData() { const value = useContext(Context); if (!value) throw new Error("useAppData debe usarse dentro de AppDataProvider"); return value; }
