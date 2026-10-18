"use client";

import { ProfileView } from "@/components/screens/profile-view";
import { useAuth } from "@/features/auth/auth-context";

export default function ProfilePage() {
  const { user } = useAuth();
  return <ProfileView user={user} />;
}
