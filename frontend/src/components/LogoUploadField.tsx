"use client";

import { ChangeEvent, useState } from "react";
import Image from "next/image";
import { uploadOrganizationLogo } from "@/lib/api";

type LogoUploadFieldProps = {
  accessToken?: string;
  disabled?: boolean;
  label?: string;
  logoUrl?: string | null;
  organizationId?: string;
  tenantName: string;
  onChange: (logoUrl: string) => void;
};

export function LogoUploadField({
  accessToken,
  disabled = false,
  label = "Logo del gimnasio",
  logoUrl,
  organizationId,
  tenantName,
  onChange,
}: LogoUploadFieldProps) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function uploadLogo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setUploading(true);
    setError(null);
    try {
      if (accessToken && organizationId) {
        const organization = await uploadOrganizationLogo(accessToken, file);
        if (!organization.logoUrl) {
          throw new Error("El servidor guardo la imagen, pero no devolvio la URL del logo.");
        }
        onChange(organization.logoUrl);
      } else {
        const formData = new FormData();
        formData.append("tenantName", tenantName || "gimnasio");
        formData.append("file", file);
        const response = await fetch("/api/tenant-logo", {
          method: "POST",
          body: formData,
        });
        const payload = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(payload?.message ?? "No fue posible cargar el logo.");
        }
        onChange(payload.logoUrl);
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar el logo.");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  return (
    <section className="logo-upload-field">
      <div>
        <span>{label}</span>
        <strong>{logoUrl ? "Logo cargado" : "Sin logo cargado"}</strong>
        <small>
          {accessToken && organizationId
            ? "Se guarda de forma persistente para todas las sucursales."
            : "Se asociara al crear el gimnasio."}
        </small>
      </div>
      {logoUrl ? (
        <Image alt="Logo del gimnasio" height={56} src={logoUrl} unoptimized width={56} />
      ) : (
        <div className="logo-upload-placeholder">Logo</div>
      )}
      <label>
        {uploading ? "Cargando..." : "Seleccionar imagen"}
        <input
          accept={accessToken && organizationId ? "image/png,image/jpeg,image/webp" : "image/png,image/jpeg,image/webp,image/svg+xml"}
          disabled={disabled || uploading}
          onChange={uploadLogo}
          type="file"
        />
      </label>
      {error ? <small className="form-field-error">{error}</small> : null}
    </section>
  );
}
