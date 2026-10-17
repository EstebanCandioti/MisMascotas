import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import AppBottomNav from "@/components/app-bottom-nav";
import { useAppData } from "../../context/app-data-context";

export default function InicioScreen() {
  const router = useRouter();
  const { currentUser, pets, reminders } = useAppData();
  const userName = currentUser?.name ?? "Usuario";
  const userInitials = userName.slice(0, 2).toUpperCase();
  const pendingReminders = reminders.filter((reminder) => !reminder.done);
  const today = new Intl.DateTimeFormat("es-AR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date()).toUpperCase();

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <View style={styles.brand}>
            <Ionicons name="paw-outline" size={25} color="#7C4DFF" />
            <Text style={styles.brandText}>Mis Mascotas</Text>
          </View>
          <TouchableOpacity style={styles.avatar} onPress={() => router.push("/account/perfil")}>
            <Text style={styles.avatarText}>{userInitials}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.welcome}>
          <Text style={styles.eyebrow}>{today}</Text>
          <Text style={styles.title}>Hola, {userName} 👋</Text>
          <Text style={styles.subtitle}>Todo el cuidado de tus mascotas, en un solo lugar.</Text>
        </View>

        <View style={styles.summary}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>{pets.length}</Text>
            <Text numberOfLines={2} style={styles.summaryLabel}>Mascotas</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>{pendingReminders.length}</Text>
            <Text numberOfLines={2} style={styles.summaryLabel}>Próximos</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>0</Text>
            <Text numberOfLines={2} style={styles.summaryLabel}>Compartido</Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Próximos recordatorios</Text>
          <TouchableOpacity style={styles.headerAction} onPress={() => router.push("/health/recordatorios")}>
            <Text style={styles.link}>Ver todos</Text>
          </TouchableOpacity>
        </View>
        {pendingReminders.slice(0, 3).map((reminder) => (
          <View key={reminder.id} style={styles.reminderCard}>
            <View style={styles.reminderIcon}>
              <Ionicons name="alarm-outline" size={21} color="#FFFFFF" />
            </View>
            <View style={styles.reminderInfo}>
              <Text style={styles.reminderTitle}>{reminder.title}</Text>
              <Text style={styles.reminderDetail}>{reminder.pet} · {reminder.detail}</Text>
            </View>
            <Text style={styles.reminderDate}>{reminder.date}</Text>
          </View>
        ))}
        {!pendingReminders.length && <Text style={styles.emptyPets}>No tenés recordatorios pendientes.</Text>}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Mis mascotas</Text>
          <TouchableOpacity style={styles.headerAction} onPress={() => router.push("/pets/mascotas")}><Text style={styles.link}>Administrar</Text></TouchableOpacity>
        </View>
        <View style={styles.petsRow}>
          {pets.slice(0, 3).map((pet) => (
            <TouchableOpacity key={pet.id} style={styles.petCard} onPress={() => router.push({ pathname: "/pets/mascota-detalle", params: { id: pet.id } })}>
              <View style={[styles.petLargeAvatar, { backgroundColor: pet.color }]}><Text style={styles.emoji}>{pet.emoji}</Text></View>
              <Text style={styles.petCardName}>{pet.name}</Text>
              <Text style={styles.petBreed}>{pet.breed}</Text>
            </TouchableOpacity>
          ))}
          {!pets.length && <Text style={styles.emptyPets}>Todavía no tenés mascotas registradas.</Text>}
        </View>
      </ScrollView>

      <AppBottomNav activeRoute="inicio" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  content: { padding: 20, paddingBottom: 36 },
  topBar: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  brand: { flex: 1, minWidth: 0, flexDirection: "row", alignItems: "center", gap: 7, marginRight: 12 },
  brandText: { color: "#7C4DFF", fontSize: 16, fontWeight: "700", flexShrink: 1 },
  avatar: { width: 38, height: 38, borderRadius: 19, backgroundColor: "#7C4DFF", justifyContent: "center", alignItems: "center", flexShrink: 0 },
  avatarText: { color: "#FFFFFF", fontSize: 12, fontWeight: "800" },
  welcome: { marginTop: 30 },
  eyebrow: { color: "#7C4DFF", fontSize: 11, fontWeight: "800", letterSpacing: 1 },
  title: { color: "#171321", fontSize: 29, fontWeight: "700", marginTop: 8, flexShrink: 1 },
  subtitle: { color: "#756F80", fontSize: 14, marginTop: 6, flexShrink: 1 },
  summary: { flexDirection: "row", backgroundColor: "#FFFFFF", borderRadius: 16, marginTop: 24, paddingVertical: 17, alignItems: "stretch", shadowColor: "#312642", shadowOpacity: 0.06, shadowRadius: 9, elevation: 2 },
  summaryItem: { flex: 1, minWidth: 0, alignItems: "center", justifyContent: "center", paddingHorizontal: 3 },
  summaryNumber: { color: "#7C4DFF", fontSize: 22, fontWeight: "800" },
  summaryLabel: { color: "#756F80", fontSize: 12, marginTop: 3, textAlign: "center", flexShrink: 1 },
  summaryDivider: { width: 1, height: 32, backgroundColor: "#EEEAF2" },
  sectionHeader: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", marginTop: 27, marginBottom: 11, gap: 12 },
  sectionTitle: { flex: 1, minWidth: 0, color: "#171321", fontSize: 19, fontWeight: "700", flexShrink: 1 },
  headerAction: { flexShrink: 0, paddingVertical: 2 },
  badge: { backgroundColor: "#EEE8FF", paddingHorizontal: 9, paddingVertical: 5, borderRadius: 99 },
  badgeText: { color: "#6844D9", fontSize: 11, fontWeight: "700" },
  link: { color: "#7C4DFF", fontSize: 13, fontWeight: "700", flexShrink: 0 },
  card: { backgroundColor: "#FFFFFF", borderRadius: 16, padding: 15, marginBottom: 12, shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 8, elevation: 1 },
  petHeader: { flexDirection: "row", alignItems: "center", marginBottom: 10 },
  petAvatar: { width: 38, height: 38, borderRadius: 19, justifyContent: "center", alignItems: "center", marginRight: 10 },
  petName: { color: "#171321", fontSize: 14, fontWeight: "800" },
  petType: { color: "#8B8593", fontSize: 12, marginTop: 2 },
  task: { flexDirection: "row", alignItems: "center", paddingVertical: 10, borderTopWidth: 1, borderTopColor: "#F0EDF3" },
  check: { width: 21, height: 21, borderWidth: 1.5, borderColor: "#C9C2D3", borderRadius: 7, justifyContent: "center", alignItems: "center", marginRight: 10 },
  checkCompleted: { backgroundColor: "#7C4DFF", borderColor: "#7C4DFF" },
  taskInfo: { flex: 1 },
  taskTitle: { color: "#282331", fontSize: 13, fontWeight: "700" },
  completedText: { color: "#96909E", textDecorationLine: "line-through" },
  taskDetail: { color: "#8B8593", fontSize: 11, marginTop: 2 },
  taskTime: { color: "#7C4DFF", fontSize: 12, fontWeight: "800" },
  activity: { flexDirection: "row", alignItems: "center", paddingVertical: 8 },
  activityIcon: { width: 36, height: 36, borderRadius: 11, justifyContent: "center", alignItems: "center", marginRight: 10 },
  activityText: { flex: 1, color: "#393341", fontSize: 13, lineHeight: 18 },
  bold: { fontWeight: "800" },
  activityTime: { color: "#96909E", fontSize: 11 },
  reminderCard: { flexDirection: "row", alignItems: "center", backgroundColor: "#7C4DFF", borderRadius: 14, padding: 14, marginBottom: 9 },
  reminderIcon: { width: 34, height: 34, borderRadius: 10, backgroundColor: "rgba(255,255,255,0.18)", justifyContent: "center", alignItems: "center", marginRight: 10, flexShrink: 0 },
  reminderInfo: { flex: 1, minWidth: 0 },
  reminderTitle: { color: "#FFFFFF", fontSize: 13, fontWeight: "800" },
  reminderDetail: { color: "#E9DFFF", fontSize: 11, marginTop: 3 },
  reminderDate: { maxWidth: "34%", color: "#FFFFFF", fontSize: 11, fontWeight: "800", flexShrink: 0, marginLeft: 8, textAlign: "right" },
  petsRow: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  petCard: { flexGrow: 1, flexBasis: 90, minWidth: 90, backgroundColor: "#FFFFFF", borderRadius: 15, padding: 9, alignItems: "center", shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 7, elevation: 1 },
  petLargeAvatar: { width: 53, height: 53, borderRadius: 27, justifyContent: "center", alignItems: "center" },
  emoji: { fontSize: 25 },
  petCardName: { width: "100%", color: "#282331", fontSize: 13, fontWeight: "800", marginTop: 7, textAlign: "center", flexShrink: 1 },
  petBreed: { width: "100%", color: "#8B8593", fontSize: 9, marginTop: 2, textAlign: "center", flexShrink: 1 },
  emptyPets: { width: "100%", color: "#8B8593", fontSize: 13, paddingVertical: 18, flexShrink: 1 },
});
