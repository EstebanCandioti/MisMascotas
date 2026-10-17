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
            <Ionicons style={styles.brandIcon} name="paw-outline" size={25} color="#7C4DFF" />
            <View style={styles.brandTextBox}>
              <Text maxFontSizeMultiplier={1.2} style={styles.brandText}>Mis Mascotas</Text>
            </View>
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
            <View style={styles.summaryLabelBox}>
              <Text maxFontSizeMultiplier={1.2} style={styles.summaryLabel}>Mascotas</Text>
            </View>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>{pendingReminders.length}</Text>
            <View style={styles.summaryLabelBox}>
              <Text maxFontSizeMultiplier={1.2} style={styles.summaryLabel}>Próximos</Text>
            </View>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>0</Text>
            <View style={styles.summaryLabelBox}>
              <Text maxFontSizeMultiplier={1.2} style={styles.summaryLabel}>Compartido</Text>
            </View>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <View style={styles.sectionTitleBox}>
            <Text maxFontSizeMultiplier={1.3} style={styles.sectionTitle}>Próximos recordatorios</Text>
          </View>
          <TouchableOpacity style={styles.headerAction} onPress={() => router.push("/health/recordatorios")}>
            <Text maxFontSizeMultiplier={1.2} style={styles.link}>Ver todos</Text>
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
            <View style={styles.reminderDateBox}>
              <Text style={styles.reminderDate}>{reminder.date}</Text>
            </View>
          </View>
        ))}
        {!pendingReminders.length && (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No tenés recordatorios pendientes.</Text>
          </View>
        )}

        <View style={styles.sectionHeader}>
          <View style={styles.sectionTitleBox}>
            <Text maxFontSizeMultiplier={1.3} style={styles.sectionTitle}>Mis mascotas</Text>
          </View>
          <TouchableOpacity style={styles.headerAction} onPress={() => router.push("/pets/mascotas")}>
            <Text maxFontSizeMultiplier={1.2} style={styles.link}>Administrar</Text>
          </TouchableOpacity>
        </View>
        {pets.length ? (
          <View style={styles.petsRow}>
            {pets.slice(0, 3).map((pet) => (
            <TouchableOpacity key={pet.id} style={styles.petCard} onPress={() => router.push({ pathname: "/pets/mascota-detalle", params: { id: pet.id } })}>
              <View style={[styles.petLargeAvatar, { backgroundColor: pet.color }]}><Text style={styles.emoji}>{pet.emoji}</Text></View>
              <Text style={styles.petCardName}>{pet.name}</Text>
              <Text style={styles.petBreed}>{pet.breed}</Text>
            </TouchableOpacity>
            ))}
          </View>
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>Todavía no tenés mascotas registradas.</Text>
          </View>
        )}
      </ScrollView>

      <AppBottomNav activeRoute="inicio" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  content: { paddingHorizontal: 20, paddingTop: 20, paddingBottom: 36, alignItems: "stretch" },
  topBar: { alignSelf: "stretch", minHeight: 44, flexDirection: "row", alignItems: "center" },
  brand: { flexGrow: 1, flexBasis: 0, minWidth: 0, flexDirection: "row", alignItems: "center", paddingRight: 12 },
  brandIcon: { flexShrink: 0, marginRight: 8 },
  brandTextBox: { flexGrow: 1, flexBasis: 0, minWidth: 0 },
  brandText: { width: "100%", color: "#7C4DFF", fontSize: 16, lineHeight: 22, fontWeight: "700" },
  avatar: { width: 38, height: 38, borderRadius: 19, backgroundColor: "#7C4DFF", justifyContent: "center", alignItems: "center", flexShrink: 0 },
  avatarText: { color: "#FFFFFF", fontSize: 12, fontWeight: "800" },
  welcome: { marginTop: 30 },
  eyebrow: { width: "100%", color: "#7C4DFF", fontSize: 11, lineHeight: 16, fontWeight: "800", letterSpacing: 1 },
  title: { width: "100%", color: "#171321", fontSize: 29, lineHeight: 36, fontWeight: "700", marginTop: 8 },
  subtitle: { width: "100%", color: "#756F80", fontSize: 14, lineHeight: 20, marginTop: 6 },
  summary: { alignSelf: "stretch", flexDirection: "row", alignItems: "stretch", backgroundColor: "#FFFFFF", borderRadius: 16, marginTop: 24, paddingVertical: 17, shadowColor: "#312642", shadowOpacity: 0.06, shadowRadius: 9, elevation: 2 },
  summaryItem: { flexGrow: 1, flexBasis: 0, minWidth: 0, alignItems: "center", justifyContent: "flex-start", paddingHorizontal: 4 },
  summaryNumber: { color: "#7C4DFF", fontSize: 22, lineHeight: 27, fontWeight: "800" },
  summaryLabelBox: { width: "100%", minHeight: 30, marginTop: 3, justifyContent: "flex-start" },
  summaryLabel: { width: "100%", color: "#756F80", fontSize: 11, lineHeight: 15, textAlign: "center" },
  summaryDivider: { width: 1, height: 32, alignSelf: "center", backgroundColor: "#EEEAF2" },
  sectionHeader: { alignSelf: "stretch", minHeight: 30, flexDirection: "row", alignItems: "flex-start", marginTop: 27, marginBottom: 11 },
  sectionTitleBox: { flexGrow: 1, flexBasis: 0, minWidth: 0, paddingRight: 8 },
  sectionTitle: { width: "100%", color: "#171321", fontSize: 19, lineHeight: 25, fontWeight: "700" },
  headerAction: { width: 96, minHeight: 28, flexShrink: 0, paddingVertical: 2, justifyContent: "flex-start", alignItems: "flex-end" },
  link: { width: "100%", color: "#7C4DFF", fontSize: 13, lineHeight: 20, fontWeight: "700", textAlign: "right" },
  reminderCard: { alignSelf: "stretch", flexDirection: "row", alignItems: "center", backgroundColor: "#7C4DFF", borderRadius: 14, padding: 14, marginBottom: 9 },
  reminderIcon: { width: 34, height: 34, borderRadius: 10, backgroundColor: "rgba(255,255,255,0.18)", justifyContent: "center", alignItems: "center", marginRight: 10, flexShrink: 0 },
  reminderInfo: { flexGrow: 1, flexBasis: 0, minWidth: 0 },
  reminderTitle: { width: "100%", color: "#FFFFFF", fontSize: 13, lineHeight: 18, fontWeight: "800" },
  reminderDetail: { width: "100%", color: "#E9DFFF", fontSize: 11, lineHeight: 16, marginTop: 3 },
  reminderDateBox: { width: 72, maxWidth: "30%", flexShrink: 0, marginLeft: 8 },
  reminderDate: { width: "100%", color: "#FFFFFF", fontSize: 11, lineHeight: 16, fontWeight: "800", textAlign: "right" },
  emptyState: { alignSelf: "stretch", paddingVertical: 18 },
  emptyText: { width: "100%", color: "#8B8593", fontSize: 13, lineHeight: 19 },
  petsRow: { alignSelf: "stretch", flexDirection: "row", flexWrap: "wrap", gap: 10 },
  petCard: { flexGrow: 1, flexBasis: 90, minWidth: 90, backgroundColor: "#FFFFFF", borderRadius: 15, padding: 9, alignItems: "center", shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 7, elevation: 1 },
  petLargeAvatar: { width: 53, height: 53, borderRadius: 27, justifyContent: "center", alignItems: "center" },
  emoji: { fontSize: 25 },
  petCardName: { width: "100%", color: "#282331", fontSize: 13, lineHeight: 18, fontWeight: "800", marginTop: 7, textAlign: "center" },
  petBreed: { width: "100%", color: "#8B8593", fontSize: 9, lineHeight: 13, marginTop: 2, textAlign: "center" },
});
