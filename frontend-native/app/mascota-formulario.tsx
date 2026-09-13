import { useMemo, useState } from "react";
import { Alert, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";

const existingPets = {
  fido: { name: "Fido", species: "Perro", breed: "Golden Retriever", age: "3 años", weight: "28,5 kg", color: "#F2D5A0" },
  luna: { name: "Luna", species: "Gato", breed: "Siamés", age: "2 años", weight: "4,1 kg", color: "#D9CDFC" },
  rex: { name: "Rex", species: "Perro", breed: "Bulldog", age: "5 años", weight: "22 kg", color: "#C9E5F3" },
} as const;
const colors = ["#F2D5A0", "#D9CDFC", "#C9E5F3", "#D6EBC7"];

export default function MascotaFormularioScreen() {
  const router = useRouter();
  const { pets, setPets } = useAppData();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const existing = useMemo(() => existingPets[id as keyof typeof existingPets], [id]);
  const [name, setName] = useState<string>(existing?.name ?? "");
  const [species, setSpecies] = useState<string>(existing?.species ?? "Perro");
  const [breed, setBreed] = useState<string>(existing?.breed ?? "");
  const [age, setAge] = useState<string>(existing?.age ?? "");
  const [weight, setWeight] = useState<string>(existing?.weight ?? "");
  const [color, setColor] = useState<string>(existing?.color ?? colors[0]);
  const save = () => {
    if (!name.trim() || !breed.trim() || !age.trim() || !weight.trim()) { Alert.alert("Completá los datos", "Nombre, raza, edad y peso son obligatorios."); return; }
    const savedPet = { id: id ?? `pet-${Date.now()}`, name: name.trim(), species, breed: breed.trim(), age: age.trim(), weight: weight.trim(), emoji: species === "Gato" ? "🐱" : "🐶", color };
    setPets(existing ? pets.map((pet) => pet.id === id ? savedPet : pet) : [...pets, savedPet]);
    Alert.alert(existing ? "Mascota actualizada" : "Mascota agregada", `${name} se guardó localmente.`, [{ text: "Aceptar", onPress: () => router.replace("/mascotas") }]);
  };
  return <SafeAreaView style={styles.container}><View style={styles.topBar}><TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity><Text style={styles.topTitle}>{existing ? "Editar mascota" : "Agregar mascota"}</Text><View style={styles.placeholder} /></View><ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}><View style={[styles.avatar, { backgroundColor: color }]}><Text style={styles.emoji}>{species === "Gato" ? "🐱" : "🐶"}</Text></View><Text style={styles.avatarLabel}>Elegí un color para su avatar</Text><View style={styles.colorRow}>{colors.map((item) => <TouchableOpacity key={item} onPress={() => setColor(item)} style={[styles.color, { backgroundColor: item }, color === item && styles.selectedColor]}>{color === item && <Ionicons name="checkmark" size={17} color="#4A3A63" />}</TouchableOpacity>)}</View><Text style={styles.label}>Nombre</Text><TextInput style={styles.input} value={name} onChangeText={setName} placeholder="Ej. Fido" placeholderTextColor="#968F9E" /><Text style={styles.label}>Especie</Text><View style={styles.speciesRow}>{["Perro", "Gato"].map((item) => <TouchableOpacity key={item} style={[styles.species, species === item && styles.speciesActive]} onPress={() => setSpecies(item)}><Ionicons name="paw-outline" size={20} color={species === item ? "#7C4DFF" : "#8E8795"} /><Text style={[styles.speciesText, species === item && styles.speciesTextActive]}>{item}</Text></TouchableOpacity>)}</View><Text style={styles.label}>Raza</Text><TextInput style={styles.input} value={breed} onChangeText={setBreed} placeholder="Ej. Golden Retriever" placeholderTextColor="#968F9E" /><View style={styles.halfRow}><View style={styles.half}><Text style={styles.label}>Edad</Text><TextInput style={styles.input} value={age} onChangeText={setAge} placeholder="Ej. 3 años" placeholderTextColor="#968F9E" /></View><View style={styles.half}><Text style={styles.label}>Peso</Text><TextInput style={styles.input} value={weight} onChangeText={setWeight} placeholder="Ej. 28 kg" placeholderTextColor="#968F9E" /></View></View><TouchableOpacity style={styles.saveButton} onPress={save}><Ionicons name="checkmark-circle-outline" size={21} color="#FFFFFF" /><Text style={styles.saveText}>{existing ? "Guardar cambios" : "Agregar mascota"}</Text></TouchableOpacity></ScrollView></SafeAreaView>;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 }, back: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" }, topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" }, placeholder: { width: 42 }, content: { padding: 20, paddingTop: 7, paddingBottom: 36 }, avatar: { alignSelf: "center", width: 93, height: 93, borderRadius: 47, alignItems: "center", justifyContent: "center" }, emoji: { fontSize: 47 }, avatarLabel: { color: "#817A89", fontSize: 12, textAlign: "center", marginTop: 10 }, colorRow: { flexDirection: "row", justifyContent: "center", gap: 11, marginTop: 12, marginBottom: 29 }, color: { width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center" }, selectedColor: { borderWidth: 2, borderColor: "#7C4DFF" }, label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 }, input: { height: 51, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#292332", fontSize: 14, marginBottom: 20 }, speciesRow: { flexDirection: "row", gap: 10, marginBottom: 20 }, species: { flex: 1, height: 54, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14 }, speciesActive: { backgroundColor: "#F4F0FF", borderColor: "#A88BFF" }, speciesText: { color: "#817A89", fontSize: 13, fontWeight: "700" }, speciesTextActive: { color: "#7C4DFF", fontWeight: "800" }, halfRow: { flexDirection: "row", gap: 11 }, half: { flex: 1 }, saveButton: { height: 52, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 3 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
