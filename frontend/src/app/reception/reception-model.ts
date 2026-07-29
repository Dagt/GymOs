export type FocusAction = {
  title: string;
  detail: string;
  href: string;
  label: string;
  accent: string;
};

export type DaySignal = {
  label: string;
  value: string;
  tone: "green" | "amber" | "blue" | "red";
};

export const FOCUS_ACTIONS: FocusAction[] = [
  {
    title: "Cobrar venta o membresia",
    detail: "Productos, planes, renovaciones y pagos manuales desde caja.",
    href: "/pos",
    label: "Abrir caja",
    accent: "money",
  },
  {
    title: "Buscar o registrar socio",
    detail: "Alta rapida, ficha, membresias, beneficios y notas.",
    href: "/clients",
    label: "Ir a socios",
    accent: "people",
  },
  {
    title: "Revisar ventas",
    detail: "Pagos pendientes, saldos, comprobantes y operaciones recientes.",
    href: "/reports/sales",
    label: "Ver reporte",
    accent: "report",
  },
  {
    title: "Pantalla para socios",
    detail: "Autoservicio para pagos, renovaciones y cambios de plan.",
    href: "/kiosk/KIOSK-MAIN",
    label: "Abrir kiosco",
    accent: "kiosk",
  },
  {
    title: "Renovaciones y Planes",
    detail: "Extensión de vigencias, cobro de renovaciones y cambios de plan.",
    href: "/reception/renewals",
    label: "Renovar plan",
    accent: "renewals",
  },
  {
    title: "Reservas",
    detail: "Agenda recursos, registra reservas y vincula pagos cuando aplique.",
    href: "/bookings",
    label: "Ver agenda",
    accent: "bookings",
  },
  {
    title: "Servicios de Bienestar",
    detail: "Agendar citas, consultar especialistas, confirmación y estatus.",
    href: "/wellness",
    label: "Ver citas",
    accent: "wellness",
  },
];

export const DAY_SIGNALS: DaySignal[] = [
  { label: "Caja", value: "Lista", tone: "green" },
  { label: "Pagos", value: "Por revisar", tone: "amber" },
  { label: "Sucursal", value: "Activa", tone: "blue" },
];

export type ReceptionDensityConfig = {
  density: "compact";
  dataDenseAdmin: true;
  signalsCount: number;
};

export function getReceptionDensityConfig(): ReceptionDensityConfig {
  return {
    density: "compact",
    dataDenseAdmin: true,
    signalsCount: DAY_SIGNALS.length,
  };
}

export function getFocusActions(): FocusAction[] {
  return FOCUS_ACTIONS;
}

export function getDaySignals(): DaySignal[] {
  return DAY_SIGNALS;
}

export function getQuickActions(): { primary: FocusAction; secondary: FocusAction[] } {
  return {
    primary: FOCUS_ACTIONS[0],
    secondary: FOCUS_ACTIONS.slice(1),
  };
}
