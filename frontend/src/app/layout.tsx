import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { BrandTheme } from "@/components/BrandTheme";
import { GlobalSessionNav } from "@/components/GlobalSessionNav";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

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
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <GlobalSessionNav />
        {children}
        <BrandTheme />
      </body>
    </html>
  );
}
