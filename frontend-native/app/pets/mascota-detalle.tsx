import { useState } from "react";
import { Alert, SafeAreaView, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";
import { deletePet } from "../../services/api";

function InfoRow({ icon, label, value }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string }) {
  return <View style={styles.infoRow}><View style={styles.infoIcon}><Ionicons name={icon} size={19} color="#7C4DFF" /></View><View><Text style={styles.infoLabel}>{label}</Text><Text style={styles.infoValue}>{value}</Text></View></View>;
}

export default function MascotaDetalleScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { pets, setPets } = useAppData();
  const pet = pets.find((item) => item.id === id);
  const [deleting, setDeleting] = useState(false);

  if (!pet) {
    return <SafeAreaView style={styles.container}><View style={styles.empty}><Text style={styles.emptyTitle}>Mascota no encontrada</Text><TouchableOpacity style={styles.backButton} onPress={() => router.replace("/pets/mascotas")}><Text style={styles.backText}>Volver a mascotas</Text></TouchableOpacity></View></SafeAreaView>;
  }

  const confirmDelete = () => {
    Alert.alert("Eliminar mascota", `¿Querés eliminar a ${pet.name}?`, [
      { text: "Cancelar", style: "cancel" },
      { text: "Eliminar", style: "destructive", onPress: async () => {
        setDeleting(true);
        try {
          await deletePet(pet.id);
          setPets(pets.filter((item) => item.id !== pet.id));
          router.replace("/pets/mascotas");
        } catch (error) {
          Alert.alert("No se pudo eliminar", error instanceof Error ? error.message : "Intentá nuevamente.");
        } finally {
          setDeleting(false);
        }
      } },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}><TouchableOpacity style={styles.backButton} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity><Text style={styles.topTitle}>Detalle de mascota</Text><TouchableOpacity style={styles.editButton} onPress={() => router.push({ pathname: "/pets/mascota-formulario", params: { id: pet.id } })}><Ionicons name="pencil-outline" size={20} color="#7C4DFF" /></TouchableOpacity></View>
        <View style={styles.hero}><View style={[styles.avatar, { backgroundColor: pet.color }]}><Text style={styles.emoji}>{pet.emoji}</Text></View><Text style={styles.petName}>{pet.name}</Text><Text style={styles.petBreed}>{pet.breed}</Text></View>
        <Text style={styles.sectionTitle}>Información</Text>
        <View style={styles.card}><InfoRow icon="paw-outline" label="Especie" value={pet.species} /><View style={styles.line} /><InfoRow icon="calendar-outline" label="Edad" value={pet.age} /><View style={styles.line} /><InfoRow icon="fitness-outline" label="Peso" value={pet.weight} />{pet.notas ? <><View style={styles.line} /><InfoRow icon="document-text-outline" label="Notas" value={pet.notas} /></> : null}</View>
        <Text style={styles.sectionTitle}>Accesos rápidos</Text>
        <View style={styles.actions}><TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/health/registrar-evento", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="medical-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Registrar evento</Text></TouchableOpacity><TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/health/recordatorios", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="alarm-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Crear recordatorio</Text></TouchableOpacity><TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/health/historial-clinico", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="document-text-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Ver historial</Text></TouchableOpacity></View>
        <TouchableOpacity style={[styles.deleteButton, deleting && styles.disabled]} onPress={confirmDelete} disabled={deleting}><Ionicons name="trash-outline" size={20} color="#D8515D" /><Text style={styles.deleteText}>{deleting ? "Eliminando..." : "Eliminar mascota"}</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { padding: 20, paddingBottom: 36 },
  topBar: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 24 }, backButton: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" }, topTitle: { color: "#201B29", fontSize: 16, fontWeight: "800" }, editButton: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#EEE8FF" },
  hero: { alignItems: "center", marginBottom: 29 }, avatar: { width: 105, height: 105, borderRadius: 53, justifyContent: "center", alignItems: "center" }, emoji: { fontSize: 55 }, petName: { color: "#201B29", fontSize: 28, fontWeight: "800", marginTop: 11 }, petBreed: { color: "#827B8C", fontSize: 14, marginTop: 3 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 12 }, card: { backgroundColor: "#FFFFFF", borderRadius: 17, paddingHorizontal: 14, marginBottom: 27 }, infoRow: { minHeight: 67, flexDirection: "row", alignItems: "center" }, infoIcon: { width: 37, height: 37, borderRadius: 11, justifyContent: "center", alignItems: "center", backgroundColor: "#EEE8FF", marginRight: 11 }, infoLabel: { color: "#8A8392", fontSize: 12 }, infoValue: { color: "#292332", fontSize: 14, fontWeight: "800", marginTop: 3 }, line: { height: 1, backgroundColor: "#F0EDF3", marginLeft: 48 },
  actions: { flexDirection: "row", gap: 9, marginBottom: 28 }, action: { flex: 1, minHeight: 120, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 9 }, actionIcon: { width: 42, height: 42, borderRadius: 14, justifyContent: "center", alignItems: "center", backgroundColor: "#EEE8FF", marginBottom: 9 }, actionText: { color: "#413A49", fontSize: 11, fontWeight: "800", textAlign: "center", lineHeight: 15 },
  deleteButton: { height: 51, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, borderWidth: 1, borderColor: "#F2CDD0", borderRadius: 14, backgroundColor: "#FFF9F9" }, deleteText: { color: "#D8515D", fontSize: 14, fontWeight: "800" }, disabled: { opacity: 0.6 }, empty: { flex: 1, alignItems: "center", justifyContent: "center", padding: 20 }, emptyTitle: { color: "#201B29", fontSize: 18, fontWeight: "800", marginBottom: 16 }, backText: { color: "#7C4DFF", fontWeight: "800" },
});
