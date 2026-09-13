import { useState } from "react";
import { Alert, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";

const types = [
  { id: "Consulta", icon: "medkit-outline" as const },
  { id: "Vacuna", icon: "shield-checkmark-outline" as const },
  { id: "Medicación", icon: "medical-outline" as const },
  { id: "Peso", icon: "fitness-outline" as const },
];

export default function RegistrarEventoScreen() {
  const router = useRouter();
  const { mascota = "Fido" } = useLocalSearchParams<{ mascota?: string }>();
  const { events, setEvents } = useAppData();
  const [type, setType] = useState("Consulta");
  const [title, setTitle] = useState("");
  const [notes, setNotes] = useState("");

  const saveEvent = () => {
    if (!title.trim()) { Alert.alert("Falta el título", "Ingresá el nombre del evento clínico."); return; }
    setEvents([{ id: `event-${Date.now()}`, type, title: title.trim(), pet: mascota, detail: notes.trim() || "Sin observaciones adicionales.", date: "27 AGO 2026" }, ...events]);
    Alert.alert("Evento registrado", `${type}: ${title} se guardó localmente para ${mascota}.`, [{ text: "Ver historial", onPress: () => router.replace({ pathname: "/historial-clinico", params: { mascota } }) }]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}><TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity><Text style={styles.topTitle}>Registrar evento</Text><View style={styles.placeholder} /></View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.petBanner}><Ionicons name="paw-outline" size={21} color="#7C4DFF" /><Text style={styles.petText}>Evento para <Text style={styles.petName}>{mascota}</Text></Text></View>
        <Text style={styles.label}>Tipo de evento</Text>
        <View style={styles.typeGrid}>{types.map((item) => <TouchableOpacity key={item.id} style={[styles.type, type === item.id && styles.typeActive]} onPress={() => setType(item.id)}><Ionicons name={item.icon} size={21} color={type === item.id ? "#7C4DFF" : "#8E8795"} /><Text style={[styles.typeText, type === item.id && styles.typeTextActive]}>{item.id}</Text></TouchableOpacity>)}</View>
        <Text style={styles.label}>Título</Text><TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder={type === "Vacuna" ? "Ej. Vacuna antirrábica" : "Ej. Control general"} placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Fecha</Text><View style={styles.dateInput}><Ionicons name="calendar-outline" size={20} color="#7C4DFF" /><Text style={styles.dateText}>27 de agosto de 2026</Text></View>
        <Text style={styles.label}>Notas (opcional)</Text><TextInput style={[styles.input, styles.notes]} value={notes} onChangeText={setNotes} multiline textAlignVertical="top" placeholder="Agregá indicaciones, dosis u observaciones." placeholderTextColor="#968F9E" />
        <TouchableOpacity style={styles.saveButton} onPress={saveEvent}><Ionicons name="checkmark-circle-outline" size={21} color="#FFFFFF" /><Text style={styles.saveText}>Guardar evento</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 }, back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" }, topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" }, placeholder: { width: 42 }, content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  petBanner: { flexDirection: "row", alignItems: "center", gap: 8, padding: 14, backgroundColor: "#EEE8FF", borderRadius: 15, marginBottom: 27 }, petText: { color: "#655E70", fontSize: 13 }, petName: { color: "#42364F", fontWeight: "800" }, label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 9 }, typeGrid: { flexDirection: "row", flexWrap: "wrap", gap: 10, marginBottom: 25 }, type: { width: "47%", height: 72, alignItems: "center", justifyContent: "center", gap: 5, backgroundColor: "#FFFFFF", borderRadius: 15, borderWidth: 1, borderColor: "#EEEAF2" }, typeActive: { borderColor: "#A88BFF", backgroundColor: "#F4F0FF" }, typeText: { color: "#817A8A", fontSize: 12, fontWeight: "700" }, typeTextActive: { color: "#7C4DFF", fontWeight: "800" },
  input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", backgroundColor: "#FFFFFF", borderRadius: 14, paddingHorizontal: 14, color: "#292332", fontSize: 14, marginBottom: 20 }, dateInput: { height: 51, flexDirection: "row", alignItems: "center", gap: 10, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, marginBottom: 20 }, dateText: { color: "#45404B", fontSize: 14 }, notes: { height: 110, paddingTop: 14 },
  saveButton: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 3 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
