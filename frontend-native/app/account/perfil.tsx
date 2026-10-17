import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";

export default function PerfilScreen() {
  const router = useRouter();
  const { currentUser, pets, events } = useAppData();
  const name = currentUser?.name ?? "Usuario";
  const email = currentUser?.email ?? "";
  const planLabel = currentUser
    ? currentUser.esPremium
      ? "Plan Premium"
      : "Plan gratuito"
    : "Plan no disponible";

  const showPasswordMessage = () => {
    Alert.alert(
      "Contraseña",
      "Esta acción se conectará al backend más adelante.",
    );
  };

  return (
    <SafeAreaView style={styles.container} edges={["top", "left", "right", "bottom"]}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.topBar}>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel="Volver"
            style={styles.backButton}
            onPress={() => router.back()}
          >
            <Ionicons name="chevron-back" size={24} color="#30293A" />
          </TouchableOpacity>

          <View style={styles.topTitleBox}>
            <Text maxFontSizeMultiplier={1.3} style={styles.topTitle}>
              Mi perfil
            </Text>
          </View>

          <View style={styles.headerSpacer} />
        </View>

        <View style={styles.hero}>
          <View style={styles.avatar}>
            <Text maxFontSizeMultiplier={1.2} style={styles.avatarText}>
              {name.slice(0, 2).toUpperCase()}
            </Text>
          </View>

          <View style={styles.identityText}>
            <Text maxFontSizeMultiplier={1.3} style={styles.name}>
              {name}
            </Text>
            {!!email && (
              <Text maxFontSizeMultiplier={1.3} style={styles.email}>
                {email}
              </Text>
            )}
            <Text maxFontSizeMultiplier={1.3} style={styles.membership}>
              {planLabel}
            </Text>
          </View>
        </View>

        <View style={styles.summary}>
          <View style={styles.summaryItem}>
            <Text maxFontSizeMultiplier={1.3} style={styles.summaryNumber}>
              {pets.length}
            </Text>
            <Text maxFontSizeMultiplier={1.2} style={styles.summaryText}>
              Mascotas
            </Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.summaryItem}>
            <Text maxFontSizeMultiplier={1.3} style={styles.summaryNumber}>
              {events.length}
            </Text>
            <Text maxFontSizeMultiplier={1.2} style={styles.summaryText}>
              Eventos
            </Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.summaryItem}>
            <Text maxFontSizeMultiplier={1.3} style={styles.summaryNumber}>
              0
            </Text>
            <Text maxFontSizeMultiplier={1.2} style={styles.summaryText}>
              Badges
            </Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <View style={styles.sectionTitleBox}>
            <Text maxFontSizeMultiplier={1.3} style={styles.sectionTitle}>
              Mis badges
            </Text>
          </View>
          <TouchableOpacity
            accessibilityRole="button"
            accessibilityLabel="Ver todos los badges"
            style={styles.sectionAction}
            onPress={() => router.push("/account/badges")}
          >
            <Text maxFontSizeMultiplier={1.2} style={styles.link}>
              Ver todos
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>
            Todavía no hay badges para mostrar.
          </Text>
        </View>

        <View style={styles.standaloneSectionHeader}>
          <Text maxFontSizeMultiplier={1.3} style={styles.sectionTitle}>
            Actividad reciente
          </Text>
        </View>

        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>
            Todavía no hay actividad para mostrar.
          </Text>
        </View>

        <TouchableOpacity
          accessibilityRole="button"
          style={styles.passwordButton}
          onPress={showPasswordMessage}
        >
          <Ionicons
            style={styles.passwordIcon}
            name="key-outline"
            size={20}
            color="#7C4DFF"
          />
          <View style={styles.passwordTextBox}>
            <Text maxFontSizeMultiplier={1.3} style={styles.passwordText}>
              Cambiar contraseña
            </Text>
          </View>
          <Ionicons
            style={styles.passwordChevron}
            name="chevron-forward"
            size={20}
            color="#A29BAA"
          />
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FAF9FC",
  },
  scroll: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 36,
    alignItems: "stretch",
  },
  topBar: {
    minHeight: 42,
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 23,
  },
  backButton: {
    width: 42,
    height: 42,
    flexShrink: 0,
    borderRadius: 14,
    backgroundColor: "#FFFFFF",
    alignItems: "center",
    justifyContent: "center",
  },
  topTitleBox: {
    flexGrow: 1,
    flexBasis: 0,
    minWidth: 0,
    paddingHorizontal: 8,
  },
  topTitle: {
    width: "100%",
    color: "#201B29",
    fontSize: 17,
    lineHeight: 23,
    fontWeight: "800",
    textAlign: "center",
  },
  headerSpacer: {
    width: 42,
    height: 42,
    flexShrink: 0,
  },
  hero: {
    alignItems: "center",
    marginBottom: 25,
  },
  avatar: {
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: "#7C4DFF",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: {
    color: "#FFFFFF",
    fontSize: 28,
    lineHeight: 34,
    fontWeight: "800",
  },
  identityText: {
    alignSelf: "stretch",
    alignItems: "center",
    paddingHorizontal: 8,
  },
  name: {
    width: "100%",
    color: "#201B29",
    fontSize: 24,
    lineHeight: 31,
    fontWeight: "800",
    marginTop: 11,
    textAlign: "center",
  },
  email: {
    width: "100%",
    color: "#7F7888",
    fontSize: 13,
    lineHeight: 19,
    marginTop: 4,
    textAlign: "center",
  },
  membership: {
    width: "100%",
    color: "#7C4DFF",
    fontSize: 11,
    lineHeight: 16,
    fontWeight: "700",
    marginTop: 6,
    textAlign: "center",
  },
  summary: {
    flexDirection: "row",
    alignItems: "stretch",
    paddingVertical: 18,
    backgroundColor: "#FFFFFF",
    borderRadius: 17,
    marginBottom: 28,
  },
  summaryItem: {
    flexGrow: 1,
    flexBasis: 0,
    minWidth: 0,
    alignItems: "center",
    justifyContent: "flex-start",
    paddingHorizontal: 4,
  },
  summaryNumber: {
    width: "100%",
    color: "#7C4DFF",
    fontSize: 21,
    lineHeight: 27,
    fontWeight: "800",
    textAlign: "center",
  },
  summaryText: {
    width: "100%",
    minHeight: 30,
    color: "#807989",
    fontSize: 11,
    lineHeight: 15,
    marginTop: 3,
    textAlign: "center",
  },
  divider: {
    width: 1,
    height: 32,
    alignSelf: "center",
    backgroundColor: "#EEEAF2",
  },
  sectionHeader: {
    minHeight: 30,
    flexDirection: "row",
    alignItems: "flex-start",
    marginBottom: 12,
  },
  sectionTitleBox: {
    flexGrow: 1,
    flexBasis: 0,
    minWidth: 0,
    paddingRight: 8,
  },
  sectionTitle: {
    width: "100%",
    color: "#201B29",
    fontSize: 19,
    lineHeight: 25,
    fontWeight: "800",
  },
  sectionAction: {
    width: 88,
    minHeight: 28,
    flexShrink: 0,
    paddingVertical: 2,
    alignItems: "flex-end",
  },
  link: {
    width: "100%",
    color: "#7C4DFF",
    fontSize: 13,
    lineHeight: 20,
    fontWeight: "800",
    textAlign: "right",
  },
  standaloneSectionHeader: {
    minHeight: 30,
    justifyContent: "flex-start",
    marginBottom: 12,
  },
  emptyState: {
    minHeight: 55,
    paddingVertical: 18,
    marginBottom: 18,
    justifyContent: "center",
  },
  emptyText: {
    width: "100%",
    color: "#8E8795",
    fontSize: 13,
    lineHeight: 19,
    textAlign: "center",
  },
  passwordButton: {
    minHeight: 55,
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    paddingHorizontal: 15,
    paddingVertical: 10,
  },
  passwordIcon: {
    flexShrink: 0,
    marginRight: 11,
  },
  passwordTextBox: {
    flexGrow: 1,
    flexBasis: 0,
    minWidth: 0,
    paddingRight: 8,
  },
  passwordText: {
    width: "100%",
    color: "#332C3B",
    fontSize: 14,
    lineHeight: 20,
    fontWeight: "800",
  },
  passwordChevron: {
    flexShrink: 0,
  },
});
