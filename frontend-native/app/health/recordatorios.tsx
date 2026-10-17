import DateTimePicker, {
  type DateTimePickerEvent,
} from "@react-native-community/datetimepicker";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { useCallback, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAppData } from "../../context/app-data-context";
import {
  confirmReminder,
  createReminder,
  unconfirmReminder,
} from "../../services/api";
import {
  construirRequestRecordatorioUnico,
  tiposRecordatorio,
  type TipoRecordatorio,
} from "../../utils/recordatorios";

type PickerMode = "date" | "time" | null;

type FormErrors = {
  title?: string;
  pet?: string;
  dateTime?: string;
};

function defaultDateTime() {
  const date = new Date(Date.now() + 60 * 60 * 1000);
  date.setSeconds(0, 0);
  return date;
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date);
}

function formatTime(date: Date) {
  return new Intl.DateTimeFormat("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function getErrorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "No se pudo completar la operación. Intentá nuevamente.";
}

export default function RecordatoriosScreen() {
  const router = useRouter();
  const { mascotaId } = useLocalSearchParams<{ mascotaId?: string }>();
  const {
    pets,
    petsLoading,
    reminders,
    remindersLoading,
    remindersError,
    refreshReminders,
    addCreatedReminder,
  } = useAppData();

  const [filter, setFilter] = useState<"pending" | "done">("pending");
  const [modalVisible, setModalVisible] = useState(Boolean(mascotaId));
  const [title, setTitle] = useState("");
  const [selectedPetId, setSelectedPetId] = useState(mascotaId ?? "");
  const [type, setType] = useState<TipoRecordatorio>("OTRO");
  const [scheduledAt, setScheduledAt] = useState(defaultDateTime);
  const [pickerMode, setPickerMode] = useState<PickerMode>(null);
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      void refreshReminders().catch(() => undefined);
    }, [refreshReminders]),
  );

  const selectedPet = useMemo(
    () => pets.find((pet) => pet.id === selectedPetId)
      ?? pets.find((pet) => pet.id === mascotaId)
      ?? (pets.length === 1 ? pets[0] : undefined),
    [mascotaId, pets, selectedPetId],
  );

  const visibleReminders = useMemo(
    () => reminders.filter((reminder) => reminder.done === (filter === "done")),
    [filter, reminders],
  );

  const canCreateReminder = pets.length > 0 && !petsLoading;

  const toggleReminder = async (id: string) => {
    const reminder = reminders.find((item) => item.id === id);
    if (!reminder || updatingId) return;

    setUpdatingId(id);
    try {
      if (reminder.done) await unconfirmReminder(id);
      else await confirmReminder(id);
      await refreshReminders();
    } catch (error) {
      Alert.alert("No se pudo actualizar", getErrorMessage(error));
    } finally {
      setUpdatingId(null);
    }
  };

  const openForm = () => {
    if (!canCreateReminder) return;
    if (!selectedPet) setSelectedPetId(pets.length === 1 ? pets[0].id : "");
    setFormErrors({});
    setFormError(null);
    setModalVisible(true);
  };

  const closeForm = () => {
    if (saving) return;
    setPickerMode(null);
    setModalVisible(false);
  };

  const openPetForm = () => {
    if (saving) return;
    setModalVisible(false);
    router.push("/pets/mascota-formulario");
  };

  const resetForm = () => {
    setTitle("");
    setType("OTRO");
    setScheduledAt(defaultDateTime());
    setPickerMode(null);
    setFormErrors({});
    setFormError(null);
    setSelectedPetId(
      mascotaId && pets.some((pet) => pet.id === mascotaId)
        ? mascotaId
        : pets.length === 1
          ? pets[0].id
          : "",
    );
  };

  const validateForm = () => {
    const errors: FormErrors = {};
    const normalizedTitle = title.trim();

    if (!normalizedTitle) {
      errors.title = "El título es obligatorio.";
    } else if (normalizedTitle.length > 150) {
      errors.title = "El título no puede superar los 150 caracteres.";
    }

    if (!selectedPet) {
      errors.pet = "Seleccioná una mascota registrada.";
    }

    if (Number.isNaN(scheduledAt.getTime())) {
      errors.dateTime = "Seleccioná una fecha y hora válidas.";
    } else if (scheduledAt.getTime() <= Date.now()) {
      errors.dateTime = "La fecha y hora deben ser futuras.";
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleDateTimeChange = (
    event: DateTimePickerEvent,
    selectedValue?: Date,
  ) => {
    const currentMode = pickerMode;
    if (Platform.OS === "android") setPickerMode(null);
    if (event.type !== "set" || !selectedValue || !currentMode) return;

    setScheduledAt((current) => {
      const next = new Date(current);
      if (currentMode === "date") {
        next.setFullYear(
          selectedValue.getFullYear(),
          selectedValue.getMonth(),
          selectedValue.getDate(),
        );
      } else {
        next.setHours(selectedValue.getHours(), selectedValue.getMinutes(), 0, 0);
      }
      return next;
    });
    setFormErrors((current) => ({ ...current, dateTime: undefined }));
  };

  const addReminder = async () => {
    if (saving || !validateForm() || !selectedPet) return;

    setSaving(true);
    setFormError(null);

    let createdReminder;
    try {
      const request = construirRequestRecordatorioUnico({
        titulo: title,
        tipo: type,
        fechaHoraInicio: scheduledAt,
      });
      createdReminder = await createReminder(selectedPet.id, request);
    } catch (error) {
      setFormError(getErrorMessage(error));
      setSaving(false);
      return;
    }

    addCreatedReminder(createdReminder, selectedPet.name);
    resetForm();
    setModalVisible(false);
    setFilter("pending");

    try {
      await refreshReminders();
      Alert.alert("Recordatorio creado", "Se guardó correctamente.");
    } catch {
      Alert.alert(
        "Recordatorio creado",
        "Se creó correctamente, pero no se pudo actualizar la lista completa. El nuevo recordatorio ya se agregó localmente.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#30293A" />
        </TouchableOpacity>
        <Text style={styles.topTitle} numberOfLines={2}>Recordatorios</Text>
        <TouchableOpacity
          style={[styles.addTop, !canCreateReminder && styles.disabled]}
          onPress={openForm}
          disabled={!canCreateReminder}
        >
          <Ionicons name="add" size={24} color="#7C4DFF" />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.intro}>
          <View style={styles.introIcon}>
            <Ionicons name="alarm-outline" size={25} color="#7C4DFF" />
          </View>
          <View style={styles.introContent}>
            <Text style={styles.introTitle}>Nunca te olvides de un cuidado</Text>
            <Text style={styles.introText}>Organizá vacunas, tratamientos y controles.</Text>
          </View>
        </View>

        {!petsLoading && !pets.length ? (
          <View style={styles.noPetsCard}>
            <Ionicons name="paw-outline" size={30} color="#7C4DFF" />
            <Text style={styles.noPetsTitle}>Primero registrá una mascota</Text>
            <Text style={styles.noPetsText}>
              Los recordatorios deben quedar asociados a una mascota existente.
            </Text>
            <TouchableOpacity style={styles.noPetsAction} onPress={openPetForm}>
              <Text style={styles.noPetsActionText}>Registrar mascota</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        <View style={styles.tabs}>
          <TouchableOpacity style={[styles.tab, filter === "pending" && styles.activeTab]} onPress={() => setFilter("pending")}>
            <Text style={[styles.tabText, filter === "pending" && styles.activeTabText]}>Pendientes</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.tab, filter === "done" && styles.activeTab]} onPress={() => setFilter("done")}>
            <Text style={[styles.tabText, filter === "done" && styles.activeTabText]}>Completados</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.count}>
          {visibleReminders.length} {filter === "pending" ? "pendientes" : "completados"}
        </Text>

        {remindersLoading && !reminders.length ? (
          <ActivityIndicator color="#7C4DFF" style={styles.loading} />
        ) : null}

        {remindersError ? (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{remindersError}</Text>
            <TouchableOpacity onPress={() => void refreshReminders().catch(() => undefined)}>
              <Text style={styles.retryText}>Reintentar</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        {visibleReminders.map((reminder) => (
          <TouchableOpacity
            key={reminder.id}
            disabled={updatingId === reminder.id}
            style={[
              styles.reminder,
              reminder.done && styles.reminderDone,
              updatingId === reminder.id && styles.disabled,
            ]}
            onPress={() => void toggleReminder(reminder.id)}
          >
            <View style={[styles.check, reminder.done && styles.checked]}>
              {reminder.done && <Ionicons name="checkmark" size={16} color="#FFFFFF" />}
            </View>
            <View style={styles.reminderInfo}>
              <Text style={[styles.reminderTitle, reminder.done && styles.strike]}>{reminder.title}</Text>
              <Text style={styles.reminderDetail}>{reminder.pet} · {reminder.detail}</Text>
            </View>
            <Text style={[styles.date, reminder.done && styles.doneDate]}>{reminder.date}</Text>
          </TouchableOpacity>
        ))}

        {!remindersLoading && !remindersError && !visibleReminders.length ? (
          <View style={styles.empty}>
            <Ionicons name="checkmark-done-outline" size={36} color="#7C4DFF" />
            <Text style={styles.emptyTitle}>No hay recordatorios</Text>
            <Text style={styles.emptyText}>Todo está al día por ahora.</Text>
          </View>
        ) : null}

        <TouchableOpacity
          style={[styles.createButton, !canCreateReminder && styles.disabled]}
          onPress={openForm}
          disabled={!canCreateReminder}
        >
          <Ionicons name="add-circle-outline" size={22} color="#FFFFFF" />
          <Text style={styles.createText}>
            {petsLoading ? "Cargando mascotas..." : "Crear recordatorio"}
          </Text>
        </TouchableOpacity>
      </ScrollView>

      <Modal
        visible={modalVisible}
        transparent
        animationType="slide"
        onRequestClose={closeForm}
      >
        <KeyboardAvoidingView
          style={styles.overlay}
          behavior={Platform.OS === "ios" ? "padding" : "height"}
        >
          <View style={styles.sheet}>
            <ScrollView
              contentContainerStyle={styles.sheetContent}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
            >
              <Text style={styles.sheetTitle}>Nuevo recordatorio</Text>

              {petsLoading ? (
                <View style={styles.modalLoading}>
                  <ActivityIndicator color="#7C4DFF" />
                  <Text style={styles.modalLoadingText}>Cargando mascotas...</Text>
                </View>
              ) : !pets.length ? (
                <View style={styles.modalEmpty}>
                  <Ionicons name="paw-outline" size={34} color="#7C4DFF" />
                  <Text style={styles.modalEmptyTitle}>No hay mascotas registradas</Text>
                  <Text style={styles.modalEmptyText}>
                    Registrá una mascota antes de crear un recordatorio.
                  </Text>
                  <TouchableOpacity style={styles.modalPetAction} onPress={openPetForm}>
                    <Text style={styles.modalPetActionText}>Registrar mascota</Text>
                  </TouchableOpacity>
                </View>
              ) : (
                <>
                  <Text style={styles.label}>¿Qué necesitás recordar?</Text>
                  <TextInput
                    style={[styles.input, formErrors.title && styles.invalidField]}
                    value={title}
                    onChangeText={(value) => {
                      setTitle(value);
                      setFormErrors((current) => ({ ...current, title: undefined }));
                      setFormError(null);
                    }}
                    placeholder="Ej. Dar medicamento"
                    placeholderTextColor="#968F9E"
                    maxLength={150}
                    editable={!saving}
                  />
                  <View style={styles.fieldMeta}>
                    <Text style={styles.inlineError}>{formErrors.title ?? ""}</Text>
                    <Text style={styles.characterCount}>{title.length}/150</Text>
                  </View>

                  <Text style={styles.label}>Mascota</Text>
                  <View style={[styles.optionList, formErrors.pet && styles.invalidField]}>
                    {pets.map((pet) => {
                      const selected = selectedPet?.id === pet.id;
                      return (
                        <TouchableOpacity
                          key={pet.id}
                          accessibilityRole="button"
                          accessibilityState={{ selected }}
                          style={[styles.optionRow, selected && styles.optionRowSelected]}
                          onPress={() => {
                            setSelectedPetId(pet.id);
                            setFormErrors((current) => ({ ...current, pet: undefined }));
                          }}
                          disabled={saving}
                        >
                          <View style={styles.optionTextBox}>
                            <Text style={[styles.optionTitle, selected && styles.optionTitleSelected]}>
                              {pet.name}
                            </Text>
                            <Text style={styles.optionSubtitle}>
                              {pet.species}{pet.breed ? ` · ${pet.breed}` : ""}
                            </Text>
                          </View>
                          <Ionicons
                            name={selected ? "checkmark-circle" : "ellipse-outline"}
                            size={21}
                            color={selected ? "#7C4DFF" : "#B3ACB9"}
                          />
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                  {formErrors.pet ? <Text style={styles.inlineErrorStandalone}>{formErrors.pet}</Text> : null}

                  <Text style={styles.label}>Tipo</Text>
                  <View style={styles.optionList}>
                    {tiposRecordatorio.map((reminderType) => {
                      const selected = type === reminderType.value;
                      return (
                        <TouchableOpacity
                          key={reminderType.value}
                          accessibilityRole="button"
                          accessibilityState={{ selected }}
                          style={[styles.optionRow, selected && styles.optionRowSelected]}
                          onPress={() => setType(reminderType.value)}
                          disabled={saving}
                        >
                          <View style={styles.optionTextBox}>
                            <Text style={[styles.optionTitle, selected && styles.optionTitleSelected]}>
                              {reminderType.label}
                            </Text>
                          </View>
                          <Ionicons
                            name={selected ? "checkmark-circle" : "ellipse-outline"}
                            size={21}
                            color={selected ? "#7C4DFF" : "#B3ACB9"}
                          />
                        </TouchableOpacity>
                      );
                    })}
                  </View>

                  <Text style={styles.label}>Modalidad</Text>
                  <View style={styles.readOnlyField}>
                    <Ionicons name="radio-button-on-outline" size={20} color="#7C4DFF" />
                    <Text style={styles.readOnlyText}>Único</Text>
                  </View>

                  <Text style={styles.label}>Fecha y hora</Text>
                  <View style={styles.dateTimeRow}>
                    <TouchableOpacity
                      accessibilityRole="button"
                      accessibilityLabel="Seleccionar fecha"
                      style={[styles.dateTimeButton, styles.dateButton, formErrors.dateTime && styles.invalidField]}
                      onPress={() => setPickerMode("date")}
                      disabled={saving}
                    >
                      <Ionicons name="calendar-outline" size={20} color="#7C4DFF" />
                      <View style={styles.dateTimeTextBox}>
                        <Text style={styles.dateTimeCaption}>Fecha</Text>
                        <Text style={styles.dateTimeValue}>{formatDate(scheduledAt)}</Text>
                      </View>
                    </TouchableOpacity>

                    <TouchableOpacity
                      accessibilityRole="button"
                      accessibilityLabel="Seleccionar hora"
                      style={[styles.dateTimeButton, styles.timeButton, formErrors.dateTime && styles.invalidField]}
                      onPress={() => setPickerMode("time")}
                      disabled={saving}
                    >
                      <Ionicons name="time-outline" size={20} color="#7C4DFF" />
                      <View style={styles.dateTimeTextBox}>
                        <Text style={styles.dateTimeCaption}>Hora</Text>
                        <Text style={styles.dateTimeValue}>{formatTime(scheduledAt)}</Text>
                      </View>
                    </TouchableOpacity>
                  </View>
                  {formErrors.dateTime ? (
                    <Text style={styles.inlineErrorStandalone}>{formErrors.dateTime}</Text>
                  ) : null}

                  {pickerMode ? (
                    <View style={styles.pickerPanel}>
                      <DateTimePicker
                        value={scheduledAt}
                        mode={pickerMode}
                        display={Platform.OS === "ios" ? "spinner" : "default"}
                        minimumDate={pickerMode === "date" ? new Date() : undefined}
                        is24Hour
                        onChange={handleDateTimeChange}
                      />
                      {Platform.OS === "ios" ? (
                        <TouchableOpacity style={styles.pickerDone} onPress={() => setPickerMode(null)}>
                          <Text style={styles.pickerDoneText}>Listo</Text>
                        </TouchableOpacity>
                      ) : null}
                    </View>
                  ) : null}

                  {formError ? (
                    <View style={styles.formErrorBox}>
                      <Text style={styles.formErrorText}>{formError}</Text>
                    </View>
                  ) : null}

                  <TouchableOpacity
                    style={[styles.save, saving && styles.disabled]}
                    onPress={() => void addReminder()}
                    disabled={saving}
                  >
                    {saving ? <ActivityIndicator color="#FFFFFF" size="small" /> : null}
                    <Text style={styles.saveText}>{saving ? "Creando..." : "Crear recordatorio"}</Text>
                  </TouchableOpacity>
                </>
              )}

              <TouchableOpacity style={styles.cancel} onPress={closeForm} disabled={saving}>
                <Text style={[styles.cancelText, saving && styles.disabledText]}>Cancelar</Text>
              </TouchableOpacity>
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { minHeight: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20, paddingVertical: 10, gap: 12 },
  back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" },
  topTitle: { flex: 1, minWidth: 0, color: "#201B29", fontSize: 17, fontWeight: "800", textAlign: "center" },
  addTop: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" },
  content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  intro: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 23 },
  introIcon: { width: 45, height: 45, borderRadius: 14, backgroundColor: "#FFFFFF", justifyContent: "center", alignItems: "center" },
  introContent: { flex: 1, minWidth: 0 },
  introTitle: { color: "#302A3B", fontSize: 14, fontWeight: "800", flexShrink: 1 },
  introText: { color: "#6B6475", fontSize: 12, marginTop: 4, flexShrink: 1 },
  noPetsCard: { alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 18, marginBottom: 18 },
  noPetsTitle: { width: "100%", color: "#302A3B", fontSize: 15, lineHeight: 21, fontWeight: "800", marginTop: 9, textAlign: "center" },
  noPetsText: { width: "100%", color: "#817A89", fontSize: 12, lineHeight: 18, marginTop: 5, textAlign: "center" },
  noPetsAction: { minHeight: 42, justifyContent: "center", marginTop: 13, paddingHorizontal: 18, paddingVertical: 9, borderRadius: 12, backgroundColor: "#EEE8FF" },
  noPetsActionText: { color: "#7C4DFF", fontSize: 13, fontWeight: "800", textAlign: "center" },
  tabs: { flexDirection: "row", backgroundColor: "#EEEAF2", padding: 4, borderRadius: 13, marginBottom: 18 },
  tab: { flex: 1, paddingVertical: 10, borderRadius: 10, alignItems: "center" },
  activeTab: { backgroundColor: "#FFFFFF" },
  tabText: { color: "#898290", fontSize: 13, fontWeight: "700" },
  activeTabText: { color: "#7C4DFF" },
  count: { color: "#827B8B", fontSize: 12, fontWeight: "700", marginBottom: 10 },
  reminder: { minHeight: 78, flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 14, marginBottom: 10 },
  reminderDone: { opacity: 0.74 },
  check: { width: 23, height: 23, borderRadius: 8, borderWidth: 1.5, borderColor: "#C9C2D3", alignItems: "center", justifyContent: "center", marginRight: 11 },
  checked: { backgroundColor: "#7C4DFF", borderColor: "#7C4DFF" },
  reminderInfo: { flex: 1, minWidth: 0 },
  reminderTitle: { color: "#292332", fontSize: 14, fontWeight: "800", flexShrink: 1 },
  strike: { color: "#97909E", textDecorationLine: "line-through" },
  reminderDetail: { color: "#857E8E", fontSize: 12, marginTop: 4, flexShrink: 1 },
  date: { color: "#7C4DFF", fontSize: 11, fontWeight: "800", flexShrink: 1, marginLeft: 8, textAlign: "right" },
  doneDate: { color: "#98919E" },
  empty: { alignItems: "center", paddingVertical: 45 },
  emptyTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800", marginTop: 10 },
  emptyText: { color: "#837C8D", fontSize: 13, marginTop: 5 },
  createButton: { minHeight: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 13, paddingHorizontal: 16, paddingVertical: 12 },
  createText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800", textAlign: "center" },
  loading: { marginVertical: 30 },
  errorBox: { backgroundColor: "#FFF4F4", borderRadius: 14, padding: 14, marginBottom: 12 },
  errorText: { color: "#9F3540", fontSize: 13 },
  retryText: { color: "#7C4DFF", fontSize: 13, fontWeight: "800", marginTop: 8 },
  disabled: { opacity: 0.55 },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" },
  sheet: { maxHeight: "92%", backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, overflow: "hidden" },
  sheetContent: { padding: 21, paddingBottom: 34 },
  sheetTitle: { width: "100%", color: "#201B29", fontSize: 21, lineHeight: 28, fontWeight: "800", marginBottom: 21 },
  label: { width: "100%", color: "#302A3B", fontSize: 13, lineHeight: 18, fontWeight: "800", marginBottom: 8, marginTop: 2 },
  input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, paddingVertical: 8, color: "#201B29", fontSize: 14 },
  invalidField: { borderColor: "#D8515D" },
  fieldMeta: { minHeight: 24, flexDirection: "row", alignItems: "flex-start", marginTop: 4, marginBottom: 8 },
  inlineError: { flex: 1, minWidth: 0, color: "#B13F4A", fontSize: 11, lineHeight: 16, paddingRight: 8 },
  inlineErrorStandalone: { width: "100%", color: "#B13F4A", fontSize: 11, lineHeight: 16, marginTop: 5, marginBottom: 10 },
  characterCount: { flexShrink: 0, color: "#918A98", fontSize: 11, lineHeight: 16 },
  optionList: { borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, overflow: "hidden", marginBottom: 16 },
  optionRow: { minHeight: 50, flexDirection: "row", alignItems: "center", paddingHorizontal: 13, paddingVertical: 9, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: "#EEEAF2" },
  optionRowSelected: { backgroundColor: "#F4F0FF" },
  optionTextBox: { flexGrow: 1, flexBasis: 0, minWidth: 0, paddingRight: 10 },
  optionTitle: { width: "100%", color: "#4A4351", fontSize: 13, lineHeight: 18, fontWeight: "700" },
  optionTitleSelected: { color: "#7C4DFF", fontWeight: "800" },
  optionSubtitle: { width: "100%", color: "#8C8593", fontSize: 11, lineHeight: 16, marginTop: 2 },
  readOnlyField: { minHeight: 48, flexDirection: "row", alignItems: "center", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, backgroundColor: "#F8F6FA", paddingHorizontal: 14, marginBottom: 16 },
  readOnlyText: { color: "#4A4351", fontSize: 14, fontWeight: "700", marginLeft: 9 },
  dateTimeRow: { flexDirection: "row", marginBottom: 2 },
  dateTimeButton: { flexGrow: 1, flexBasis: 0, minWidth: 0, minHeight: 62, flexDirection: "row", alignItems: "center", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 12, paddingVertical: 9 },
  dateButton: { marginRight: 5 },
  timeButton: { marginLeft: 5 },
  dateTimeTextBox: { flexGrow: 1, flexBasis: 0, minWidth: 0, marginLeft: 8 },
  dateTimeCaption: { width: "100%", color: "#8C8593", fontSize: 10, lineHeight: 14, fontWeight: "700" },
  dateTimeValue: { width: "100%", color: "#302A3B", fontSize: 13, lineHeight: 18, fontWeight: "800", marginTop: 2 },
  pickerPanel: { marginBottom: 12 },
  pickerDone: { minHeight: 40, alignItems: "flex-end", justifyContent: "center", paddingHorizontal: 8 },
  pickerDoneText: { color: "#7C4DFF", fontSize: 14, fontWeight: "800" },
  formErrorBox: { backgroundColor: "#FFF4F4", borderRadius: 12, padding: 12, marginBottom: 12 },
  formErrorText: { color: "#9F3540", fontSize: 12, lineHeight: 18 },
  save: { minHeight: 51, flexDirection: "row", backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 2, paddingHorizontal: 16, paddingVertical: 12 },
  saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800", textAlign: "center", marginLeft: 8 },
  cancel: { alignItems: "center", paddingTop: 15, paddingBottom: 4 },
  cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
  disabledText: { opacity: 0.55 },
  modalLoading: { minHeight: 160, alignItems: "center", justifyContent: "center" },
  modalLoadingText: { color: "#817A89", fontSize: 13, marginTop: 10 },
  modalEmpty: { alignItems: "center", paddingVertical: 20 },
  modalEmptyTitle: { width: "100%", color: "#302A3B", fontSize: 16, lineHeight: 22, fontWeight: "800", marginTop: 10, textAlign: "center" },
  modalEmptyText: { width: "100%", color: "#817A89", fontSize: 13, lineHeight: 19, marginTop: 5, textAlign: "center" },
  modalPetAction: { minHeight: 46, justifyContent: "center", backgroundColor: "#7C4DFF", borderRadius: 13, paddingHorizontal: 18, paddingVertical: 10, marginTop: 16 },
  modalPetActionText: { color: "#FFFFFF", fontSize: 14, fontWeight: "800", textAlign: "center" },
});
