import { useState } from "react";
import {
  Alert,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData, type ClinicalEvent } from "../../context/app-data-context";

const eventTypes = [
  { id: "CONSULTA", label: "Consulta", icon: "medkit-outline" as const },
  { id: "VACUNA", label: "Vacuna", icon: "shield-checkmark-outline" as const },
  { id: "MEDICACION", label: "Medicación", icon: "medical-outline" as const },
  { id: "PESO", label: "Peso", icon: "fitness-outline" as const },
  { id: "OTRO", label: "Otro", icon: "document-text-outline" as const },
];

function todayAsIsoDate() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function toDateInput(value?: string) {
  if (!value) return todayAsIsoDate();
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;

  const legacyDate = value.match(/^(\d{1,2})\s+([A-ZÁÉÍÓÚ]+)\s+(\d{4})$/i);
  if (!legacyDate) return todayAsIsoDate();
  const monthNumbers: Record<string, string> = {
    ENE: "01",
    FEB: "02",
    MAR: "03",
    ABR: "04",
    MAY: "05",
    JUN: "06",
    JUL: "07",
    AGO: "08",
    SEP: "09",
    OCT: "10",
    NOV: "11",
    DIC: "12",
  };
  const month = monthNumbers[legacyDate[2].toLocaleUpperCase("es-AR").slice(0, 3)];
  return month
    ? `${legacyDate[3]}-${month}-${legacyDate[1].padStart(2, "0")}`
    : todayAsIsoDate();
}

function isValidIsoDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

function isValidHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

