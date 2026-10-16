import type { Pet, PetId } from "@/types/app";

export const pets: Record<PetId, Pet> = {
  fido: { name: "Fido", species: "Perro", breed: "Golden Retriever", age: "3 años", weight: "28.5 kg", emoji: "🐶", color: "#f2d5a0", days: 3 },
  luna: { name: "Luna", species: "Gato", breed: "Siamés", age: "2 años", weight: "4.1 kg", emoji: "🐱", color: "#d9cdfc", days: 7 },
  rex: { name: "Rex", species: "Perro", breed: "Bulldog", age: "5 años", weight: "22 kg", emoji: "🐶", color: "#c9e5f3", days: 3 },
};
