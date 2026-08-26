import { apiFetch, normalizePayload } from "./client";
import { requireOperationalPagination } from "./capabilities";
import type { PageResult } from "../pagination";
import type {
  ClientDetailResponse,
  ClientImportResult,
  ClientPayload,
  ClientSummary,
  ClientUpdatePayload,
} from "./client-types";

export async function searchClients(
  accessToken: string,
  query: string,
  status = "",
): Promise<ClientSummary[]> {
  const params = new URLSearchParams();
  if (query.trim()) params.set("query", query.trim());
  if (status.trim()) params.set("status", status.trim());
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<ClientSummary[]>(`/clients${suffix}`, accessToken);
}

export async function searchClientsPage(
  accessToken: string,
  { query = "", status = "", page = 0, size = 25 }: { query?: string; status?: string; page?: number; size?: number } = {},
): Promise<PageResult<ClientSummary>> {
  await requireOperationalPagination();
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (query.trim()) params.set("query", query.trim());
  if (status.trim()) params.set("status", status.trim());
  return apiFetch<PageResult<ClientSummary>>(`/clients/search?${params}`, accessToken);
}

export async function createClient(
  accessToken: string,
  payload: ClientPayload,
): Promise<ClientDetailResponse> {
  return apiFetch<ClientDetailResponse>("/clients", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function importClientsCsv(
  accessToken: string,
  fileName: string,
  csvText: string,
  dryRun: boolean,
): Promise<ClientImportResult> {
  return apiFetch<ClientImportResult>("/clients/imports", accessToken, {
    method: "POST",
    body: JSON.stringify({ fileName, csvText, dryRun }),
  });
}

export async function getClient(
  accessToken: string,
  clientId: string,
): Promise<ClientDetailResponse> {
  return apiFetch<ClientDetailResponse>(`/clients/${clientId}`, accessToken);
}

export async function updateClient(
  accessToken: string,
  clientId: string,
  payload: ClientUpdatePayload,
): Promise<ClientDetailResponse> {
  return apiFetch<ClientDetailResponse>(`/clients/${clientId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
