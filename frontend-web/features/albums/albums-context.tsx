"use client";

import { createContext, type PropsWithChildren, useContext } from "react";

import type { AlbumsController } from "@/features/albums/use-albums";

const AlbumsContext = createContext<AlbumsController | null>(null);

export function AlbumsProvider({ children, value }: PropsWithChildren<{ value: AlbumsController }>) {
  return <AlbumsContext.Provider value={value}>{children}</AlbumsContext.Provider>;
}

export function useAlbumsController(): AlbumsController {
  const context = useContext(AlbumsContext);
  if (!context) throw new Error("useAlbumsController debe utilizarse dentro de AlbumsProvider");
  return context;
}
