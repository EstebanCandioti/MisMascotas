import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import {
  createAlbumPhoto,
  deleteAlbumPhoto,
  getAlbum,
  getAlbumPhotos,
  type AlbumResponse,
  type FotoResponse,
} from "../../services/api";

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString("es-AR", { day: "numeric", month: "short", year: "numeric" }).toUpperCase();
}

function isValidHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

async function fetchAlbumDetails(id: string): Promise<[AlbumResponse, FotoResponse[]]> {
  return Promise.all([getAlbum(id), getAlbumPhotos(id)]);
}

export default function AlbumDetalleScreen() {
  const router = useRouter();
  const { id, nombre = "Álbum", mascota = "" } = useLocalSearchParams<{
    id?: string;
    nombre?: string;
    mascota?: string;
  }>();
  const [album, setAlbum] = useState<AlbumResponse | null>(null);
  const [photos, setPhotos] = useState<FotoResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [photoUrl, setPhotoUrl] = useState("");
  const [photoFormat, setPhotoFormat] = useState("");

  const loadAlbum = useCallback(async () => {
    if (!id) {
      setError("No se indicó qué álbum querés abrir.");
      setLoading(false);
      return;
    }

    try {
      const [albumResponse, photosResponse] = await fetchAlbumDetails(id);
      setAlbum(albumResponse);
      setPhotos(photosResponse);
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "No se pudo cargar el álbum.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    let cancelled = false;
    if (!id) {
      Promise.resolve().then(() => {
        if (cancelled) return;
        setError("No se indicó qué álbum querés abrir.");
        setLoading(false);
      });
      return () => {
        cancelled = true;
      };
    }

    fetchAlbumDetails(id)
      .then(([albumResponse, photosResponse]) => {
        if (cancelled) return;
        setAlbum(albumResponse);
        setPhotos(photosResponse);
        setError(null);
      })
      .catch((loadError: unknown) => {
        if (cancelled) return;
        setError(loadError instanceof Error ? loadError.message : "No se pudo cargar el álbum.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  const addPhoto = async () => {
    const url = photoUrl.trim();
    if (!url) {
      Alert.alert("Falta la URL", "Ingresá la URL pública de la imagen.");
      return;
    }
    if (!isValidHttpUrl(url)) {
      Alert.alert("URL inválida", "La imagen debe tener una URL que empiece con http:// o https://.");
      return;
    }
    if (!id) return;

    setSaving(true);
    try {
      const photo = await createAlbumPhoto(id, {
        urlArchivo: url,
        formato: photoFormat.trim() || undefined,
      });
      setPhotos((current) => [photo, ...current]);
      setAlbum((current) => current ? { ...current, cantidadFotos: current.cantidadFotos + 1 } : current);
      setPhotoUrl("");
      setPhotoFormat("");
      setModalVisible(false);
    } catch (saveError) {
      Alert.alert("No se pudo agregar la foto", saveError instanceof Error ? saveError.message : "Intentá nuevamente.");
    } finally {
      setSaving(false);
    }
  };

  const confirmDeletePhoto = (photo: FotoResponse) => {
    Alert.alert("Eliminar foto", "¿Querés eliminar esta foto del álbum?", [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Eliminar",
        style: "destructive",
        onPress: async () => {
          try {
            await deleteAlbumPhoto(photo.idFoto);
            setPhotos((current) => current.filter((item) => item.idFoto !== photo.idFoto));
            setAlbum((current) => current ? { ...current, cantidadFotos: Math.max(0, current.cantidadFotos - 1) } : current);
          } catch (deleteError) {
            Alert.alert("No se pudo eliminar la foto", deleteError instanceof Error ? deleteError.message : "Intentá nuevamente.");
          }
        },
      },
    ]);
  };

  const title = album?.nombre ?? nombre;

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity style={styles.back} onPress={() => router.back()} accessibilityLabel="Volver a álbumes">
          <Ionicons name="chevron-back" size={24} color="#30293A" />
        </TouchableOpacity>
        <Text numberOfLines={1} style={styles.topTitle}>{title}</Text>
        <TouchableOpacity style={styles.add} onPress={() => setModalVisible(true)} disabled={!album} accessibilityLabel="Agregar foto">
          <Ionicons name="add" size={24} color="#7C4DFF" />
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {loading ? (
          <View style={styles.state}><ActivityIndicator color="#7C4DFF" /><Text style={styles.stateText}>Cargando álbum...</Text></View>
        ) : error ? (
          <View style={styles.state}>
            <Ionicons name="cloud-offline-outline" size={34} color="#D8515D" />
            <Text style={styles.errorText}>{error}</Text>
            <TouchableOpacity style={styles.retryButton} onPress={() => { setLoading(true); void loadAlbum(); }}><Text style={styles.retryText}>Reintentar</Text></TouchableOpacity>
          </View>
        ) : album ? (
          <>
            <View style={styles.summary}>
              <View style={styles.summaryIcon}><Ionicons name="images-outline" size={25} color="#7C4DFF" /></View>
              <View style={styles.summaryInfo}>
                <Text style={styles.summaryTitle}>{album.cantidadFotos} fotos</Text>
                <Text style={styles.summaryText}>{mascota || "Álbum de mascota"}{album.descripcion ? ` · ${album.descripcion}` : ""}</Text>
              </View>
            </View>
            <View style={styles.urlNotice}>
              <Ionicons name="link-outline" size={17} color="#806227" />
              <Text style={styles.urlNoticeText}>Agregá imágenes usando una URL pública. La app no sube archivos directamente.</Text>
            </View>

            {photos.length ? (
              <View style={styles.grid}>
                {photos.map((photo) => (
                  <View key={photo.idFoto} style={styles.photoCard}>
                    <Image source={{ uri: photo.urlArchivo }} style={styles.photo} resizeMode="cover" />
                    <View style={styles.photoInfo}>
                      <Text numberOfLines={1} style={styles.photoTitle}>{photo.formato || "Imagen"}</Text>
                      <Text style={styles.photoDate}>{formatDate(photo.creadoEn)}</Text>
                      <TouchableOpacity style={styles.deleteButton} onPress={() => confirmDeletePhoto(photo)}>
                        <Ionicons name="trash-outline" size={15} color="#D8515D" />
                        <Text style={styles.deleteText}>Eliminar</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <View style={styles.empty}>
                <Ionicons name="image-outline" size={38} color="#7C4DFF" />
                <Text style={styles.emptyTitle}>Este álbum todavía no tiene fotos</Text>
                <Text style={styles.emptyText}>Agregá una foto con una URL pública para verla acá.</Text>
              </View>
            )}
            <TouchableOpacity style={styles.addPhoto} onPress={() => setModalVisible(true)}>
              <Ionicons name="camera-outline" size={22} color="#FFFFFF" /><Text style={styles.addText}>Agregar foto</Text>
            </TouchableOpacity>
          </>
        ) : null}
      </ScrollView>

      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <View style={styles.overlay}>
          <View style={styles.sheet}>
            <View style={styles.handle} />
            <Text style={styles.sheetTitle}>Agregar foto</Text>
            <Text style={styles.modalNotice}>Pegá una URL pública de imagen. La foto se guardará en el backend mediante esa URL.</Text>
            <Text style={styles.label}>URL de la imagen *</Text>
            <TextInput
              style={styles.input}
              value={photoUrl}
              onChangeText={setPhotoUrl}
              placeholder="https://ejemplo.com/foto.jpg"
              placeholderTextColor="#968F9E"
              autoCapitalize="none"
              keyboardType="url"
              accessibilityLabel="URL pública de la foto"
            />
            <Text style={styles.label}>Formato (opcional)</Text>
            <TextInput style={styles.input} value={photoFormat} onChangeText={setPhotoFormat} placeholder="Ej. JPG o PNG" placeholderTextColor="#968F9E" maxLength={20} />
            <TouchableOpacity style={[styles.save, saving && styles.disabled]} onPress={() => void addPhoto()} disabled={saving}>
              {saving ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.saveText}>Agregar al álbum</Text>}
            </TouchableOpacity>
            <TouchableOpacity style={styles.cancel} onPress={() => setModalVisible(false)} disabled={saving}><Text style={styles.cancelText}>Cancelar</Text></TouchableOpacity>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FAF9FC" },
  topBar: { height: 72, flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 20 },
  back: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#FFFFFF", alignItems: "center", justifyContent: "center" },
  topTitle: { maxWidth: "65%", color: "#201B29", fontSize: 16, fontWeight: "800" },
  add: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#EEE8FF", alignItems: "center", justifyContent: "center" },
  content: { flexGrow: 1, padding: 20, paddingTop: 8, paddingBottom: 36 },
  summary: { flexDirection: "row", alignItems: "center", gap: 12, backgroundColor: "#EEE8FF", borderRadius: 17, padding: 15, marginBottom: 12 },
  summaryIcon: { width: 46, height: 46, backgroundColor: "#FFFFFF", borderRadius: 14, alignItems: "center", justifyContent: "center" },
  summaryInfo: { flex: 1 },
  summaryTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800" },
  summaryText: { color: "#6F6877", fontSize: 12, marginTop: 4 },
  urlNotice: { flexDirection: "row", alignItems: "center", gap: 7, padding: 11, borderRadius: 13, backgroundColor: "#FFF7E5", marginBottom: 20 },
  urlNoticeText: { flex: 1, color: "#725C31", fontSize: 11, lineHeight: 16 },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 11 },
  photoCard: { width: "47.8%", backgroundColor: "#FFFFFF", borderRadius: 15, overflow: "hidden" },
  photo: { width: "100%", aspectRatio: 1, backgroundColor: "#EEE8FF" },
  photoInfo: { padding: 9 },
  photoTitle: { color: "#342D3D", fontSize: 12, fontWeight: "800" },
  photoDate: { color: "#938C99", fontSize: 9, fontWeight: "700", marginTop: 3 },
  deleteButton: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 8 },
  deleteText: { color: "#D8515D", fontSize: 11, fontWeight: "700" },
  empty: { alignItems: "center", justifyContent: "center", paddingVertical: 45, paddingHorizontal: 20 },
  emptyTitle: { color: "#302A3B", fontSize: 16, fontWeight: "800", textAlign: "center", marginTop: 10 },
  emptyText: { color: "#837C8D", fontSize: 13, lineHeight: 19, textAlign: "center", marginTop: 5 },
  addPhoto: { height: 52, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: "#7C4DFF", borderRadius: 14, marginTop: 19 },
  addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  state: { alignItems: "center", justifyContent: "center", paddingHorizontal: 20, paddingVertical: 40, gap: 10 },
  stateText: { color: "#756E7D", fontSize: 13, textAlign: "center" },
  errorText: { color: "#B63E49", fontSize: 13, textAlign: "center" },
  retryButton: { paddingHorizontal: 18, paddingVertical: 9, borderRadius: 11, backgroundColor: "#EEE8FF" },
  retryText: { color: "#7C4DFF", fontWeight: "800" },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" },
  sheet: { backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 21, paddingBottom: 34 },
  handle: { width: 38, height: 4, borderRadius: 2, alignSelf: "center", backgroundColor: "#D9D4DF", marginBottom: 17 },
  sheetTitle: { color: "#201B29", fontSize: 21, fontWeight: "800", marginBottom: 10 },
  modalNotice: { color: "#6F6877", fontSize: 12, lineHeight: 17, marginBottom: 17 },
  label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 },
  input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#201B29", fontSize: 14, marginBottom: 16 },
  save: { height: 51, backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 2 },
  saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  cancel: { alignItems: "center", paddingTop: 15 },
  cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
  disabled: { opacity: 0.55 },
});