export default function RegistrarEventoScreen() {
  const router = useRouter();
  const { mascotaId, mascota, eventoId } = useLocalSearchParams<{
    mascotaId?: string;
    mascota?: string;
    eventoId?: string;
  }>();
  const { pets, events, setEvents } = useAppData();
  const pet = pets.find((item) => item.id === mascotaId)
    ?? pets.find((item) => item.name === mascota);
  const eventToEdit = events.find((event) =>
    event.id === eventoId
    && pet
    && (event.petId ? event.petId === pet.id : event.pet === pet.name),
  );
  const initialType = eventToEdit
    ? eventTypes.find((item) => item.id === eventToEdit.type.toUpperCase()
      || item.label.toLocaleUpperCase("es-AR") === eventToEdit.type.toLocaleUpperCase("es-AR"))?.id ?? "OTRO"
    : "CONSULTA";
  const [type, setType] = useState(initialType);
  const [title, setTitle] = useState(eventToEdit?.title ?? "");
  const [date, setDate] = useState(toDateInput(eventToEdit?.date));
  const [dose, setDose] = useState(eventToEdit?.dose ?? "");
  const [reason, setReason] = useState(eventToEdit?.reason ?? "");
  const [diagnosis, setDiagnosis] = useState(eventToEdit?.diagnosis ?? "");
  const [weight, setWeight] = useState(eventToEdit?.weight?.toString() ?? "");
  const [observations, setObservations] = useState(eventToEdit?.observations ?? eventToEdit?.detail ?? "");
  const [attachmentUrl, setAttachmentUrl] = useState(eventToEdit?.attachmentUrl ?? "");
  const [attachmentFormat, setAttachmentFormat] = useState(eventToEdit?.attachmentFormat ?? "");

  const saveEvent = () => {
    if (!pet) {
      Alert.alert("Mascota no encontrada", "Volvé al detalle de una mascota para registrar su evento clínico.");
      return;
    }
    if (!title.trim()) {
      Alert.alert("Falta el nombre", "Ingresá el nombre del evento clínico.");
      return;
    }
    if (!isValidIsoDate(date)) {
      Alert.alert("Fecha inválida", "Ingresá una fecha válida con el formato AAAA-MM-DD.");
      return;
    }
    const parsedWeight = weight.trim() ? Number(weight.replace(",", ".")) : undefined;
    if (parsedWeight !== undefined && (!Number.isFinite(parsedWeight) || parsedWeight <= 0)) {
      Alert.alert("Peso inválido", "Ingresá un peso mayor que cero.");
      return;
    }
    if (attachmentUrl.trim() && !isValidHttpUrl(attachmentUrl.trim())) {
      Alert.alert("URL inválida", "El adjunto debe tener una dirección web que empiece con http:// o https://.");
      return;
    }

    const nextEvent: ClinicalEvent = {
      id: eventToEdit?.id ?? `event-${Date.now()}`,
      petId: pet.id,
      type,
      title: title.trim(),
      pet: pet.name,
      detail: observations.trim() || diagnosis.trim() || reason.trim() || "Sin observaciones adicionales.",
      date,
      dose: dose.trim() || undefined,
      reason: reason.trim() || undefined,
      diagnosis: diagnosis.trim() || undefined,
      weight: parsedWeight,
      observations: observations.trim() || undefined,
      attachmentUrl: attachmentUrl.trim() || undefined,
      attachmentFormat: attachmentFormat.trim() || undefined,
    };

    setEvents(eventToEdit
      ? events.map((event) => event.id === eventToEdit.id ? nextEvent : event)
      : [nextEvent, ...events]);
    Alert.alert(
      eventToEdit ? "Evento actualizado" : "Evento registrado",
      "Se guardó en este dispositivo. La API clínica del backend todavía no está disponible.",
      [{
        text: "Ver historial",
        onPress: () => router.replace({
          pathname: "/health/historial-clinico",
          params: { mascotaId: pet.id, mascota: pet.name },
        }),
      }],
    );
  };

  if (!pet) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.topBar}>
          <TouchableOpacity style={styles.back} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={24} color="#30293A" />
          </TouchableOpacity>
          <Text style={styles.topTitle}>Registrar evento</Text>
          <View style={styles.placeholder} />
        </View>
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>Seleccioná una mascota para continuar.</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={24} color="#30293A" />
        </TouchableOpacity>
        <Text style={styles.topTitle}>{eventToEdit ? "Editar evento" : "Registrar evento"}</Text>
        <View style={styles.placeholder} />
      </View>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <View style={styles.petBanner}>
          <Ionicons name="paw-outline" size={21} color="#7C4DFF" />
          <Text style={styles.petText}>Evento para <Text style={styles.petName}>{pet.name}</Text></Text>
        </View>
        <View style={styles.notice}>
          <Ionicons name="information-circle-outline" size={19} color="#806227" />
          <Text style={styles.noticeText}>Se guarda localmente. El backend aún no tiene endpoints clínicos.</Text>
        </View>

        <Text style={styles.label}>Tipo de evento</Text>
        <View style={styles.typeGrid}>
          {eventTypes.map((item) => (
            <TouchableOpacity
              key={item.id}
              style={[styles.type, type === item.id && styles.typeActive]}
              onPress={() => setType(item.id)}
            >
              <Ionicons name={item.icon} size={21} color={type === item.id ? "#7C4DFF" : "#8E8795"} />
              <Text style={[styles.typeText, type === item.id && styles.typeTextActive]}>{item.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <Text style={styles.label}>Nombre del evento *</Text>
        <TextInput
          style={styles.input}
          value={title}
          onChangeText={setTitle}
          placeholder={type === "VACUNA" ? "Ej. Vacuna antirrábica" : "Ej. Control general"}
          placeholderTextColor="#968F9E"
          accessibilityLabel="Nombre del evento clínico"
        />

        <Text style={styles.label}>Fecha *</Text>
        <View style={styles.fieldWithIcon}>
          <Ionicons name="calendar-outline" size={20} color="#7C4DFF" />
          <TextInput
            style={styles.dateInput}
            value={date}
            onChangeText={setDate}
            placeholder="AAAA-MM-DD"
            placeholderTextColor="#968F9E"
            maxLength={10}
            accessibilityLabel="Fecha del evento, formato año-mes-día"
          />
        </View>

        <Text style={styles.label}>Dosis (opcional)</Text>
        <TextInput style={styles.input} value={dose} onChangeText={setDose} placeholder="Ej. 1 ml" placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Motivo (opcional)</Text>
        <TextInput style={styles.input} value={reason} onChangeText={setReason} placeholder="Motivo de la consulta o registro" placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Diagnóstico (opcional)</Text>
        <TextInput style={styles.input} value={diagnosis} onChangeText={setDiagnosis} placeholder="Diagnóstico veterinario" placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Peso en kg (opcional)</Text>
        <TextInput
          style={styles.input}
          value={weight}
          onChangeText={setWeight}
          placeholder="Ej. 12,5"
          placeholderTextColor="#968F9E"
          keyboardType="decimal-pad"
        />
        <Text style={styles.label}>Observaciones (opcional)</Text>
        <TextInput
          style={[styles.input, styles.notes]}
          value={observations}
          onChangeText={setObservations}
          multiline
          textAlignVertical="top"
          placeholder="Indicaciones u observaciones adicionales."
          placeholderTextColor="#968F9E"
        />
        <Text style={styles.label}>URL de adjunto (opcional)</Text>
        <TextInput
          style={styles.input}
          value={attachmentUrl}
          onChangeText={setAttachmentUrl}
          autoCapitalize="none"
          keyboardType="url"
          placeholder="https://..."
          placeholderTextColor="#968F9E"
        />
        <Text style={styles.label}>Formato del adjunto (opcional)</Text>
        <TextInput style={styles.input} value={attachmentFormat} onChangeText={setAttachmentFormat} placeholder="Ej. PDF o JPG" placeholderTextColor="#968F9E" />

        <TouchableOpacity style={styles.saveButton} onPress={saveEvent}>
          <Ionicons name="checkmark-circle-outline" size={21} color="#FFFFFF" />
          <Text style={styles.saveText}>{eventToEdit ? "Guardar cambios" : "Guardar evento"}</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 },
  back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" },
  topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" },
  placeholder: { width: 42 },
  content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  petBanner: { flexDirection: "row", alignItems: "center", gap: 8, padding: 14, backgroundColor: "#EEE8FF", borderRadius: 15, marginBottom: 12 },
  petText: { color: "#655E70", fontSize: 13 },
  petName: { color: "#42364F", fontWeight: "800" },
  notice: { flexDirection: "row", alignItems: "center", gap: 8, padding: 12, borderRadius: 13, backgroundColor: "#FFF7E5", marginBottom: 23 },
  noticeText: { flex: 1, color: "#725C31", fontSize: 12, lineHeight: 17 },
  label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 9 },
  typeGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 25 },
  type: { width: "31%", minHeight: 72, alignItems: "center", justifyContent: "center", gap: 5, backgroundColor: "#FFFFFF", borderRadius: 15, borderWidth: 1, borderColor: "#EEEAF2", paddingHorizontal: 4 },
  typeActive: { borderColor: "#A88BFF", backgroundColor: "#F4F0FF" },
  typeText: { color: "#817A8A", fontSize: 12, fontWeight: "700" },
  typeTextActive: { color: "#7C4DFF", fontWeight: "800" },
  input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", backgroundColor: "#FFFFFF", borderRadius: 14, paddingHorizontal: 14, color: "#292332", fontSize: 14, marginBottom: 20 },
  fieldWithIcon: { height: 51, flexDirection: "row", alignItems: "center", gap: 10, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, marginBottom: 20 },
  dateInput: { flex: 1, color: "#45404B", fontSize: 14 },
  notes: { height: 110, paddingTop: 14 },
  saveButton: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 3 },
  saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  empty: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24 },
  emptyTitle: { color: "#302A3B", textAlign: "center", fontSize: 16, fontWeight: "700" },
});
