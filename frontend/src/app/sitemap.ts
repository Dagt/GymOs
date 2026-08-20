import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const lastModified = new Date("2026-08-20T00:00:00-06:00");

  return [
    { url: "https://gymfort.mx", lastModified, changeFrequency: "weekly", priority: 1 },
    { url: "https://gymfort.mx/privacy-policy", lastModified, changeFrequency: "monthly", priority: 0.8 },
    { url: "https://gymfort.mx/terms-of-service", lastModified, changeFrequency: "monthly", priority: 0.8 },
    { url: "https://gymfort.mx/support", lastModified, changeFrequency: "weekly", priority: 0.8 },
    { url: "https://gymfort.mx/login", lastModified, changeFrequency: "monthly", priority: 0.5 },
  ];
}
