import { useCallback, useState } from "react";
import {
  Alert,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useFocusEffect, useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";
import { login as loginRequest } from "../../services/api";

export default function LoginScreen() {
  const router = useRouter();
  const { setPendingAuthToken, setPendingCredentials } = useAppData();

  const [email, setEmail] = useState("");
  const [emailInputKey, setEmailInputKey] = useState(0);
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  useFocusEffect(
    useCallback(() => {
      setEmail("");
      setEmailInputKey((key) => key + 1);
      setPassword("");
      setShowPassword(false);
    }, []),
  );

  async function handleLogin() {
    if (!email.trim() || !password) {
      Alert.alert("Completá los datos", "Ingresá tu correo y contraseña.");
      return;
    }

    setLoading(true);
    try {
      const response = await loginRequest(email.trim().toLowerCase(), password);
      setPendingAuthToken(response.token);
      setPendingCredentials(email.trim().toLowerCase(), password);
      router.push("/auth/verificar-codigo");
    } catch (error) {
      Alert.alert("No se pudo iniciar sesión", error instanceof Error ? error.message : "Revisá tus datos e intentá nuevamente.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.logo}>
          <Ionicons name="paw-outline" size={28} color="#7C4DFF" />
          <Text style={styles.logoText}>Mis Mascotas</Text>
        </View>

        <View style={styles.header}>
          <Text style={styles.welcome}>BIENVENIDO DE NUEVO</Text>
          <Text style={styles.title}>Iniciar sesión</Text>
          <Text style={styles.subtitle}>
            Ingresá para ver cómo están tus mascotas.
          </Text>
        </View>

        <View style={styles.form}>
          <Text style={styles.label}>Correo electrónico</Text>
          <View style={styles.inputBox}>
            <Ionicons name="mail-outline" size={21} color="#948FA0" />
            <View style={styles.inputField}>
              {!email && (
                <View pointerEvents="none" style={styles.placeholderOverlay}>
                  <Text style={styles.emailPlaceholder}>usuario@demo.com</Text>
                </View>
              )}
              <TextInput
                key={emailInputKey}
                style={styles.input}
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
              />
            </View>
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
              secureTextEntry={!showPassword}
            />

            <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
              <Text style={styles.showPassword}>
                {showPassword ? "Ocultar" : "Ver"}
              </Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity style={styles.button} onPress={handleLogin} disabled={loading}>
            <Text style={styles.buttonText}>{loading ? "Ingresando..." : "Ingresar"}</Text>
            <Ionicons name="chevron-forward" size={22} color="#FFFFFF" />
          </TouchableOpacity>
        </View>

        <Text style={styles.switchText}>
          ¿No tenés cuenta?{" "}
          <Text
            style={styles.switchLink}
            onPress={() => router.push("/auth/registro")}
          >
            Registrate gratis
          </Text>
        </Text>

        <Text style={styles.terms}>
          Al continuar, aceptás los Términos y la Política de Privacidad.
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#FFFFFF" },
  content: { flex: 1, paddingHorizontal: 39, paddingTop: 22 },

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
  inputField: { flex: 1, height: "100%", justifyContent: "center" },
  placeholderOverlay: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 13,
    justifyContent: "center",
  },
  emailPlaceholder: {
    color: "#7A7486",
    fontSize: 14,
    fontWeight: "600",
  },
  input: {
    flex: 1,
    color: "#171321",
    fontSize: 14,
    fontWeight: "600",
    marginLeft: 13,
  },
  showPassword: { color: "#7C4DFF", fontSize: 13, fontWeight: "700" },

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
