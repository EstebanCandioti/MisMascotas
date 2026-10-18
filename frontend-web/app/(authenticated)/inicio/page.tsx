"use client";

import { HomeView } from "@/components/screens/home-view";
import { useAuth } from "@/features/auth/auth-context";
import { usePetsController } from "@/features/pets/pets-context";

export default function HomePage() {
  const { user } = useAuth();
  const controller = usePetsController();
  return <HomeView userName={user?.nombre ?? "Usuario"} controller={controller} />;
}
