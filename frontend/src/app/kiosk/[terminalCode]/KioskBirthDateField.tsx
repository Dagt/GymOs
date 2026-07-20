"use client";

import { useEffect } from "react";

export default function KioskBirthDateField() {
  useEffect(() => {
    function mountField() {
      const fieldsets = Array.from(document.querySelectorAll<HTMLFieldSetElement>("fieldset.kiosk-field"));
      const fieldset = fieldsets.find((item) => item.querySelector("legend")?.textContent?.trim() === "Fecha de nacimiento");
      if (!fieldset || fieldset.dataset.mexicanDateMounted === "true") return;

      const selects = Array.from(fieldset.querySelectorAll<HTMLSelectElement>("select"));
      if (selects.length !== 3) return;

      const [daySelect, monthSelect, yearSelect] = selects;
      const originalContainer = daySelect.closest("div");
      const originalHelp = fieldset.querySelector("small");
      if (originalContainer instanceof HTMLElement) originalContainer.hidden = true;
      if (originalHelp instanceof HTMLElement) originalHelp.hidden = true;

      const input = document.createElement("input");
      input.className = "kiosk-input compact";
      input.type = "text";
      input.inputMode = "numeric";
      input.autocomplete = "bday";
      input.placeholder = "DD/MM/AAAA";
      input.maxLength = 10;
      input.required = true;
      input.setAttribute("aria-label", "Fecha de nacimiento en formato día mes año");

      const help = document.createElement("small");
      help.textContent = "Formato: día/mes/año, por ejemplo 25/08/1990.";

      input.addEventListener("input", () => {
        const digits = input.value.replace(/\D/g, "").slice(0, 8);
        const parts = [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)].filter(Boolean);
        input.value = parts.join("/");

        if (digits.length !== 8) {
          input.setCustomValidity("Captura la fecha completa en formato día/mes/año.");
          syncSelect(daySelect, "");
          syncSelect(monthSelect, "");
          syncSelect(yearSelect, "");
          return;
        }

        const day = Number(digits.slice(0, 2));
        const month = Number(digits.slice(2, 4));
        const year = Number(digits.slice(4, 8));
        const date = new Date(year, month - 1, day);
        const valid = year >= 1900
          && year <= new Date().getFullYear()
          && date.getFullYear() === year
          && date.getMonth() === month - 1
          && date.getDate() === day;

        if (!valid) {
          input.setCustomValidity("La fecha de nacimiento no es válida.");
          syncSelect(daySelect, "");
          syncSelect(monthSelect, "");
          syncSelect(yearSelect, "");
          return;
        }

        input.setCustomValidity("");
        syncSelect(daySelect, String(day));
        syncSelect(monthSelect, String(month));
        syncSelect(yearSelect, String(year));
      });

      fieldset.append(input, help);
      fieldset.dataset.mexicanDateMounted = "true";
    }

    mountField();
    const observer = new MutationObserver(mountField);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return null;
}

function syncSelect(select: HTMLSelectElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")?.set;
  setter?.call(select, value);
  select.dispatchEvent(new Event("change", { bubbles: true }));
}
