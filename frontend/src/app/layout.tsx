import type { Metadata } from "next";
import { SessionRouteGuard } from "@/components/SessionRouteGuard";
import { TenantThemeProvider } from "@/components/TenantThemeProvider";
import "./globals.css";
import "./workout-pdf-single-template.css";

export const metadata: Metadata = {
  title: { default: "GymFort | Gestión inteligente para gimnasios", template: "%s | GymFort" },
  description: "Plataforma todo en uno para administrar miembros, accesos, ventas, reservas, inventario y reportes de gimnasios.",
  applicationName: "GymFort",
  keywords: ["software para gimnasios", "gestión de gimnasios", "membresías", "control de acceso", "GymFort"],
  icons: { icon: "/brand/gymfort-symbol.svg" },
  openGraph: {
    title: "GymFort | Todo tu gimnasio. Un solo sistema.",
    description: "Centraliza la operación de tu gimnasio y enfócate en crecer.",
    type: "website",
    locale: "es_MX",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="es"
      className="h-full antialiased"
    >
      <body className="min-h-full flex flex-col">
        <SessionRouteGuard />
        {children}
        <TenantThemeProvider />
      </body>
    </html>
  );
}
