"use client";

import { useState } from "react";
import { ChevronRight, Mail, PawPrint, ShieldCheck } from "lucide-react";

export function AuthScreen({ onLogin }: { onLogin: () => void }) {
  const [register, setRegister] = useState(false);
  const [showPass, setShowPass] = useState(false);

  return <main className="auth-page"><section className="auth-brand"><div className="logo-big"><PawPrint /></div><span>Tu familia, siempre cuidada</span><h1>Mis Mascotas</h1><p>Organizá la salud, los cuidados y los recuerdos de quienes hacen tu hogar más feliz.</p><div className="auth-pets"><span>🐶</span><span>🐱</span><span>🐰</span><span>🐦</span></div></section><section className="auth-card"><div className="mobile-logo"><PawPrint /><b>Mis Mascotas</b></div><span className="eyebrow">{register ? "CREÁ TU CUENTA" : "BIENVENIDO DE NUEVO"}</span><h2>{register ? "Empecemos" : "Iniciar sesión"}</h2><p>{register ? "Completá tus datos para cuidar mejor." : "Ingresá para ver cómo están tus mascotas."}</p><form onSubmit={(event) => { event.preventDefault(); onLogin(); }}>{register && <div className="form-row"><label>Nombre<input required placeholder="Usuario" /></label><label>Apellido<input required placeholder="Demo" /></label></div>}<label>Correo electrónico<div className="input-icon"><Mail /><input required type="email" defaultValue="usuario@demo.com" /></div></label><label>Contraseña<div className="input-icon"><ShieldCheck /><input required type={showPass ? "text" : "password"} defaultValue="Demo1234" /><button type="button" onClick={() => setShowPass(!showPass)}>{showPass ? "Ocultar" : "Ver"}</button></div></label>{register && <label>Repetir contraseña<input required type="password" placeholder="••••••••" /></label>}<button className="primary" type="submit">{register ? "Crear cuenta" : "Ingresar"}<ChevronRight /></button></form><div className="auth-switch">{register ? "¿Ya tenés cuenta?" : "¿No tenés cuenta?"} <button onClick={() => setRegister(!register)}>{register ? "Iniciar sesión" : "Registrate gratis"}</button></div><small className="terms">Al continuar, aceptás los Términos y la Política de Privacidad.</small></section></main>;
}
