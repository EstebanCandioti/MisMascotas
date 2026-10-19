"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Album as AlbumIcon, Image as ImageIcon, Plus, RefreshCw } from "lucide-react";

import { Card, EmptyState, Header } from "@/components/layout/app-navigation";
import { AlbumFormDialog } from "@/features/albums/album-form-dialog";
import { useAlbumsController } from "@/features/albums/albums-context";
import type { AlbumRequest } from "@/features/albums/types";
import { usePetsController } from "@/features/pets/pets-context";

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Fecha no disponible" : new Intl.DateTimeFormat("es-AR", { dateStyle: "medium" }).format(date);
}

export function AlbumsView() {
  const searchParams = useSearchParams();
  const requestedPetId = searchParams.get("mascotaId") ?? "";
  const albumsController = useAlbumsController();
  const petsController = usePetsController();
  const [selectedPetId, setSelectedPetId] = useState(requestedPetId);
  const [creating, setCreating] = useState(false);
  const [toast, setToast] = useState("");
  const petsById = useMemo(() => new Map(petsController.pets.map((pet) => [pet.idMascota, pet])), [petsController.pets]);
  const visibleAlbums = selectedPetId
    ? albumsController.albums.filter((album) => album.mascotaId === selectedPetId)
    : albumsController.albums;

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };

  const createAlbum = async (request: AlbumRequest) => {
    await albumsController.createAlbum(request);
    setCreating(false);
    setSelectedPetId(request.mascotaId);
    notify("Álbum creado correctamente");
  };

  const headerAction = petsController.pets.length > 0
    ? <button className="icon-btn" onClick={() => setCreating(true)} aria-label="Crear álbum"><Plus /></button>
    : undefined;

  return (
    <>
      <Header title="Álbumes" subtitle="Tus recuerdos favoritos" action={headerAction} />
      {petsController.pets.length > 0 && (
        <div className="album-toolbar">
          <label>Mostrar álbumes de
            <select value={selectedPetId} onChange={(event) => setSelectedPetId(event.target.value)}>
              <option value="">Todas mis mascotas</option>
              {petsController.pets.map((pet) => <option key={pet.idMascota} value={pet.idMascota}>{pet.nombre}</option>)}
            </select>
          </label>
          <button className="secondary-action" onClick={() => setCreating(true)}><Plus /> Nuevo álbum</button>
        </div>
      )}

      {albumsController.status === "loading" && albumsController.albums.length === 0 && (
        <EmptyState icon={<span className="pet-loader" />} title="Cargando álbumes" description="Estamos obteniendo tus recuerdos." />
      )}
      {albumsController.status === "error" && albumsController.albums.length === 0 && (
        <EmptyState
          icon={<span>!</span>}
          title="No pudimos cargar los álbumes"
          description={albumsController.error?.message ?? "Ocurrió un error inesperado."}
          action={<button className="secondary-action" onClick={() => void albumsController.reload()}><RefreshCw /> Reintentar</button>}
        />
      )}
      {albumsController.status === "success" && petsController.pets.length === 0 && (
        <EmptyState
          icon={<AlbumIcon />}
          title="Primero agregá una mascota"
          description="Cada álbum debe estar asociado a una mascota existente."
          action={<Link className="secondary-action" href="/mascotas">Ir a Mascotas</Link>}
        />
      )}
      {albumsController.status === "success" && petsController.pets.length > 0 && visibleAlbums.length === 0 && (
        <EmptyState
          icon={<AlbumIcon />}
          title={selectedPetId ? "Esta mascota todavía no tiene álbumes" : "Sin álbumes"}
          description="Creá un álbum para empezar a organizar sus fotos."
          action={<button className="secondary-action" onClick={() => setCreating(true)}><Plus /> Crear álbum</button>}
        />
      )}
      {visibleAlbums.length > 0 && (
        <div className="album-list">
          {visibleAlbums.map((album) => {
            const pet = petsById.get(album.mascotaId);
            return (
              <Link className="album-card-link" href={`/albumes/${album.idAlbum}`} key={album.idAlbum}>
                <Card>
                  <span className="album-art" aria-hidden="true"><ImageIcon /></span>
                  <div>
                    <h3>{album.nombre}</h3>
                    <p>{album.descripcion || "Sin descripción"}</p>
                    <span>{pet?.nombre ?? "Mascota"}</span>
                  </div>
                  <strong>{album.cantidadFotos} {album.cantidadFotos === 1 ? "foto" : "fotos"}</strong>
                  <small>{formatDate(album.creadoEn)}</small>
                </Card>
              </Link>
            );
          })}
        </div>
      )}
      {albumsController.status === "error" && albumsController.albums.length > 0 && (
        <p className="auth-error album-inline-error" role="alert">{albumsController.error?.message} <button type="button" onClick={() => void albumsController.reload()}>Reintentar</button></p>
      )}
      {creating && <AlbumFormDialog pets={petsController.pets} initialPetId={selectedPetId} onClose={() => setCreating(false)} onSave={createAlbum} />}
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </>
  );
}
