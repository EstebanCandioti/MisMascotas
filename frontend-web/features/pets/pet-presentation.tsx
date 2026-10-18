import type { CSSProperties } from "react";

import type { Pet } from "@/features/pets/types";

const speciesEmoji: Record<string, string> = {
  perro: "🐶",
  gato: "🐱",
  ave: "🐦",
  conejo: "🐰",
};

const speciesColor: Record<string, string> = {
  perro: "#f2d5a0",
  gato: "#d9cdfc",
  ave: "#c9e5f3",
  conejo: "#d7ecc8",
};

function normalizedSpecies(pet: Pick<Pet, "especie">): string {
  return pet.especie.trim().toLocaleLowerCase("es");
}

export function PetAvatar({ pet, size = "md" }: { pet: Pet; size?: "sm" | "md" | "lg" }) {
  const species = normalizedSpecies(pet);
  const style = { background: speciesColor[species] ?? "#eee9ff" } satisfies CSSProperties;

  return (
    <div className={`pet-avatar ${size}`} style={style} aria-label={`${pet.nombre}, ${pet.especie}`}>
      {pet.fotoPerfil
        // Las URLs pertenecen a cada usuario y no tienen un dominio predecible para next/image.
        ? <img src={pet.fotoPerfil} alt="" /> // eslint-disable-line @next/next/no-img-element
        : <span aria-hidden="true">{speciesEmoji[species] ?? "🐾"}</span>}
    </div>
  );
}

export function petAgeLabel(pet: Pet): string | null {
  if (pet.edadValor !== null && pet.edadUnidad) {
    const units: Record<string, [string, string]> = {
      dias: ["día", "días"],
      semanas: ["semana", "semanas"],
      meses: ["mes", "meses"],
      anios: ["año", "años"],
    };
    const unit = units[pet.edadUnidad];
    return unit ? `${pet.edadValor} ${pet.edadValor === 1 ? unit[0] : unit[1]}` : null;
  }

  if (!pet.fechaNacimiento) return null;
  const birthDate = new Date(`${pet.fechaNacimiento}T00:00:00`);
  if (Number.isNaN(birthDate.getTime())) return null;
  const today = new Date();
  let years = today.getFullYear() - birthDate.getFullYear();
  const beforeBirthday = today.getMonth() < birthDate.getMonth()
    || (today.getMonth() === birthDate.getMonth() && today.getDate() < birthDate.getDate());
  if (beforeBirthday) years -= 1;
  return years === 0 ? "Menos de 1 año" : `${years} ${years === 1 ? "año" : "años"}`;
}

export function petSummary(pet: Pet): string {
  return [pet.raza, pet.especie, petAgeLabel(pet)].filter(Boolean).join(" · ");
}
