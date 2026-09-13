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
import PetCard from "@/components/pet-card";
import ScreenHeader from "@/components/screen-header";
import { useAppData } from "../context/app-data-context";

export default function MascotasScreen() {
  const router = useRouter();
  const { pets } = useAppData();

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <ScreenHeader
          title="Mis mascotas"
          subtitle="Administrá la información y el cuidado de cada una."
        />

        <View style={styles.summary}>
          <View>
            <Text style={styles.summaryNumber}>{pets.length}</Text>
            <Text style={styles.summaryText}>Mascotas registradas</Text>
          </View>

          <Ionicons name="paw-outline" size={35} color="#7C4DFF" />
        </View>

        <Text style={styles.sectionTitle}>Tus mascotas</Text>

        {pets.map((pet) => (
          <PetCard
            key={pet.id}
            name={pet.name}
            breed={`${pet.breed} · ${pet.age}`}
            emoji={pet.emoji}
            color={pet.color}
            onPress={() => router.push({ pathname: "/mascota-detalle", params: { id: pet.id } })}
          />
        ))}

        <TouchableOpacity
          style={styles.addButton}
          onPress={() => router.push("/mascota-formulario")}
        >
          <Ionicons name="add-circle-outline" size={24} color="#7C4DFF" />

          <View>
            <Text style={styles.addTitle}>Agregar mascota</Text>
            <Text style={styles.addSubtitle}>
              Registrá un nuevo integrante
            </Text>
          </View>
        </TouchableOpacity>
      </ScrollView>

      <AppBottomNav activeRoute="mascotas" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FAF9FC",
  },

  content: {
    flexGrow: 1,
    padding: 20,
    paddingBottom: 35,
  },

  summary: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "#EEE8FF",
    borderRadius: 17,
    padding: 18,
    marginBottom: 28,
  },

  summaryNumber: {
    color: "#7C4DFF",
    fontSize: 28,
    fontWeight: "800",
  },

  summaryText: {
    color: "#6C6576",
    fontSize: 13,
    marginTop: 3,
  },

  sectionTitle: {
    color: "#201B29",
    fontSize: 19,
    fontWeight: "800",
    marginBottom: 13,
  },

  addButton: {
    minHeight: 76,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderWidth: 1.5,
    borderStyle: "dashed",
    borderColor: "#B7A2FF",
    borderRadius: 17,
    paddingHorizontal: 17,
    marginTop: 2,
  },

  addTitle: {
    color: "#7C4DFF",
    fontSize: 15,
    fontWeight: "800",
  },

  addSubtitle: {
    color: "#827A8D",
    fontSize: 12,
    marginTop: 3,
  },
});
