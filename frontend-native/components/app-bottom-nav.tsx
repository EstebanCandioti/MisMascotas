import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { type Href, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

type RouteName =
  | "inicio"
  | "mascotas"
  | "albumes"
  | "calendario"
  | "cuidadores"
  | "configuracion";

type NavItemProps = {
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  route: RouteName;
  activeRoute: RouteName;
};

function NavItem({ label, icon, route, activeRoute }: NavItemProps) {
  const router = useRouter();
  const active = route === activeRoute;

  return (
    <TouchableOpacity
      style={styles.item}
      onPress={() => router.replace(`/${route}` as Href)}
    >
      <Ionicons
        name={icon}
        size={21}
        color={active ? "#7C4DFF" : "#9A94A4"}
      />

      <Text style={[styles.label, active && styles.activeLabel]}>
        {label}
      </Text>
    </TouchableOpacity>
  );
}

export default function AppBottomNav({
  activeRoute,
}: {
  activeRoute: RouteName;
}) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom || 8 }]}>
      <NavItem
        label="Inicio"
        icon="home-outline"
        route="inicio"
        activeRoute={activeRoute}
      />

      <NavItem
        label="Mascotas"
        icon="paw-outline"
        route="mascotas"
        activeRoute={activeRoute}
      />

      <NavItem
        label="Álbumes"
        icon="images-outline"
        route="albumes"
        activeRoute={activeRoute}
      />

      <NavItem
        label="Calendario"
        icon="calendar-outline"
        route="calendario"
        activeRoute={activeRoute}
      />

      <NavItem
        label="Cuidadores"
        icon="person-add-outline"
        route="cuidadores"
        activeRoute={activeRoute}
      />

      <NavItem
        label="Ajustes"
        icon="settings-outline"
        route="configuracion"
        activeRoute={activeRoute}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    backgroundColor: "#FFFFFF",
    borderTopWidth: 1,
    borderTopColor: "#ECE8F0",
    paddingTop: 9,
  },

  item: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
  },

  label: {
    color: "#9A94A4",
    fontSize: 8,
    fontWeight: "600",
  },

  activeLabel: {
    color: "#7C4DFF",
    fontWeight: "800",
  },
});
