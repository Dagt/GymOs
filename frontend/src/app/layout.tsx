import type { Metadata } from "next";
import { SessionRouteGuard } from "@/components/SessionRouteGuard";
import { TenantThemeProvider } from "@/components/TenantThemeProvider";
import "./globals.css";
import "./admin-hierarchy.css";
import "./settings-console-refinements.css";
import "./workout-pdf-single-template.css";

export const metadata: Metadata = {
  metadataBase: new URL("https://gymfort.mx"),
  title: { default: "GymFort | Gestión inteligente para gimnasios", template: "%s | GymFort" },
  description: "Plataforma todo en uno para administrar miembros, accesos, ventas, reservas, inventario y reportes de gimnasios.",
  applicationName: "GymFort",
  keywords: ["software para gimnasios", "gestión de gimnasios", "membresías", "control de acceso", "GymFort"],
  icons: {
    icon: [
      { url: "/gymfort_web_assets/gymfort-icon-16x16.png", sizes: "16x16", type: "image/png" },
      { url: "/gymfort_web_assets/gymfort-icon-32x32.png", sizes: "32x32", type: "image/png" },
      { url: "/gymfort_web_assets/gymfort-icon-192x192.png", sizes: "192x192", type: "image/png" },
    ],
    apple: "/gymfort_web_assets/gymfort-icon-180x180.png",
  },
  openGraph: {
    title: "GymFort | Todo tu gimnasio. Un solo sistema.",
    description: "Centraliza la operación de tu gimnasio y enfócate en crecer.",
    type: "website",
    locale: "es_MX",
    url: "https://gymfort.mx",
    siteName: "GymFort",
    images: [{ url: "/gymfort_web_assets/gymfort-banner-1920x600.png", width: 1920, height: 600, alt: "GymFort" }],
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
      data-scroll-behavior="smooth"
    >
      <body className="min-h-full flex flex-col">
        <SessionRouteGuard />
        {children}
        <TenantThemeProvider />
      </body>
    </html>
  );
}
