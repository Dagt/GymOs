import type { Metadata } from "next";
import { GlobalSessionNav } from "@/components/GlobalSessionNav";
import { TenantThemeProvider } from "@/components/TenantThemeProvider";
import "./globals.css";

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
        <GlobalSessionNav />
        {children}
        <TenantThemeProvider />
      </body>
    </html>
  );
}
