const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type CurrentUser = {
  userId: string;
  organizationId: string;
  branchId: string | null;
  username: string;
  fullName: string;
  permissions: string[];
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: CurrentUser;
};

export type StoredSession = {
  accessToken: string;
  refreshToken: string;
  user: CurrentUser;
};

export type OrganizationSummary = {
  id: string;
  legalName: string;
  tradeName: string;
  taxId: string | null;
  status: string;
  timezone: string;
  defaultCurrency: string;
  contactEmail: string | null;
  contactPhone: string | null;
};

export type BranchSummary = {
  id: string;
  name: string;
  code: string;
  status: string;
  timezone: string;
  phone: string | null;
  accessScope: string;
};

export type ClientSummary = {
  id: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  email: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  joinedAt: string;
  updatedAt: string;
};

export type ClientDetail = {
  id: string;
  personId: string;
  firstName: string;
  lastName: string;
  birthDate: string | null;
  gender: string | null;
  phone: string | null;
  email: string | null;
  photoReference: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  joinedAt: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ClientTimelineEntry = {
  action: string;
  origin: string | null;
  reason: string | null;
  occurredAt: string;
};

export type ClientDetailResponse = {
  client: ClientDetail;
  timeline: ClientTimelineEntry[];
};

export type ClientPayload = {
  firstName: string;
  lastName: string;
  birthDate?: string | null;
  gender?: string | null;
  phone?: string | null;
  email?: string | null;
  photoReference?: string | null;
  notes?: string | null;
};

export type ClientUpdatePayload = ClientPayload & {
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
};

export type OrganizationUpdatePayload = {
  legalName: string;
  tradeName: string;
  taxId?: string | null;
  timezone: string;
  defaultCurrency: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
};

export type BranchUpdatePayload = {
  name: string;
  code: string;
  timezone: string;
  phone?: string | null;
};

export type ProductSummary = {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  productType: "PHYSICAL" | "SERVICE" | "MEMBERSHIP_PLAN";
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type PlanSummary = {
  id: string;
  productId: string;
  sku: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
  benefitsJson: string | null;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type ProductPayload = {
  sku: string;
  name: string;
  description?: string | null;
  productType: "PHYSICAL" | "SERVICE" | "MEMBERSHIP_PLAN";
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type SaleSummary = {
  id: string;
  saleNumber: string;
  clientId: string | null;
  clientName: string;
  status: "PENDING_PAYMENT" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
  soldAt: string;
};

export type SaleHeader = SaleSummary & {
  branchId: string;
  subtotalCents: number;
  discountCents: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type SaleItemDetail = {
  id: string;
  productId: string | null;
  itemType: string;
  name: string;
  quantity: number;
  unitPriceCents: number;
  discountCents: number;
  totalCents: number;
  createdAt: string;
};

export type PaymentDetail = {
  id: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  status: "PENDING_VALIDATION" | "COMPLETED" | "REJECTED" | "CANCELLED";
  amountCents: number;
  currency: string;
  reference: string | null;
  receiptObjectKey: string | null;
  receivedAt: string;
  validatedAt: string | null;
  notes: string | null;
};

export type MembershipDetail = {
  id: string;
  clientId: string;
  planId: string;
  planName: string;
  status: "ACTIVE" | "EXPIRED" | "CANCELLED";
  activatedAt: string;
  startsOn: string;
  endsOn: string;
  graceEndsOn: string;
};

export type ClientMembershipSummary = MembershipDetail & {
  saleId: string;
  saleNumber: string;
};

export type RenewMembershipPayload = {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  reference?: string | null;
  validateNow: boolean;
  notes?: string | null;
};

export type ChangeMembershipPlanPayload = RenewMembershipPayload & {
  targetPlanId: string;
};

export type RenewMembershipResponse = {
  saleId: string;
  saleNumber: string;
  saleStatus: "PENDING_PAYMENT" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
  membership: ClientMembershipSummary | null;
};

export type KioskMembership = {
  id: string;
  planId: string;
  planName: string;
  status: "ACTIVE" | "EXPIRED" | "CANCELLED";
  startsOn: string;
  endsOn: string;
  graceEndsOn: string;
};

export type KioskPendingPayment = {
  id: string;
  saleId: string;
  saleNumber: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  status: "PENDING_VALIDATION";
  amountCents: number;
  currency: string;
  receivedAt: string;
};

export type KioskPlanOption = {
  id: string;
  productId: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
};

export type KioskSessionResponse = {
  sessionId: string;
  terminalName: string;
  expiresAt: string;
  verified: boolean;
};

export type KioskSummaryResponse = {
  sessionId: string;
  expiresAt: string;
  clientName: string;
  memberships: KioskMembership[];
  pendingPayments: KioskPendingPayment[];
};

export type SaleDetailResponse = {
  sale: SaleHeader;
  items: SaleItemDetail[];
  payments: PaymentDetail[];
  memberships: MembershipDetail[];
};

export type SalesReportTotals = {
  currency: string;
  saleCount: number;
  subtotalCents: number;
  discountCents: number;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  paidSales: number;
  partiallyPaidSales: number;
  pendingSales: number;
};

export type PaymentReportTotals = {
  paymentCount: number;
  completedPayments: number;
  pendingPayments: number;
  rejectedPayments: number;
  completedCents: number;
  pendingCents: number;
  rejectedCents: number;
};

export type SaleStatusBreakdown = {
  status: "PENDING_PAYMENT" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  saleCount: number;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
};

export type PaymentMethodBreakdown = {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  paymentCount: number;
  totalCents: number;
  completedCents: number;
  pendingCents: number;
  rejectedCents: number;
};

export type SalesReportResponse = {
  fromDate: string;
  toDate: string;
  currency: string;
  sales: SalesReportTotals;
  payments: PaymentReportTotals;
  salesByStatus: SaleStatusBreakdown[];
  paymentsByMethod: PaymentMethodBreakdown[];
  recentSales: SaleSummary[];
};

export type AccessDevice = {
  id: string;
  name: string;
  deviceCode: string;
  deviceType: "TURNSTILE" | "DOOR" | string;
  status: "ACTIVE" | "INACTIVE" | string;
  provider: string;
  lastSeenAt: string | null;
};

export type AccessDecision = {
  logId: string;
  decision: "GRANTED" | "DENIED";
  accessType: "MEMBERSHIP_CHECK" | "TEMPORARY_PASS" | "MANUAL_OPEN" | string;
  reason: string | null;
  commandStatus: "SIMULATED_OPEN" | "NOT_SENT" | string | null;
  occurredAt: string;
  deviceName: string | null;
  deviceCode: string | null;
  clientId: string | null;
  clientName: string | null;
  membershipId: string | null;
  planName: string | null;
  temporaryPassId: string | null;
};

export type AccessLog = {
  id: string;
  decision: "GRANTED" | "DENIED";
  accessType: string;
  source: string;
  reason: string | null;
  commandStatus: string | null;
  occurredAt: string;
  deviceName: string | null;
  deviceCode: string | null;
  clientId: string | null;
  clientName: string | null;
};

export type TemporaryPass = {
  id: string;
  clientId: string;
  clientName: string;
  status: "ACTIVE" | "CONSUMED" | "CANCELLED" | string;
  startsAt: string;
  expiresAt: string;
  maxUses: number;
  usesCount: number;
  reason: string;
};

export type AccessRequestPayload = {
  clientId?: string | null;
  identifier?: string | null;
  deviceCode?: string | null;
};

export type ManualOpenPayload = {
  clientId?: string | null;
  deviceCode?: string | null;
  reason: string;
};

export type TemporaryPassPayload = {
  clientId: string;
  startsAt?: string | null;
  expiresAt: string;
  maxUses: number;
  reason: string;
};

export type SaleItemPayload = {
  productId: string;
  quantity: number;
  discountCents: number;
};

export type ManualPaymentPayload = {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  amountCents: number;
  reference?: string | null;
  receiptObjectKey?: string | null;
  validateNow: boolean;
  notes?: string | null;
};

export type CreateSalePayload = {
  clientId?: string | null;
  items: SaleItemPayload[];
  discountCents: number;
  notes?: string | null;
  payment?: ManualPaymentPayload | null;
};

export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? "No fue posible iniciar sesion.");
  }

  return response.json();
}

export async function selectBranch(accessToken: string, branchId: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/select-branch", accessToken, {
    method: "POST",
    body: JSON.stringify({ branchId }),
  });
}

