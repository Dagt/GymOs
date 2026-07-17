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

export type ManagedOrganizationSummary = OrganizationSummary & {
  branchCount: number;
  activeBranchCount: number;
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

export type CreateOrganizationPayload = OrganizationUpdatePayload & {
  firstBranchName: string;
  firstBranchCode: string;
  firstBranchPhone?: string | null;
};

export type BranchUpdatePayload = {
  name: string;
  code: string;
  timezone: string;
  phone?: string | null;
};

export type CreateBranchPayload = BranchUpdatePayload;

export type RoleOption = {
  id: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissionCount: number;
};

export type InternalUserSummary = {
  id: string;
  fullName: string;
  username: string;
  email: string | null;
  phone: string | null;
  status: string;
  roles: string[];
  branches: string[];
};

export type CreateInternalUserPayload = {
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  username: string;
  temporaryPassword: string;
  roleId: string;
  branchIds: string[];
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

export type RefundDetail = {
  id: string;
  saleId: string;
  paymentId: string | null;
  status: "REQUESTED" | "PROCESSED" | "REJECTED" | "CANCELLED" | string;
  amountCents: number;
  currency: string;
  reason: string;
  idempotencyKey: string | null;
  requestedBy: string | null;
  requestedAt: string;
  processedAt: string | null;
  createdAt: string;
  updatedAt: string;
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
  idempotencyKey?: string | null;
};

export type ChangeMembershipPlanPayload = RenewMembershipPayload & {
  targetPlanId: string;
};

export type BenefitSummary = {
  id: string;
  branchId: string | null;
  code: string;
  name: string;
  benefitType: string;
  discountType: string;
  discountValueCents: number | null;
  discountPercent: number | null;
  requiresEvidence: boolean;
  combinable: boolean;
  priority: number;
  validFrom: string | null;
  validUntil: string | null;
  status: "ACTIVE" | "INACTIVE";
};

export type BenefitPayload = {
  branchId?: string | null;
  code: string;
  name: string;
  benefitType: "STUDENT" | "SENIOR" | "AGREEMENT" | "PROMOTION" | "ACCESS" | "CUSTOM";
  discountType: "PERCENT" | "FIXED" | "SPECIAL_PRICE" | "NONE";
  discountValueCents?: number | null;
  discountPercent?: number | null;
  requiresEvidence: boolean;
  combinable: boolean;
  priority: number;
  validFrom?: string | null;
  validUntil?: string | null;
  status: "ACTIVE" | "INACTIVE";
};

export type ClientBenefitSummary = {
  id: string;
  clientId: string;
  benefitId: string;
  benefitName: string;
  status: string;
  validFrom: string | null;
  validUntil: string | null;
  verifiedAt: string | null;
};

export type AssignClientBenefitPayload = {
  benefitId: string;
  branchId?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
  notes?: string | null;
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
  sessionToken: string;
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
  refunds: RefundDetail[];
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

export type VisitPass = {
  id: string;
  clientId: string;
  clientName: string;
  saleId: string | null;
  passType: "DROP_IN" | "VISIT_PACK" | "COURTESY" | "PROMOTION" | string;
  status: "ACTIVE" | "CONSUMED" | "CANCELLED" | string;
  totalVisits: number;
  remainingVisits: number;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
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

export type CreateVisitPassPayload = {
  clientId: string;
  saleId?: string | null;
  passType: "DROP_IN" | "VISIT_PACK" | "COURTESY" | "PROMOTION";
  totalVisits: number;
  validFrom?: string | null;
  validUntil?: string | null;
};

export type ConsumeVisitPassPayload = {
  reason?: string | null;
};

export type InventoryStock = {
  stockId: string | null;
  productId: string;
  sku: string;
  productName: string;
  priceCents: number;
  currency: string;
  quantityOnHand: number;
  reorderPoint: number;
  averageCostCents: number | null;
  updatedAt: string | null;
  lowStock: boolean;
};

export type InventoryMovement = {
  id: string;
  productId: string;
  sku: string;
  productName: string;
  movementType: InventoryMovementType;
  quantityDelta: number;
  quantityBefore: number;
  quantityAfter: number;
  unitCostCents: number | null;
  referenceType: string | null;
  referenceId: string | null;
  reason: string;
  occurredAt: string;
};

export type InventoryMovementType =
  | "INITIAL"
  | "PURCHASE"
  | "ADJUSTMENT_IN"
  | "ADJUSTMENT_OUT"
  | "WASTE"
  | "RETURN"
  | "SALE";

export type InventoryMovementPayload = {
  productId: string;
  movementType: InventoryMovementType;
  quantity: number;
  unitCostCents?: number | null;
  reorderPoint?: number | null;
  referenceType?: string | null;
  referenceId?: string | null;
  reason: string;
};

export type ResourceSummary = {
  id: string;
  name: string;
  resourceType: "ROOM" | "CLASS_AREA" | "EQUIPMENT" | string;
  capacity: number;
  slotMinutes: number;
  status: "ACTIVE" | "INACTIVE" | string;
  opensAt: string;
  closesAt: string;
  notes: string | null;
};

export type BookingSummary = {
  id: string;
  resourceId: string;
  resourceName: string;
  clientId: string;
  clientName: string;
  status: "RESERVED" | "CONFIRMED" | "CHECKED_IN" | "COMPLETED" | "CANCELLED" | string;
  startsAt: string;
  endsAt: string;
  notes: string | null;
  bookingPaymentId: string | null;
  saleId: string | null;
  paymentStatus: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED" | string | null;
  saleNumber: string | null;
};

export type AvailabilitySlot = {
  startsAt: string;
  endsAt: string;
  reserved: number;
  capacity: number;
  available: boolean;
};

export type CreateResourcePayload = {
  name: string;
  resourceType: string;
  capacity: number;
  slotMinutes: number;
  opensAt?: string | null;
  closesAt?: string | null;
  notes?: string | null;
};

export type CreateBookingPayload = {
  resourceId: string;
  clientId: string;
  startsAt: string;
  endsAt: string;
  notes?: string | null;
};

export type LinkBookingPaymentPayload = {
  saleId: string;
  status: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED";
  expiresAt?: string | null;
};

export type CoachSummary = {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  specialtiesJson: string | null;
  bio: string | null;
  status: "ACTIVE" | "INACTIVE" | string;
  createdAt: string;
  updatedAt: string;
};

export type WorkoutSummary = {
  id: string;
  coachId: string | null;
  coachName: string | null;
  title: string;
  goal: string | null;
  level: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL" | string;
  status: "ACTIVE" | "INACTIVE" | string;
  latestVersionId: string | null;
  latestVersionNumber: number | null;
  createdAt: string;
  updatedAt: string;
};

export type WorkoutVersion = {
  id: string;
  workoutId: string;
  versionNumber: number;
  summary: string | null;
  contentJson: string;
  status: "PUBLISHED" | "DRAFT" | string;
  createdAt: string;
};

export type WorkoutDetail = {
  workout: Omit<WorkoutSummary, "latestVersionId" | "latestVersionNumber">;
  versions: WorkoutVersion[];
};

export type WorkoutAssignment = {
  id: string;
  workoutId: string;
  workoutVersionId: string;
  clientId: string;
  clientName: string;
  coachId: string | null;
  coachName: string | null;
  workoutTitle: string;
  level: string;
  versionNumber: number;
  status: "ACTIVE" | "COMPLETED" | "CANCELLED" | string;
  assignedOn: string;
  startsOn: string | null;
  endsOn: string | null;
  notes: string | null;
};

export type CoachReport = {
  coachCount: number;
  workoutCount: number;
  activeAssignments: number;
  assignmentsLast30Days: number;
};

export type CreateCoachPayload = {
  displayName: string;
  email?: string | null;
  phone?: string | null;
  specialties?: string[];
  bio?: string | null;
};

export type CreateWorkoutPayload = {
  coachId?: string | null;
  title: string;
  goal?: string | null;
  level: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL";
  summary?: string | null;
  contentJson: string;
};

export type AssignWorkoutPayload = {
  clientId: string;
  workoutVersionId?: string | null;
  startsOn?: string | null;
  endsOn?: string | null;
  notes?: string | null;
};

export type ProfessionalSummary = {
  id: string;
  displayName: string;
  professionalType: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  status: "ACTIVE" | "INACTIVE" | string;
  createdAt: string;
  updatedAt: string;
};

export type ProfessionalServiceSummary = {
  id: string;
  professionalId: string;
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type ProfessionalAvailability = {
  id: string;
  professionalId: string;
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
  status: string;
};

export type CreateProfessionalPayload = {
  displayName: string;
  professionalType: string;
  email?: string | null;
  phone?: string | null;
  bio?: string | null;
};

export type CreateProfessionalServicePayload = {
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
};

export type CreateProfessionalAvailabilityPayload = {
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
};

export type NotificationLog = {
  id: string;
  channel: string;
  recipient: string | null;
  subject: string | null;
  body: string;
  status: string;
  triggerType: string | null;
  referenceType: string | null;
  referenceId: string | null;
  scheduledAt: string | null;
  sentAt: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type CreateNotificationPayload = {
  channel: string;
  recipient?: string | null;
  subject?: string | null;
  body: string;
  triggerType?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
  scheduledAt?: string | null;
};

export type AutomationRule = {
  organizationId: string;
  branchId: string | null;
  id: string;
  name: string;
  triggerType: string;
  conditionJson: string;
  actionType: string;
  actionConfigJson: string;
  status: "ENABLED" | "DISABLED" | string;
  retryLimit: number;
  scheduleCron: string | null;
  nextRunAt: string | null;
  lockedUntil: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AutomationExecution = {
  id: string;
  ruleId: string;
  status: string;
  triggerReference: string | null;
  errorMessage: string | null;
  occurredAt: string;
};

export type CreateAutomationPayload = {
  name: string;
  triggerType: string;
  conditionJson?: string | null;
  actionType: string;
  actionConfigJson?: string | null;
  retryLimit: number;
};

export type OwnerDashboard = {
  fromDate: string;
  toDate: string;
  paidRevenueCents: number;
  saleCount: number;
  newClients: number;
  activeMemberships: number;
  bookingCount: number;
  lowStockItems: number;
  activeWorkoutAssignments: number;
  activeProfessionals: number;
  queuedNotifications: number;
  enabledAutomations: number;
};

export type OnboardingChecklist = {
  organizationProfileComplete: boolean;
  branchConfigured: boolean;
  hasMembershipPlans: boolean;
  hasRetailProducts: boolean;
  hasKioskTerminal: boolean;
  hasClients: boolean;
  readyForPilot: boolean;
  activePlanCount: number;
  activeProductCount: number;
  clientCount: number;
  activeKioskCount: number;
};

export type PilotSetupPayload = {
  organization: OrganizationUpdatePayload;
  branch: BranchUpdatePayload;
  plans: Array<{
    sku: string;
    name: string;
    description?: string | null;
    priceCents: number;
    currency: string;
    durationDays: number;
    graceDays: number;
    benefitsJson?: string | null;
  }>;
  products: Array<{
    sku: string;
    name: string;
    description?: string | null;
    productType: "PHYSICAL" | "SERVICE";
    priceCents: number;
    currency: string;
    taxable: boolean;
  }>;
  kioskTerminalCode?: string | null;
  kioskName?: string | null;
};

export type PilotSetupResult = {
  checklist: OnboardingChecklist;
  productsCreated: number;
  plansCreated: number;
  kioskCreated: boolean;
};

export type ClientImportRow = {
  rowNumber: number;
  status: "VALID" | "CREATED" | "SKIPPED" | "ERROR";
  reason: string;
  clientId: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
};

export type ClientImportResult = {
  batchId: string;
  dryRun: boolean;
  status: string;
  totalRows: number;
  createdCount: number;
  skippedCount: number;
  errorCount: number;
  createdAt: string;
  rows: ClientImportRow[];
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
  idempotencyKey?: string | null;
};

export type SaleRefundPayload = {
  paymentId?: string | null;
  amountCents: number;
  reason: string;
  idempotencyKey?: string | null;
};

export type CreateSalePayload = {
  clientId?: string | null;
  items: SaleItemPayload[];
  discountCents: number;
  notes?: string | null;
  payment?: ManualPaymentPayload | null;
  idempotencyKey?: string | null;
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
    throw new Error(error?.message ?? "No fue posible iniciar sesión.");
  }

  return response.json();
}

export async function selectBranch(accessToken: string, branchId: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/select-branch", accessToken, {
    method: "POST",
    body: JSON.stringify({ branchId }),
  });
}

export async function selectOrganization(accessToken: string, organizationId: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/select-organization", accessToken, {
    method: "POST",
    body: JSON.stringify({ organizationId }),
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

export async function getOrganizations(accessToken: string): Promise<ManagedOrganizationSummary[]> {
  return apiFetch<ManagedOrganizationSummary[]>("/organizations", accessToken);
}

export async function createOrganization(
  accessToken: string,
  payload: CreateOrganizationPayload,
): Promise<ManagedOrganizationSummary> {
  return apiFetch<ManagedOrganizationSummary>("/organizations", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getBranches(accessToken: string): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>("/organizations/current/branches", accessToken);
}

export async function createBranch(
  accessToken: string,
  payload: CreateBranchPayload,
): Promise<BranchSummary> {
  return apiFetch<BranchSummary>("/organizations/current/branches", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
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

export async function getUserRoles(accessToken: string): Promise<RoleOption[]> {
  return apiFetch<RoleOption[]>("/users/roles", accessToken);
}

export async function getInternalUsers(accessToken: string): Promise<InternalUserSummary[]> {
  return apiFetch<InternalUserSummary[]>("/users/internal", accessToken);
}

export async function createInternalUser(
  accessToken: string,
  payload: CreateInternalUserPayload,
): Promise<InternalUserSummary> {
  return apiFetch<InternalUserSummary>("/users/internal", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
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

export async function getOnboardingChecklist(accessToken: string): Promise<OnboardingChecklist> {
  return apiFetch<OnboardingChecklist>("/onboarding/checklist", accessToken);
}

export async function applyPilotSetup(
  accessToken: string,
  payload: PilotSetupPayload,
): Promise<PilotSetupResult> {
  return apiFetch<PilotSetupResult>("/onboarding/pilot-setup", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
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
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "membership-renewal", membershipId))),
  });
}

export async function changeMembershipPlan(
  accessToken: string,
  membershipId: string,
  payload: ChangeMembershipPlanPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>(`/memberships/${membershipId}/change-plan`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "membership-plan-change", membershipId))),
  });
}

export async function createKioskSession(terminalCode: string): Promise<KioskSessionResponse> {
  return publicApiFetch<KioskSessionResponse>(`/self-service/terminals/${terminalCode}/sessions`, {
    method: "POST",
  });
}

export async function verifyKioskSession(
  sessionId: string,
  sessionToken: string,
  identifier: string,
  birthDate: string,
): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/verify`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
    body: JSON.stringify({ identifier, birthDate }),
  });
}

export async function getKioskSummary(sessionId: string, sessionToken: string): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/summary`, {
    headers: kioskSessionHeaders(sessionToken),
  });
}

