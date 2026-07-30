"use client";

import { ChangeEvent, useEffect, useState } from "react";
import Image from "next/image";
import { uploadOrganizationLogo } from "@/lib/api";

type LogoUploadFieldProps = {
  accessToken?: string;
  deferUpload?: boolean;
  disabled?: boolean;
  label?: string;
  logoUrl?: string | null;
  organizationId?: string;
  onChange?: (logoUrl: string) => void;
  onFileChange?: (file: File | null) => void;
};

export function LogoUploadField({
  accessToken,
  deferUpload = false,
  disabled = false,
  label = "Logo del gimnasio",
  logoUrl,
  organizationId,
  onChange,
  onFileChange,
}: LogoUploadFieldProps) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  async function uploadLogo(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setError(null);
    const validationError = validateLogoFile(file);
    if (validationError) {
      setError(validationError);
      event.target.value = "";
      return;
    }

    if (deferUpload) {
      setPreviewUrl(URL.createObjectURL(file));
      onFileChange?.(file);
      event.target.value = "";
      return;
    }

    setUploading(true);
    try {
      if (!accessToken || !organizationId || !onChange) {
        throw new Error("No hay un gimnasio disponible para guardar el logo.");
      }
      const organization = await uploadOrganizationLogo(accessToken, file);
      if (!organization.logoUrl) {
        throw new Error("El servidor guardo la imagen, pero no devolvio la URL del logo.");
      }
      onChange(organization.logoUrl);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar el logo.");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  const visibleLogoUrl = previewUrl ?? logoUrl;

  return (
    <section className="logo-upload-field">
      <div>
        <span>{label}</span>
        <strong>
          {previewUrl ? "Logo seleccionado" : logoUrl ? "Logo cargado" : "Sin logo cargado"}
        </strong>
        <small>
          {deferUpload
            ? "Se guardara automaticamente al crear el gimnasio."
            : accessToken && organizationId
            ? "Se guarda de forma persistente para todas las sucursales."
            : "Selecciona primero un gimnasio."}
        </small>
      </div>
      {visibleLogoUrl ? (
        <Image alt="Logo del gimnasio" height={56} src={visibleLogoUrl} unoptimized width={56} />
      ) : (
        <div className="logo-upload-placeholder">Logo</div>
      )}
      <label>
        {uploading ? "Cargando..." : "Seleccionar imagen"}
        <input
          accept="image/png,image/jpeg,image/webp"
          disabled={disabled || uploading}
          onChange={uploadLogo}
          type="file"
        />
      </label>
      {error ? <small className="form-field-error">{error}</small> : null}
    </section>
  );
}

function validateLogoFile(file: File) {
  if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
    return "Usa una imagen JPG, PNG o WebP.";
  }
  if (file.size > 2 * 1024 * 1024) {
    return "El logo no puede superar 2 MB.";
  }
  return null;
}
