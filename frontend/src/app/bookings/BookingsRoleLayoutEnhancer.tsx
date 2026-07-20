"use client";

import { useEffect } from "react";

const DETAIL_TEXT = "Detalle del día seleccionado.";

export default function BookingsRoleLayoutEnhancer() {
  useEffect(() => {
    let scheduled = 0;

    function arrangeLayout() {
      const headings = Array.from(document.querySelectorAll<HTMLHeadingElement>("h2"));
      const bookingHeading = headings.find((heading) => heading.textContent?.trim() === "Nueva reserva");
      const bookingForm = bookingHeading?.closest("form");
      const sidebar = bookingForm?.parentElement;
      const workspace = sidebar?.parentElement;

      if (!bookingForm || !sidebar || !workspace) return;

      const directForms = Array.from(sidebar.children).filter((element) => element.tagName === "FORM");
      const canCreateResources = directForms.length > 1;
      const agendaPanel = Array.from(workspace.children).find(
        (element): element is HTMLElement => element instanceof HTMLElement && element !== sidebar && element.querySelector("h2")?.textContent?.trim() === "Agenda mensual",
      );

      if (!agendaPanel) return;

      const movedDetail = workspace.querySelector<HTMLElement>(":scope > .bookings-full-width-detail");

      if (canCreateResources) {
        workspace.classList.remove("bookings-operational-layout");
        agendaPanel.classList.remove("bookings-operational-agenda");
        if (movedDetail) {
          const calendarStack = agendaPanel.querySelector<HTMLElement>(".mt-5.grid.gap-5");
          calendarStack?.appendChild(movedDetail);
          movedDetail.classList.remove("bookings-full-width-detail");
        }
        return;
      }

      const detailInsideAgenda = Array.from(agendaPanel.querySelectorAll<HTMLElement>("section")).find(
        (section) => section.querySelector("p")?.textContent?.trim() === DETAIL_TEXT,
      );
      const detail = detailInsideAgenda ?? movedDetail;
      if (!detail) return;

      workspace.classList.add("bookings-operational-layout");
      agendaPanel.classList.add("bookings-operational-agenda");
      detail.classList.add("bookings-full-width-detail");

      if (detail.parentElement !== workspace) {
        workspace.appendChild(detail);
      }
    }

    function scheduleArrange() {
      window.clearTimeout(scheduled);
      scheduled = window.setTimeout(arrangeLayout, 50);
    }

    arrangeLayout();
    const observer = new MutationObserver(scheduleArrange);
    observer.observe(document.body, { childList: true, subtree: true });
    window.addEventListener("resize", scheduleArrange);

    return () => {
      window.clearTimeout(scheduled);
      observer.disconnect();
      window.removeEventListener("resize", scheduleArrange);
    };
  }, []);

  return null;
}
