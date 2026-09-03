import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";
import { useAppData } from "../context/app-data-context";

export default function CalendarioScreen() {
  const { reminders, events } = useAppData();
  const calendarItems = [
    ...reminders.filter((reminder) => !reminder.done).map((reminder) => ({ id: reminder.id, day: reminder.date.split(" ")[0], month: reminder.date.split(" ")[1] ?? "PRÓX.", title: reminder.title, pet: reminder.pet, icon: "alarm-outline" as const, color: "#7C4DFF" })),
    ...events.map((event) => ({ id: event.id, day: "27", month: "AGO", title: event.title, pet: event.pet, icon: "medical-outline" as const, color: "#2A91BD" })),
  ];
  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Calendario" subtitle="Todos los cuidados organizados por fecha." icon="calendar-outline" />
        <View style={styles.month}><TouchableOpacity><Ionicons name="chevron-back" size={21} color="#7C4DFF" /></TouchableOpacity><Text style={styles.monthText}>Agosto 2026</Text><TouchableOpacity><Ionicons name="chevron-forward" size={21} color="#7C4DFF" /></TouchableOpacity></View>
        <View style={styles.calendar}><View style={styles.week}>{["L", "M", "X", "J", "V", "S", "D"].map((day) => <Text key={day} style={styles.weekDay}>{day}</Text>)}</View><View style={styles.days}>{Array.from({ length: 31 }, (_, index) => index + 1).map((day) => <View key={day} style={[styles.day, day === 27 && styles.today]}><Text style={[styles.dayText, day === 27 && styles.todayText]}>{day}</Text>{[30].includes(day) && <View style={styles.dot} />}</View>)}</View></View>
        <Text style={styles.sectionTitle}>Próximos eventos</Text>
        {calendarItems.map((event) => <TouchableOpacity key={event.id} style={styles.event} onPress={() => Alert.alert(event.title, `Evento para ${event.pet}.`)}><View style={[styles.dateBox, { backgroundColor: `${event.color}18` }]}><Text style={[styles.dateDay, { color: event.color }]}>{event.day}</Text><Text style={[styles.dateMonth, { color: event.color }]}>{event.month}</Text></View><View style={styles.eventInfo}><Text style={styles.eventTitle}>{event.title}</Text><Text style={styles.eventPet}>{event.pet}</Text></View><Ionicons name={event.icon} size={21} color={event.color} /></TouchableOpacity>)}
      </ScrollView>
      <AppBottomNav activeRoute="calendario" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  month: { height: 55, flexDirection: "row", alignItems: "center", justifyContent: "space-between", backgroundColor: "#FFFFFF", borderRadius: 15, paddingHorizontal: 17, marginBottom: 14 }, monthText: { color: "#201B29", fontSize: 16, fontWeight: "800" },
  calendar: { backgroundColor: "#FFFFFF", borderRadius: 17, padding: 12, marginBottom: 27 }, week: { flexDirection: "row", marginBottom: 9 }, weekDay: { flex: 1, textAlign: "center", color: "#918A99", fontSize: 11, fontWeight: "700" }, days: { flexDirection: "row", flexWrap: "wrap" }, day: { width: "14.28%", aspectRatio: 1, alignItems: "center", justifyContent: "center", position: "relative" }, today: { backgroundColor: "#7C4DFF", borderRadius: 20 }, dayText: { color: "#403A48", fontSize: 12, fontWeight: "600" }, todayText: { color: "#FFFFFF", fontWeight: "800" }, dot: { position: "absolute", bottom: 5, width: 4, height: 4, borderRadius: 2, backgroundColor: "#7C4DFF" },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 13 }, event: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 12, marginBottom: 10 }, dateBox: { width: 48, height: 48, borderRadius: 12, justifyContent: "center", alignItems: "center", marginRight: 12 }, dateDay: { fontSize: 16, fontWeight: "800" }, dateMonth: { fontSize: 9, fontWeight: "800", marginTop: 1 }, eventInfo: { flex: 1 }, eventTitle: { color: "#201B29", fontSize: 14, fontWeight: "800" }, eventPet: { color: "#847D8D", fontSize: 12, marginTop: 3 },
});
