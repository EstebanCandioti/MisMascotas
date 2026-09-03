import { useState } from "react";
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

type TaskProps = {
  title: string;
  detail: string;
  time: string;
};

function Task({ title, detail, time }: TaskProps) {
  const [completed, setCompleted] = useState(false);

  return (
    <TouchableOpacity style={styles.task} onPress={() => setCompleted(!completed)}>
      <View style={[styles.check, completed && styles.checkCompleted]}>
        {completed && <Ionicons name="checkmark" size={15} color="#FFFFFF" />}
      </View>
      <View style={styles.taskInfo}>
        <Text style={[styles.taskTitle, completed && styles.completedText]}>{title}</Text>
        <Text style={styles.taskDetail}>{detail}</Text>
      </View>
      <Text style={styles.taskTime}>{time}</Text>
    </TouchableOpacity>
  );
}

export default function InicioScreen() {
  const router = useRouter();
  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <View style={styles.brand}>
            <Ionicons name="paw-outline" size={25} color="#7C4DFF" />
            <Text style={styles.brandText}>Mis Mascotas</Text>
          </View>
          <TouchableOpacity style={styles.avatar} onPress={() => router.push("/perfil")}>
            <Text style={styles.avatarText}>US</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.welcome}>
          <Text style={styles.eyebrow}>JUEVES, 27 DE AGOSTO</Text>
          <Text style={styles.title}>Hola, Usuario 👋</Text>
          <Text style={styles.subtitle}>Todo el cuidado de tus mascotas, en un solo lugar.</Text>
        </View>

        <View style={styles.summary}>
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>3</Text>
            <Text style={styles.summaryLabel}>Mascotas</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>2</Text>
            <Text style={styles.summaryLabel}>Próximos</Text>
          </View>
          <View style={styles.summaryDivider} />
          <View style={styles.summaryItem}>
            <Text style={styles.summaryNumber}>1</Text>
            <Text style={styles.summaryLabel}>Compartido</Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Hoy</Text>
          <View style={styles.badge}><Text style={styles.badgeText}>3 pendientes</Text></View>
        </View>

        <View style={styles.card}>
          <View style={styles.petHeader}>
            <View style={[styles.petAvatar, { backgroundColor: "#F2D5A0" }]}><Text>🐶</Text></View>
            <View><Text style={styles.petName}>Fido</Text><Text style={styles.petType}>Salud preventiva</Text></View>
          </View>
          <Task title="Selamectina 18 mg" detail="Aplicar pipeta antiparasitaria" time="20:00" />
          <Task title="Comprimido antigarrapatas" detail="Con la comida" time="21:00" />
        </View>

        <View style={styles.card}>
          <View style={styles.petHeader}>
            <View style={[styles.petAvatar, { backgroundColor: "#D9CDFC" }]}><Text>🐱</Text></View>
            <View><Text style={styles.petName}>Luna</Text><Text style={styles.petType}>Cuidado diario</Text></View>
          </View>
          <Task title="Pipeta antiparasitaria" detail="Recordatorio mensual" time="Hoy" />
        </View>

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
          <TouchableOpacity onPress={() => router.push("/recordatorios")}>
            <Text style={styles.link}>Ver todos</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.reminderCard}>
          <View style={styles.reminderIcon}>
            <Ionicons name="medical-outline" size={21} color="#FFFFFF" />
          </View>
          <View style={styles.reminderInfo}>
            <Text style={styles.reminderTitle}>Vacuna antirrábica</Text>
            <Text style={styles.reminderDetail}>Fido · En 3 días</Text>
          </View>
          <Text style={styles.reminderDate}>30 AGO</Text>
        </View>
        <View style={styles.reminderCard}>
          <View style={styles.reminderIcon}>
            <Ionicons name="heart-outline" size={21} color="#FFFFFF" />
          </View>
          <View style={styles.reminderInfo}>
            <Text style={styles.reminderTitle}>Control anual</Text>
            <Text style={styles.reminderDetail}>Rex · En 7 días</Text>
          </View>
          <Text style={styles.reminderDate}>03 SEP</Text>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Mis mascotas</Text>
          <Text style={styles.link}>Administrar</Text>
        </View>
        <View style={styles.petsRow}>
          <View style={styles.petCard}><View style={[styles.petLargeAvatar, { backgroundColor: "#F2D5A0" }]}><Text style={styles.emoji}>🐶</Text></View><Text style={styles.petCardName}>Fido</Text><Text style={styles.petBreed}>Golden Retriever</Text></View>
          <View style={styles.petCard}><View style={[styles.petLargeAvatar, { backgroundColor: "#D9CDFC" }]}><Text style={styles.emoji}>🐱</Text></View><Text style={styles.petCardName}>Luna</Text><Text style={styles.petBreed}>Siamés</Text></View>
          <View style={styles.petCard}><View style={[styles.petLargeAvatar, { backgroundColor: "#C9E5F3" }]}><Text style={styles.emoji}>🐶</Text></View><Text style={styles.petCardName}>Rex</Text><Text style={styles.petBreed}>Bulldog</Text></View>
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
  reminderIcon: { width: 34, height: 34, borderRadius: 10, backgroundColor: "rgba(255,255,255,0.18)", justifyContent: "center", alignItems: "center", marginRight: 10 },
  reminderInfo: { flex: 1 },
  reminderTitle: { color: "#FFFFFF", fontSize: 13, fontWeight: "800" },
  reminderDetail: { color: "#E9DFFF", fontSize: 11, marginTop: 3 },
  reminderDate: { color: "#FFFFFF", fontSize: 11, fontWeight: "800" },
  petsRow: { flexDirection: "row", gap: 10 },
  petCard: { flex: 1, backgroundColor: "#FFFFFF", borderRadius: 15, padding: 9, alignItems: "center", shadowColor: "#312642", shadowOpacity: 0.05, shadowRadius: 7, elevation: 1 },
  petLargeAvatar: { width: 53, height: 53, borderRadius: 27, justifyContent: "center", alignItems: "center" },
  emoji: { fontSize: 25 },
  petCardName: { color: "#282331", fontSize: 13, fontWeight: "800", marginTop: 7 },
  petBreed: { color: "#8B8593", fontSize: 9, marginTop: 2, textAlign: "center" },
});
