import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { NextRequest, NextResponse } from "next/server";

const allowedTypes = new Map([
  ["image/png", "png"],
  ["image/jpeg", "jpg"],
  ["image/webp", "webp"],
  ["image/svg+xml", "svg"],
]);

export async function POST(request: NextRequest) {
  const formData = await request.formData();
  const file = formData.get("file");
  const tenantName = String(formData.get("tenantName") ?? "gimnasio");

  if (!(file instanceof File)) {
    return NextResponse.json({ message: "Selecciona una imagen para el logo." }, { status: 400 });
  }

  const extension = allowedTypes.get(file.type);
  if (!extension) {
    return NextResponse.json({ message: "Usa una imagen PNG, JPG, WEBP o SVG." }, { status: 400 });
  }

  if (file.size > 2 * 1024 * 1024) {
    return NextResponse.json({ message: "El logo no debe pesar mas de 2 MB." }, { status: 400 });
  }

  const slug = slugify(tenantName);
  const tenantDirectory = path.join(process.cwd(), "public", "tenants", slug);
  await mkdir(tenantDirectory, { recursive: true });

  const bytes = Buffer.from(await file.arrayBuffer());
  const fileName = `logo.${extension}`;
  await writeFile(path.join(tenantDirectory, fileName), bytes);

  return NextResponse.json({
    logoUrl: `/tenants/${slug}/${fileName}`,
  });
}

function slugify(value: string) {
  const slug = value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "gimnasio";
}
