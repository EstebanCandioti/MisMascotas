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
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAppData } from "../../context/app-data-context";
import { confirmReminder, createReminder, unconfirmReminder } from "../../services/api";
import { construirRequestRecordatorio } from "../../utils/recordatorios";

function defaultDateTime() {
  const date = new Date(Date.now() + 60 * 60 * 1000);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16).replace("T", " ");
}

export default function RecordatoriosScreen() {
  const router = useRouter();
  const { mascota, mascotaId } = useLocalSearchParams<{ mascota?: string; mascotaId?: string }>();
  const [filter, setFilter] = useState<"pending" | "done">("pending");
  const [modalVisible, setModalVisible] = useState(Boolean(mascota));
  const [title, setTitle] = useState("");
  const [pet, setPet] = useState(mascota ?? "");
  const [dateTime, setDateTime] = useState(defaultDateTime);
  const [saving, setSaving] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const { pets, reminders, remindersLoading, remindersError, refreshReminders } = useAppData();

  useFocusEffect(
    useCallback(() => {
      void refreshReminders().catch(() => undefined);
    }, [refreshReminders]),
  );

  const visibleReminders = useMemo(
    () => reminders.filter((reminder) => reminder.done === (filter === "done")),
    [filter, reminders],
  );

  const toggleReminder = async (id: string) => {
    const reminder = reminders.find((item) => item.id === id);
    if (!reminder || updatingId) return;

    setUpdatingId(id);
    try {
      if (reminder.done) await unconfirmReminder(id);
      else await confirmReminder(id);
      await refreshReminders();
    } catch (error) {
      Alert.alert("No se pudo actualizar", error instanceof Error ? error.message : "Intentá nuevamente.");
    } finally {
      setUpdatingId(null);
    }
  };

  const addReminder = async () => {
    if (!title.trim()) {
      Alert.alert("Falta el título", "Escribí qué necesitás recordar.");
      return;
    }

    const normalizedPetName = pet.trim().toLowerCase();
    const selectedPet = pets.find((item) => (
      item.name.toLowerCase() === normalizedPetName
      || (!normalizedPetName && item.id === mascotaId)
    ));
    if (!selectedPet) {
      Alert.alert("Mascota no encontrada", "Ingresá el nombre de una mascota registrada.");
      return;
    }

    const [date = "", time = ""] = dateTime.trim().split(/\s+/);
    const start = new Date(`${date}T${time}:00`);
    if (Number.isNaN(start.getTime())) {
      Alert.alert("Fecha inválida", "Usá el formato AAAA-MM-DD HH:mm.");
      return;
    }

    setSaving(true);
    try {
      const request = construirRequestRecordatorio({
        titulo: title.trim(),
        tipo: "OTRO",
        fecha: date,
        hora: time,
        modalidad: "unica",
      });
      await createReminder(selectedPet.id, { ...request, mascotaId: selectedPet.id });
      await refreshReminders();
      setTitle("");
      setDateTime(defaultDateTime());
      setModalVisible(false);
      setFilter("pending");
      Alert.alert("Recordatorio creado", "Se guardó correctamente.");
    } catch (error) {
      Alert.alert("No se pudo crear", error instanceof Error ? error.message : "Intentá nuevamente.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
        <Text style={styles.topTitle} numberOfLines={2}>Recordatorios</Text>
        <TouchableOpacity style={styles.addTop} onPress={() => setModalVisible(true)}><Ionicons name="add" size={24} color="#7C4DFF" /></TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.intro}><View style={styles.introIcon}><Ionicons name="alarm-outline" size={25} color="#7C4DFF" /></View><View style={styles.introContent}><Text style={styles.introTitle}>Nunca te olvides de un cuidado</Text><Text style={styles.introText}>Organizá vacunas, tratamientos y controles.</Text></View></View>
        <View style={styles.tabs}><TouchableOpacity style={[styles.tab, filter === "pending" && styles.activeTab]} onPress={() => setFilter("pending")}><Text style={[styles.tabText, filter === "pending" && styles.activeTabText]}>Pendientes</Text></TouchableOpacity><TouchableOpacity style={[styles.tab, filter === "done" && styles.activeTab]} onPress={() => setFilter("done")}><Text style={[styles.tabText, filter === "done" && styles.activeTabText]}>Completados</Text></TouchableOpacity></View>
        <Text style={styles.count}>{visibleReminders.length} {filter === "pending" ? "pendientes" : "completados"}</Text>
        {remindersLoading && !reminders.length ? <ActivityIndicator color="#7C4DFF" style={styles.loading} /> : null}
        {remindersError ? <View style={styles.errorBox}><Text style={styles.errorText}>{remindersError}</Text><TouchableOpacity onPress={() => void refreshReminders().catch(() => undefined)}><Text style={styles.retryText}>Reintentar</Text></TouchableOpacity></View> : null}
        {visibleReminders.map((reminder) => <TouchableOpacity key={reminder.id} disabled={updatingId === reminder.id} style={[styles.reminder, reminder.done && styles.reminderDone, updatingId === reminder.id && styles.disabled]} onPress={() => void toggleReminder(reminder.id)}><View style={[styles.check, reminder.done && styles.checked]}>{reminder.done && <Ionicons name="checkmark" size={16} color="#FFFFFF" />}</View><View style={styles.reminderInfo}><Text style={[styles.reminderTitle, reminder.done && styles.strike]}>{reminder.title}</Text><Text style={styles.reminderDetail}>{reminder.pet} · {reminder.detail}</Text></View><Text style={[styles.date, reminder.done && styles.doneDate]}>{reminder.date}</Text></TouchableOpacity>)}
        {!remindersLoading && !remindersError && !visibleReminders.length && <View style={styles.empty}><Ionicons name="checkmark-done-outline" size={36} color="#7C4DFF" /><Text style={styles.emptyTitle}>No hay recordatorios</Text><Text style={styles.emptyText}>Todo está al día por ahora.</Text></View>}
        <TouchableOpacity style={styles.createButton} onPress={() => setModalVisible(true)}><Ionicons name="add-circle-outline" size={22} color="#FFFFFF" /><Text style={styles.createText}>Crear recordatorio</Text></TouchableOpacity>
      </ScrollView>

      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <KeyboardAvoidingView style={styles.overlay} behavior={Platform.OS === "ios" ? "padding" : "height"}>
          <View style={styles.sheet}>
            <ScrollView contentContainerStyle={styles.sheetContent} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
              <View style={styles.sheetHandle} />
              <Text style={styles.sheetTitle}>Nuevo recordatorio</Text>
              <Text style={styles.label}>¿Qué necesitás recordar?</Text>
              <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="Ej. Dar medicamento" placeholderTextColor="#968F9E" />
              <Text style={styles.label}>Mascota</Text>
              <TextInput style={styles.input} value={pet} onChangeText={setPet} placeholder={pets[0]?.name ?? "Sin mascotas registradas"} placeholderTextColor="#968F9E" />
              <Text style={styles.label}>Fecha y hora</Text>
              <TextInput style={styles.input} value={dateTime} onChangeText={setDateTime} placeholder="AAAA-MM-DD HH:mm" placeholderTextColor="#968F9E" />
              <TouchableOpacity style={[styles.save, saving && styles.disabled]} onPress={() => void addReminder()} disabled={saving}><Text style={styles.saveText}>{saving ? "Creando..." : "Crear recordatorio"}</Text></TouchableOpacity>
              <TouchableOpacity style={styles.cancel} onPress={() => setModalVisible(false)} disabled={saving}><Text style={styles.cancelText}>Cancelar</Text></TouchableOpacity>
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { minHeight: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20, paddingVertical: 10, gap: 12 }, back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" }, topTitle: { flex: 1, minWidth: 0, color: "#201B29", fontSize: 17, fontWeight: "800", textAlign: "center" }, addTop: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" }, content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  intro: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 23 }, introIcon: { width: 45, height: 45, borderRadius: 14, backgroundColor: "#FFFFFF", justifyContent: "center", alignItems: "center" }, introContent: { flex: 1, minWidth: 0 }, introTitle: { color: "#302A3B", fontSize: 14, fontWeight: "800", flexShrink: 1 }, introText: { color: "#6B6475", fontSize: 12, marginTop: 4, flexShrink: 1 },
  tabs: { flexDirection: "row", backgroundColor: "#EEEAF2", padding: 4, borderRadius: 13, marginBottom: 18 }, tab: { flex: 1, paddingVertical: 10, borderRadius: 10, alignItems: "center" }, activeTab: { backgroundColor: "#FFFFFF" }, tabText: { color: "#898290", fontSize: 13, fontWeight: "700" }, activeTabText: { color: "#7C4DFF" }, count: { color: "#827B8B", fontSize: 12, fontWeight: "700", marginBottom: 10 },
  reminder: { minHeight: 78, flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 14, marginBottom: 10 }, reminderDone: { opacity: 0.74 }, check: { width: 23, height: 23, borderRadius: 8, borderWidth: 1.5, borderColor: "#C9C2D3", alignItems: "center", justifyContent: "center", marginRight: 11 }, checked: { backgroundColor: "#7C4DFF", borderColor: "#7C4DFF" }, reminderInfo: { flex: 1, minWidth: 0 }, reminderTitle: { color: "#292332", fontSize: 14, fontWeight: "800", flexShrink: 1 }, strike: { color: "#97909D", textDecorationLine: "line-through" }, reminderDetail: { color: "#857E8E", fontSize: 12, marginTop: 4, flexShrink: 1 }, date: { color: "#7C4DFF", fontSize: 11, fontWeight: "800", flexShrink: 1, marginLeft: 8, textAlign: "right" }, doneDate: { color: "#98919E" }, empty: { alignItems: "center", paddingVertical: 45 }, emptyTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800", marginTop: 10 }, emptyText: { color: "#837C8D", fontSize: 13, marginTop: 5 },
  createButton: { minHeight: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 13, paddingHorizontal: 16, paddingVertical: 12 }, createText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800", textAlign: "center" },
  loading: { marginVertical: 30 }, errorBox: { backgroundColor: "#FFF4F4", borderRadius: 14, padding: 14, marginBottom: 12 }, errorText: { color: "#9F3540", fontSize: 13 }, retryText: { color: "#7C4DFF", fontSize: 13, fontWeight: "800", marginTop: 8 }, disabled: { opacity: 0.6 },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" }, sheet: { maxHeight: "90%", backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, overflow: "hidden" }, sheetContent: { padding: 21, paddingBottom: 34 }, sheetHandle: { width: 38, height: 4, borderRadius: 2, alignSelf: "center", backgroundColor: "#D9D4DF", marginBottom: 17 }, sheetTitle: { color: "#201B29", fontSize: 21, fontWeight: "800", marginBottom: 21, flexShrink: 1 }, label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 }, input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, paddingVertical: 8, color: "#201B29", fontSize: 14, marginBottom: 16 }, save: { minHeight: 51, backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 2, paddingHorizontal: 16, paddingVertical: 12 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800", textAlign: "center" }, cancel: { alignItems: "center", paddingTop: 15, paddingBottom: 4 }, cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
});
