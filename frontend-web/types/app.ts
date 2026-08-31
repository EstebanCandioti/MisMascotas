export type AppView =
  | "home"
  | "pets"
  | "profile"
  | "reminders"
  | "albums"
  | "calendar"
  | "shared"
  | "settings";

export type PetId = "fido" | "luna" | "rex";

export type Pet = {
  name: string;
  species: string;
  breed: string;
  age: string;
  weight: string;
  emoji: string;
  color: string;
  days: number;
};
