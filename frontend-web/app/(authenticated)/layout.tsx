import type { PropsWithChildren } from "react";

import { AuthenticatedShell } from "@/components/layout/authenticated-shell";

export default function PrivateLayout({ children }: PropsWithChildren) {
  return <AuthenticatedShell>{children}</AuthenticatedShell>;
}
