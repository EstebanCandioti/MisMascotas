import { useState } from "react";
import { Alert, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";

export default function CuidadoresScreen() {
  const [email, setEmail] = useState("");
  const [people, setPeople] = useState([
    { initials: "LM", name: "Laura Martínez", role: "Familia", pet: "Fido y Luna", color: "#EEE8FF" },
    { initials: "CL", name: "Carlos López", role: "Cuidador", pet: "Rex", color: "#E2F3FC" },
  ]);

  const invite = () => {
    if (!email.trim()) { Alert.alert("Falta el correo", "Ingresá un correo para enviar la invitación."); return; }
    setPeople([...people, { initials: "IN", name: email.trim(), role: "Cuidador", pet: "Pendiente de aceptar", color: "#FDF0DF" }]);
    setEmail("");
    Alert.alert("Invitación enviada", "Se creó una invitación simulada.");
  };

  const managePerson = (index: number) => {
    const person = people[index];
    const nextRole = person.role === "Familia" ? "Cuidador" : "Familia";

    Alert.alert(person.name, `Acceso a: ${person.pet}`, [
      {
        text: `Cambiar a ${nextRole}`,
        onPress: () => setPeople(people.map((item, itemIndex) => itemIndex === index ? { ...item, role: nextRole } : item)),
      },
      {
        text: "Revocar acceso",
        style: "destructive",
        onPress: () => setPeople(people.filter((_, itemIndex) => itemIndex !== index)),
      },
      { text: "Cancelar", style: "cancel" },
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Cuidadores" subtitle="Compartí el cuidado de tus mascotas." icon="person-add-outline" />
        <View style={styles.info}><Ionicons name="people-outline" size={24} color="#7C4DFF" /><Text style={styles.infoText}>Las personas invitadas pueden recibir recordatorios y registrar actividades.</Text></View>
        <Text style={styles.label}>Invitar por correo</Text>
        <View style={styles.inputBox}><Ionicons name="mail-outline" size={20} color="#918A99" /><TextInput style={styles.input} value={email} onChangeText={setEmail} keyboardType="email-address" autoCapitalize="none" placeholder="cuidador@email.com" placeholderTextColor="#918A99" /></View>
        <TouchableOpacity style={styles.inviteButton} onPress={invite}><Ionicons name="send-outline" size={19} color="#FFFFFF" /><Text style={styles.inviteText}>Enviar invitación</Text></TouchableOpacity>
        <Text style={styles.sectionTitle}>Personas con acceso</Text>
        {people.map((person, index) => <TouchableOpacity key={`${person.name}-${index}`} style={styles.person} onPress={() => managePerson(index)}><View style={[styles.initials, { backgroundColor: person.color }]}><Text style={styles.initialsText}>{person.initials}</Text></View><View style={styles.personInfo}><Text style={styles.personName}>{person.name}</Text><Text style={styles.personDetail}>{person.role} · {person.pet}</Text></View><Ionicons name="chevron-forward" size={20} color="#9A94A4" /></TouchableOpacity>)}
      </ScrollView>
      <AppBottomNav activeRoute="cuidadores" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  info: { flexDirection: "row", gap: 12, alignItems: "center", padding: 15, backgroundColor: "#EEE8FF", borderRadius: 16, marginBottom: 25 }, infoText: { flex: 1, color: "#655E70", fontSize: 13, lineHeight: 18 },
  label: { color: "#201B29", fontSize: 13, fontWeight: "800", marginBottom: 8 }, inputBox: { height: 52, flexDirection: "row", alignItems: "center", paddingHorizontal: 15, backgroundColor: "#FFFFFF", borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14 }, input: { flex: 1, marginLeft: 10, color: "#201B29", fontSize: 14 },
  inviteButton: { height: 50, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 11 }, inviteText: { color: "#FFFFFF", fontWeight: "800", fontSize: 14 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginTop: 29, marginBottom: 13 }, person: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 13, marginBottom: 11 }, initials: { width: 47, height: 47, borderRadius: 24, justifyContent: "center", alignItems: "center", marginRight: 12 }, initialsText: { color: "#564B6A", fontWeight: "800", fontSize: 13 }, personInfo: { flex: 1 }, personName: { color: "#201B29", fontSize: 14, fontWeight: "800" }, personDetail: { color: "#837C8D", fontSize: 12, marginTop: 4 },
});
