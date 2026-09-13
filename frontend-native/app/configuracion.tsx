import { Alert, ScrollView, StyleSheet, Switch, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";
import { useAppData } from "../context/app-data-context";

function SettingRow({ icon, title, subtitle, onPress }: { icon: keyof typeof Ionicons.glyphMap; title: string; subtitle: string; onPress?: () => void }) {
  return <TouchableOpacity style={styles.row} onPress={onPress}><View style={styles.rowIcon}><Ionicons name={icon} size={20} color="#7C4DFF" /></View><View style={styles.rowInfo}><Text style={styles.rowTitle}>{title}</Text><Text style={styles.rowSubtitle}>{subtitle}</Text></View><Ionicons name="chevron-forward" size={20} color="#A29BAA" /></TouchableOpacity>;
}

export default function ConfiguracionScreen() {
  const router = useRouter();
  const { currentUser, notifications, setNotifications, premium, setCurrentUser } = useAppData();

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Ajustes" subtitle="Personalizá tu experiencia." icon="settings-outline" />
        <TouchableOpacity style={styles.profile} onPress={() => router.push("/perfil")}><View style={styles.avatar}><Text style={styles.avatarText}>{currentUser?.name.slice(0, 2).toUpperCase() ?? "US"}</Text></View><View style={styles.rowInfo}><Text style={styles.profileName}>{currentUser?.name ?? "Usuario Demo"}</Text><Text style={styles.rowSubtitle}>{currentUser?.email ?? "usuario@demo.com"}</Text></View><Ionicons name="chevron-forward" size={20} color="#A29BAA" /></TouchableOpacity>
        <Text style={styles.groupTitle}>PREFERENCIAS</Text>
        <View style={styles.group}><SettingRow icon="card-outline" title="Plan premium" subtitle={premium ? "Plan Premium activo" : "Plan gratuito"} onPress={() => router.push("/plan-premium")} /><View style={styles.line} /><SettingRow icon="shield-checkmark-outline" title="Privacidad" subtitle="Datos y permisos" onPress={() => router.push("/privacidad")} /><View style={styles.line} /><View style={styles.switchRow}><View style={styles.rowIcon}><Ionicons name="notifications-outline" size={20} color="#7C4DFF" /></View><View style={styles.rowInfo}><Text style={styles.rowTitle}>Notificaciones</Text><Text style={styles.rowSubtitle}>Recordatorios y actividad</Text></View><Switch value={notifications} onValueChange={setNotifications} trackColor={{ false: "#DDD7E3", true: "#B7A2FF" }} thumbColor={notifications ? "#7C4DFF" : "#FFFFFF"} /></View></View>
        <Text style={styles.groupTitle}>CUENTA</Text>
        <View style={styles.group}><SettingRow icon="help-circle-outline" title="Ayuda y soporte" subtitle="Preguntas frecuentes" onPress={() => Alert.alert("Ayuda", "Centro de ayuda simulado.")} /></View>
        <TouchableOpacity style={styles.logout} onPress={() => { setCurrentUser(null); router.replace("/login"); }}><Ionicons name="log-out-outline" size={21} color="#D8515D" /><Text style={styles.logoutText}>Cerrar sesión</Text></TouchableOpacity>
      </ScrollView>
      <AppBottomNav activeRoute="configuracion" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  profile: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 17, padding: 14, marginBottom: 27 }, avatar: { width: 51, height: 51, borderRadius: 26, justifyContent: "center", alignItems: "center", backgroundColor: "#7C4DFF", marginRight: 12 }, avatarText: { color: "#FFFFFF", fontWeight: "800", fontSize: 15 }, profileName: { color: "#201B29", fontSize: 15, fontWeight: "800" },
  groupTitle: { color: "#8B8393", fontSize: 11, fontWeight: "800", letterSpacing: 1, marginBottom: 9 }, group: { backgroundColor: "#FFFFFF", borderRadius: 17, overflow: "hidden", marginBottom: 25 }, row: { minHeight: 69, flexDirection: "row", alignItems: "center", paddingHorizontal: 14 }, switchRow: { minHeight: 69, flexDirection: "row", alignItems: "center", paddingHorizontal: 14 }, rowIcon: { width: 36, height: 36, backgroundColor: "#EEE8FF", borderRadius: 11, justifyContent: "center", alignItems: "center", marginRight: 11 }, rowInfo: { flex: 1 }, rowTitle: { color: "#282231", fontSize: 14, fontWeight: "800" }, rowSubtitle: { color: "#857E8E", fontSize: 12, marginTop: 3 }, line: { height: 1, backgroundColor: "#F0EDF3", marginLeft: 61 },
  logout: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, borderWidth: 1, borderColor: "#F1C9CD", borderRadius: 14, backgroundColor: "#FFF8F8" }, logoutText: { color: "#D8515D", fontSize: 14, fontWeight: "800" },
});
