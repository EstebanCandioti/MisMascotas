import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title:"Mis Mascotas", description:"Gestión simple y compartida de la salud y el cuidado de tus mascotas.", icons:{icon:"/favicon.svg",shortcut:"/favicon.svg"} };
export default function RootLayout({children}:Readonly<{children:React.ReactNode}>){return <html lang="es"><body>{children}</body></html>}
