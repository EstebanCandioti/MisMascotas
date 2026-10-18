"use client";

import { SettingsView } from "@/components/screens/settings-view";
import { useAuth } from "@/features/auth/auth-context";

export default function SettingsPage() {
  const { logout } = useAuth();
  return <SettingsView logout={() => void logout()} />;
}
