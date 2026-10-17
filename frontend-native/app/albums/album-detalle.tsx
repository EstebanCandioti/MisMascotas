import { Alert, SafeAreaView, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";

export default function AlbumDetalleScreen() {
  const router = useRouter();
  const { id, nombre = "Álbum" } = useLocalSearchParams<{ id?: string; nombre?: string }>();
  const { albums } = useAppData();
  const album = albums.find((item) => item.id === id);
  const photos = album?.photos ?? [];

  const addPhoto = () => {
    Alert.alert("Función no disponible", "Las fotos estarán disponibles cuando el servicio de álbumes esté conectado.");
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}><TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity><Text numberOfLines={1} style={styles.topTitle}>{nombre}</Text><TouchableOpacity style={styles.add} onPress={addPhoto}><Ionicons name="add" size={24} color="#7C4DFF" /></TouchableOpacity></View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.summary}><View style={styles.summaryIcon}><Ionicons name="images-outline" size={25} color="#7C4DFF" /></View><View><Text style={styles.summaryTitle}>{photos.length} fotos</Text><Text style={styles.summaryText}>Tocá una foto para eliminarla.</Text></View></View>
        <View style={styles.grid}>{photos.map((photo) => <View key={photo.id} style={styles.photoCard}><View style={[styles.photo, { backgroundColor: photo.color }]}><Text style={styles.emoji}>{photo.emoji}</Text></View><Text numberOfLines={1} style={styles.photoTitle}>{photo.title}</Text><Text style={styles.photoDate}>{photo.date}</Text></View>)}</View>
        {!photos.length && <Text style={styles.empty}>Todavía no hay fotos para mostrar.</Text>}
        <TouchableOpacity style={styles.addPhoto} onPress={addPhoto}><Ionicons name="camera-outline" size={22} color="#FFFFFF" /><Text style={styles.addText}>Agregar foto</Text></TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 }, back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" }, topTitle: { maxWidth: "65%", color: "#201B29", fontSize: 16, fontWeight: "800" }, add: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" }, content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  summary: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 23 }, summaryIcon: { width: 46, height: 46, backgroundColor: "#FFFFFF", borderRadius: 14, alignItems: "center", justifyContent: "center" }, summaryTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800" }, summaryText: { color: "#6F6877", fontSize: 12, marginTop: 4 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 11 }, photoCard: { width: "47.8%", backgroundColor: "#FFFFFF", padding: 8, borderRadius: 15 }, photo: { width: "100%", aspectRatio: 1, borderRadius: 11, alignItems: "center", justifyContent: "center" }, emoji: { fontSize: 47 }, photoTitle: { color: "#342D3D", fontSize: 12, fontWeight: "800", marginTop: 8 }, photoDate: { color: "#938C99", fontSize: 9, fontWeight: "700", marginTop: 3 },
  addPhoto: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 19 }, addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  empty: { color: "#837C8D", fontSize: 13, textAlign: "center", paddingVertical: 22 },
});
