import { useState } from "react";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";
import { useAppData, type Reminder } from "../../context/app-data-context";

type CalendarOccurrence = {
  id: string;
  reminder: Reminder;
  date: Date;
};

function startOfLocalDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function advanceRecurrence(date: Date, reminder: Reminder, start: Date) {
  const next = new Date(date);
  const interval = reminder.intervalValue ?? 1;
  if (reminder.intervalUnit === "dias") {
    next.setDate(next.getDate() + interval);
  } else if (reminder.intervalUnit === "semanas") {
    next.setDate(next.getDate() + interval * 7);
  } else if (reminder.intervalUnit === "meses") {
    const dayOfMonth = start.getDate();
    next.setDate(1);
    next.setMonth(next.getMonth() + interval);
    next.setDate(Math.min(dayOfMonth, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
  } else if (reminder.intervalUnit === "anios") {
    const monthOfYear = start.getMonth();
    const dayOfMonth = start.getDate();
    next.setDate(1);
    next.setFullYear(next.getFullYear() + interval);
    next.setMonth(monthOfYear);
    next.setDate(Math.min(dayOfMonth, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate()));
  } else {
    return null;
  }
  return next;
}

function occurrencesInMonth(reminder: Reminder, year: number, month: number): CalendarOccurrence[] {
  const start = new Date(reminder.date);
  if (Number.isNaN(start.getTime())) return [];
  const monthStart = new Date(year, month, 1);
  const monthEnd = new Date(year, month + 1, 0);
  const rangeStart = new Date(Math.max(startOfLocalDay(start).getTime(), monthStart.getTime()));
  const end = reminder.endDate ? new Date(reminder.endDate) : null;
  const finalDate = end && !Number.isNaN(end.getTime()) ? new Date(Math.min(end.getTime(), monthEnd.getTime())) : monthEnd;
  if (rangeStart > finalDate || start > monthEnd) return [];

  const result: CalendarOccurrence[] = [];
  if (reminder.modality === "dias_semana") {
    const weekdays = new Set((reminder.weekdays ?? "").split(",").map((day) => Number(day.trim())).filter(Number.isFinite));
    for (const date = new Date(rangeStart); date <= finalDate; date.setDate(date.getDate() + 1)) {
      const isoDay = date.getDay() === 0 ? 7 : date.getDay();
      if (weekdays.has(isoDay)) result.push({ id: `${reminder.id}-${date.toISOString()}`, reminder, date: new Date(date) });
    }
    return result;
  }

  if (reminder.modality === "recurrente" && reminder.intervalValue && reminder.intervalValue > 0) {
    const date = new Date(start);
    while (date < monthStart) {
      const next = advanceRecurrence(date, reminder, start);
      if (!next || next <= date) return [];
      date.setTime(next.getTime());
    }
    while (date <= finalDate) {
      if (date >= monthStart) result.push({ id: `${reminder.id}-${date.toISOString()}`, reminder, date: new Date(date) });
      const next = advanceRecurrence(date, reminder, start);
      if (!next || next <= date) break;
      date.setTime(next.getTime());
    }
    return result;
  }

  if (start >= monthStart && start <= finalDate) return [{ id: reminder.id, reminder, date: start }];
  return [];
}

function reminderIcon(type: string): keyof typeof Ionicons.glyphMap {
  if (type === "VACUNA") return "shield-checkmark-outline";
  if (type === "VISITA_VETERINARIO") return "medkit-outline";
  if (type === "PIPETA_ANTIPARASITARIO") return "bug-outline";
  return "medical-outline";
}

export default function CalendarioScreen() {
  const router = useRouter();
  const { reminders } = useAppData();
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const year = visibleMonth.getFullYear();
  const month = visibleMonth.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const firstWeekdayOffset = (new Date(year, month, 1).getDay() + 6) % 7;
  const occurrences = reminders.flatMap((reminder) => occurrencesInMonth(reminder, year, month))
    .sort((first, second) => first.date.getTime() - second.date.getTime());
  const daysWithReminders = new Set(occurrences.map((item) => item.date.getDate()));
  const monthLabel = visibleMonth.toLocaleDateString("es-AR", { month: "long", year: "numeric" });
  const calendarCells = [
    ...Array.from({ length: firstWeekdayOffset }, (_, index) => ({ key: `empty-${index}`, day: null as number | null })),
    ...Array.from({ length: daysInMonth }, (_, index) => ({ key: `day-${index + 1}`, day: index + 1 })),
  ];

  const moveMonth = (amount: number) => {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + amount, 1));
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Calendario" subtitle="Tus recordatorios y cuidados organizados por fecha." icon="calendar-outline" />
        <View style={styles.month}>
          <TouchableOpacity onPress={() => moveMonth(-1)} accessibilityLabel="Mes anterior"><Ionicons name="chevron-back" size={21} color="#7C4DFF" /></TouchableOpacity>
          <Text style={styles.monthText}>{monthLabel}</Text>
          <TouchableOpacity onPress={() => moveMonth(1)} accessibilityLabel="Mes siguiente"><Ionicons name="chevron-forward" size={21} color="#7C4DFF" /></TouchableOpacity>
        </View>
        <View style={styles.calendar}>
          <View style={styles.week}>{["L", "M", "X", "J", "V", "S", "D"].map((day) => <Text key={day} style={styles.weekDay}>{day}</Text>)}</View>
          <View style={styles.days}>
            {calendarCells.map(({ key, day }) => {
              const today = day === new Date().getDate() && month === new Date().getMonth() && year === new Date().getFullYear();
              return (
                <View key={key} style={[styles.day, today && styles.today]}>
                  {day !== null ? <Text style={[styles.dayText, today && styles.todayText]}>{day}</Text> : null}
                  {day !== null && daysWithReminders.has(day) ? <View style={[styles.dot, today && styles.todayDot]} /> : null}
                </View>
              );
            })}
          </View>
        </View>
        <Text style={styles.sectionTitle}>Recordatorios del mes ({occurrences.length})</Text>
        {occurrences.length ? occurrences.map((occurrence) => (
          <TouchableOpacity
            key={occurrence.id}
            style={styles.event}
            onPress={() => router.push({
              pathname: "/health/recordatorios",
              params: { mascotaId: occurrence.reminder.petId, mascota: occurrence.reminder.pet },
            })}
          >
            <View style={styles.dateBox}>
              <Text style={styles.dateDay}>{String(occurrence.date.getDate()).padStart(2, "0")}</Text>
              <Text style={styles.dateMonth}>{occurrence.date.toLocaleDateString("es-AR", { month: "short" }).toUpperCase()}</Text>
            </View>
            <View style={styles.eventInfo}>
              <Text style={styles.eventTitle}>{occurrence.reminder.title}</Text>
              <Text style={styles.eventPet}>{occurrence.reminder.pet} · {occurrence.reminder.done ? "Completado" : occurrence.reminder.detail}</Text>
            </View>
            <Ionicons name={reminderIcon(occurrence.reminder.type)} size={21} color="#7C4DFF" />
          </TouchableOpacity>
        )) : (
          <View style={styles.empty}><Ionicons name="calendar-clear-outline" size={32} color="#7C4DFF" /><Text style={styles.emptyText}>No hay recordatorios en este mes.</Text></View>
        )}
      </ScrollView>
      <AppBottomNav activeRoute="calendario" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  month: { height: 55, flexDirection: "row", alignItems: "center", justifyContent: "space-between", backgroundColor: "#FFFFFF", borderRadius: 15, paddingHorizontal: 17, marginBottom: 14 },
  monthText: { color: "#201B29", fontSize: 16, fontWeight: "800", textTransform: "capitalize" },
  calendar: { backgroundColor: "#FFFFFF", borderRadius: 17, padding: 12, marginBottom: 27 },
  week: { flexDirection: "row", marginBottom: 9 },
  weekDay: { flex: 1, textAlign: "center", color: "#918A99", fontSize: 11, fontWeight: "700" },
  days: { flexDirection: "row", flexWrap: "wrap" },
  day: { width: "14.28%", aspectRatio: 1, alignItems: "center", justifyContent: "center", position: "relative" },
  today: { backgroundColor: "#7C4DFF", borderRadius: 20 },
  dayText: { color: "#403A48", fontSize: 12, fontWeight: "600" },
  todayText: { color: "#FFFFFF", fontWeight: "800" },
  dot: { position: "absolute", bottom: 5, width: 4, height: 4, borderRadius: 2, backgroundColor: "#7C4DFF" },
  todayDot: { backgroundColor: "#FFFFFF" },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 13 },
  event: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 16, padding: 12, marginBottom: 10 },
  dateBox: { width: 48, height: 48, borderRadius: 12, justifyContent: "center", alignItems: "center", marginRight: 12, backgroundColor: "#EEE8FF" },
  dateDay: { color: "#7C4DFF", fontSize: 16, fontWeight: "800" },
  dateMonth: { color: "#7C4DFF", fontSize: 9, fontWeight: "800", marginTop: 1 },
  eventInfo: { flex: 1 },
  eventTitle: { color: "#201B29", fontSize: 14, fontWeight: "800" },
  eventPet: { color: "#847D8D", fontSize: 12, marginTop: 3 },
  empty: { alignItems: "center", paddingVertical: 34, gap: 8 },
  emptyText: { color: "#837C8D", fontSize: 13 },
});