export async function getKioskMembershipOptions(sessionId: string, sessionToken: string): Promise<KioskPlanOption[]> {
  return publicApiFetch<KioskPlanOption[]>(`/self-service/sessions/${sessionId}/membership-options`, {
    headers: kioskSessionHeaders(sessionToken),
  });
}

export async function renewMembershipFromKiosk(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  payload: RenewMembershipPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(`/self-service/sessions/${sessionId}/membership-renewals/${membershipId}`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function changeMembershipPlanFromKiosk(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  payload: ChangeMembershipPlanPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(`/self-service/sessions/${sessionId}/membership-change-requests/${membershipId}`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function endKioskSession(sessionId: string, sessionToken: string): Promise<void> {
  await publicApiFetch<void>(`/self-service/sessions/${sessionId}/end`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
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

export async function getVisitPasses(accessToken: string, clientId?: string): Promise<VisitPass[]> {
  const params = new URLSearchParams();
  if (clientId) {
    params.set("clientId", clientId);
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<VisitPass[]>(`/access/visit-passes${suffix}`, accessToken);
}

export async function createVisitPass(
  accessToken: string,
  payload: CreateVisitPassPayload,
): Promise<VisitPass> {
  return apiFetch<VisitPass>("/access/visit-passes", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function consumeVisitPass(
  accessToken: string,
  visitPassId: string,
  payload: ConsumeVisitPassPayload = {},
): Promise<VisitPass> {
  return apiFetch<VisitPass>(`/access/visit-passes/${visitPassId}/consume`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getInventoryStock(accessToken: string): Promise<InventoryStock[]> {
  return apiFetch<InventoryStock[]>("/inventory", accessToken);
}

export async function getLowInventoryStock(accessToken: string): Promise<InventoryStock[]> {
  return apiFetch<InventoryStock[]>("/inventory/low-stock", accessToken);
}

export async function getInventoryMovements(
  accessToken: string,
  productId?: string,
  limit = 50,
): Promise<InventoryMovement[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (productId) {
    params.set("productId", productId);
  }
  return apiFetch<InventoryMovement[]>(`/inventory/movements?${params.toString()}`, accessToken);
}

export async function createInventoryMovement(
  accessToken: string,
  payload: InventoryMovementPayload,
): Promise<InventoryStock> {
  return apiFetch<InventoryStock>("/inventory/movements", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getResources(accessToken: string): Promise<ResourceSummary[]> {
  return apiFetch<ResourceSummary[]>("/resources", accessToken);
}

export async function createResource(
  accessToken: string,
  payload: CreateResourcePayload,
): Promise<ResourceSummary> {
  return apiFetch<ResourceSummary>("/resources", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getResourceAvailability(
  accessToken: string,
  resourceId: string,
  date?: string,
): Promise<AvailabilitySlot[]> {
  const suffix = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiFetch<AvailabilitySlot[]>(`/resources/${resourceId}/availability${suffix}`, accessToken);
}

export async function getBookings(accessToken: string, date?: string): Promise<BookingSummary[]> {
  const suffix = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiFetch<BookingSummary[]>(`/bookings${suffix}`, accessToken);
}

export async function createBooking(
  accessToken: string,
  payload: CreateBookingPayload,
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>("/bookings", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function transitionBooking(
  accessToken: string,
  bookingId: string,
  action: "confirm" | "check-in" | "complete" | "cancel",
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>(`/bookings/${bookingId}/${action}`, accessToken, {
    method: "POST",
  });
}

export async function linkBookingPayment(
  accessToken: string,
  bookingId: string,
  payload: LinkBookingPaymentPayload,
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>(`/bookings/${bookingId}/payments`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getCoaches(accessToken: string): Promise<CoachSummary[]> {
  return apiFetch<CoachSummary[]>("/coaches", accessToken);
}

export async function createCoach(
  accessToken: string,
  payload: CreateCoachPayload,
): Promise<CoachSummary> {
  return apiFetch<CoachSummary>("/coaches", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getWorkouts(accessToken: string): Promise<WorkoutSummary[]> {
  return apiFetch<WorkoutSummary[]>("/workouts", accessToken);
}

export async function createWorkout(
  accessToken: string,
  payload: CreateWorkoutPayload,
): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>("/workouts", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function assignWorkout(
  accessToken: string,
  workoutId: string,
  payload: AssignWorkoutPayload,
): Promise<WorkoutAssignment> {
  return apiFetch<WorkoutAssignment>(`/workouts/${workoutId}/assign`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getClientWorkouts(
  accessToken: string,
  clientId: string,
): Promise<WorkoutAssignment[]> {
  return apiFetch<WorkoutAssignment[]>(`/clients/${clientId}/workouts`, accessToken);
}

export async function getCoachReport(accessToken: string): Promise<CoachReport> {
  return apiFetch<CoachReport>("/reports/coaches", accessToken);
}

export async function getProfessionals(accessToken: string): Promise<ProfessionalSummary[]> {
  return apiFetch<ProfessionalSummary[]>("/professionals", accessToken);
}

export async function createProfessional(
  accessToken: string,
  payload: CreateProfessionalPayload,
): Promise<ProfessionalSummary> {
  return apiFetch<ProfessionalSummary>("/professionals", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getProfessionalServices(
  accessToken: string,
  professionalId: string,
): Promise<ProfessionalServiceSummary[]> {
  return apiFetch<ProfessionalServiceSummary[]>(`/professionals/${professionalId}/services`, accessToken);
}

export async function createProfessionalService(
  accessToken: string,
  professionalId: string,
  payload: CreateProfessionalServicePayload,
): Promise<ProfessionalServiceSummary> {
  return apiFetch<ProfessionalServiceSummary>(`/professionals/${professionalId}/services`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getProfessionalAvailability(
  accessToken: string,
  professionalId: string,
): Promise<ProfessionalAvailability[]> {
  return apiFetch<ProfessionalAvailability[]>(`/professionals/${professionalId}/availability`, accessToken);
}

export async function createProfessionalAvailability(
  accessToken: string,
  professionalId: string,
  payload: CreateProfessionalAvailabilityPayload,
): Promise<ProfessionalAvailability> {
  return apiFetch<ProfessionalAvailability>(`/professionals/${professionalId}/availability`, accessToken, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function getNotifications(accessToken: string, limit = 80): Promise<NotificationLog[]> {
  return apiFetch<NotificationLog[]>(`/notifications?limit=${limit}`, accessToken);
}

export async function createNotification(
  accessToken: string,
  payload: CreateNotificationPayload,
): Promise<NotificationLog> {
  return apiFetch<NotificationLog>("/notifications", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getAutomations(accessToken: string): Promise<AutomationRule[]> {
  return apiFetch<AutomationRule[]>("/automations", accessToken);
}

export async function createAutomation(
  accessToken: string,
  payload: CreateAutomationPayload,
): Promise<AutomationRule> {
  return apiFetch<AutomationRule>("/automations", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function setAutomationStatus(
  accessToken: string,
  ruleId: string,
  action: "enable" | "disable",
): Promise<AutomationRule> {
  return apiFetch<AutomationRule>(`/automations/${ruleId}/${action}`, accessToken, {
    method: "POST",
  });
}

export async function executeAutomation(
  accessToken: string,
  ruleId: string,
  triggerReference?: string,
): Promise<AutomationExecution> {
  return apiFetch<AutomationExecution>(`/automations/${ruleId}/execute`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload({ triggerReference: triggerReference ?? null })),
  });
}

export async function getAutomationExecutions(
  accessToken: string,
  ruleId: string,
): Promise<AutomationExecution[]> {
  return apiFetch<AutomationExecution[]>(`/automations/${ruleId}/executions`, accessToken);
}

export async function getOwnerDashboard(
  accessToken: string,
  fromDate?: string,
  toDate?: string,
): Promise<OwnerDashboard> {
  const params = new URLSearchParams();
  if (fromDate) {
    params.set("fromDate", fromDate);
  }
  if (toDate) {
    params.set("toDate", toDate);
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<OwnerDashboard>(`/reports/owner-dashboard${suffix}`, accessToken);
}

export async function createSale(
  accessToken: string,
  payload: CreateSalePayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>("/sales", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "sale"))),
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
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "manual-payment", saleId))),
  });
}

export async function requestSaleRefund(
  accessToken: string,
  saleId: string,
  payload: SaleRefundPayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}/refunds`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "sale-refund", saleId))),
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

export async function getBenefits(accessToken: string): Promise<BenefitSummary[]> {
  return apiFetch<BenefitSummary[]>("/benefits", accessToken);
}

export async function createBenefit(
  accessToken: string,
  payload: BenefitPayload,
): Promise<BenefitSummary> {
  return apiFetch<BenefitSummary>("/benefits", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function assignClientBenefit(
  accessToken: string,
  clientId: string,
  payload: AssignClientBenefitPayload,
): Promise<ClientBenefitSummary> {
  return apiFetch<ClientBenefitSummary>(`/clients/${clientId}/benefits`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function verifyClientBenefit(
  accessToken: string,
  clientBenefitId: string,
): Promise<ClientBenefitSummary> {
  return apiFetch<ClientBenefitSummary>(`/client-benefits/${clientBenefitId}/verify`, accessToken, {
    method: "POST",
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

function kioskSessionHeaders(sessionToken: string): HeadersInit {
	return {
		"X-Self-Service-Session-Token": sessionToken,
	};
}

function withIdempotency<T extends { idempotencyKey?: string | null }>(payload: T, ...parts: string[]): T {
  if (payload.idempotencyKey) {
    return payload;
  }
  return {
    ...payload,
    idempotencyKey: operationKey(...parts),
  };
}

function operationKey(...parts: string[]) {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}`;
  return [...parts, random].join(":");
}

async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const isFormData = init.body instanceof FormData;
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        ...(isFormData ? {} : { "Content-Type": "application/json" }),
        Authorization: `Bearer ${accessToken}`,
        ...init.headers,
      },
    });
  } catch {
    throw new Error(`No se pudo conectar con el servidor (${API_BASE_URL}). Revisa que el backend esté encendido y que la URL de API sea correcta.`);
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(formatApiError(response, path, error));
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
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init.headers,
      },
    });
  } catch {
    throw new Error(`No se pudo conectar con el servidor (${API_BASE_URL}). Revisa que el backend esté encendido y que la URL de API sea correcta.`);
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(formatApiError(response, path, error));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function formatApiError(response: Response, path: string, error: unknown) {
  const payload = isApiErrorPayload(error) ? error : null;
  const fallback = fallbackMessage(response.status);
  const message = payload?.message || fallback;
  const code = payload?.code ? ` Código: ${payload.code}.` : "";
  const correlationId = payload?.correlationId ? ` CorrelationId: ${payload.correlationId}.` : "";
  return `${message} (${response.status} ${response.statusText || "HTTP"} en ${path}).${code}${correlationId}`;
}

function fallbackMessage(status: number) {
  if (status === 400) {
    return "La solicitud tiene datos inválidos.";
  }
  if (status === 401) {
    return "Tu sesión expiró o no es válida. Vuelve a iniciar sesión.";
  }
  if (status === 403) {
    return "Tu usuario no tiene permiso para realizar esta acción.";
  }
  if (status === 404) {
    return "No se encontró la información solicitada.";
  }
  if (status >= 500) {
    return "El servidor tuvo un problema al procesar la operación.";
  }
  return "No fue posible completar la operación.";
}

function isApiErrorPayload(value: unknown): value is { code?: string; message?: string; correlationId?: string } {
  return typeof value === "object" && value !== null;
}
