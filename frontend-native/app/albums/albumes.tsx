import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import AppBottomNav from "@/components/app-bottom-nav";
import ScreenHeader from "@/components/screen-header";
import { useAppData } from "../../context/app-data-context";
import {
  createAlbum,
  deleteAlbum,
  getAlbums,
  updateAlbum,
  type AlbumResponse,
} from "../../services/api";

type AlbumListItem = AlbumResponse & { petName: string };

async function fetchAlbumsForPets(pets: { id: string; name: string }[]): Promise<AlbumListItem[]> {
  const groupedAlbums = await Promise.all(
    pets.map(async (pet) => {
      const petAlbums = await getAlbums(pet.id);
      return petAlbums.map((album) => ({ ...album, petName: pet.name }));
    }),
  );
  return groupedAlbums.flat();
}

export default function AlbumesScreen() {
  const router = useRouter();
  const { pets } = useAppData();
  const [albums, setAlbums] = useState<AlbumListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingAlbum, setEditingAlbum] = useState<AlbumListItem | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedPetId, setSelectedPetId] = useState("");

  const loadAlbums = useCallback(async () => {
    try {
      setAlbums(await fetchAlbumsForPets(pets));
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "No se pudieron cargar los álbumes.");
    } finally {
      setLoading(false);
    }
  }, [pets]);

  useEffect(() => {
    let cancelled = false;
    fetchAlbumsForPets(pets)
      .then((result) => {
        if (cancelled) return;
        setAlbums(result);
        setError(null);
      })
      .catch((loadError: unknown) => {
        if (cancelled) return;
        setError(loadError instanceof Error ? loadError.message : "No se pudieron cargar los álbumes.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [pets]);

  const openCreateModal = () => {
    setEditingAlbum(null);
    setName("");
    setDescription("");
    setSelectedPetId(pets[0]?.id ?? "");
    setModalVisible(true);
  };

  const openEditModal = (album: AlbumListItem) => {
    setEditingAlbum(album);
    setName(album.nombre);
    setDescription(album.descripcion ?? "");
    setSelectedPetId(album.mascotaId ?? "");
    setModalVisible(true);
  };

  const saveAlbum = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      Alert.alert("Falta el nombre", "Escribí un nombre para el álbum.");
      return;
    }
    if (!selectedPetId) {
      Alert.alert("Seleccioná una mascota", "Cada álbum debe estar asociado a una mascota.");
      return;
    }

    setSaving(true);
    try {
      const request = {
        nombre: trimmedName,
        descripcion: description.trim() || undefined,
        mascotaId: selectedPetId,
      };
      if (editingAlbum) {
        await updateAlbum(editingAlbum.idAlbum, request);
      } else {
        await createAlbum(request);
      }
      setModalVisible(false);
      await loadAlbums();
    } catch (saveError) {
      Alert.alert(
        editingAlbum ? "No se pudo actualizar el álbum" : "No se pudo crear el álbum",
        saveError instanceof Error ? saveError.message : "Intentá nuevamente.",
      );
    } finally {
      setSaving(false);
    }
  };

  const confirmDeleteAlbum = (album: AlbumListItem) => {
    Alert.alert("Eliminar álbum", `¿Querés eliminar “${album.nombre}” y sus fotos?`, [
      { text: "Cancelar", style: "cancel" },
      {
        text: "Eliminar",
        style: "destructive",
        onPress: async () => {
          try {
            await deleteAlbum(album.idAlbum);
            setAlbums((current) => current.filter((item) => item.idAlbum !== album.idAlbum));
          } catch (deleteError) {
            Alert.alert("No se pudo eliminar el álbum", deleteError instanceof Error ? deleteError.message : "Intentá nuevamente.");
          }
        },
      },
    ]);
  };

  const openAlbum = (album: AlbumListItem) => {
    router.push({
      pathname: "/albums/album-detalle",
      params: { id: album.idAlbum, nombre: album.nombre, mascota: album.petName },
    });
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <ScreenHeader title="Álbumes" subtitle="Guardá los mejores recuerdos de tus mascotas." icon="images-outline" />
        <View style={styles.tip}>
          <Ionicons name="camera-outline" size={24} color="#7C4DFF" />
          <Text style={styles.tipText}>Tus álbumes y fotos se guardan en tu cuenta y se organizan por mascota.</Text>
        </View>
        <Text style={styles.sectionTitle}>Tus álbumes</Text>

        {loading ? (
          <View style={styles.state}><ActivityIndicator color="#7C4DFF" /><Text style={styles.stateText}>Cargando álbumes...</Text></View>
        ) : error ? (
          <View style={styles.state}>
            <Ionicons name="cloud-offline-outline" size={31} color="#D8515D" />
            <Text style={styles.errorText}>{error}</Text>
            <TouchableOpacity style={styles.retryButton} onPress={() => { setLoading(true); void loadAlbums(); }}><Text style={styles.retryText}>Reintentar</Text></TouchableOpacity>
          </View>
        ) : pets.length === 0 ? (
          <View style={styles.state}><Ionicons name="paw-outline" size={31} color="#7C4DFF" /><Text style={styles.stateText}>Primero registrá una mascota para crear sus álbumes.</Text></View>
        ) : albums.length === 0 ? (
          <View style={styles.state}><Ionicons name="images-outline" size={31} color="#7C4DFF" /><Text style={styles.stateText}>Todavía no hay álbumes. Creá uno para guardar recuerdos.</Text></View>
        ) : albums.map((album) => (
          <View key={album.idAlbum} style={styles.album}>
            <TouchableOpacity style={styles.albumMain} onPress={() => openAlbum(album)}>
              <View style={styles.cover}><Ionicons name="images-outline" size={27} color="#7C4DFF" /></View>
              <View style={styles.info}>
                <Text style={styles.albumName}>{album.nombre}</Text>
                <Text style={styles.albumCount}>{album.cantidadFotos} fotos · {album.petName}</Text>
                {album.descripcion ? <Text numberOfLines={1} style={styles.albumDescription}>{album.descripcion}</Text> : null}
              </View>
              <Ionicons name="chevron-forward" size={21} color="#9A94A4" />
            </TouchableOpacity>
            <View style={styles.albumActions}>
              <TouchableOpacity style={styles.action} onPress={() => openEditModal(album)}>
                <Ionicons name="pencil-outline" size={16} color="#7C4DFF" /><Text style={styles.editText}>Editar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.action} onPress={() => confirmDeleteAlbum(album)}>
                <Ionicons name="trash-outline" size={16} color="#D8515D" /><Text style={styles.deleteText}>Eliminar</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))}

        <TouchableOpacity style={[styles.addButton, pets.length === 0 && styles.disabled]} onPress={openCreateModal} disabled={pets.length === 0}>
          <Ionicons name="add" size={23} color="#FFFFFF" /><Text style={styles.addText}>Crear álbum</Text>
        </TouchableOpacity>
      </ScrollView>
      <AppBottomNav activeRoute="albumes" />

      <Modal visible={modalVisible} transparent animationType="slide" onRequestClose={() => setModalVisible(false)}>
        <View style={styles.overlay}>
          <View style={styles.sheet}>
            <View style={styles.handle} />
            <Text style={styles.sheetTitle}>{editingAlbum ? "Editar álbum" : "Crear álbum"}</Text>
            <Text style={styles.label}>Nombre *</Text>
            <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="Ej. Aventuras de Luna" placeholderTextColor="#968F9E" maxLength={150} />
            <Text style={styles.label}>Descripción (opcional)</Text>
            <TextInput style={[styles.input, styles.descriptionInput]} value={description} onChangeText={setDescription} placeholder="Contá de qué trata el álbum" placeholderTextColor="#968F9E" multiline textAlignVertical="top" />
            <Text style={styles.label}>Mascota *</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.petOptions}>
              {pets.map((pet) => (
                <TouchableOpacity key={pet.id} style={[styles.petOption, selectedPetId === pet.id && styles.petOptionSelected]} onPress={() => setSelectedPetId(pet.id)}>
                  <Text style={styles.petOptionText}>{pet.name}</Text>
                </TouchableOpacity>
              ))}
            </ScrollView>
            <TouchableOpacity style={[styles.save, saving && styles.disabled]} onPress={() => void saveAlbum()} disabled={saving}>
              {saving ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.saveText}>{editingAlbum ? "Guardar cambios" : "Crear álbum"}</Text>}
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
  content: { flexGrow: 1, padding: 20, paddingBottom: 35 },
  tip: { flexDirection: "row", gap: 12, alignItems: "center", backgroundColor: "#EEE8FF", borderRadius: 16, padding: 15, marginBottom: 27 },
  tipText: { flex: 1, color: "#645D70", fontSize: 13, lineHeight: 18 },
  sectionTitle: { color: "#201B29", fontSize: 19, fontWeight: "800", marginBottom: 13 },
  album: { backgroundColor: "#FFFFFF", borderRadius: 17, padding: 13, marginBottom: 12, shadowColor: "#312642", shadowOpacity: 0.06, shadowRadius: 8, elevation: 2 },
  albumMain: { flexDirection: "row", alignItems: "center" },
  cover: { width: 59, height: 59, borderRadius: 14, justifyContent: "center", alignItems: "center", marginRight: 13, backgroundColor: "#EEE8FF" },
  info: { flex: 1 },
  albumName: { color: "#201B29", fontSize: 15, fontWeight: "800" },
  albumCount: { color: "#837C8D", fontSize: 12, marginTop: 4 },
  albumDescription: { color: "#837C8D", fontSize: 11, marginTop: 3 },
  albumActions: { flexDirection: "row", gap: 18, borderTopWidth: 1, borderTopColor: "#F0EDF3", marginTop: 11, paddingTop: 10 },
  action: { flexDirection: "row", alignItems: "center", gap: 5 },
  editText: { color: "#7C4DFF", fontSize: 12, fontWeight: "700" },
  deleteText: { color: "#D8515D", fontSize: 12, fontWeight: "700" },
  addButton: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7, height: 51, borderRadius: 14, backgroundColor: "#7C4DFF", marginTop: 10 },
  addText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  state: { alignItems: "center", justifyContent: "center", paddingHorizontal: 20, paddingVertical: 35, gap: 10 },
  stateText: { color: "#756E7D", fontSize: 13, lineHeight: 19, textAlign: "center" },
  errorText: { color: "#B63E49", fontSize: 13, textAlign: "center" },
  retryButton: { paddingHorizontal: 18, paddingVertical: 9, borderRadius: 11, backgroundColor: "#EEE8FF" },
  retryText: { color: "#7C4DFF", fontWeight: "800" },
  disabled: { opacity: 0.55 },
  overlay: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(25,18,35,0.35)" },
  sheet: { backgroundColor: "#FFFFFF", borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 21, paddingBottom: 34 },
  handle: { width: 38, height: 4, borderRadius: 2, alignSelf: "center", backgroundColor: "#D9D4DF", marginBottom: 17 },
  sheetTitle: { color: "#201B29", fontSize: 21, fontWeight: "800", marginBottom: 16 },
  label: { color: "#302A3B", fontSize: 13, fontWeight: "800", marginBottom: 8 },
  input: { minHeight: 51, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, paddingHorizontal: 14, color: "#201B29", fontSize: 14, marginBottom: 16 },
  descriptionInput: { minHeight: 78, paddingTop: 13 },
  petOptions: { gap: 8, paddingBottom: 8 },
  petOption: { paddingHorizontal: 15, paddingVertical: 10, borderRadius: 12, backgroundColor: "#F3F0F6", borderWidth: 1, borderColor: "#F3F0F6" },
  petOptionSelected: { backgroundColor: "#F4F0FF", borderColor: "#A88BFF" },
  petOptionText: { color: "#423A4D", fontSize: 13, fontWeight: "700" },
  save: { height: 51, backgroundColor: "#7C4DFF", borderRadius: 14, alignItems: "center", justifyContent: "center", marginTop: 14 },
  saveText: { color: "#FFFFFF", fontSize: 15, fontWeight: "800" },
  cancel: { alignItems: "center", paddingTop: 15 },
  cancelText: { color: "#756E7D", fontSize: 14, fontWeight: "700" },
});
