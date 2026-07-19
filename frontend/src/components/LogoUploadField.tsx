"use client";

import { ChangeEvent, useState } from "react";
import Image from "next/image";

type LogoUploadFieldProps = {
  disabled?: boolean;
  label?: string;
  logoUrl?: string | null;
  tenantName: string;
  onChange: (logoUrl: string) => void;
};

export function LogoUploadField({
  disabled = false,
  label = "Logo del gimnasio",
  logoUrl,
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
        <small>Se guardara en public/tenants/nombre-del-gimnasio/logo.*</small>
      </div>
      {logoUrl ? (
        <Image alt="Logo del gimnasio" height={56} src={logoUrl} unoptimized width={56} />
      ) : (
        <div className="logo-upload-placeholder">Logo</div>
      )}
      <label>
        {uploading ? "Cargando..." : "Seleccionar imagen"}
        <input accept="image/png,image/jpeg,image/webp,image/svg+xml" disabled={disabled || uploading} onChange={uploadLogo} type="file" />
      </label>
      {error ? <small className="form-field-error">{error}</small> : null}
    </section>
  );
}
