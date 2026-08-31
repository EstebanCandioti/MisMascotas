import type { FormEvent } from "react";

import { pets } from "@/data/pets";
import type { PetId } from "@/types/app";
import { actionTitles, type ActionKind } from "@/types/actions";

type ActionDialogProps = {
  kind: ActionKind;
  pet: PetId;
  close: () => void;
  done: (message: string) => void;
};

const noFormActions: ActionKind[] = ["history", "badges", "legal"];

export function ActionDialog({ kind, pet, close, done }: ActionDialogProps) {
  const selectedPet = pets[pet];
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    done(`${actionTitles[kind]} guardado correctamente`);
  };

  return (
    <div className="modal-backdrop" onMouseDown={close}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" onMouseDown={(event) => event.stopPropagation()}>
        <header>
          <div><span className="eyebrow">MIS MASCOTAS</span><h2 id="modal-title">{actionTitles[kind]}</h2></div>
          <button className="icon-btn" onClick={close} aria-label="Cerrar">×</button>
        </header>
        {kind === "history" ? (
          <div className="timeline">
            <div><span /><p><b>Consulta veterinaria</b><small>20 ago 2026 · Control general sin hallazgos.</small></p></div>
            <div><span /><p><b>Peso actualizado</b><small>15 ago 2026 · {selectedPet.weight}</small></p></div>
            <div><span /><p><b>Vacuna quíntuple</b><small>4 mar 2026 · Aplicación completa.</small></p></div>
            <button className="secondary-action" onClick={() => done("Historial exportado")}>Exportar historial</button>
          </div>
        ) : kind === "badges" ? (
          <div className="badge-gallery">
            <span>💀<b>Destructor</b><small>12 juguetes</small></span>
            <span>🍖<b>Glotón</b><small>30 comidas</small></span>
            <span>🏆<b>Puntual</b><small>10 cuidados</small></span>
          </div>
        ) : kind === "legal" ? (
          <div className="legal-copy"><p>Mis Mascotas protege la información de tus animales y permite exportarla libremente. El acceso compartido siempre puede ser revocado por el responsable.</p><p>Versión académica demostrativa · PP2.</p></div>
        ) : (
          <form className="modal-form" onSubmit={submit}>
            {(kind === "pet" || kind === "edit") && <PetFields edit={kind === "edit"} pet={selectedPet} />}
            {kind === "event" && <EventFields weight={selectedPet.weight} />}
            {kind === "reminder" && <ReminderFields />}
            {kind === "album" && <AlbumFields />}
            {kind === "invite" && <InviteFields />}
            {kind === "manage" && <ManageFields />}
            {kind === "notifications" && <NotificationFields />}
            {kind === "security" && <SecurityFields />}
            {kind === "support" && <SupportFields />}
            {!noFormActions.includes(kind) && <div className="modal-actions"><button type="button" onClick={close}>Cancelar</button><button className="primary" type="submit">Guardar</button></div>}
          </form>
        )}
      </section>
    </div>
  );
}

function PetFields({ edit, pet }: { edit: boolean; pet: (typeof pets)[PetId] }) {
  return <><label>Nombre<input required defaultValue={edit ? pet.name : ""} placeholder="Nombre de la mascota" /></label><div className="form-row"><label>Especie<select defaultValue={edit ? pet.species : "Perro"}><option>Perro</option><option>Gato</option><option>Ave</option><option>Conejo</option><option>Otra</option></select></label><label>Raza<input defaultValue={edit ? pet.breed : ""} placeholder="Raza" /></label></div><label>Fecha de nacimiento<input type="date" defaultValue="2023-06-12" /></label></>;
}
function EventFields({ weight }: { weight: string }) {
  return <><label>Tipo de evento<select><option>Consulta veterinaria</option><option>Vacunación</option><option>Medicación</option><option>Estudio</option><option>Otro</option></select></label><label>Fecha<input type="date" required defaultValue="2026-08-27" /></label><label>Descripción<textarea required placeholder="Diagnóstico, indicaciones y observaciones" /></label><label>Peso registrado (kg)<input type="number" step="0.1" defaultValue={weight.replace(" kg", "")} /></label></>;
}
function ReminderFields() { return <><label>Título<input required placeholder="Ej. Vacuna antirrábica" /></label><div className="form-row"><label>Fecha<input type="date" required defaultValue="2026-08-30" /></label><label>Hora<input type="time" defaultValue="10:30" /></label></div><label>Repetición<select><option>Una vez</option><option>Mensual</option><option>Cada 3 meses</option><option>Anual</option></select></label></>; }
function AlbumFields() { return <><label>Nombre del álbum<input required placeholder="Ej. Vacaciones 2026" /></label><label>Descripción<textarea placeholder="Contá de qué se trata" /></label><label>Mascota<select><option>Fido</option><option>Luna</option><option>Rex</option></select></label></>; }
function InviteFields() { return <><label>Correo electrónico<input required type="email" placeholder="persona@correo.com" /></label><label>Perfil<select><option>Familia</option><option>Cuidador</option></select></label><label>Mascotas<select><option>Todas</option><option>Fido</option><option>Luna</option><option>Rex</option></select></label></>; }
function ManageFields() { return <><label>Perfil de acceso<select><option>Familia</option><option>Cuidador</option></select></label><label>Mascotas autorizadas<select><option>Fido y Luna</option><option>Fido</option><option>Luna</option><option>Rex</option></select></label><label className="switch-row"><span>Permitir registrar eventos</span><input type="checkbox" defaultChecked /></label></>; }
function NotificationFields() { return <><label className="switch-row"><span>Recordatorios de salud</span><input type="checkbox" defaultChecked /></label><label className="switch-row"><span>Actividad de cuidadores</span><input type="checkbox" defaultChecked /></label><label className="switch-row"><span>Novedades del producto</span><input type="checkbox" /></label></>; }
function SecurityFields() { return <><label>Contraseña actual<input type="password" required /></label><label>Nueva contraseña<input type="password" required minLength={8} /></label><label>Repetir contraseña<input type="password" required minLength={8} /></label></>; }
function SupportFields() { return <><label>Asunto<select><option>Problema técnico</option><option>Consulta sobre mi cuenta</option><option>Sugerencia</option></select></label><label>Mensaje<textarea required placeholder="¿Cómo podemos ayudarte?" /></label></>; }
