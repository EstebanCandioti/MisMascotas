import { SafeAreaView, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";

export default function HistorialClinicoScreen() {
  const router = useRouter();
  const { mascota = "Fido" } = useLocalSearchParams<{ mascota?: string }>();
  const { events } = useAppData();
  const petEvents = events.filter((event) => event.pet === mascota);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity>
        <Text style={styles.topTitle}>Historial clínico</Text>
        <TouchableOpacity style={styles.add} onPress={() => router.push({ pathname: "/registrar-evento", params: { mascota } })}><Ionicons name="add" size={24} color="#7C4DFF" /></TouchableOpacity>
      </View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.hero}><View style={styles.heroIcon}><Ionicons name="document-text-outline" size={28} color="#7C4DFF" /></View><View><Text style={styles.heroTitle}>{mascota}</Text><Text style={styles.heroText}>Eventos y controles de salud</Text></View></View>
        <Text style={styles.sectionTitle}>2026</Text>
        <View style={styles.timeline}>
          {petEvents.map((event, index) => <View key={event.id} style={styles.eventRow}><View style={styles.timelineColumn}><View style={styles.eventDot}><Ionicons name="medical-outline" size={18} color="#7C4DFF" /></View>{index !== petEvents.length - 1 && <View style={styles.timelineLine} />}</View><View style={styles.eventCard}><Text style={styles.eventType}>{event.type.toUpperCase()}</Text><Text style={styles.eventTitle}>{event.title}</Text><Text style={styles.eventDetail}>{event.detail}</Text><Text style={styles.eventDate}>{event.date}</Text></View></View>)}
        </View>
        <TouchableOpacity style={styles.addEvent} onPress={() => router.push({ pathname: "/registrar-evento", params: { mascota } })}><Ionicons name="add-circle-outline" size={22} color="#FFFFFF" /><Text style={styles.addText}>Registrar evento clínico</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 }, back: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" }, topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" }, add: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#EEE8FF" }, content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  hero: { flexDirection: "row", alignItems: "center", gap: 13, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 16, marginBottom: 28 }, heroIcon: { width: 49, height: 49, borderRadius: 15, backgroundColor: "#FFFFFF", justifyContent: "center", alignItems: "center" }, heroTitle: { color: "#292332", fontSize: 17, fontWeight: "800" }, heroText: { color: "#6E6678", fontSize: 12, marginTop: 4 }, sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 14 },
  timeline: { paddingLeft: 2 }, eventRow: { flexDirection: "row" }, timelineColumn: { width: 45, alignItems: "center" }, eventDot: { width: 36, height: 36, borderRadius: 12, alignItems: "center", justifyContent: "center" }, timelineLine: { flex: 1, width: 2, backgroundColor: "#E6E0EC", marginVertical: 3 }, eventCard: { flex: 1, backgroundColor: "#FFFFFF", borderRadius: 16, padding: 14, marginBottom: 13 }, eventType: { fontSize: 10, fontWeight: "800", letterSpacing: 0.6 }, eventTitle: { color: "#292332", fontSize: 15, fontWeight: "800", marginTop: 5 }, eventDetail: { color: "#7F7888", fontSize: 12, marginTop: 5, lineHeight: 17 }, eventDate: { color: "#9B94A1", fontSize: 10, fontWeight: "700", marginTop: 10 },
  addEvent: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 10 }, addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
