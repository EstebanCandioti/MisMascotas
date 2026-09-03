import { useState } from "react";
import {
  Alert,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";

export default function RegistroScreen() {
  const router = useRouter();
  const { users, setUsers, setCurrentUser } = useAppData();

  const [name, setName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeatPassword, setRepeatPassword] = useState("");

  const register = () => {
    const normalizedEmail = email.trim().toLowerCase();
    if (!name.trim() || !lastName.trim() || !normalizedEmail || !password) { Alert.alert("Completá los datos", "Todos los campos son obligatorios."); return; }
    if (password !== repeatPassword) { Alert.alert("Las contraseñas no coinciden", "Revisá la confirmación de contraseña."); return; }
    if (users.some((user) => user.email === normalizedEmail)) { Alert.alert("Correo registrado", "Ya existe una cuenta con ese correo."); return; }
    const user = { id: `user-${Date.now()}`, name: `${name.trim()} ${lastName.trim()}`, email: normalizedEmail, password };
    setUsers([...users, user]); setCurrentUser(user); router.replace("/inicio");
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.logo}>
          <Ionicons name="paw-outline" size={28} color="#7C4DFF" />
          <Text style={styles.logoText}>Mis Mascotas</Text>
        </View>

        <View style={styles.header}>
          <Text style={styles.welcome}>CREÁ TU CUENTA</Text>
          <Text style={styles.title}>Empecemos</Text>
          <Text style={styles.subtitle}>
            Completá tus datos para cuidar mejor.
          </Text>
        </View>

        <View style={styles.form}>
          <View style={styles.namesRow}>
            <View style={styles.halfField}>
              <Text style={styles.label}>Nombre</Text>
              <View style={styles.inputBox}>
                <TextInput
                  style={styles.input}
                  value={name}
                  onChangeText={setName}
                  placeholder="Usuario"
                  placeholderTextColor="#7A7486"
                />
              </View>
            </View>

            <View style={styles.halfField}>
              <Text style={styles.label}>Apellido</Text>
              <View style={styles.inputBox}>
                <TextInput
                  style={styles.input}
                  value={lastName}
                  onChangeText={setLastName}
                  placeholder="Demo"
                  placeholderTextColor="#7A7486"
                />
              </View>
            </View>
          </View>

          <Text style={styles.label}>Correo electrónico</Text>
          <View style={styles.inputBox}>
            <Ionicons name="mail-outline" size={21} color="#948FA0" />
            <TextInput
              style={styles.input}
              value={email}
              onChangeText={setEmail}
              placeholder="usuario@demo.com"
              placeholderTextColor="#7A7486"
              keyboardType="email-address"
              autoCapitalize="none"
            />
          </View>

          <Text style={styles.label}>Contraseña</Text>
          <View style={styles.inputBox}>
            <Ionicons name="shield-outline" size={21} color="#948FA0" />
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              placeholder="••••••••"
              placeholderTextColor="#171321"
              secureTextEntry
            />
          </View>

          <Text style={styles.label}>Repetir contraseña</Text>
          <View style={styles.inputBox}>
            <Ionicons
              name="shield-checkmark-outline"
              size={21}
              color="#948FA0"
            />
            <TextInput
              style={styles.input}
              value={repeatPassword}
              onChangeText={setRepeatPassword}
              placeholder="••••••••"
              placeholderTextColor="#171321"
              secureTextEntry
            />
          </View>

          <TouchableOpacity style={styles.button} onPress={register}>
            <Text style={styles.buttonText}>Crear cuenta</Text>
            <Ionicons name="chevron-forward" size={22} color="#FFFFFF" />
          </TouchableOpacity>
        </View>

        <Text style={styles.switchText}>
          ¿Ya tenés cuenta?{" "}
          <Text
            style={styles.switchLink}
            onPress={() => router.replace("/login")}
          >
            Iniciar sesión
          </Text>
        </Text>

        <Text style={styles.terms}>
          Al continuar, aceptás los Términos y la Política de Privacidad.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FFFFFF" },
  content: {
    flexGrow: 1,
    paddingHorizontal: 39,
    paddingTop: 22,
    paddingBottom: 30,
  },

  logo: { flexDirection: "row", alignItems: "center", gap: 8 },
  logoText: { color: "#7C4DFF", fontSize: 16, fontWeight: "700" },

  header: { marginTop: 48 },
  welcome: {
    color: "#7C4DFF",
    fontSize: 12,
    fontWeight: "800",
    letterSpacing: 1.1,
  },
  title: {
    color: "#171321",
    fontSize: 31,
    fontWeight: "400",
    marginTop: 19,
  },
  subtitle: { color: "#756F80", fontSize: 16, marginTop: 13 },

  form: { marginTop: 33 },
  namesRow: { flexDirection: "row", gap: 12 },
  halfField: { flex: 1 },

  label: {
    color: "#171321",
    fontSize: 13,
    fontWeight: "700",
    marginBottom: 9,
  },
  inputBox: {
    height: 51,
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#E6E1EC",
    borderRadius: 14,
    backgroundColor: "#FCFAFD",
    paddingHorizontal: 15,
    marginBottom: 19,
  },
  input: {
    flex: 1,
    color: "#171321",
    fontSize: 14,
    fontWeight: "600",
    marginLeft: 13,
  },

  button: {
    height: 51,
    backgroundColor: "#7C4DFF",
    borderRadius: 14,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 7,
  },
  buttonText: { color: "#FFFFFF", fontSize: 16, fontWeight: "800" },

  switchText: {
    textAlign: "center",
    color: "#756F80",
    fontSize: 14,
    marginTop: 27,
  },
  switchLink: { color: "#7C4DFF", fontWeight: "800" },

  terms: {
    color: "#9C96A7",
    fontSize: 11,
    textAlign: "center",
    marginTop: 22,
  },
});
