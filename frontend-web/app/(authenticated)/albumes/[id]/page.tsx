"use client";

import { useParams } from "next/navigation";

import { AlbumDetailView } from "@/components/screens/album-detail-view";
import { useAlbumsController } from "@/features/albums/albums-context";
import { usePetsController } from "@/features/pets/pets-context";

export default function AlbumPage() {
  const { id } = useParams<{ id: string }>();
  return <AlbumDetailView id={id} controller={useAlbumsController()} pets={usePetsController().pets} />;
}
