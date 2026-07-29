import type { Metadata } from "next";
import { SessionRouteGuard } from "@/components/SessionRouteGuard";
import { TenantThemeProvider } from "@/components/TenantThemeProvider";
import "./globals.css";
import "./workout-pdf-single-template.css";

export const metadata: Metadata = {
  title: "GymOS",
  description: "Operación modular para gimnasios y centros de bienestar",
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
