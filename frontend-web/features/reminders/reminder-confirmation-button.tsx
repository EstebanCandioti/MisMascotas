"use client";

import { useState } from "react";
import { CheckCircle2, Circle } from "lucide-react";

import { isReminderConfirmed } from "@/features/reminders/mappers";
import type { Reminder } from "@/features/reminders/types";
import type { RemindersController } from "@/features/reminders/use-reminders";
import { getApiErrorMessage } from "@/lib/api/api-error";

type ReminderConfirmationButtonProps = {
  reminder: Reminder;
  controller: RemindersController;
  compact?: boolean;
};

export function ReminderConfirmationButton({ reminder, controller, compact = false }: ReminderConfirmationButtonProps) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const confirmed = isReminderConfirmed(reminder);
  const actionLabel = confirmed ? "Desconfirmar" : "Confirmar";

  const toggleConfirmation = async () => {
    setPending(true);
    setError("");
    try {
      if (confirmed) await controller.unconfirmReminder(reminder.idRecordatorio);
      else await controller.confirmReminder(reminder.idRecordatorio);
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setPending(false);
    }
  };

  return (
    <div className={`reminder-confirmation${compact ? " reminder-confirmation-compact" : ""}`}>
      <button
        className="reminder-confirm-button"
        type="button"
        disabled={pending}
        onClick={() => void toggleConfirmation()}
        aria-label={`${actionLabel} ${reminder.titulo}`}
        aria-pressed={confirmed}
      >
        {confirmed ? <CheckCircle2 /> : <Circle />}
        <span>{pending ? "Guardando…" : actionLabel}</span>
      </button>
      {error && <span className="reminder-confirm-error" role="alert">{error}</span>}
    </div>
  );
}
