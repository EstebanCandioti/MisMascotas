"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Image as ImageIcon, Pencil, Plus, Trash2 } from "lucide-react";

import { Card, EmptyState, Header } from "@/components/layout/app-navigation";
import { AlbumFormDialog } from "@/features/albums/album-form-dialog";
import type { Album, AlbumRequest } from "@/features/albums/types";
import type { AlbumsController } from "@/features/albums/use-albums";
import type { Pet } from "@/features/pets/types";
import { createPhoto, deletePhoto, getPhoto, listPhotosByAlbum } from "@/features/photos/api";
import { PhotoFormDialog } from "@/features/photos/photo-form-dialog";
import type { Photo, PhotoRequest } from "@/features/photos/types";
import { getApiErrorMessage } from "@/lib/api/api-error";

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Fecha no disponible"
    : new Intl.DateTimeFormat("es-AR", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function AlbumDetailView({ id, controller, pets }: { id: string; controller: AlbumsController; pets: Pet[] }) {
  const router = useRouter();
  const requestAlbum = controller.getAlbum;
  const [album, setAlbum] = useState<Album | null>(() => controller.albums.find((item) => item.idAlbum === id) ?? null);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [photosError, setPhotosError] = useState("");
  const [editing, setEditing] = useState(false);
  const [addingPhoto, setAddingPhoto] = useState(false);
  const [selectedPhoto, setSelectedPhoto] = useState<Photo | null>(null);
  const [photoLoading, setPhotoLoading] = useState(false);
  const [photoError, setPhotoError] = useState("");
  const [confirmDeleteAlbum, setConfirmDeleteAlbum] = useState(false);
  const [confirmDeletePhoto, setConfirmDeletePhoto] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [toast, setToast] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    setPhotosError("");
    const [albumResult, photosResult] = await Promise.allSettled([
      requestAlbum(id),
      listPhotosByAlbum(id),
    ]);
    if (albumResult.status === "fulfilled") setAlbum(albumResult.value);
    else setError(getApiErrorMessage(albumResult.reason));
    if (photosResult.status === "fulfilled") setPhotos(photosResult.value);
    else setPhotosError(getApiErrorMessage(photosResult.reason));
    setLoading(false);
  }, [id, requestAlbum]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) void load();
    });
    return () => { active = false; };
  }, [load]);

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };

  const updateAlbum = async (request: AlbumRequest) => {
    const updated = await controller.updateAlbum(id, request);
    setAlbum(updated);
    setEditing(false);
    notify("Álbum actualizado correctamente");
  };

  const removeAlbum = async () => {
    setDeleting(true);
    setDeleteError("");
    try {
      await controller.deleteAlbum(id);
      router.replace("/albumes");
    } catch (caught) {
      setDeleteError(getApiErrorMessage(caught));
      setDeleting(false);
    }
  };

  const addPhoto = async (request: PhotoRequest) => {
    const created = await createPhoto(id, request);
    setPhotos((current) => [...current, created]);
    controller.changePhotoCount(id, 1);
    setAlbum((current) => current ? { ...current, cantidadFotos: current.cantidadFotos + 1 } : current);
    setAddingPhoto(false);
    notify("Foto agregada correctamente");
  };

  const openPhoto = async (photo: Photo) => {
    setSelectedPhoto(photo);
    setPhotoLoading(true);
    setPhotoError("");
    try {
      setSelectedPhoto(await getPhoto(photo.idFoto));
    } catch (caught) {
      setPhotoError(getApiErrorMessage(caught));
    } finally {
      setPhotoLoading(false);
    }
  };

  const closePhoto = () => {
    setSelectedPhoto(null);
    setConfirmDeletePhoto(false);
    setDeleteError("");
  };

  const removePhoto = async () => {
    if (!selectedPhoto) return;
    setDeleting(true);
    setDeleteError("");
    try {
      await deletePhoto(selectedPhoto.idFoto);
      setPhotos((current) => current.filter((photo) => photo.idFoto !== selectedPhoto.idFoto));
      controller.changePhotoCount(id, -1);
      setAlbum((current) => current ? { ...current, cantidadFotos: Math.max(0, current.cantidadFotos - 1) } : current);
      closePhoto();
      notify("Foto eliminada correctamente");
    } catch (caught) {
      setDeleteError(getApiErrorMessage(caught));
    } finally {
      setDeleting(false);
    }
  };

  if (loading && !album) {
    return <><Header title="Álbum" subtitle="Detalle del álbum" onBack={() => router.back()} /><EmptyState icon={<span className="pet-loader" />} title="Cargando álbum" description="Estamos obteniendo el álbum y sus fotos." /></>;
  }

  if (!album) {
    return (
      <>
        <Header title="Álbum" subtitle="Detalle del álbum" onBack={() => router.back()} />
        <EmptyState icon={<span>!</span>} title="No pudimos cargar el álbum" description={error || "El álbum no está disponible."} action={<div className="empty-state-actions"><button className="secondary-action" onClick={() => void load()}>Reintentar</button><Link className="secondary-action" href="/albumes">Volver a álbumes</Link></div>} />
      </>
    );
  }

  const pet = pets.find((item) => item.idMascota === album.mascotaId);

  return (
    <>
      <Header title={album.nombre} subtitle={pet ? `Álbum de ${pet.nombre}` : "Detalle del álbum"} onBack={() => router.back()} action={<button className="icon-btn" onClick={() => setEditing(true)} aria-label="Editar álbum"><Pencil /></button>} />
      {loading && <p className="pet-inline-status" role="status">Actualizando información…</p>}
      {error && <p className="auth-error album-inline-error" role="alert">{error} <button type="button" onClick={() => void load()}>Reintentar</button></p>}
      <Card className="album-detail-card">
        <div>
          <span className="eyebrow">DESCRIPCIÓN</span>
          <p>{album.descripcion || "Sin descripción"}</p>
        </div>
        <div><span className="eyebrow">CREADO</span><strong>{formatDate(album.creadoEn)}</strong></div>
        <div><span className="eyebrow">FOTOS</span><strong>{photos.length}</strong></div>
      </Card>
      <div className="album-section-heading">
        <div><h2>Fotos</h2><p>El backend registra imágenes mediante una URL pública.</p></div>
        <button className="secondary-action" onClick={() => setAddingPhoto(true)}><Plus /> Agregar foto</button>
      </div>
      {photosError && <p className="auth-error album-inline-error" role="alert">{photosError} <button type="button" onClick={() => void load()}>Reintentar</button></p>}
      {!photosError && !loading && photos.length === 0 && (
        <EmptyState icon={<ImageIcon />} title="Este álbum todavía no tiene fotos" description="Agregá una imagen mediante su URL. La aplicación no sube archivos al backend." action={<button className="secondary-action" onClick={() => setAddingPhoto(true)}><Plus /> Agregar foto</button>} />
      )}
      {photos.length > 0 && (
        <div className="album-photo-grid">
          {photos.map((photo) => (
            <button key={photo.idFoto} type="button" onClick={() => void openPhoto(photo)} aria-label="Ver detalle de la foto">
              <img src={photo.urlArchivo} alt="" loading="lazy" /> {/* eslint-disable-line @next/next/no-img-element */}
              <span>{photo.formato || "Imagen"}</span>
            </button>
          ))}
        </div>
      )}
      <div className="pet-detail-actions">
        <button className="secondary-action" onClick={() => setEditing(true)}><Pencil /> Editar álbum</button>
        <button className="danger-action" onClick={() => { setDeleteError(""); setConfirmDeleteAlbum(true); }}><Trash2 /> Eliminar álbum</button>
      </div>

      {editing && <AlbumFormDialog pets={pets} album={album} onClose={() => setEditing(false)} onSave={updateAlbum} />}
      {addingPhoto && <PhotoFormDialog onClose={() => setAddingPhoto(false)} onSave={addPhoto} />}

      {selectedPhoto && (
        <div className="modal-backdrop" onMouseDown={() => !deleting && closePhoto()}>
          <section className="modal photo-detail-modal" role="dialog" aria-modal="true" aria-labelledby="photo-detail-title" onMouseDown={(event) => event.stopPropagation()}>
            <header><div><span className="eyebrow">DETALLE DE FOTO</span><h2 id="photo-detail-title">{selectedPhoto.formato || "Imagen"}</h2></div></header>
            <img src={selectedPhoto.urlArchivo} alt="" /> {/* eslint-disable-line @next/next/no-img-element */}
            {photoLoading && <p className="pet-inline-status" role="status">Consultando detalle…</p>}
            {photoError && <p className="auth-error" role="alert">{photoError}</p>}
            <dl className="detail-list">
              <div><dt>URL</dt><dd><a href={selectedPhoto.urlArchivo} target="_blank" rel="noreferrer">{selectedPhoto.urlArchivo}</a></dd></div>
              <div><dt>Creada</dt><dd>{formatDate(selectedPhoto.creadoEn)}</dd></div>
            </dl>
            {!confirmDeletePhoto ? (
              <div className="detail-actions"><button type="button" onClick={closePhoto}>Cerrar</button><button className="danger-action" type="button" onClick={() => { setDeleteError(""); setConfirmDeletePhoto(true); }}><Trash2 /> Eliminar foto</button></div>
            ) : (
              <div className="inline-confirm">
                <p>¿Eliminar esta foto del álbum? La operación no se puede deshacer desde la Web.</p>
                {deleteError && <p className="auth-error" role="alert">{deleteError}</p>}
                <div className="modal-actions"><button type="button" onClick={() => setConfirmDeletePhoto(false)} disabled={deleting}>Cancelar</button><button className="danger-action" type="button" onClick={() => void removePhoto()} disabled={deleting}>{deleting ? "Eliminando…" : "Confirmar eliminación"}</button></div>
              </div>
            )}
          </section>
        </div>
      )}

      {confirmDeleteAlbum && (
        <div className="modal-backdrop" onMouseDown={() => !deleting && setConfirmDeleteAlbum(false)}>
          <section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-album-title" onMouseDown={(event) => event.stopPropagation()}>
            <header><div><span className="eyebrow">CONFIRMAR ACCIÓN</span><h2 id="delete-album-title">Eliminar {album.nombre}</h2></div></header>
            <p>El backend eliminará lógicamente el álbum, su asociación y todas sus fotos activas.</p>
            {deleteError && <p className="auth-error" role="alert">{deleteError}</p>}
            <div className="modal-actions"><button type="button" onClick={() => setConfirmDeleteAlbum(false)} disabled={deleting}>Cancelar</button><button className="danger-action" type="button" onClick={() => void removeAlbum()} disabled={deleting}>{deleting ? "Eliminando…" : "Eliminar álbum"}</button></div>
          </section>
        </div>
      )}
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </>
  );
}
