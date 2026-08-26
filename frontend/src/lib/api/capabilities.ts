import { publicApiFetch } from "./client";

let paginationCheck: Promise<void> | null = null;

/** Do not silently treat a legacy array/ignored filter as a complete paginated result. */
export function requireOperationalPagination(): Promise<void> {
  if (!paginationCheck) {
    paginationCheck = publicApiFetch<{ capabilities?: string[] }>("/system/info")
      .then((info) => {
        if (!info?.capabilities?.includes("operational-pagination-v1")) {
          throw new Error("El frontend y el backend no están actualizados a la misma versión. Este servidor no admite los nuevos listados paginados. Reinicia o despliega el backend de esta rama y comprueba la URL de API del frontend. No se mostrará un total parcial como si fuera el total real.");
        }
      })
      .catch((error: unknown) => { paginationCheck = null; throw error; });
  }
  return paginationCheck;
}
