"use client";

import { useState } from "react";
import { ChevronRight, Pencil, Plus, Trash2, Weight } from "lucide-react";

import { Card, EmptyState, Header } from "@/components/layout/app-navigation";
import { PetFormDialog } from "@/features/pets/pet-form-dialog";
import { PetAvatar, petAgeLabel, petSummary } from "@/features/pets/pet-presentation";
import type { Pet, PetRequest } from "@/features/pets/types";
import type { PetsController } from "@/features/pets/use-pets";
import { getApiErrorMessage } from "@/lib/api/api-error";

type PetFormMode = "create" | "edit" | null;

export function PetsView({ controller }: { controller: PetsController }) {
  const [selectedPet, setSelectedPet] = useState<Pet | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [formMode, setFormMode] = useState<PetFormMode>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [toast, setToast] = useState("");

  const notify = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  };

  const openDetail = async (pet: Pet) => {
    setSelectedPet(pet);
    setDetailLoading(true);
    setDetailError("");
    try {
      setSelectedPet(await controller.loadPet(pet.idMascota));
    } catch (caught) {
      setDetailError(getApiErrorMessage(caught));
    } finally {
      setDetailLoading(false);
    }
  };

  const savePet = async (request: PetRequest) => {
    if (formMode === "edit" && selectedPet) {
      const updated = await controller.updatePet(selectedPet.idMascota, request);
      setSelectedPet(updated);
      setFormMode(null);
      notify("Mascota actualizada correctamente");
      return;
    }

    const created = await controller.createPet(request);
    setFormMode(null);
    setSelectedPet(created);
    notify("Mascota agregada correctamente");
  };

  const removePet = async () => {
    if (!selectedPet) return;
    setDeleting(true);
    setDeleteError("");
    try {
      await controller.deletePet(selectedPet.idMascota);
      setConfirmDelete(false);
      setSelectedPet(null);
      notify("Mascota eliminada correctamente");
    } catch (caught) {
      setDeleteError(getApiErrorMessage(caught));
    } finally {
      setDeleting(false);
    }
  };

  if (selectedPet) {
    const age = petAgeLabel(selectedPet);
    return (
      <>
        <Header
          title={selectedPet.nombre}
          subtitle="Detalle de mascota"
          onBack={() => { setSelectedPet(null); setDetailError(""); }}
          action={<button className="icon-btn" onClick={() => setFormMode("edit")} aria-label="Editar mascota"><Pencil /></button>}
        />
        {detailLoading && <p className="pet-inline-status" role="status">Actualizando información…</p>}
        {detailError && (
          <p className="auth-error pet-detail-error" role="alert">
            {detailError} <button type="button" onClick={() => void openDetail(selectedPet)}>Reintentar</button>
          </p>
        )}
        <div className="profile-hero">
          <PetAvatar pet={selectedPet} size="lg" />
          <h2>{selectedPet.nombre}</h2>
          <p>{petSummary(selectedPet)}</p>
        </div>
        <div className="pet-detail-grid">
          <Card className="metric-card">
            <div><span>Peso actual</span><strong>{selectedPet.pesoActual !== null ? `${selectedPet.pesoActual} kg` : "No informado"}</strong></div>
            <Weight />
          </Card>
          <Card>
            <div className="pet-detail-field"><span>Especie</span><strong>{selectedPet.especie}</strong></div>
            <div className="pet-detail-field"><span>Raza</span><strong>{selectedPet.raza || "No informada"}</strong></div>
            <div className="pet-detail-field"><span>Edad</span><strong>{age || "No informada"}</strong></div>
            {selectedPet.fechaNacimiento && <div className="pet-detail-field"><span>Fecha de nacimiento</span><strong>{selectedPet.fechaNacimiento}{selectedPet.fechaAproximada ? " (aproximada)" : ""}</strong></div>}
          </Card>
          {selectedPet.notas && <Card><div className="pet-detail-field"><span>Notas</span><p>{selectedPet.notas}</p></div></Card>}
        </div>
        <div className="pet-detail-actions">
          <button className="secondary-action" onClick={() => setFormMode("edit")}><Pencil /> Editar mascota</button>
          <button className="danger-action" onClick={() => { setDeleteError(""); setConfirmDelete(true); }}><Trash2 /> Eliminar mascota</button>
        </div>

        {formMode === "edit" && <PetFormDialog pet={selectedPet} onClose={() => setFormMode(null)} onSave={savePet} />}
        {confirmDelete && (
          <div className="modal-backdrop" onMouseDown={() => !deleting && setConfirmDelete(false)}>
            <section className="modal confirm-modal" role="alertdialog" aria-modal="true" aria-labelledby="delete-pet-title" onMouseDown={(event) => event.stopPropagation()}>
              <header><div><span className="eyebrow">CONFIRMAR ACCIÓN</span><h2 id="delete-pet-title">Eliminar a {selectedPet.nombre}</h2></div></header>
              <p>Esta acción quitará la mascota y sus datos asociados de tu listado.</p>
              {deleteError && <p className="auth-error" role="alert">{deleteError}</p>}
              <div className="modal-actions">
                <button type="button" onClick={() => setConfirmDelete(false)} disabled={deleting}>Cancelar</button>
                <button className="danger-action" type="button" onClick={() => void removePet()} disabled={deleting}>{deleting ? "Eliminando…" : "Eliminar"}</button>
              </div>
            </section>
          </div>
        )}
        {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
      </>
    );
  }

  return (
    <>
      <Header
        title="Mascotas"
        subtitle="Tu familia, siempre cerca"
        action={<button className="icon-btn" onClick={() => setFormMode("create")} aria-label="Agregar mascota"><Plus /></button>}
      />

      {(controller.status === "idle" || controller.status === "loading") && (
        <EmptyState icon={<span className="pet-loader" />} title="Cargando mascotas" description="Estamos buscando la información de tu familia." />
      )}
      {controller.status === "error" && (
        <EmptyState
          icon={<span>!</span>}
          title="No pudimos cargar tus mascotas"
          description={controller.error ? getApiErrorMessage(controller.error) : "Intentá nuevamente."}
          action={<button className="secondary-action" onClick={() => void controller.reload()}>Reintentar</button>}
        />
      )}
      {controller.status === "success" && controller.pets.length === 0 && (
        <EmptyState
          icon={<span>🐾</span>}
          title="Todavía no cargaste mascotas"
          description="Agregá la primera para empezar a organizar su información."
          action={<button className="secondary-action" onClick={() => setFormMode("create")}>Agregar mascota</button>}
        />
      )}
      {controller.status === "success" && controller.pets.length > 0 && (
        <div className="pet-list">
          {controller.pets.map((pet) => (
            <button className="pet-list-card" key={pet.idMascota} onClick={() => void openDetail(pet)}>
              <PetAvatar pet={pet} size="lg" />
              <div><h3>{pet.nombre}</h3><p>{petSummary(pet)}</p></div>
              <ChevronRight />
            </button>
          ))}
        </div>
      )}
      {(controller.status === "success" || controller.status === "error") && <button className="fab" onClick={() => setFormMode("create")} aria-label="Agregar mascota"><Plus /></button>}
      {formMode === "create" && <PetFormDialog onClose={() => setFormMode(null)} onSave={savePet} />}
      {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
    </>
  );
}
