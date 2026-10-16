import { useState } from "react";
import { Alert, SafeAreaView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useAppData } from "../../context/app-data-context";
import { ApiError, verifyCode } from "../../services/api";

export default function VerificarCodigoScreen() {
  const router = useRouter();
  const { pendingAuthToken, completeLogin, setPendingAuthToken, resendCode } = useAppData();
  const [codigo, setCodigo] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);

  async function handleVerify() {
    if (!pendingAuthToken) {
      router.replace("/auth/login");
      return;
    }

    if (codigo.length !== 6) {
      Alert.alert("Código incompleto", "Ingresá el código de 6 dígitos que recibiste por email.");
      return;
    }

    setLoading(true);
    try {
      const response = await verifyCode(pendingAuthToken, codigo.trim());
      await completeLogin(response.token);
      router.replace("/home/inicio");
    } catch (error) {
      if (error instanceof ApiError && error.status === 410) {
        setCodigo("");
        Alert.alert("Código vencido", "El código venció, te enviamos uno nuevo. Revisá tu email.");
      } else {
        Alert.alert("Código incorrecto", "El código no es válido. Revisalo e intentá nuevamente.");
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    setResending(true);
    try {
      await resendCode();
      setCodigo("");
      Alert.alert("Código reenviado", "Te enviamos un código nuevo a tu email.");
    } catch (error) {
      Alert.alert("No se pudo reenviar", error instanceof Error ? error.message : "Volvé a iniciar sesión e intentá nuevamente.");
    } finally {
      setResending(false);
    }
  }

  function cancelVerification() {
    setPendingAuthToken(null);
    router.replace("/auth/login");
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <View style={styles.logo}>
          <Ionicons name="paw-outline" size={28} color="#7C4DFF" />
          <Text style={styles.logoText}>Mis Mascotas</Text>
        </View>

        <View style={styles.header}>
          <Text style={styles.welcome}>VERIFICACIÓN</Text>
          <Text style={styles.title}>Revisá tu email</Text>
          <Text style={styles.subtitle}>Te enviamos un código para confirmar tu inicio de sesión.</Text>
        </View>

        <View style={styles.form}>
          <Text style={styles.label}>Código de verificación</Text>
          <TextInput
            style={styles.codeInput}
            value={codigo}
            onChangeText={(value) => setCodigo(value.replace(/\D/g, "").slice(0, 6))}
            placeholder="123456"
            placeholderTextColor="#7A7486"
            keyboardType="number-pad"
            maxLength={6}
            autoFocus
          />

          <TouchableOpacity style={styles.button} onPress={handleVerify} disabled={loading}>
            <Text style={styles.buttonText}>{loading ? "Verificando..." : "Confirmar"}</Text>
            <Ionicons name="chevron-forward" size={22} color="#FFFFFF" />
          </TouchableOpacity>

          <TouchableOpacity onPress={handleResend} disabled={loading || resending}>
            <Text style={styles.resendText}>{resending ? "Enviando..." : "Reenviar código"}</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={cancelVerification}>
          <Text style={styles.backText}>Volver al inicio de sesión</Text>
        </TouchableOpacity>
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
  welcome: { color: "#7C4DFF", fontSize: 12, fontWeight: "800", letterSpacing: 1.1 },
  title: { color: "#171321", fontSize: 31, fontWeight: "400", marginTop: 19 },
  subtitle: { color: "#756F80", fontSize: 16, marginTop: 13, lineHeight: 23 },
  form: { marginTop: 33 },
  label: { color: "#171321", fontSize: 13, fontWeight: "700", marginBottom: 9 },
  codeInput: { height: 56, borderWidth: 1, borderColor: "#E6E1EC", borderRadius: 14, backgroundColor: "#FCFAFD", paddingHorizontal: 15, color: "#171321", fontSize: 24, fontWeight: "700", letterSpacing: 8, textAlign: "center", marginBottom: 19 },
  button: { height: 51, backgroundColor: "#7C4DFF", borderRadius: 14, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7 },
  buttonText: { color: "#FFFFFF", fontSize: 16, fontWeight: "800" },
  resendText: { color: "#7C4DFF", fontSize: 14, fontWeight: "800", textAlign: "center", marginTop: 18 },
  backText: { color: "#7C4DFF", fontSize: 14, fontWeight: "800", textAlign: "center", marginTop: 27 },
});
