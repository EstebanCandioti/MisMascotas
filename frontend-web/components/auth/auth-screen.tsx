"use client";

import { type FormEvent, useState } from "react";
import { ChevronLeft, ChevronRight, KeyRound, Mail, PawPrint, ShieldCheck } from "lucide-react";

import { useAuth } from "@/features/auth/auth-context";
import { getApiErrorMessage } from "@/lib/api/api-error";

export function AuthScreen() {
  const {
    status,
    notice,
    preAuthMessage,
    login,
    register,
    verifyCode,
    cancelVerification,
  } = useAuth();
  const [registerMode, setRegisterMode] = useState(false);
  const [showPass, setShowPass] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const run = async (action: () => Promise<void>) => {
    setError("");
    setSubmitting(true);
    try {
      await action();
    } catch (caught) {
      setError(getApiErrorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  const submitCredentials = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const email = String(values.get("email") ?? "").trim();
    const password = String(values.get("password") ?? "");

    if (registerMode) {
      const passwordConfirmation = String(values.get("passwordConfirmation") ?? "");
      if (password !== passwordConfirmation) {
        setError("Las contraseñas no coinciden.");
        return;
      }
      const nombre = [values.get("firstName"), values.get("lastName")]
        .map((value) => String(value ?? "").trim())
        .filter(Boolean)
        .join(" ");
      void run(() => register({ nombre, email, password }));
      return;
    }

    void run(() => login({ email, password }));
  };

  const submitCode = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    void run(() => verifyCode(String(values.get("code") ?? "").trim()));
  };

  const authBrand = (
    <section className="auth-brand">
      <div className="logo-big"><PawPrint /></div>
      <span>Tu familia, siempre cuidada</span>
      <h1>Mis Mascotas</h1>
      <p>Organizá la salud, los cuidados y los recuerdos de quienes hacen tu hogar más feliz.</p>
      <div className="auth-pets"><span>🐶</span><span>🐱</span><span>🐰</span><span>🐦</span></div>
    </section>
  );

  if (status === "loading") {
    return (
      <main className="auth-page">
        {authBrand}
        <section className="auth-card auth-loading" aria-live="polite">
          <div className="mobile-logo"><PawPrint /><b>Mis Mascotas</b></div>
          <span className="eyebrow">MIS MASCOTAS</span>
          <h2>Recuperando sesión…</h2>
          <p>Estamos verificando tu acceso.</p>
        </section>
      </main>
    );
  }

  if (status === "awaiting-code") {
    return (
      <main className="auth-page">
        {authBrand}
        <section className="auth-card">
          <div className="mobile-logo"><PawPrint /><b>Mis Mascotas</b></div>
          <span className="eyebrow">VERIFICACIÓN EN DOS PASOS</span>
          <h2>Revisá tu correo</h2>
          <p>{preAuthMessage || "Ingresá el código de verificación que te enviamos."}</p>
          <form onSubmit={submitCode}>
            <label>Código de verificación
              <div className="input-icon auth-code-input">
                <KeyRound />
                <input name="code" required autoFocus autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]*" minLength={6} maxLength={6} placeholder="000000" />
              </div>
            </label>
            {error && <p className="auth-error" role="alert">{error}</p>}
            <button className="primary" type="submit" disabled={submitting}>
              {submitting ? "Verificando…" : "Verificar código"}<ChevronRight />
            </button>
            <button className="auth-back" type="button" onClick={() => { setError(""); cancelVerification(); }} disabled={submitting}>
              <ChevronLeft /> Volver al inicio de sesión
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="auth-page">
      {authBrand}
      <section className="auth-card">
        <div className="mobile-logo"><PawPrint /><b>Mis Mascotas</b></div>
        <span className="eyebrow">{registerMode ? "CREÁ TU CUENTA" : "BIENVENIDO DE NUEVO"}</span>
        <h2>{registerMode ? "Empecemos" : "Iniciar sesión"}</h2>
        <p>{registerMode ? "Completá tus datos para cuidar mejor." : "Ingresá para ver cómo están tus mascotas."}</p>
        <form onSubmit={submitCredentials}>
          {registerMode && (
            <div className="form-row">
              <label>Nombre<input name="firstName" required autoComplete="given-name" placeholder="Nombre" /></label>
              <label>Apellido<input name="lastName" autoComplete="family-name" placeholder="Apellido" /></label>
            </div>
          )}
          <label>Correo electrónico
            <div className="input-icon"><Mail /><input name="email" required type="email" autoComplete="email" placeholder="tu@correo.com" /></div>
          </label>
          <label>Contraseña
            <div className="input-icon"><ShieldCheck /><input name="password" required minLength={registerMode ? 8 : undefined} type={showPass ? "text" : "password"} autoComplete={registerMode ? "new-password" : "current-password"} /><button type="button" onClick={() => setShowPass(!showPass)}>{showPass ? "Ocultar" : "Ver"}</button></div>
          </label>
          {registerMode && <label>Repetir contraseña<input name="passwordConfirmation" required minLength={8} type={showPass ? "text" : "password"} autoComplete="new-password" placeholder="••••••••" /></label>}
          {(error || notice) && <p className="auth-error" role="alert">{error || notice}</p>}
          <button className="primary" type="submit" disabled={submitting}>{submitting ? "Conectando…" : registerMode ? "Crear cuenta" : "Ingresar"}<ChevronRight /></button>
        </form>
        <div className="auth-switch">{registerMode ? "¿Ya tenés cuenta?" : "¿No tenés cuenta?"} <button onClick={() => { setError(""); setRegisterMode(!registerMode); }}>{registerMode ? "Iniciar sesión" : "Registrate gratis"}</button></div>
        <small className="terms">Al continuar, aceptás los Términos y la Política de Privacidad.</small>
      </section>
    </main>
  );
}
