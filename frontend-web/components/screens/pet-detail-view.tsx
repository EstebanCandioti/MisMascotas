"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Pencil, Trash2, Weight } from "lucide-react";

import { Card, EmptyState, Header } from "@/components/layout/app-navigation";
import { ClinicalHistory } from "@/features/clinical-events/clinical-history";
import type { ClinicalEventsController } from "@/features/clinical-events/use-clinical-events";
import { PetFormDialog } from "@/features/pets/pet-form-dialog";
import { PetAvatar, petAgeLabel, petSummary } from "@/features/pets/pet-presentation";
import type { Pet, PetRequest } from "@/features/pets/types";
import type { PetsController } from "@/features/pets/use-pets";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function PetDetailView({ id, controller, clinicalEventsController }: { id: string; controller: PetsController; clinicalEventsController: ClinicalEventsController }) {
  const router = useRouter();
  const requestPet = controller.loadPet;
  const [pet, setPet] = useState<Pet | null>(() => controller.pets.find((item) => item.idMascota === id) ?? null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [toast, setToast] = useState("");

  const loadPet = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setPet(await requestPet(id));
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [id, requestPet]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) void loadPet();
    });
    return () => { active = false; };
  }, [loadPet]);

  const refreshedPet = controller.pets.find((item) => item.idMascota === id);
  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active && refreshedPet) setPet(refreshedPet);
    });
    return () => { active = false; };
  }, [refreshedPet]);

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };

  const updatePet = async (request: PetRequest) => {
    const updated = await controller.updatePet(id, request);
    setPet(updated);
    setEditing(false);
    notify("Mascota actualizada correctamente");
  };

  const removePet = async () => {
    setDeleting(true);
    setDeleteError("");
    try {
      await controller.deletePet(id);
      router.replace("/mascotas");
    } catch (caught) {
      setDeleteError(getApiErrorMessage(caught));
      setDeleting(false);
    }
  };

  if (loading && !pet) return <><Header title="Mascota" subtitle="Detalle de mascota" onBack={() => router.back()} /><EmptyState icon={<span className="pet-loader" />} title="Cargando mascota" description="Estamos obteniendo su información." /></>;

  if (!pet) return <><Header title="Mascota" subtitle="Detalle de mascota" onBack={() => router.back()} /><EmptyState icon={<span>!</span>} title="No pudimos cargar la mascota" description={error || "La mascota no está disponible."} action={<div className="empty-state-actions"><button className="secondary-action" onClick={() => void loadPet()}>Reintentar</button><Link className="secondary-action" href="/mascotas">Volver al listado</Link></div>} /></>;

  const age = petAgeLabel(pet);
  return (
    <>
      <Header title={pet.nombre} subtitle="Detalle de mascota" onBack={() => router.back()} action={<button className="icon-btn" onClick={() => setEditing(true)} aria-label="Editar mascota"><Pencil /></button>} />
      {loading && <p className="pet-inline-status" role="status">Actualizando información…</p>}
      {error && <p className="auth-error pet-detail-error" role="alert">{error} <button type="button" onClick={() => void loadPet()}>Reintentar</button></p>}
      <div className="profile-hero"><PetAvatar pet={pet} size="lg" /><h2>{pet.nombre}</h2><p>{petSummary(pet)}</p></div>
      <div className="pet-detail-grid">
        <Card className="metric-card"><div><span>Peso actual</span><strong>{pet.pesoActual !== null ? `${pet.pesoActual} kg` : "No informado"}</strong></div><Weight /></Card>
        <Card>
          <div className="pet-detail-field"><span>Especie</span><strong>{pet.especie}</strong></div>
          <div className="pet-detail-field"><span>Raza</span><strong>{pet.raza || "No informada"}</strong></div>
          <div className="pet-detail-field"><span>Edad</span><strong>{age || "No informada"}</strong></div>
          {pet.fechaNacimiento && <div className="pet-detail-field"><span>Fecha de nacimiento</span><strong>{pet.fechaNacimiento}{pet.fechaAproximada ? " (aproximada)" : ""}</strong></div>}
        </Card>
        {pet.notas && <Card><div className="pet-detail-field"><span>Notas</span><p>{pet.notas}</p></div></Card>}
      </div>
      <ClinicalHistory petId={pet.idMascota} petName={pet.nombre} controller={clinicalEventsController} />
      <div className="pet-detail-actions"><button className="secondary-action" onClick={() => setEditing(true)}><Pencil /> Editar mascota</button><button className="danger-action" onClick={() => { setDeleteError(""); setConfirmDelete(true); }}><Trash2 /> Eliminar mascota</button></div>
      {editing && <PetFormDialog pet={pet} onClose={() => setEditing(false)} onSave={updatePet} />}
      {confirmDelete && <div className="modal-backdrop" onMouseDown={() => !deleting && setConfirmDelete(false)}><section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-pet-title" onMouseDown={(event) => event.stopPropagation()}><header><div><span className="eyebrow">CONFIRMAR ACCIÓN</span><h2 id="delete-pet-title">Eliminar a {pet.nombre}</h2></div></header><p>Esta acción quitará la mascota de tu listado.</p>{deleteError && <p className="auth-error" role="alert">{deleteError}</p>}<div className="modal-actions"><button type="button" onClick={() => setConfirmDelete(false)} disabled={deleting}>Cancelar</button><button className="danger-action" type="button" onClick={() => void removePet()} disabled={deleting}>{deleting ? "Eliminando…" : "Eliminar"}</button></div></section></div>}
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </>
  );
}
