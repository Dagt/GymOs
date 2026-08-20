import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/"],
    },
    host: "https://gymfort.mx",
    sitemap: "https://gymfort.mx/sitemap.xml",
  };
}
