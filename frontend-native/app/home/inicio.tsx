import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import AppBottomNav from "@/components/app-bottom-nav";
import { useAppData, type Reminder } from "../../context/app-data-context";

function localDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function displayReminderDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString("es-AR", { day: "2-digit", month: "short" }).toUpperCase();
}

function displayRelativeDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Fecha sin definir";
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const days = Math.round((target.getTime() - today.getTime()) / 86_400_000);
  if (days === 0) return "Hoy";
  if (days === 1) return "Mañana";
  if (days > 1) return `En ${days} días`;
  return `Hace ${Math.abs(days)} días`;
}

function nextReminderDate(reminder: Reminder, fromDate: Date): Date | null {
  const start = new Date(reminder.date);
  if (Number.isNaN(start.getTime())) return null;
  const end = reminder.endDate ? new Date(reminder.endDate) : null;
  const limit = end && !Number.isNaN(end.getTime()) ? end : null;

  if (reminder.modality === "dias_semana") {
    const weekdays = new Set((reminder.weekdays ?? "").split(",").map((day) => Number(day.trim())));
    const candidate = new Date(Math.max(start.getTime(), fromDate.getTime()));
    for (let offset = 0; offset < 7; offset += 1) {
      const day = new Date(candidate);
      day.setDate(candidate.getDate() + offset);
      const isoDay = day.getDay() === 0 ? 7 : day.getDay();
      if (weekdays.has(isoDay) && (!limit || day <= limit)) {
        day.setHours(start.getHours(), start.getMinutes(), start.getSeconds(), start.getMilliseconds());
        if (day >= start && day >= fromDate) return day;
      }
    }
    return null;
  }

  if (reminder.modality !== "recurrente" || !reminder.intervalValue || reminder.intervalValue < 1) {
    return start >= fromDate && (!limit || start <= limit) ? start : null;
  }

  let candidate = new Date(start);
  while (candidate < fromDate && (!limit || candidate <= limit)) {
    const next = new Date(candidate);
    if (reminder.intervalUnit === "dias") next.setDate(next.getDate() + reminder.intervalValue);
    else if (reminder.intervalUnit === "semanas") next.setDate(next.getDate() + reminder.intervalValue * 7);
    else if (reminder.intervalUnit === "meses") {
      const dayOfMonth = start.getDate();
      next.setDate(1);
      next.setMonth(next.getMonth() + reminder.intervalValue);
      next.setDate(Math.min(dayOfMonth, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
    } else if (reminder.intervalUnit === "anios") {
      const monthOfYear = start.getMonth();
      const dayOfMonth = start.getDate();
      next.setDate(1);
      next.setFullYear(next.getFullYear() + reminder.intervalValue);
      next.setMonth(monthOfYear);
      next.setDate(Math.min(dayOfMonth, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
    } else {
      return null;
    }
    if (next <= candidate) return null;
    candidate = next;
  }
  return !limit || candidate <= limit ? candidate : null;
}

function reminderIcon(type: string): keyof typeof Ionicons.glyphMap {
  if (type === "VACUNA") return "shield-checkmark-outline";
  if (type === "VISITA_VETERINARIO") return "medkit-outline";
  if (type === "PIPETA_ANTIPARASITARIO") return "bug-outline";
  return "medical-outline";
}

function ReminderCard({ reminder, occurrenceDate = reminder.date }: { reminder: Reminder; occurrenceDate?: string }) {
  return (
    <View style={styles.reminderCard}>
      <View style={styles.reminderIcon}><Ionicons name={reminderIcon(reminder.type)} size={21} color="#FFFFFF" /></View>
      <View style={styles.reminderInfo}>
        <Text numberOfLines={1} style={styles.reminderTitle}>{reminder.title}</Text>
        <Text style={styles.reminderDetail}>{reminder.pet} · {displayRelativeDate(occurrenceDate)}</Text>
      </View>
      <Text style={styles.reminderDate}>{displayReminderDate(occurrenceDate)}</Text>
    </View>
  );
}

export default function InicioScreen() {
  const router = useRouter();
  const { currentUser, pets, reminders, remindersError } = useAppData();
  const userName = currentUser?.name ?? "Usuario";
  const userInitials = userName.slice(0, 2).toUpperCase();
  const today = new Date();
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  const upcomingReminders = reminders
    .filter((reminder) => !reminder.done)
    .map((reminder) => ({ reminder, occurrenceDate: nextReminderDate(reminder, new Date(todayStart)) }))
    .filter((item): item is { reminder: Reminder; occurrenceDate: Date } => item.occurrenceDate !== null)
    .sort((first, second) => first.occurrenceDate.getTime() - second.occurrenceDate.getTime());
  const todaysReminders = upcomingReminders.filter((item) => localDateKey(item.occurrenceDate) === localDateKey(today));

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <View style={styles.brand}><Ionicons name="paw-outline" size={25} color="#7C4DFF" /><Text style={styles.brandText}>Mis Mascotas</Text></View>
          <TouchableOpacity style={styles.avatar} onPress={() => router.push("/account/perfil")}><Text style={styles.avatarText}>{userInitials}</Text></TouchableOpacity>
        </View>

        <View style={styles.welcome}>
          <Text style={styles.eyebrow}>{today.toLocaleDateString("es-AR", { weekday: "long", day: "numeric", month: "long" }).toLocaleUpperCase("es-AR")}</Text>
          <Text style={styles.title}>Hola, {userName} 👋</Text>
          <Text style={styles.subtitle}>Todo el cuidado de tus mascotas, en un solo lugar.</Text>
        </View>

        <View style={styles.summary}>
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>{pets.length}</Text><Text style={styles.summaryLabel}>Mascotas</Text></View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>{upcomingReminders.length}</Text><Text style={styles.summaryLabel}>Próximos</Text></View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}><Text style={styles.summaryNumber}>{todaysReminders.length}</Text><Text style={styles.summaryLabel}>Para hoy</Text></View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Hoy</Text>
          <View style={styles.badge}><Text style={styles.badgeText}>{todaysReminders.length} pendientes</Text></View>
        </View>
        {todaysReminders.length ? todaysReminders.map(({ reminder, occurrenceDate }) => <ReminderCard key={`${reminder.id}-${occurrenceDate.toISOString()}`} reminder={reminder} occurrenceDate={occurrenceDate.toISOString()} />) : (
          <View style={styles.card}><Text style={styles.emptyText}>{remindersError ?? "No tenés recordatorios pendientes para hoy."}</Text></View>
        )}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Actividad reciente</Text>
          <Text style={styles.link}>Ver todo</Text>
        </View>
        <View style={styles.card}>
          <View style={styles.activity}>
            <View style={[styles.activityIcon, { backgroundColor: "#EEE8FF" }]}><Ionicons name="medical-outline" size={20} color="#7C4DFF" /></View>
            <Text style={styles.activityText}><Text style={styles.bold}>Laura</Text> marcó que le dio Amoxicilina a Fido{"\n"}<Text style={styles.activityTime}>Hace 25 min</Text></Text>
          </View>
          <View style={styles.activity}>
            <View style={[styles.activityIcon, { backgroundColor: "#E2F3FC" }]}><Ionicons name="heart-outline" size={20} color="#2789B9" /></View>
            <Text style={styles.activityText}>Registraste una consulta de Luna{"\n"}<Text style={styles.activityTime}>Ayer, 18:40</Text></Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Próximos recordatorios</Text>
          <TouchableOpacity onPress={() => router.push("/health/recordatorios")}><Text style={styles.link}>Ver todos</Text></TouchableOpacity>
        </View>
        {remindersError ? <View style={styles.card}><Text style={styles.errorText}>{remindersError}</Text></View> : null}
        {upcomingReminders.slice(0, 3).map(({ reminder, occurrenceDate }) => (
          <TouchableOpacity
            key={`${reminder.id}-${occurrenceDate.toISOString()}`}
            onPress={() => router.push({ pathname: "/health/recordatorios", params: { mascotaId: reminder.petId, mascota: reminder.pet } })}
          >
            <ReminderCard reminder={reminder} occurrenceDate={occurrenceDate.toISOString()} />
          </TouchableOpacity>
        ))}
        {!remindersError && !upcomingReminders.length ? (
          <View style={styles.card}><Text style={styles.emptyText}>No hay próximos recordatorios.</Text></View>
        ) : null}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Mis mascotas</Text>
          <TouchableOpacity onPress={() => router.push("/pets/mascotas")}><Text style={styles.link}>Administrar</Text></TouchableOpacity>
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
  brand: { flexDirection: "row", alignItems: "center", gap: 7 },
  brandText: { color: "#7C4DFF", fontSize: 16, fontWeight: "700" },
  avatar: { width: 38, height: 38, borderRadius: 19, backgroundColor: "#7C4DFF", justifyContent: "center", alignItems: "center" },
  avatarText: { color: "#FFFFFF", fontSize: 12, fontWeight: "800" },
  welcome: { marginTop: 30 },
  eyebrow: { color: "#7C4DFF", fontSize: 11, fontWeight: "800", letterSpacing: 1 },
  title: { color: "#171321", fontSize: 29, fontWeight: "700", marginTop: 8 },
  subtitle: { color: "#756F80", fontSize: 14, marginTop: 6 },
  summary: { flexDirection: "row", backgroundColor: "#FFFFFF", borderRadius: 16, marginTop: 24, paddingVertical: 17, alignItems: "center", shadowColor: "#312642", shadowOpacity: 0.06, shadowRadius: 9, elevation: 2 },
  summaryItem: { flex: 1, alignItems: "center" },
  summaryNumber: { color: "#7C4DFF", fontSize: 22, fontWeight: "800" },
  summaryLabel: { color: "#756F80", fontSize: 12, marginTop: 3 },
  summaryDivider: { width: 1, height: 32, backgroundColor: "#EEEAF2" },
  sectionHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 27, marginBottom: 11 },
  sectionTitle: { color: "#171321", fontSize: 19, fontWeight: "700" },
  badge: { backgroundColor: "#EEE8FF", paddingHorizontal: 9, paddingVertical: 5, borderRadius: 99 },
  badgeText: { color: "#6844D9", fontSize: 11, fontWeight: "700" },
  link: { color: "#7C4DFF", fontSize: 13, fontWeight: "700" },
  card: { backgroundColor: "#FFFFFF", borderRadius: 16, padding: 15, marginBottom: 12, shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 8, elevation: 1 },
  activity: { flexDirection: "row", alignItems: "center", paddingVertical: 8 },
  activityIcon: { width: 36, height: 36, borderRadius: 11, justifyContent: "center", alignItems: "center", marginRight: 10 },
  activityText: { flex: 1, color: "#393341", fontSize: 13, lineHeight: 18 },
  bold: { fontWeight: "800" },
  activityTime: { color: "#96909E", fontSize: 11 },
  reminderCard: { flexDirection: "row", alignItems: "center", backgroundColor: "#7C4DFF", borderRadius: 14, padding: 14, marginBottom: 9 },
  reminderIcon: { width: 34, height: 34, borderRadius: 10, backgroundColor: "rgba(255,255,255,0.18)", justifyContent: "center", alignItems: "center", marginRight: 10 },
  reminderInfo: { flex: 1 },
  reminderTitle: { color: "#FFFFFF", fontSize: 13, fontWeight: "800" },
  reminderDetail: { color: "#E9DFFF", fontSize: 11, marginTop: 3 },
  reminderDate: { color: "#FFFFFF", fontSize: 11, fontWeight: "800" },
  emptyText: { color: "#8B8593", fontSize: 13, lineHeight: 19 },
  errorText: { color: "#B63E49", fontSize: 13, lineHeight: 19 },
  petsRow: { flexDirection: "row", gap: 10 },
  petCard: { flex: 1, backgroundColor: "#FFFFFF", borderRadius: 15, padding: 9, alignItems: "center", shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 7, elevation: 1 },
  petLargeAvatar: { width: 53, height: 53, borderRadius: 27, justifyContent: "center", alignItems: "center" },
  emoji: { fontSize: 25 },
  petCardName: { color: "#282331", fontSize: 13, fontWeight: "800", marginTop: 7 },
  petBreed: { color: "#8B8593", fontSize: 9, marginTop: 2, textAlign: "center" },
  emptyPets: { color: "#8B8593", fontSize: 13, paddingVertical: 18 },
});
