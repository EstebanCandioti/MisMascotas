import { useMemo, useState } from "react";
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

const pets = {
  fido: { name: "Fido", emoji: "🐶", color: "#F2D5A0", breed: "Golden Retriever", age: "3 años", weight: "28,5 kg" },
  luna: { name: "Luna", emoji: "🐱", color: "#D9CDFC", breed: "Siamés", age: "2 años", weight: "4,1 kg" },
  rex: { name: "Rex", emoji: "🐶", color: "#C9E5F3", breed: "Bulldog", age: "5 años", weight: "22 kg" },
} as const;

type PetId = keyof typeof pets;

function InfoRow({ icon, label, value }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <View style={styles.infoIcon}><Ionicons name={icon} size={19} color="#7C4DFF" /></View>
      <View><Text style={styles.infoLabel}>{label}</Text><Text style={styles.infoValue}>{value}</Text></View>
    </View>
  );
}

export default function MascotaDetalleScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const pet = useMemo(() => pets[(id as PetId) ?? "fido"] ?? pets.fido, [id]);
  const [editing, setEditing] = useState(false);
  const [weight, setWeight] = useState<string>(pet.weight);

  const deletePet = () => {
    Alert.alert("Eliminar mascota", `¿Querés eliminar a ${pet.name}?`, [
      { text: "Cancelar", style: "cancel" },
      { text: "Eliminar", style: "destructive", onPress: () => router.replace("/mascotas") },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
          <Text style={styles.topTitle}>Detalle de mascota</Text>
          <TouchableOpacity style={styles.editButton} onPress={() => router.push({ pathname: "/mascota-formulario", params: { id: id ?? "fido" } })}><Ionicons name="pencil-outline" size={20} color="#7C4DFF" /></TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <View style={[styles.avatar, { backgroundColor: pet.color }]}><Text style={styles.emoji}>{pet.emoji}</Text></View>
          <Text style={styles.petName}>{pet.name}</Text>
          <Text style={styles.petBreed}>{pet.breed}</Text>
        </View>

        <Text style={styles.sectionTitle}>Información</Text>
        <View style={styles.card}>
          <InfoRow icon="paw-outline" label="Especie" value={pet.name === "Luna" ? "Gato" : "Perro"} />
          <View style={styles.line} />
          <InfoRow icon="calendar-outline" label="Edad" value={pet.age} />
          <View style={styles.line} />
          {editing ? (
            <View style={styles.editWeight}><View style={styles.infoIcon}><Ionicons name="fitness-outline" size={19} color="#7C4DFF" /></View><View style={styles.weightContent}><Text style={styles.infoLabel}>Peso</Text><TextInput value={weight} onChangeText={setWeight} style={styles.weightInput} /></View></View>
          ) : <InfoRow icon="fitness-outline" label="Peso" value={weight} />}
        </View>

        <Text style={styles.sectionTitle}>Accesos rápidos</Text>
        <View style={styles.actions}>
          <TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/registrar-evento", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="medical-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Registrar evento</Text></TouchableOpacity>
          <TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/recordatorios", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="alarm-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Crear recordatorio</Text></TouchableOpacity>
          <TouchableOpacity style={styles.action} onPress={() => router.push({ pathname: "/historial-clinico", params: { mascota: pet.name } })}><View style={styles.actionIcon}><Ionicons name="document-text-outline" size={22} color="#7C4DFF" /></View><Text style={styles.actionText}>Ver historial</Text></TouchableOpacity>
        </View>

        {editing && <TouchableOpacity style={styles.saveButton} onPress={() => { setEditing(false); Alert.alert("Cambios guardados", `El peso de ${pet.name} se actualizó de forma simulada.`); }}><Text style={styles.saveText}>Guardar cambios</Text></TouchableOpacity>}
        <TouchableOpacity style={styles.deleteButton} onPress={deletePet}><Ionicons name="trash-outline" size={20} color="#D8515D" /><Text style={styles.deleteText}>Eliminar mascota</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { padding: 20, paddingBottom: 36 },
  topBar: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 24 }, backButton: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" }, topTitle: { color: "#201B29", fontSize: 16, fontWeight: "800" }, editButton: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#EEE8FF" },
  hero: { alignItems: "center", marginBottom: 29 }, avatar: { width: 105, height: 105, borderRadius: 53, justifyContent: "center", alignItems: "center" }, emoji: { fontSize: 55 }, petName: { color: "#201B29", fontSize: 28, fontWeight: "800", marginTop: 11 }, petBreed: { color: "#827B8C", fontSize: 14, marginTop: 3 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 12 }, card: { backgroundColor: "#FFFFFF", borderRadius: 17, paddingHorizontal: 14, marginBottom: 27 }, infoRow: { minHeight: 67, flexDirection: "row", alignItems: "center" }, infoIcon: { width: 37, height: 37, borderRadius: 11, justifyContent: "center", alignItems: "center", backgroundColor: "#EEE8FF", marginRight: 11 }, infoLabel: { color: "#8A8392", fontSize: 12 }, infoValue: { color: "#292332", fontSize: 14, fontWeight: "800", marginTop: 3 }, line: { height: 1, backgroundColor: "#F0EDF3", marginLeft: 48 }, editWeight: { minHeight: 67, flexDirection: "row", alignItems: "center" }, weightContent: { flex: 1 }, weightInput: { color: "#292332", fontSize: 14, fontWeight: "800", paddingVertical: 2, marginTop: 2, borderBottomWidth: 1, borderBottomColor: "#C5B5FF" },
  actions: { flexDirection: "row", gap: 9, marginBottom: 28 }, action: { flex: 1, minHeight: 120, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 9 }, actionIcon: { width: 42, height: 42, borderRadius: 14, justifyContent: "center", alignItems: "center", backgroundColor: "#EEE8FF", marginBottom: 9 }, actionText: { color: "#413A49", fontSize: 11, fontWeight: "800", textAlign: "center", lineHeight: 15 },
  saveButton: { height: 52, justifyContent: "center", alignItems: "center", backgroundColor: "#7C4DFF", borderRadius: 14, marginBottom: 11 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" }, deleteButton: { height: 51, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, borderWidth: 1, borderColor: "#F2CDD0", borderRadius: 14, backgroundColor: "#FFF9F9" }, deleteText: { color: "#D8515D", fontSize: 14, fontWeight: "800" },
});
