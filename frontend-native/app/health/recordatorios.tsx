import { useCallback, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Modal,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { mapReminderResponse, useAppData, type Reminder } from "../../context/app-data-context";
import {
  confirmReminder,
  createReminder,
  deleteReminder,
  getReminder,
  unconfirmReminder,
  updateReminder,
  updateReminderStatus,
  type RecordatorioRequest,
  type RecordatorioResponse,
} from "../../services/api";

const reminderTypes: { id: RecordatorioRequest["tipo"]; label: string }[] = [
  { id: "VACUNA", label: "Vacuna" },
  { id: "VISITA_VETERINARIO", label: "Visita veterinaria" },
  { id: "MEDICACION", label: "Medicación" },
  { id: "PIPETA_ANTIPARASITARIO", label: "Antiparasitario" },
  { id: "OTRO", label: "Otro" },
];
const intervalUnits: NonNullable<RecordatorioRequest["intervaloUnidad"]>[] = ["dias", "semanas", "meses", "anios"];
const weekDays = [
  { id: "1", label: "L" },
  { id: "2", label: "M" },
  { id: "3", label: "X" },
  { id: "4", label: "J" },
  { id: "5", label: "V" },
  { id: "6", label: "S" },
  { id: "7", label: "D" },
];

type FormValues = {
  petId: string;
  title: string;
  type: RecordatorioRequest["tipo"];
  date: string;
  time: string;
  modality: NonNullable<RecordatorioRequest["modalidad"]>;
  intervalValue: string;
  intervalUnit: NonNullable<RecordatorioRequest["intervaloUnidad"]>;
  weekdays: string[];
  endDate: string;
};

function localIsoDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function dateInputFromInstant(value?: string | null) {
  if (!value) return localIsoDate();
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? localIsoDate() : localIsoDate(date);
}

function timeInputFromInstant(value?: string | null) {
  if (!value) return "09:00";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "09:00"
    : `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function isValidDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Fecha sin definir";
  return date.toLocaleDateString("es-AR", { day: "numeric", month: "short" }).toUpperCase();
}

function reminderTypeLabel(type: string) {
  return reminderTypes.find((item) => item.id === type)?.label ?? type;
}

function relativeDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const days = Math.round((target.getTime() - today.getTime()) / 86_400_000);
  if (days === 0) return "Hoy";
  if (days === 1) return "Mañana";
  if (days > 1) return `En ${days} días`;
  if (days === -1) return "Ayer";
  return `Hace ${Math.abs(days)} días`;
}

function initialForm(petId: string): FormValues {
  return {
    petId,
    title: "",
    type: "VACUNA",
    date: localIsoDate(),
    time: "09:00",
    modality: "unica",
    intervalValue: "1",
    intervalUnit: "meses",
    weekdays: [],
    endDate: "",
  };
}

export default function RecordatoriosScreen() {
  const router = useRouter();
  const { mascotaId, mascota } = useLocalSearchParams<{ mascotaId?: string; mascota?: string }>();
  const { pets, reminders, setReminders, refreshPets, refreshReminders, remindersError } = useAppData();
  const [filter, setFilter] = useState<"pending" | "done">("pending");
  const [modalVisible, setModalVisible] = useState(Boolean(mascotaId || mascota));
  const [form, setForm] = useState<FormValues>(() => initialForm(mascotaId ?? ""));
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  const requestedPet = pets.find((pet) => pet.id === mascotaId)
    ?? pets.find((pet) => pet.name === mascota);
  const visibleReminders = useMemo(
    () => reminders
      .filter((reminder) => reminder.done === (filter === "done"))
      .filter((reminder) => !requestedPet || reminder.petId === requestedPet.id)
      .sort((first, second) => Date.parse(first.date) - Date.parse(second.date)),
    [filter, reminders, requestedPet],
  );

  useFocusEffect(useCallback(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setLocalError(null);
      try {
        const remotePets = await refreshPets();
        const activePet = remotePets.find((pet) => pet.id === mascotaId)
          ?? remotePets.find((pet) => pet.name === mascota);
        if (activePet) {
          setForm((current) => ({ ...current, petId: activePet.id }));
        } else if (remotePets[0]) {
          setForm((current) => ({ ...current, petId: remotePets[0].id }));
        }
        await refreshReminders(remotePets);
      } catch (error) {
        if (!cancelled) setLocalError(error instanceof Error ? error.message : "No se pudieron cargar las mascotas.");
      }
      if (!cancelled) setLoading(false);
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [mascota, mascotaId, refreshPets, refreshReminders]));

  const patchForm = <K extends keyof FormValues>(key: K, value: FormValues[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const openCreateForm = () => {
    setEditingId(null);
    setForm(initialForm(requestedPet?.id ?? pets[0]?.id ?? ""));
    setModalVisible(true);
  };

  const openEditForm = async (reminder: Reminder) => {
    setBusyId(reminder.id);
    try {
      const remote = await getReminder(reminder.id);
      setEditingId(remote.idRecordatorio);
      setForm({
        petId: remote.mascotaId,
        title: remote.titulo,
        type: (reminderTypes.find((item) => item.id === remote.tipo)?.id ?? "OTRO"),
        date: dateInputFromInstant(remote.fechaHoraInicio),
        time: timeInputFromInstant(remote.fechaHoraInicio),
        modality: (remote.modalidad === "recurrente" || remote.modalidad === "dias_semana" ? remote.modalidad : "unica"),
        intervalValue: String(remote.intervaloValor ?? 1),
        intervalUnit: (intervalUnits.find((unit) => unit === remote.intervaloUnidad) ?? "meses"),
        weekdays: remote.diaSemana ? remote.diaSemana.split(",").map((day) => day.trim()) : [],
        endDate: remote.fechaFin ? dateInputFromInstant(remote.fechaFin) : "",
      });
      setModalVisible(true);
    } catch (error) {
      Alert.alert("No se pudo abrir el recordatorio", error instanceof Error ? error.message : "Intentá nuevamente.");
    } finally {
      setBusyId(null);
    }
  };

  const buildRequest = (): RecordatorioRequest | null => {
    if (!form.petId || !pets.some((pet) => pet.id === form.petId)) {
      Alert.alert("Seleccioná una mascota", "Elegí una mascota activa para el recordatorio.");
      return null;
    }
    if (!form.title.trim()) {
      Alert.alert("Falta el título", "Escribí qué necesitás recordar.");
      return null;
    }
    if (!isValidDate(form.date) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(form.time)) {
      Alert.alert("Fecha u hora inválida", "Usá AAAA-MM-DD para la fecha y HH:MM para la hora.");
      return null;
    }
    const start = new Date(`${form.date}T${form.time}:00`);
    if (Number.isNaN(start.getTime())) {
      Alert.alert("Fecha u hora inválida", "Revisá la fecha y la hora del recordatorio.");
      return null;
    }
    if (form.modality === "dias_semana" && form.weekdays.length === 0) {
      Alert.alert("Elegí los días", "Seleccioná al menos un día para repetir el recordatorio.");
      return null;
    }

    let endDate: string | undefined;
    if (form.endDate.trim()) {
      if (!isValidDate(form.endDate)) {
        Alert.alert("Fecha de finalización inválida", "Usá el formato AAAA-MM-DD.");
        return null;
      }
      const parsedEndDate = new Date(`${form.endDate}T23:59:59`);
      if (parsedEndDate < start) {
        Alert.alert("Fecha de finalización inválida", "La repetición no puede terminar antes de empezar.");
        return null;
      }
      endDate = parsedEndDate.toISOString();
    }

    if (form.modality === "recurrente" && (!Number.isInteger(Number(form.intervalValue)) || Number(form.intervalValue) < 1)) {
      Alert.alert("Intervalo inválido", "Ingresá un intervalo de repetición de al menos 1.");
      return null;
    }

    return {
      mascotaId: form.petId,
      titulo: form.title.trim(),
      tipo: form.type,
      fechaHoraInicio: start.toISOString(),
      modalidad: form.modality,
      intervaloValor: form.modality === "recurrente" ? Number(form.intervalValue) : undefined,
      intervaloUnidad: form.modality === "recurrente" ? form.intervalUnit : undefined,
      diasSemana: form.modality === "dias_semana" ? form.weekdays.join(",") : undefined,
      fechaFin: endDate,
    };
  };

  const applyResponse = (response: RecordatorioResponse) => {
    const pet = pets.find((item) => item.id === response.mascotaId);
    if (!pet) {
      setLocalError("El recordatorio se guardó, pero la mascota ya no está disponible para mostrarlo.");
      return;
    }
    const mapped = mapReminderResponse(response, pet);
    setReminders([
      mapped,
      ...reminders.filter((reminder) => reminder.id !== mapped.id),
    ].sort((first, second) => Date.parse(first.date) - Date.parse(second.date)));
  };

  const saveReminder = async () => {
    const request = buildRequest();
    if (!request) return;
    setSaving(true);
    setLocalError(null);
    try {
      const response = editingId
        ? await updateReminder(editingId, request)
        : await createReminder(request);
      applyResponse(response);
      setModalVisible(false);
      setFilter(response.confirmadoEn ? "done" : "pending");
    } catch (error) {
      Alert.alert(
        editingId ? "No se pudo editar el recordatorio" : "No se pudo crear el recordatorio",
        error instanceof Error ? error.message : "Intentá nuevamente.",
      );
    } finally {
      setSaving(false);
    }
  };

  const toggleReminder = async (reminder: Reminder) => {
    setBusyId(reminder.id);
    try {
      const response = await updateReminderStatus(reminder.id, reminder.done ? "ACTIVO" : "COMPLETADO");
      applyResponse(response);
      try {
        const confirmation = reminder.done
          ? await unconfirmReminder(reminder.id)
          : await confirmReminder(reminder.id);
        applyResponse(confirmation);
      } catch (confirmationError) {
        Alert.alert(
          reminder.done ? "Recordatorio reabierto" : "Recordatorio completado",
          `Se actualizó el estado, pero no se pudo actualizar la confirmación: ${confirmationError instanceof Error ? confirmationError.message : "intentá nuevamente."}`,
        );
      }
    } catch (error) {
      Alert.alert("No se pudo actualizar el recordatorio", error instanceof Error ? error.message : "Intentá nuevamente.");
    } finally {
      setBusyId(null);
    }
  };

  const removeReminder = (reminder: Reminder) => {
    Alert.alert("Eliminar recordatorio", `¿Querés eliminar “${reminder.title}”?`, [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Eliminar",
        style: "destructive",
        onPress: async () => {
          setBusyId(reminder.id);
          try {
            await deleteReminder(reminder.id);
            setReminders(reminders.filter((item) => item.id !== reminder.id));
          } catch (error) {
            Alert.alert("No se pudo eliminar el recordatorio", error instanceof Error ? error.message : "Intentá nuevamente.");
          } finally {
            setBusyId(null);
          }
        },
      },
    ]);
  };

  const loadError = localError ?? remindersError;

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
        <Text style={styles.topTitle}>Recordatorios</Text>
        <TouchableOpacity style={styles.addTop} onPress={openCreateForm} disabled={!pets.length}><Ionicons name="add" size={24} color="#7C4DFF" /></TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <View style={styles.intro}>
          <View style={styles.introIcon}><Ionicons name="alarm-outline" size={25} color="#7C4DFF" /></View>
          <View><Text style={styles.introTitle}>Nunca te olvides de un cuidado</Text><Text style={styles.introText}>Organizá vacunas, tratamientos y controles.</Text></View>
        </View>
        {loadError ? (
          <View style={styles.errorBanner}>
            <Text style={styles.errorText}>{loadError}</Text>
            <TouchableOpacity onPress={() => { setLoading(true); void refreshReminders(pets); }}><Text style={styles.retryText}>Reintentar</Text></TouchableOpacity>
          </View>
        ) : null}
        <View style={styles.tabs}>
          <TouchableOpacity style={[styles.tab, filter === "pending" && styles.activeTab]} onPress={() => setFilter("pending")}><Text style={[styles.tabText, filter === "pending" && styles.activeTabText]}>Pendientes</Text></TouchableOpacity>
          <TouchableOpacity style={[styles.tab, filter === "done" && styles.activeTab]} onPress={() => setFilter("done")}><Text style={[styles.tabText, filter === "done" && styles.activeTabText]}>Completados</Text></TouchableOpacity>
        </View>
        <Text style={styles.count}>{visibleReminders.length} {filter === "pending" ? "pendientes" : "completados"}</Text>
        {loading && !reminders.length ? (
          <View style={styles.empty}><ActivityIndicator color="#7C4DFF" /><Text style={styles.emptyText}>Cargando recordatorios...</Text></View>
        ) : !pets.length ? (
          <View style={styles.empty}><Ionicons name="paw-outline" size={36} color="#7C4DFF" /><Text style={styles.emptyTitle}>Primero registrá una mascota</Text><Text style={styles.emptyText}>Los recordatorios se asocian a una mascota.</Text></View>
        ) : visibleReminders.map((reminder) => (
          <View key={reminder.id} style={[styles.reminder, reminder.done && styles.reminderDone]}>
            <TouchableOpacity style={styles.checkButton} onPress={() => void toggleReminder(reminder)} disabled={busyId === reminder.id} accessibilityLabel={reminder.done ? "Desmarcar completado" : "Marcar completado"}>
              {busyId === reminder.id ? <ActivityIndicator size="small" color="#7C4DFF" /> : <View style={[styles.check, reminder.done && styles.checked]}>{reminder.done && <Ionicons name="checkmark" size={16} color="#FFFFFF" />}</View>}
            </TouchableOpacity>
            <View style={styles.reminderInfo}>
              <Text style={[styles.reminderTitle, reminder.done && styles.strike]}>{reminder.title}</Text>
              <Text style={styles.reminderType}>{reminderTypeLabel(reminder.type)} · {reminder.pet}</Text>
              <Text style={styles.reminderDetail}>{reminder.detail} · {relativeDate(reminder.date)}</Text>
            </View>
            <View style={styles.reminderActions}>
              <Text style={[styles.date, reminder.done && styles.doneDate]}>{formatDate(reminder.date)}</Text>
              <TouchableOpacity onPress={() => void openEditForm(reminder)} disabled={busyId === reminder.id} accessibilityLabel="Editar recordatorio"><Ionicons name="pencil-outline" size={17} color="#7C4DFF" /></TouchableOpacity>
              <TouchableOpacity onPress={() => removeReminder(reminder)} disabled={busyId === reminder.id} accessibilityLabel="Eliminar recordatorio"><Ionicons name="trash-outline" size={17} color="#D8515D" /></TouchableOpacity>
            </View>
          </View>
        ))}
        {!loading && pets.length > 0 && !visibleReminders.length && (
          <View style={styles.empty}><Ionicons name="checkmark-done-outline" size={36} color="#7C4DFF" /><Text style={styles.emptyTitle}>No hay recordatorios {filter === "pending" ? "pendientes" : "completados"}</Text><Text style={styles.emptyText}>Los datos se cargan desde el backend.</Text></View>
        )}
        <TouchableOpacity style={[styles.createButton, !pets.length && styles.disabled]} onPress={openCreateForm} disabled={!pets.length}><Ionicons name="add-circle-outline" size={22} color="#FFFFFF" /><Text style={styles.createText}>Crear recordatorio</Text></TouchableOpacity>
      </ScrollView>

      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <View style={styles.overlay}>
          <ScrollView contentContainerStyle={styles.sheetScroll} keyboardShouldPersistTaps="handled">
            <View style={styles.sheet}>
              <View style={styles.sheetHandle} />
              <Text style={styles.sheetTitle}>{editingId ? "Editar recordatorio" : "Nuevo recordatorio"}</Text>
              <Text style={styles.label}>Mascota *</Text>
              <View style={styles.chipRow}>{pets.map((item) => <TouchableOpacity key={item.id} style={[styles.chip, form.petId === item.id && styles.chipActive]} onPress={() => patchForm("petId", item.id)}><Text style={[styles.chipText, form.petId === item.id && styles.chipTextActive]}>{item.name}</Text></TouchableOpacity>)}</View>
              <Text style={styles.label}>Título *</Text>
              <TextInput style={styles.input} value={form.title} onChangeText={(value) => patchForm("title", value)} placeholder="Ej. Vacuna antirrábica" placeholderTextColor="#968F9E" maxLength={150} />
              <Text style={styles.label}>Tipo *</Text>
              <View style={styles.chipRow}>{reminderTypes.map((item) => <TouchableOpacity key={item.id} style={[styles.chip, form.type === item.id && styles.chipActive]} onPress={() => patchForm("type", item.id)}><Text style={[styles.chipText, form.type === item.id && styles.chipTextActive]}>{item.label}</Text></TouchableOpacity>)}</View>
              <Text style={styles.label}>Fecha (AAAA-MM-DD) *</Text>
              <TextInput style={styles.input} value={form.date} onChangeText={(value) => patchForm("date", value)} placeholder="2026-12-31" placeholderTextColor="#968F9E" maxLength={10} />
              <Text style={styles.label}>Hora (HH:MM) *</Text>
              <TextInput style={styles.input} value={form.time} onChangeText={(value) => patchForm("time", value)} placeholder="09:00" placeholderTextColor="#968F9E" maxLength={5} />
              <Text style={styles.label}>Repetición</Text>
              <View style={styles.chipRow}>
                {([{ id: "unica", label: "Una vez" }, { id: "recurrente", label: "Cada intervalo" }, { id: "dias_semana", label: "Días de semana" }] as const).map((item) => (
                  <TouchableOpacity key={item.id} style={[styles.chip, form.modality === item.id && styles.chipActive]} onPress={() => patchForm("modality", item.id)}>
                    <Text style={[styles.chipText, form.modality === item.id && styles.chipTextActive]}>{item.label}</Text>
                  </TouchableOpacity>
                ))}
              </View>
              {form.modality === "recurrente" ? (
                <View style={styles.intervalRow}>
                  <TextInput style={[styles.input, styles.intervalInput]} value={form.intervalValue} onChangeText={(value) => patchForm("intervalValue", value)} keyboardType="number-pad" />
                  <View style={styles.chipRow}>{intervalUnits.map((unit) => <TouchableOpacity key={unit} style={[styles.chip, form.intervalUnit === unit && styles.chipActive]} onPress={() => patchForm("intervalUnit", unit)}><Text style={[styles.chipText, form.intervalUnit === unit && styles.chipTextActive]}>{unit}</Text></TouchableOpacity>)}</View>
                </View>
              ) : null}
              {form.modality === "dias_semana" ? (
                <View style={styles.chipRow}>{weekDays.map((day) => {
                  const selected = form.weekdays.includes(day.id);
                  return <TouchableOpacity key={day.id} style={[styles.dayChip, selected && styles.chipActive]} onPress={() => patchForm("weekdays", selected ? form.weekdays.filter((value) => value !== day.id) : [...form.weekdays, day.id])}><Text style={[styles.chipText, selected && styles.chipTextActive]}>{day.label}</Text></TouchableOpacity>;
                })}</View>
              ) : null}
              {form.modality !== "unica" ? (
                <>
                  <Text style={styles.label}>Finaliza el (opcional, AAAA-MM-DD)</Text>
                  <TextInput style={styles.input} value={form.endDate} onChangeText={(value) => patchForm("endDate", value)} placeholder="2027-12-31" placeholderTextColor="#968F9E" maxLength={10} />
                </>
              ) : null}
              <TouchableOpacity style={[styles.save, saving && styles.disabled]} onPress={() => void saveReminder()} disabled={saving}>
                {saving ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.saveText}>{editingId ? "Guardar cambios" : "Crear recordatorio"}</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={styles.cancel} onPress={() => setModalVisible(false)} disabled={saving}><Text style={styles.cancelText}>Cancelar</Text></TouchableOpacity>
            </View>
          </ScrollView>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 },
  back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" },
  topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" },
  addTop: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" },
  content: { flexGrow: 1, padding: 20, paddingTop: 8, paddingBottom: 36 },
  intro: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 16 },
  introIcon: { width: 45, height: 45, borderRadius: 14, backgroundColor: "#FFFFFF", justifyContent: "center", alignItems: "center" },
  introTitle: { color: "#302A3B", fontSize: 14, fontWeight: "800" },
  introText: { color: "#6B6475", fontSize: 12, marginTop: 4 },
  errorBanner: { backgroundColor: "#FFF0F0", borderRadius: 13, padding: 12, marginBottom: 14 },
  errorText: { color: "#B63E49", fontSize: 12, lineHeight: 17 },
  retryText: { color: "#7C4DFF", fontSize: 12, fontWeight: "800", marginTop: 7 },
  tabs: { flexDirection: "row", backgroundColor: "#EEEAF2", padding: 4, borderRadius: 13, marginBottom: 18 },
  tab: { flex: 1, paddingVertical: 10, borderRadius: 10, alignItems: "center" },
  activeTab: { backgroundColor: "#FFFFFF" },
  tabText: { color: "#898290", fontSize: 13, fontWeight: "700" },
  activeTabText: { color: "#7C4DFF" },
  count: { color: "#827B8B", fontSize: 12, fontWeight: "700", marginBottom: 10 },
  reminder: { minHeight: 88, flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 12, marginBottom: 10 },
  reminderDone: { opacity: 0.74 },
  checkButton: { minWidth: 34, minHeight: 40, justifyContent: "center", alignItems: "center", marginRight: 7 },
  check: { width: 23, height: 23, borderRadius: 8, borderWidth: 1.5, borderColor: "#C9C2D3", alignItems: "center", justifyContent: "center" },
  checked: { backgroundColor: "#7C4DFF", borderColor: "#7C4DFF" },
  reminderInfo: { flex: 1 },
  reminderTitle: { color: "#292332", fontSize: 14, fontWeight: "800" },
  reminderType: { color: "#7C4DFF", fontSize: 11, fontWeight: "700", marginTop: 4 },
  strike: { color: "#97909D", textDecorationLine: "line-through" },
  reminderDetail: { color: "#857E8E", fontSize: 11, marginTop: 3 },
  reminderActions: { alignItems: "center", gap: 11, paddingLeft: 7 },
  date: { color: "#7C4DFF", fontSize: 10, fontWeight: "800" },
  doneDate: { color: "#98919E" },
  empty: { alignItems: "center", paddingVertical: 38, gap: 9 },
  emptyTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800", marginTop: 2, textAlign: "center" },
  emptyText: { color: "#837C8D", fontSize: 13, textAlign: "center" },
  createButton: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 13 },
  createText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" },
  sheetScroll: { flexGrow: 1, justifyContent: "flex-end" },
  sheet: { backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 21, paddingBottom: 34 },
  sheetHandle: { width: 38, height: 4, borderRadius: 2, alignSelf: "center", backgroundColor: "#D9D4DF", marginBottom: 17 },
  sheetTitle: { color: "#201B29", fontSize: 21, fontWeight: "800", marginBottom: 16 },
  label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8, marginTop: 3 },
  input: { minHeight: 49, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#201B29", fontSize: 14, marginBottom: 13 },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 13 },
  chip: { paddingHorizontal: 12, paddingVertical: 9, borderRadius: 12, backgroundColor: "#F3F0F6", borderWidth: 1, borderColor: "#F3F0F6" },
  chipActive: { backgroundColor: "#F4F0FF", borderColor: "#A88BFF" },
  chipText: { color: "#514A5A", fontSize: 12, fontWeight: "700" },
  chipTextActive: { color: "#7C4DFF" },
  intervalRow: { gap: 4 },
  intervalInput: { width: 85 },
  dayChip: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center", backgroundColor: "#F3F0F6", borderWidth: 1, borderColor: "#F3F0F6" },
  save: { height: 51, backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 10 },
  saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  cancel: { alignItems: "center", paddingTop: 15 },
  cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
  disabled: { opacity: 0.55 },
});
