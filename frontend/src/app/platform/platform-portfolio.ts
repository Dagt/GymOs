import type {
  PlatformCustomer,
  PlatformCustomerHealth,
  PlatformCustomerLifecycle,
} from "../../lib/api/platform-customer-types";

export type PlatformCustomerAction = {
  organizationId: string;
  customerName: string;
  priority: 1 | 2 | 3 | 4;
  title: string;
  detail: string;
  destination: string;
};

export function customerActions(
  customers: PlatformCustomer[],
  today = isoDate(new Date()),
): PlatformCustomerAction[] {
  const actions: PlatformCustomerAction[] = [];
  for (const customer of customers) {
    if (customer.overdueBranchCount > 0) {
      actions.push({
        organizationId: customer.organizationId,
        customerName: customer.tradeName,
        priority: 1,
        title: `${customer.overdueBranchCount} cobro${customer.overdueBranchCount === 1 ? "" : "s"} vencido${customer.overdueBranchCount === 1 ? "" : "s"}`,
        detail: "Revisa las sucursales vencidas y registra el pago sólo cuando esté confirmado.",
        destination: `/platform/billing?organization=${encodeURIComponent(customer.organizationId)}`,
      });
    }
    if (customer.lifecycleStatus === "AT_RISK") {
      actions.push({
        organizationId: customer.organizationId,
        customerName: customer.tradeName,
        priority: 2,
        title: "Cliente marcado en riesgo",
        detail: "Consulta el expediente y documenta el siguiente seguimiento.",
        destination: `/platform/gymnasiums?organization=${encodeURIComponent(customer.organizationId)}`,
      });
    }
    if (customer.unconfiguredBranchCount > 0) {
      actions.push({
        organizationId: customer.organizationId,
        customerName: customer.tradeName,
        priority: 2,
        title: `${customer.unconfiguredBranchCount} sucursal${customer.unconfiguredBranchCount === 1 ? "" : "es"} sin contrato`,
        detail: "Asigna plan, fecha de cobro e implementación para completar el alta comercial.",
        destination: `/platform/billing?organization=${encodeURIComponent(customer.organizationId)}`,
      });
    }
    if (customer.activeBranchCount === 0 && customer.lifecycleStatus !== "FORMER") {
      actions.push({
        organizationId: customer.organizationId,
        customerName: customer.tradeName,
        priority: 3,
        title: "Sin sucursales activas",
        detail: "Completa la estructura operativa antes de entregar el servicio.",
        destination: `/platform/gymnasiums?organization=${encodeURIComponent(customer.organizationId)}`,
      });
    }
    if (customer.nextFollowUpOn && customer.nextFollowUpOn <= today
        && customer.lifecycleStatus !== "FORMER") {
      actions.push({
        organizationId: customer.organizationId,
        customerName: customer.tradeName,
        priority: 3,
        title: "Seguimiento comercial pendiente",
        detail: `Programado para ${formatDate(customer.nextFollowUpOn)}.`,
        destination: `/platform/gymnasiums?organization=${encodeURIComponent(customer.organizationId)}`,
      });
    }
  }
  return actions.sort((left, right) =>
    left.priority - right.priority || left.customerName.localeCompare(right.customerName));
}

export function customerHealthPresentation(status: PlatformCustomerHealth) {
  const values: Record<PlatformCustomerHealth, { label: string; tone: "success" | "warning" | "danger" | "neutral" }> = {
    HEALTHY: { label: "Saludable", tone: "success" },
    SETUP_REQUIRED: { label: "Configuración pendiente", tone: "warning" },
    AT_RISK: { label: "En riesgo", tone: "danger" },
    SUSPENDED: { label: "Suspendido", tone: "danger" },
    OFFBOARDING: { label: "En salida", tone: "warning" },
    FORMER: { label: "Cliente anterior", tone: "neutral" },
  };
  return values[status];
}

export function lifecycleLabel(status: PlatformCustomerLifecycle) {
  const labels: Record<PlatformCustomerLifecycle, string> = {
    ONBOARDING: "Implementación",
    ACTIVE: "Cliente activo",
    AT_RISK: "En riesgo",
    OFFBOARDING: "En salida",
    FORMER: "Cliente anterior",
  };
  return labels[status];
}

export function matchesCustomer(
  customer: PlatformCustomer,
  query: string,
  lifecycle: string,
) {
  const normalized = query.trim().toLowerCase();
  const matchesQuery = !normalized || [
    customer.tradeName,
    customer.legalName,
    customer.taxId ?? "",
    customer.accountOwner ?? "",
    customer.billingEmail ?? "",
  ].some((value) => value.toLowerCase().includes(normalized));
  const matchesLifecycle = lifecycle === "ALL" || customer.lifecycleStatus === lifecycle;
  return matchesQuery && matchesLifecycle;
}

export function portfolioCsv(customers: PlatformCustomer[]) {
  const header = [
    "Cliente", "Razón social", "RFC", "Etapa", "Salud", "Responsable",
    "Correo de cobranza", "Sucursales activas", "Socios activos", "MRR MXN",
    "Sucursales vencidas", "Próximo cobro", "Próximo seguimiento",
  ];
  const rows = customers.map((customer) => [
    customer.tradeName,
    customer.legalName,
    customer.taxId ?? "",
    lifecycleLabel(customer.lifecycleStatus),
    customerHealthPresentation(customer.healthStatus).label,
    customer.accountOwner ?? "",
    customer.billingEmail ?? "",
    String(customer.activeBranchCount),
    String(customer.activeMembers),
    String(customer.monthlyRecurringRevenue),
    String(customer.overdueBranchCount),
    customer.nextBillingDate ?? "",
    customer.nextFollowUpOn ?? "",
  ]);
  return [header, ...rows]
    .map((row) => row.map(csvCell).join(","))
    .join("\r\n");
}

export function formatDate(value: string) {
  const [year, month, day] = value.split("-");
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function csvCell(value: string) {
  return `"${value.replaceAll('"', '""')}"`;
}

function isoDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
