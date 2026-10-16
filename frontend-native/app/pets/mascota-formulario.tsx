import { useState } from "react";
import { Alert, SafeAreaView, ScrollView, StyleSheet, Switch, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";
import { createPet, updatePet } from "../../services/api";

const colors = ["#F2D5A0", "#D9CDFC", "#C9E5F3", "#D6EBC7"];

function parseAge(value: string) {
  const match = value.trim().match(/^(\d+(?:[.,]\d+)?)\s*(.*)$/);
  if (!match) return {};

  const unidadIngresada = match[2].trim().toLowerCase();
  const unidades: Record<string, string> = {
    día: "dias",
    días: "dias",
    dia: "dias",
    dias: "dias",
    semana: "semanas",
    semanas: "semanas",
    mes: "meses",
    meses: "meses",
    año: "anios",
    años: "anios",
    ano: "anios",
    anos: "anios",
  };

  return {
    edadValor: Number(match[1].replace(",", ".")),
    edadUnidad: unidades[unidadIngresada] ?? "anios",
  };
}

export default function MascotaFormularioScreen() {
  const router = useRouter();
  const { pets, refreshPets } = useAppData();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const existing = pets.find((pet) => pet.id === id);
  const [name, setName] = useState(existing?.name ?? "");
  const [species, setSpecies] = useState(existing?.species ?? "Perro");
  const [breed, setBreed] = useState(existing?.breed === "Raza no indicada" ? "" : existing?.breed ?? "");
  const [age, setAge] = useState(existing?.age === "Edad no indicada" ? "" : existing?.age ?? "");
  const [weight, setWeight] = useState(existing?.weight === "Peso no indicado" ? "" : existing?.weight.replace(" kg", "") ?? "");
  const [birthDate, setBirthDate] = useState(existing?.fechaNacimiento ?? "");
  const [approximateDate, setApproximateDate] = useState(existing?.fechaAproximada ?? false);
  const [color, setColor] = useState(existing?.color ?? colors[0]);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!name.trim() || !species.trim()) {
      Alert.alert("Completá los datos", "El nombre y la especie son obligatorios.");
      return;
    }

    const parsedWeight = Number(weight.replace(",", ".").trim());
    const payload = {
      nombre: name.trim(),
      especie: species,
      raza: breed.trim() || undefined,
      fechaNacimiento: birthDate.trim() || undefined,
      fechaAproximada: approximateDate,
      ...parseAge(age),
      pesoActual: weight.trim() && Number.isFinite(parsedWeight) ? parsedWeight : undefined,
    };

    setSaving(true);
    try {
      if (existing) await updatePet(existing.id, payload);
      else await createPet(payload);
      await refreshPets();
      Alert.alert(existing ? "Mascota actualizada" : "Mascota agregada", `${name.trim()} se guardó correctamente.`, [
        { text: "Aceptar", onPress: () => router.replace("/pets/mascotas") },
      ]);
    } catch (error) {
      Alert.alert("No se pudo guardar", error instanceof Error ? error.message : "Revisá los datos e intentá nuevamente.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
        <Text style={styles.topTitle}>{existing ? "Editar mascota" : "Agregar mascota"}</Text>
        <View style={styles.placeholder} />
      </View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={[styles.avatar, { backgroundColor: color }]}><Text style={styles.emoji}>{species.toLowerCase().includes("gato") ? "🐱" : "🐶"}</Text></View>
        <Text style={styles.avatarLabel}>Elegí un color para su avatar</Text>
        <View style={styles.colorRow}>{colors.map((item) => <TouchableOpacity key={item} onPress={() => setColor(item)} style={[styles.color, { backgroundColor: item }, color === item && styles.selectedColor]}>{color === item && <Ionicons name="checkmark" size={17} color="#4A3A63" />}</TouchableOpacity>)}</View>
        <Text style={styles.label}>Nombre</Text>
        <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="Ej. Fido" placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Especie</Text>
        <View style={styles.speciesRow}>{["Perro", "Gato"].map((item) => <TouchableOpacity key={item} style={[styles.species, species === item && styles.speciesActive]} onPress={() => setSpecies(item)}><Ionicons name="paw-outline" size={20} color={species === item ? "#7C4DFF" : "#8E8795"} /><Text style={[styles.speciesText, species === item && styles.speciesTextActive]}>{item}</Text></TouchableOpacity>)}</View>
        <Text style={styles.label}>Raza (opcional)</Text>
        <TextInput style={styles.input} value={breed} onChangeText={setBreed} placeholder="Ej. Golden Retriever" placeholderTextColor="#968F9E" />
        <Text style={styles.label}>Fecha de nacimiento (opcional)</Text>
        <TextInput style={styles.input} value={birthDate} onChangeText={setBirthDate} placeholder="AAAA-MM-DD" placeholderTextColor="#968F9E" maxLength={10} />
        <View style={styles.switchRow}><View><Text style={styles.switchTitle}>Fecha aproximada</Text><Text style={styles.switchSubtitle}>Usá esta opción si no conocés la fecha exacta.</Text></View><Switch value={approximateDate} onValueChange={setApproximateDate} trackColor={{ false: "#D9D4E0", true: "#C5B5FF" }} thumbColor={approximateDate ? "#7C4DFF" : "#FFFFFF"} /></View>
        <View style={styles.halfRow}>
          <View style={styles.half}><Text style={styles.label}>Edad (opcional)</Text><TextInput style={styles.input} value={age} onChangeText={setAge} placeholder="Ej. 3 años" placeholderTextColor="#968F9E" /></View>
          <View style={styles.half}><Text style={styles.label}>Peso (kg)</Text><TextInput style={styles.input} value={weight} onChangeText={setWeight} keyboardType="decimal-pad" placeholder="Ej. 28,5" placeholderTextColor="#968F9E" /></View>
        </View>
        <TouchableOpacity style={[styles.saveButton, saving && styles.disabled]} onPress={save} disabled={saving}><Ionicons name="cloud-upload-outline" size={20} color="#FFFFFF" /><Text style={styles.saveText}>{saving ? "Guardando..." : existing ? "Guardar cambios" : "Guardar mascota"}</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 },
  back: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" },
  topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" }, placeholder: { width: 42 },
  content: { padding: 20, paddingTop: 7, paddingBottom: 36 },
  avatar: { alignSelf: "center", width: 93, height: 93, borderRadius: 47, alignItems: "center", justifyContent: "center" }, emoji: { fontSize: 47 },
  avatarLabel: { color: "#817A89", fontSize: 12, textAlign: "center", marginTop: 10 },
  colorRow: { flexDirection: "row", justifyContent: "center", gap: 11, marginTop: 12, marginBottom: 29 },
  color: { width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center" }, selectedColor: { borderWidth: 2, borderColor: "#7C4DFF" },
  label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 },
  input: { height: 51, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#292332", fontSize: 14, marginBottom: 20 },
  speciesRow: { flexDirection: "row", gap: 10, marginBottom: 20 }, species: { flex: 1, height: 54, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14 },
  speciesActive: { backgroundColor: "#F4F0FF", borderColor: "#A88BFF" }, speciesText: { color: "#817A89", fontSize: 13, fontWeight: "700" }, speciesTextActive: { color: "#7C4DFF", fontWeight: "800" },
  switchRow: { minHeight: 58, flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }, switchTitle: { color: "#302A3B", fontSize: 13, fontWeight: "800" }, switchSubtitle: { color: "#817A89", fontSize: 11, marginTop: 4 },
  halfRow: { flexDirection: "row", gap: 11 }, half: { flex: 1 },
  saveButton: { height: 52, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 3 }, disabled: { opacity: 0.6 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