export async function logout(accessToken: string, refreshToken: string): Promise<void> {
  await apiFetch<void>("/auth/logout", accessToken, {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  });
}

export async function getCurrentOrganization(accessToken: string): Promise<OrganizationSummary> {
  return apiFetch<OrganizationSummary>("/organizations/current", accessToken);
}

export async function getBranches(accessToken: string): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>("/organizations/current/branches", accessToken);
}

export async function updateOrganization(
  accessToken: string,
  payload: OrganizationUpdatePayload,
): Promise<OrganizationSummary> {
  return apiFetch<OrganizationSummary>("/organizations/current", accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function updateBranch(
  accessToken: string,
  branchId: string,
  payload: BranchUpdatePayload,
): Promise<BranchSummary> {
  return apiFetch<BranchSummary>(`/organizations/current/branches/${branchId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function searchClients(
  accessToken: string,
  query: string,
  status = "",
): Promise<ClientSummary[]> {
  const params = new URLSearchParams();
  if (query.trim()) {
    params.set("query", query.trim());
  }
  if (status.trim()) {
    params.set("status", status.trim());
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<ClientSummary[]>(`/clients${suffix}`, accessToken);
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

export async function getClient(
  accessToken: string,
  clientId: string,
): Promise<ClientDetailResponse> {
  return apiFetch<ClientDetailResponse>(`/clients/${clientId}`, accessToken);
}

export async function getClientMemberships(
  accessToken: string,
  clientId: string,
): Promise<ClientMembershipSummary[]> {
  return apiFetch<ClientMembershipSummary[]>(`/clients/${clientId}/memberships`, accessToken);
}

export async function renewMembership(
  accessToken: string,
  membershipId: string,
  payload: RenewMembershipPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>(`/memberships/${membershipId}/renew`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function changeMembershipPlan(
  accessToken: string,
  membershipId: string,
  payload: ChangeMembershipPlanPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>(`/memberships/${membershipId}/change-plan`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function createKioskSession(terminalCode: string): Promise<KioskSessionResponse> {
  return publicApiFetch<KioskSessionResponse>(`/self-service/terminals/${terminalCode}/sessions`, {
    method: "POST",
  });
}

export async function verifyKioskSession(sessionId: string, identifier: string): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/verify`, {
    method: "POST",
    body: JSON.stringify({ identifier }),
  });
}

export async function getKioskSummary(sessionId: string): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/summary`);
}

export async function getKioskMembershipOptions(sessionId: string): Promise<KioskPlanOption[]> {
  return publicApiFetch<KioskPlanOption[]>(`/self-service/sessions/${sessionId}/membership-options`);
}

export async function renewMembershipFromKiosk(
  sessionId: string,
  membershipId: string,
  payload: RenewMembershipPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(`/self-service/sessions/${sessionId}/membership-renewals/${membershipId}`, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function changeMembershipPlanFromKiosk(
  sessionId: string,
  membershipId: string,
  payload: ChangeMembershipPlanPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(`/self-service/sessions/${sessionId}/membership-change-requests/${membershipId}`, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function endKioskSession(sessionId: string): Promise<void> {
  await publicApiFetch<void>(`/self-service/sessions/${sessionId}/end`, {
    method: "POST",
  });
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

export async function getProducts(accessToken: string): Promise<ProductSummary[]> {
  return apiFetch<ProductSummary[]>("/catalog/products", accessToken);
}

export async function createProduct(
  accessToken: string,
  payload: ProductPayload,
): Promise<ProductSummary> {
  return apiFetch<ProductSummary>("/catalog/products", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getPlans(accessToken: string): Promise<PlanSummary[]> {
  return apiFetch<PlanSummary[]>("/catalog/plans", accessToken);
}

export async function getRecentSales(accessToken: string): Promise<SaleSummary[]> {
  return apiFetch<SaleSummary[]>("/sales", accessToken);
}

export async function getSalesReport(
  accessToken: string,
  fromDate?: string,
  toDate?: string,
): Promise<SalesReportResponse> {
  const params = new URLSearchParams();
  if (fromDate) {
    params.set("fromDate", fromDate);
  }
  if (toDate) {
    params.set("toDate", toDate);
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<SalesReportResponse>(`/sales/reports/summary${suffix}`, accessToken);
}

export async function getAccessDevices(accessToken: string): Promise<AccessDevice[]> {
  return apiFetch<AccessDevice[]>("/access/devices", accessToken);
}

export async function getAccessLogs(accessToken: string, limit = 30): Promise<AccessLog[]> {
  return apiFetch<AccessLog[]>(`/access/logs?limit=${limit}`, accessToken);
}

export async function requestAccess(
  accessToken: string,
  payload: AccessRequestPayload,
): Promise<AccessDecision> {
  return apiFetch<AccessDecision>("/access/requests", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function manualOpenAccess(
  accessToken: string,
  payload: ManualOpenPayload,
): Promise<AccessDecision> {
  return apiFetch<AccessDecision>("/access/commands/open", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function createTemporaryPass(
  accessToken: string,
  payload: TemporaryPassPayload,
): Promise<TemporaryPass> {
  return apiFetch<TemporaryPass>("/access/commands/temporary", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function createSale(
  accessToken: string,
  payload: CreateSalePayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>("/sales", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getSale(
  accessToken: string,
  saleId: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}`, accessToken);
}

