import { useState } from "react";
import { Alert, Modal, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import { type AlbumPhoto, useAppData } from "../context/app-data-context";

export default function AlbumDetalleScreen() {
  const router = useRouter();
  const { id, nombre = "Álbum" } = useLocalSearchParams<{ id?: string; nombre?: string }>();
  const { albums, setAlbums } = useAppData();
  const album = albums.find((item) => item.id === id);
  const photos = album?.photos ?? [];
  const [modalVisible, setModalVisible] = useState(false);
  const [photoTitle, setPhotoTitle] = useState("");

  const addPhoto = () => {
    if (!photoTitle.trim()) { Alert.alert("Falta un título", "Escribí un nombre para la foto."); return; }
    setAlbums(albums.map((item) => item.id === id ? { ...item, photos: [{ id: `${Date.now()}`, emoji: "📷", color: "#C9E5F3", title: photoTitle.trim(), date: "HOY" }, ...item.photos] } : item));
    setPhotoTitle("");
    setModalVisible(false);
    Alert.alert("Foto agregada", "Se agregó una foto simulada al álbum.");
  };

  const deletePhoto = (photo: AlbumPhoto) => {
    Alert.alert("Eliminar foto", `¿Querés eliminar “${photo.title}”?`, [
      { text: "Cancelar", style: "cancel" },
      { text: "Eliminar", style: "destructive", onPress: () => setAlbums(albums.map((item) => item.id === id ? { ...item, photos: item.photos.filter((current) => current.id !== photo.id) } : item)) },
    ]);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}><TouchableOpacity style={styles.back} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color="#30293A" /></TouchableOpacity><Text numberOfLines={1} style={styles.topTitle}>{nombre}</Text><TouchableOpacity style={styles.add} onPress={() => setModalVisible(true)}><Ionicons name="add" size={24} color="#7C4DFF" /></TouchableOpacity></View>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.summary}><View style={styles.summaryIcon}><Ionicons name="images-outline" size={25} color="#7C4DFF" /></View><View><Text style={styles.summaryTitle}>{photos.length} fotos</Text><Text style={styles.summaryText}>Tocá una foto para eliminarla.</Text></View></View>
        <View style={styles.grid}>{photos.map((photo) => <TouchableOpacity key={photo.id} style={styles.photoCard} onPress={() => deletePhoto(photo)}><View style={[styles.photo, { backgroundColor: photo.color }]}><Text style={styles.emoji}>{photo.emoji}</Text></View><Text numberOfLines={1} style={styles.photoTitle}>{photo.title}</Text><Text style={styles.photoDate}>{photo.date}</Text></TouchableOpacity>)}</View>
        <TouchableOpacity style={styles.addPhoto} onPress={() => setModalVisible(true)}><Ionicons name="camera-outline" size={22} color="#FFFFFF" /><Text style={styles.addText}>Agregar foto</Text></TouchableOpacity>
      </ScrollView>
      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}><View style={styles.overlay}><View style={styles.sheet}><View style={styles.handle} /><Text style={styles.sheetTitle}>Agregar foto</Text><View style={styles.fakePhoto}><Ionicons name="image-outline" size={36} color="#7C4DFF" /><Text style={styles.fakePhotoText}>Foto simulada</Text></View><Text style={styles.label}>Título de la foto</Text><TextInput style={styles.input} value={photoTitle} onChangeText={setPhotoTitle} placeholder="Ej. Paseo de domingo" placeholderTextColor="#968F9E" /><TouchableOpacity style={styles.save} onPress={addPhoto}><Text style={styles.saveText}>Agregar al álbum</Text></TouchableOpacity><TouchableOpacity style={styles.cancel} onPress={() => setModalVisible(false)}><Text style={styles.cancelText}>Cancelar</Text></TouchableOpacity></View></View></Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" }, topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 }, back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" }, topTitle: { maxWidth: "65%", color: "#201B29", fontSize: 16, fontWeight: "800" }, add: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" }, content: { padding: 20, paddingTop: 8, paddingBottom: 36 },
  summary: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 23 }, summaryIcon: { width: 46, height: 46, backgroundColor: "#FFFFFF", borderRadius: 14, alignItems: "center", justifyContent: "center" }, summaryTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800" }, summaryText: { color: "#6F6877", fontSize: 12, marginTop: 4 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 11 }, photoCard: { width: "47.8%", backgroundColor: "#FFFFFF", padding: 8, borderRadius: 15 }, photo: { width: "100%", aspectRatio: 1, borderRadius: 11, alignItems: "center", justifyContent: "center" }, emoji: { fontSize: 47 }, photoTitle: { color: "#342D3D", fontSize: 12, fontWeight: "800", marginTop: 8 }, photoDate: { color: "#938C99", fontSize: 9, fontWeight: "700", marginTop: 3 },
  addPhoto: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 19 }, addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" }, sheet: { backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 21, paddingBottom: 34 }, handle: { width: 38, height: 4, borderRadius: 2, alignSelf: "center", backgroundColor: "#D9D4DF", marginBottom: 17 }, sheetTitle: { color: "#201B29", fontSize: 21, fontWeight: "800", marginBottom: 16 }, fakePhoto: { height: 100, alignItems: "center", justifyContent: "center", backgroundColor: "#EEE8FF", borderRadius: 15, marginBottom: 19 }, fakePhotoText: { color: "#6F6877", fontSize: 12, marginTop: 5 }, label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 }, input: { height: 51, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#201B29", fontSize: 14 }, save: { height: 51, backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 18 }, saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" }, cancel: { alignItems: "center", paddingTop: 15 }, cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
});
