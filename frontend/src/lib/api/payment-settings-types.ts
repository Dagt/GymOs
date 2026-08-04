export type PaymentConfiguration = {
  receptionManualTerminalEnabled: boolean;
  receptionMercadoPagoEnabled: boolean;
  mercadoPagoAccountId: string | null;
  mercadoPagoTerminalId: string | null;
  mercadoPagoConnected: boolean;
  mercadoPagoReady: boolean;
  stripeSelfCheckoutEnabled: boolean;
  stripeRecurringEnabled: boolean;
  stripeAccountId: string | null;
  stripeConnected: boolean;
  stripeCheckoutReady: boolean;
  stripeRecurringReady: boolean;
};

export type UpdatePaymentConfigurationPayload = {
  receptionManualTerminalEnabled: boolean;
  receptionMercadoPagoEnabled: boolean;
  mercadoPagoTerminalId?: string | null;
  stripeSelfCheckoutEnabled: boolean;
  stripeRecurringEnabled: boolean;
};

export type PaymentProviderLink = {
  url: string;
  accountId?: string;
  expiresAt?: string;
};

export type MercadoPagoTerminal = {
  id: string;
  name: string | null;
  status: string | null;
  storeId: string | null;
};

export type PaymentHistoryPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RecurringMandateHistory = {
  id: string;
  branchId: string;
  clientId: string;
  membershipId: string;
  planId: string;
  provider: string;
  status: string;
  brand: string | null;
  lastFour: string | null;
  expiresMonth: number | null;
  expiresYear: number | null;
  termsVersion: string;
  consentedAt: string;
  consentOrigin: string;
  nextChargeOn: string;
  consecutiveFailures: number;
  lastError: string | null;
  revokedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type RecurringChargeHistory = {
  id: string;
  branchId: string;
  mandateId: string;
  membershipId: string;
  clientId: string;
  planId: string;
  dueOn: string;
  status: string;
  saleId: string | null;
  paymentId: string | null;
  providerOperationId: string | null;
  amountCents: number | null;
  currency: string | null;
  attemptNumber: number;
  nextAttemptAt: string;
  lockedUntil: string | null;
  lastError: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PaymentWebhookHistory = {
  id: string;
  provider: string;
  externalEventId: string;
  eventType: string | null;
  status: string;
  errorMessage: string | null;
  receivedAt: string;
  processedAt: string | null;
};

export type PaymentOperations = {
  pastDueMandates: number;
  pausedMandates: number;
  retryCharges: number;
  failedCharges: number;
  expiredChargeLeases: number;
  failedWebhooks: number;
  staleOperations: number;
  alertLevel: "HEALTHY" | "WARNING" | "CRITICAL";
};
