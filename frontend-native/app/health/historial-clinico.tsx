import { Alert, Linking, SafeAreaView, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData, type ClinicalEvent } from "../../context/app-data-context";

const eventLabels: Record<string, string> = {
  CONSULTA: "Consulta",
  VACUNA: "Vacuna",
  MEDICACION: "Medicación",
  PESO: "Peso",
  OTRO: "Otro",
};

function getYear(date: string) {
  return date.match(/\b\d{4}\b/)?.[0] ?? "Otros";
}

function formatDate(date: string) {
  const isoDate = date.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!isoDate) return date;
  const [, year, month, day] = isoDate;
  const parsed = new Date(Number(year), Number(month) - 1, Number(day));
  return parsed.toLocaleDateString("es-AR", { day: "numeric", month: "long", year: "numeric" });
}

function eventTimestamp(date: string) {
  const parsed = Date.parse(date);
  return Number.isNaN(parsed) ? 0 : parsed;
}

function eventTypeLabel(type: string) {
  return eventLabels[type.toUpperCase()] ?? type;
}

export default function HistorialClinicoScreen() {
  const router = useRouter();
  const { mascotaId, mascota } = useLocalSearchParams<{ mascotaId?: string; mascota?: string }>();
  const { pets, events, setEvents } = useAppData();
  const pet = pets.find((item) => item.id === mascotaId)
    ?? pets.find((item) => item.name === mascota);
  const petEvents = events
    .filter((event) => pet && (event.petId ? event.petId === pet.id : event.pet === pet.name))
    .sort((first, second) => eventTimestamp(second.date) - eventTimestamp(first.date));
  const groupedEvents = petEvents.reduce<Record<string, ClinicalEvent[]>>((groups, event) => {
    const year = getYear(event.date);
    groups[year] = [...(groups[year] ?? []), event];
    return groups;
  }, {});

  const openEditor = (event?: ClinicalEvent) => {
    if (!pet) return;
    router.push({
      pathname: "/health/registrar-evento",
      params: {
        mascotaId: pet.id,
        mascota: pet.name,
        ...(event ? { eventoId: event.id } : {}),
      },
    });
  };

  const deleteEvent = (event: ClinicalEvent) => {
    Alert.alert("Eliminar evento", `¿Querés eliminar “${event.title}” del historial de ${pet?.name ?? "la mascota"}?`, [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Eliminar",
        style: "destructive",
        onPress: () => setEvents(events.filter((item) => item.id !== event.id)),
      },
    ]);
  };

  const openAttachment = async (url: string) => {
    try {
      await Linking.openURL(url);
    } catch {
      Alert.alert("No se pudo abrir el adjunto", "Verificá que la dirección siga disponible.");
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()} accessibilityLabel="Volver">
          <Ionicons name="chevron-back" size={24} color="#30293A" />
        </TouchableOpacity>
        <Text style={styles.topTitle}>Historial clínico</Text>
        <TouchableOpacity style={styles.add} onPress={() => openEditor()} accessibilityLabel="Registrar evento clínico">
          <Ionicons name="add" size={24} color="#7C4DFF" />
        </TouchableOpacity>
      </View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {!pet ? (
          <View style={styles.empty}>
            <Ionicons name="paw-outline" size={38} color="#7C4DFF" />
            <Text style={styles.emptyTitle}>No encontramos esa mascota</Text>
            <Text style={styles.emptyText}>Volvé al detalle de una mascota para consultar su historial clínico.</Text>
          </View>
        ) : (
          <>
            <View style={styles.hero}>
              <View style={styles.heroIcon}><Ionicons name="document-text-outline" size={28} color="#7C4DFF" /></View>
              <View><Text style={styles.heroTitle}>{pet.name}</Text><Text style={styles.heroText}>Eventos y controles de salud</Text></View>
            </View>
            <View style={styles.notice}>
              <Ionicons name="information-circle-outline" size={19} color="#806227" />
              <Text style={styles.noticeText}>Los eventos se guardan en este dispositivo. El backend aún no ofrece una API clínica.</Text>
            </View>

            {!petEvents.length ? (
              <View style={styles.empty}>
                <Ionicons name="medical-outline" size={38} color="#7C4DFF" />
                <Text style={styles.emptyTitle}>Todavía no hay eventos clínicos</Text>
                <Text style={styles.emptyText}>Registrá consultas, vacunas, medicación, peso y otros datos de salud.</Text>
              </View>
            ) : Object.entries(groupedEvents).map(([year, yearEvents]) => (
              <View key={year}>
                <Text style={styles.sectionTitle}>{year}</Text>
                <View style={styles.timeline}>
                  {yearEvents.map((event, index) => (
                    <View key={event.id} style={styles.eventRow}>
                      <View style={styles.timelineColumn}>
                        <View style={styles.eventDot}><Ionicons name="medical-outline" size={18} color="#7C4DFF" /></View>
                        {index !== yearEvents.length - 1 && <View style={styles.timelineLine} />}
                      </View>
                      <View style={styles.eventCard}>
                        <Text style={styles.eventType}>{eventTypeLabel(event.type).toUpperCase()}</Text>
                        <Text style={styles.eventTitle}>{event.title}</Text>
                        {event.reason ? <Text style={styles.eventDetail}>Motivo: {event.reason}</Text> : null}
                        {event.diagnosis ? <Text style={styles.eventDetail}>Diagnóstico: {event.diagnosis}</Text> : null}
                        {event.dose ? <Text style={styles.eventDetail}>Dosis: {event.dose}</Text> : null}
                        {event.weight != null ? <Text style={styles.eventDetail}>Peso: {event.weight} kg</Text> : null}
                        {event.observations || event.detail ? <Text style={styles.eventDetail}>{event.observations ?? event.detail}</Text> : null}
                        <Text style={styles.eventDate}>{formatDate(event.date)}</Text>
                        {event.attachmentUrl ? (
                          <TouchableOpacity
                            style={styles.attachment}
                            onPress={() => {
                              if (event.attachmentUrl) void openAttachment(event.attachmentUrl);
                            }}
                          >
                            <Ionicons name="attach-outline" size={17} color="#7C4DFF" />
                            <Text style={styles.attachmentText}>Abrir adjunto{event.attachmentFormat ? ` (${event.attachmentFormat})` : ""}</Text>
                          </TouchableOpacity>
                        ) : null}
                        <View style={styles.actions}>
                          <TouchableOpacity style={styles.action} onPress={() => openEditor(event)}>
                            <Ionicons name="pencil-outline" size={16} color="#7C4DFF" />
                            <Text style={styles.actionText}>Editar</Text>
                          </TouchableOpacity>
                          <TouchableOpacity style={styles.action} onPress={() => deleteEvent(event)}>
                            <Ionicons name="trash-outline" size={16} color="#D8515D" />
                            <Text style={styles.deleteText}>Eliminar</Text>
                          </TouchableOpacity>
                        </View>
                      </View>
                    </View>
                  ))}
                </View>
              </View>
            ))}
            <TouchableOpacity style={styles.addEvent} onPress={() => openEditor()}>
              <Ionicons name="add-circle-outline" size={22} color="#FFFFFF" />
              <Text style={styles.addText}>Registrar evento clínico</Text>
            </TouchableOpacity>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 },
  back: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#FFFFFF" },
  topTitle: { color: "#201B29", fontSize: 17, fontWeight: "800" },
  add: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center", backgroundColor: "#EEE8FF" },
  content: { flexGrow: 1, padding: 20, paddingTop: 8, paddingBottom: 36 },
  hero: { flexDirection: "row", alignItems: "center", gap: 13, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 16, marginBottom: 12 },
  heroIcon: { width: 49, height: 49, borderRadius: 15, backgroundColor: "#FFFFFF", justifyContent: "center", alignItems: "center" },
  heroTitle: { color: "#292332", fontSize: 17, fontWeight: "800" },
  heroText: { color: "#6E6678", fontSize: 12, marginTop: 4 },
  notice: { flexDirection: "row", alignItems: "center", gap: 8, padding: 12, borderRadius: 13, backgroundColor: "#FFF7E5", marginBottom: 24 },
  noticeText: { flex: 1, color: "#725C31", fontSize: 12, lineHeight: 17 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 14 },
  timeline: { paddingLeft: 2 },
  eventRow: { flexDirection: "row" },
  timelineColumn: { width: 45, alignItems: "center" },
  eventDot: { width: 36, height: 36, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  timelineLine: { flex: 1, width: 2, backgroundColor: "#E6E0EC", marginVertical: 3 },
  eventCard: { flex: 1, backgroundColor: "#FFFFFF", borderRadius: 16, padding: 14, marginBottom: 13 },
  eventType: { color: "#7C4DFF", fontSize: 10, fontWeight: "800", letterSpacing: 0.6 },
  eventTitle: { color: "#292332", fontSize: 15, fontWeight: "800", marginTop: 5 },
  eventDetail: { color: "#7F7888", fontSize: 12, marginTop: 5, lineHeight: 17 },
  eventDate: { color: "#9B94A1", fontSize: 10, fontWeight: "700", marginTop: 10 },
  attachment: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 10 },
  attachmentText: { color: "#7C4DFF", fontSize: 12, fontWeight: "700" },
  actions: { flexDirection: "row", gap: 17, marginTop: 13, paddingTop: 10, borderTopWidth: 1, borderTopColor: "#F0EDF3" },
  action: { flexDirection: "row", alignItems: "center", gap: 5 },
  actionText: { color: "#7C4DFF", fontSize: 12, fontWeight: "700" },
  deleteText: { color: "#D8515D", fontSize: 12, fontWeight: "700" },
  empty: { alignItems: "center", justifyContent: "center", paddingVertical: 48, paddingHorizontal: 20 },
  emptyTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800", textAlign: "center", marginTop: 11 },
  emptyText: { color: "#837C8D", fontSize: 13, lineHeight: 19, textAlign: "center", marginTop: 6 },
  addEvent: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 10 },
  addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
