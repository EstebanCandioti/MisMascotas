"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { AuthScreen } from "@/components/auth/auth-screen";
import { useAuth } from "@/features/auth/auth-context";

export default function LoginPage() {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "authenticated") router.replace("/inicio");
  }, [router, status]);

  return <AuthScreen />;
}