export async function addManualPayment(
  accessToken: string,
  saleId: string,
  payload: ManualPaymentPayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}/payments`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function validatePayment(
  accessToken: string,
  paymentId: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/validate`, accessToken, {
    method: "POST",
  });
}

export async function rejectPayment(
  accessToken: string,
  paymentId: string,
  reason: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/reject`, accessToken, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export async function uploadPaymentReceipt(
  accessToken: string,
  paymentId: string,
  file: File,
): Promise<SaleDetailResponse> {
  const body = new FormData();
  body.set("file", file);
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/receipt`, accessToken, {
    method: "POST",
    body,
  });
}

export function storeSession(session: AuthResponse) {
  window.localStorage.setItem("gymos.accessToken", session.accessToken);
  window.localStorage.setItem("gymos.refreshToken", session.refreshToken);
  window.localStorage.setItem("gymos.currentUser", JSON.stringify(session.user));
}

export function getStoredSession(): StoredSession | null {
  const accessToken = window.localStorage.getItem("gymos.accessToken");
  const refreshToken = window.localStorage.getItem("gymos.refreshToken");
  const currentUser = window.localStorage.getItem("gymos.currentUser");

  if (!accessToken || !refreshToken || !currentUser) {
    return null;
  }

  try {
    return {
      accessToken,
      refreshToken,
      user: JSON.parse(currentUser) as CurrentUser,
    };
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession() {
  window.localStorage.removeItem("gymos.accessToken");
  window.localStorage.removeItem("gymos.refreshToken");
  window.localStorage.removeItem("gymos.currentUser");
}

function normalizePayload<T extends Record<string, unknown>>(payload: T): T {
  return Object.fromEntries(
    Object.entries(payload).map(([key, value]) => [
      key,
      typeof value === "string" && value.trim() === "" ? null : value,
    ]),
  ) as T;
}

async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const isFormData = init.body instanceof FormData;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      Authorization: `Bearer ${accessToken}`,
      ...init.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? "No fue posible completar la operacion.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function publicApiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? "No fue posible completar la operacion.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
