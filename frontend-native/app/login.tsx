import { useState } from "react";
import {
  Alert,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useAppData } from "../context/app-data-context";
import { loginUser, verifyCode } from "../services/auth";

export default function LoginScreen() {
  const router = useRouter();
  const { setCurrentUser } = useAppData();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [code, setCode] = useState("");
  const [preAuthToken, setPreAuthToken] = useState("");
  const [isCodeStep, setIsCodeStep] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async () => {
    const emailNormalizado = email.trim().toLowerCase();

    if (!emailNormalizado || !password) {
      Alert.alert("Faltan datos", "Ingresá tu correo y contraseña.");
      return;
    }

    try {
      setIsLoading(true);
      const response = await loginUser({ email: emailNormalizado, password });
      setPreAuthToken(response.token);
      setIsCodeStep(true);
      Alert.alert(
        "Código enviado",
        "Se envió un código de verificación a tu correo. Ingresalo para terminar el login.",
      );
    } catch (error) {
      Alert.alert(
        "No se pudo iniciar sesión",
        error instanceof Error ? error.message : "Revisá tus credenciales e intentá nuevamente.",
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyCode = async () => {
    if (!code.trim()) {
      Alert.alert("Falta el código", "Ingresá el código de verificación.");
      return;
    }

    try {
      setIsLoading(true);
      await verifyCode({ codigo: code.trim() }, preAuthToken);

      setCurrentUser({
        id: "backend-user",
        name: email.trim().split("@")[0],
        email: email.trim().toLowerCase(),
        password,
      });

      Alert.alert("Login exitoso", "Tu sesión quedó activa.");
      router.replace("/inicio");
    } catch (error) {
      Alert.alert(
        "Código inválido",
        error instanceof Error ? error.message : "El código ingresado no es válido.",
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleBackToLogin = () => {
    setIsCodeStep(false);
    setCode("");
    setPreAuthToken("");
  };

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
          {!isCodeStep ? (
            <>
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
                  secureTextEntry={!showPassword}
                />

                <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
                  <Ionicons
                    name={showPassword ? "eye-off-outline" : "eye-outline"}
                    size={21}
                    color="#7C4DFF"
                  />
                </TouchableOpacity>
              </View>

              <Pressable style={styles.button} onPress={handleLogin} disabled={isLoading}>
                <Text style={styles.buttonText}>{isLoading ? "Procesando..." : "Ingresar"}</Text>
                <Ionicons name="chevron-forward" size={22} color="#FFFFFF" />
              </Pressable>
            </>
          ) : (
            <>
              <Text style={styles.label}>Código de verificación</Text>
              <View style={styles.inputBox}>
                <Ionicons name="key-outline" size={21} color="#948FA0" />
                <TextInput
                  style={styles.input}
                  value={code}
                  onChangeText={setCode}
                  placeholder="Ingrese el código"
                  placeholderTextColor="#7A7486"
                  keyboardType="number-pad"
                  autoCapitalize="none"
                />
              </View>

              <Pressable style={styles.button} onPress={handleVerifyCode} disabled={isLoading}>
                <Text style={styles.buttonText}>{isLoading ? "Verificando..." : "Confirmar código"}</Text>
                <Ionicons name="checkmark-circle-outline" size={22} color="#FFFFFF" />
              </Pressable>

              <Pressable onPress={handleBackToLogin} style={styles.backToLoginButton}>
                <Text style={styles.backToLoginText}>Volver al login</Text>
              </Pressable>
            </>
          )}
        </View>

        <Text style={styles.switchText}>
          ¿No tenés cuenta?{" "}
          <Text
            style={styles.switchLink}
            onPress={() => router.push("/registro")}
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
  backToLoginButton: {
    alignItems: "center",
    justifyContent: "center",
    marginTop: 8,
  },
  backToLoginText: {
    color: "#7C4DFF",
    fontWeight: "800",
    fontSize: 18,
  },

  terms: {
    color: "#9C96A7",
    fontSize: 11,
    textAlign: "center",
    marginTop: 22,
  },
});
