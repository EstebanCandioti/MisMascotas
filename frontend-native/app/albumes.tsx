import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";
import { useAppData } from "../context/app-data-context";

export default function AlbumesScreen() {
  const router = useRouter();
  const { albums, setAlbums } = useAppData();

  const createAlbum = () => {
    setAlbums([...albums, { id: `album-${Date.now()}`, name: "Nuevo álbum", emoji: "📷", color: "#C9E5F3", photos: [] }]);
    Alert.alert("Álbum creado", "Se agregó un álbum de ejemplo.");
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Álbumes" subtitle="Guardá los mejores recuerdos de tus mascotas." icon="images-outline" />
        <View style={styles.tip}><Ionicons name="camera-outline" size={24} color="#7C4DFF" /><Text style={styles.tipText}>Cada foto puede quedar organizada por mascota y momento.</Text></View>
        <Text style={styles.sectionTitle}>Tus álbumes</Text>
        {albums.map((album) => (
          <TouchableOpacity key={album.id} style={styles.album} onPress={() => router.push({ pathname: "/album-detalle", params: { id: album.id, nombre: album.name } })}>
            <View style={[styles.cover, { backgroundColor: album.color }]}><Text style={styles.emoji}>{album.emoji}</Text></View>
            <View style={styles.info}><Text style={styles.albumName}>{album.name}</Text><Text style={styles.albumCount}>{album.photos.length} fotos</Text></View>
            <Ionicons name="chevron-forward" size={21} color="#9A94A4" />
          </TouchableOpacity>
        ))}
        <TouchableOpacity style={styles.addButton} onPress={createAlbum}><Ionicons name="add" size={23} color="#FFFFFF" /><Text style={styles.addText}>Crear álbum</Text></TouchableOpacity>
      </ScrollView>
      <AppBottomNav activeRoute="albumes" />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  tip: { flexDirection: "row", gap: 12, alignItems: "center", backgroundColor: "#EEE8FF", borderRadius: 16, padding: 15, marginBottom: 27 }, tipText: { flex: 1, color: "#645D70", fontSize: 13, lineHeight: 18 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 13 },
  album: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFFFFF", borderRadius: 17, padding: 13, marginBottom: 12, shadowColor: "#312642", shadowOpacity: 0.06, shadowRadius: 8, elevation: 2 },
  cover: { width: 59, height: 59, borderRadius: 14, justifyContent: "center", alignItems: "center", marginRight: 13 }, emoji: { fontSize: 29 }, info: { flex: 1 }, albumName: { color: "#201B29", fontSize: 15, fontWeight: "800" }, albumCount: { color: "#837C8D", fontSize: 12, marginTop: 4 },
  addButton: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7, height: 51, borderRadius: 14, backgroundColor: "#7C4DFF", marginTop: 10 }, addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
});
