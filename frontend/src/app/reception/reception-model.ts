export type FocusAction = {
  title: string;
  detail: string;
  href: string;
  label: string;
  accent: string;
};

export const FOCUS_ACTIONS: FocusAction[] = [
  {
    title: "Cobrar venta o membresía",
    detail: "Productos, planes, renovaciones y pagos manuales desde caja.",
    href: "/pos",
    label: "Abrir caja",
    accent: "money",
  },
  {
    title: "Buscar o registrar socio",
    detail: "Alta rápida, ficha, membresías, beneficios y notas.",
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
    detail: "Autoservicio seguro para membresías, pagos Stripe, reservas gratuitas y datos de contacto. No requiere registrar esta computadora.",
    href: "/kiosk/launch",
    label: "Abrir kiosco seguro",
    accent: "kiosk",
  },
  {
    title: "Reservas",
    detail: "Agenda recursos, registra reservas y vincula pagos cuando aplique.",
    href: "/bookings",
    label: "Ver agenda",
    accent: "bookings",
  },
  {
    title: "Servicios de bienestar",
    detail: "Agenda citas, consulta especialistas, confirmación y estatus.",
    href: "/wellness",
    label: "Ver citas",
    accent: "wellness",
  },
];

export type ReceptionDensityConfig = {
  density: "compact";
  dataDenseAdmin: true;
};

export function getReceptionDensityConfig(): ReceptionDensityConfig {
  return {
    density: "compact",
    dataDenseAdmin: true,
  };
}

export function getFocusActions(): FocusAction[] {
  return FOCUS_ACTIONS;
}

export function getVisibleFocusActions(canLaunchKiosk: boolean): FocusAction[] {
  return FOCUS_ACTIONS.filter((action) => action.href !== "/kiosk/launch" || canLaunchKiosk);
}

export function getQuickActions(): { primary: FocusAction; secondary: FocusAction[] } {
  return {
    primary: FOCUS_ACTIONS[0],
    secondary: FOCUS_ACTIONS.slice(1),
  };
}
