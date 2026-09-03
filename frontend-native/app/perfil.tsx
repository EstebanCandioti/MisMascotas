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
import { useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";

export default function PerfilScreen() {
  const router = useRouter();
  const { currentUser, users, setUsers, setCurrentUser, pets, events } = useAppData();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(currentUser?.name ?? "Usuario Demo");

  const saveProfile = () => {
    if (currentUser) { const updated = { ...currentUser, name: name.trim() || currentUser.name }; setUsers(users.map((user) => user.id === updated.id ? updated : user)); setCurrentUser(updated); }
    setEditing(false); Alert.alert("Perfil actualizado", "Tus datos se guardaron localmente.");
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
          <Text style={styles.topTitle}>Mi perfil</Text>
          <TouchableOpacity style={styles.editButton} onPress={() => editing ? saveProfile() : setEditing(true)}><Ionicons name={editing ? "checkmark" : "pencil-outline"} size={20} color="#7C4DFF" /></TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <View style={styles.avatar}><Text style={styles.avatarText}>{name.slice(0, 2).toUpperCase()}</Text></View>
          {editing ? <TextInput value={name} onChangeText={setName} style={styles.nameInput} /> : <Text style={styles.name}>{name}</Text>}
          <Text style={styles.email}>{currentUser?.email ?? "usuario@demo.com"}</Text>
          <Text style={styles.since}>Miembro desde agosto de 2026</Text>
        </View>

        <View style={styles.summary}>
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>{pets.length}</Text><Text style={styles.summaryText}>Mascotas</Text></View>
          <View style={styles.divider} />
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>{events.length}</Text><Text style={styles.summaryText}>Eventos</Text></View>
          <View style={styles.divider} />
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>4</Text><Text style={styles.summaryText}>Badges</Text></View>
        </View>

        <View style={styles.headerRow}><Text style={styles.sectionTitle}>Mis badges</Text><TouchableOpacity onPress={() => router.push("/badges")}><Text style={styles.link}>Ver todos</Text></TouchableOpacity></View>
        <View style={styles.badges}>
          <View style={styles.badge}><Text style={styles.badgeEmoji}>🏆</Text><Text style={styles.badgeText}>Primer registro</Text></View>
          <View style={styles.badge}><Text style={styles.badgeEmoji}>💜</Text><Text style={styles.badgeText}>Cuidador atento</Text></View>
          <View style={styles.badge}><Text style={styles.badgeEmoji}>📅</Text><Text style={styles.badgeText}>Siempre a tiempo</Text></View>
        </View>

        <Text style={styles.sectionTitle}>Actividad reciente</Text>
        <View style={styles.card}>
          <View style={styles.activity}><View style={styles.activityIcon}><Ionicons name="medical-outline" size={20} color="#7C4DFF" /></View><View><Text style={styles.activityTitle}>Registraste una consulta de Luna</Text><Text style={styles.activityDate}>Ayer, 18:40</Text></View></View>
          <View style={styles.line} />
          <View style={styles.activity}><View style={styles.activityIcon}><Ionicons name="checkmark-done-outline" size={20} color="#7C4DFF" /></View><View><Text style={styles.activityTitle}>Confirmaste un recordatorio</Text><Text style={styles.activityDate}>Hace 2 días</Text></View></View>
        </View>

        <TouchableOpacity style={styles.passwordButton} onPress={() => Alert.alert("Contraseña", "Esta acción se conectará al backend más adelante.")}><Ionicons name="key-outline" size={20} color="#7C4DFF" /><Text style={styles.passwordText}>Cambiar contraseña</Text><Ionicons name="chevron-forward" size={20} color="#A29BAA" /></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { padding: 20, paddingBottom: 36 },
  topBar: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 23 }, backButton: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" }, topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" }, editButton: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" },
  hero: { alignItems: "center", marginBottom: 25 }, avatar: { width: 90, height: 90, borderRadius: 45, backgroundColor: "#7C4DFF", alignItems: "center", justifyContent: "center" }, avatarText: { color: "#FFFFFF", fontSize: 28, fontWeight: "800" }, name: { color: "#201B29", fontSize: 24, fontWeight: "800", marginTop: 11 }, nameInput: { color: "#201B29", fontSize: 22, fontWeight: "800", textAlign: "center", marginTop: 7, minWidth: 200, borderBottomWidth: 1, borderBottomColor: "#C5B5FF", paddingVertical: 3 }, email: { color: "#7F7888", fontSize: 13, marginTop: 4 }, since: { color: "#9A94A2", fontSize: 11, marginTop: 6 },
  summary: { flexDirection: "row", alignItems: "center", paddingVertical: 18, backgroundColor: "#FFFFFF", borderRadius: 17, marginBottom: 28 }, summaryItem: { flex: 1, alignItems: "center" }, summaryNumber: { color: "#7C4DFF", fontSize: 21, fontWeight: "800" }, summaryText: { color: "#807989", fontSize: 11, marginTop: 3 }, divider: { height: 32, width: 1, backgroundColor: "#EEEAF2" },
  headerRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }, sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 12 }, link: { color: "#7C4DFF", fontSize: 13, fontWeight: "800" },
  badges: { flexDirection: "row", gap: 10, marginBottom: 29 }, badge: { flex: 1, alignItems: "center", paddingVertical: 13, backgroundColor: "#FFFFFF", borderRadius: 16 }, badgeEmoji: { fontSize: 24 }, badgeText: { color: "#58505F", fontSize: 9, fontWeight: "700", marginTop: 7, textAlign: "center" },
  card: { backgroundColor: "#FFFFFF", borderRadius: 17, paddingHorizontal: 14, marginBottom: 25 }, activity: { flexDirection: "row", alignItems: "center", minHeight: 72 }, activityIcon: { width: 38, height: 38, borderRadius: 12, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center", marginRight: 11 }, activityTitle: { color: "#322B3A", fontSize: 13, fontWeight: "700" }, activityDate: { color: "#8E8795", fontSize: 11, marginTop: 4 }, line: { height: 1, backgroundColor: "#F0EDF3", marginLeft: 49 },
  passwordButton: { height: 55, flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, paddingHorizontal: 15, gap: 11 }, passwordText: { flex: 1, color: "#332C3B", fontSize: 14, fontWeight: "800" },
});
