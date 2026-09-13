import { StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

type Props = {
  title: string;
  subtitle?: string;
  icon?: keyof typeof Ionicons.glyphMap;
};

export default function ScreenHeader({
  title,
  subtitle,
  icon = "paw-outline",
}: Props) {
  return (
    <View style={styles.header}>
      <View style={styles.icon}>
        <Ionicons name={icon} size={23} color="#7C4DFF" />
      </View>

      <View style={styles.textContent}>
        <Text style={styles.title}>{title}</Text>

        {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 25,
  },

  icon: {
    width: 42,
    height: 42,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#EEE8FF",
  },

  title: {
    color: "#171321",
    fontSize: 24,
    fontWeight: "800",
  },

  subtitle: {
    color: "#817A8B",
    fontSize: 13,
    marginTop: 3,
    flexShrink: 1,
  },

  textContent: {
    flex: 1,
    flexShrink: 1,
  },
});
