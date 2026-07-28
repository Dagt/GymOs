"use client";

import { useEffect } from "react";

function labelText(input: HTMLInputElement): string {
  return input.closest("label")?.querySelector(":scope > span")?.textContent?.trim() ?? "";
}

function addHelp(label: HTMLLabelElement, text: string) {
  if (label.querySelector("[data-workout-field-help]")) return;
  const help = document.createElement("small");
  help.dataset.workoutFieldHelp = "true";
  help.className = "text-xs font-normal leading-relaxed text-[#6b7d72]";
  help.textContent = text;
  label.insertBefore(help, label.lastElementChild);
}

function enhanceAdvancedFields(root: ParentNode) {
  root.querySelectorAll<HTMLLabelElement>("label").forEach((label) => {
    const title = label.querySelector<HTMLElement>(":scope > span");
    const input = label.querySelector<HTMLInputElement>("input");
    if (!title || !input) return;

    if (title.textContent?.trim() === "Indicación complementaria") {
      title.textContent = "Complemento de la prescripción";
      input.placeholder = "Ej. con mancuernas · agarre neutro · RIR 2";
      addHelp(
        label,
        "Variante, agarre, equipo, RPE, RIR o condición que completa las series y repeticiones.",
      );
    }

    if (title.textContent?.trim() === "Notas técnicas") {
      title.textContent = "Indicaciones técnicas y seguridad";
      input.placeholder = "Ej. mantener los codos fijos y detener si aparece dolor";
      addHelp(
        label,
        "Postura, ejecución, errores que deben evitarse, molestias o adaptaciones del movimiento.",
      );
    }
  });
}

function focusNewestExercise(block: Element) {
  window.setTimeout(() => {
    const cards = block.querySelectorAll<HTMLDetailsElement>(".workout-exercise-card");
    const card = cards.item(cards.length - 1);
    if (!card) return;
    card.open = true;

    const input = Array.from(card.querySelectorAll<HTMLInputElement>("input"))
      .find((candidate) => labelText(candidate) === "Ejercicio");
    if (!input) return;

    const descriptor = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype,
      "value",
    );
    descriptor?.set?.call(input, "");
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.focus();
  }, 0);
}

function enhanceExerciseActions(root: ParentNode) {
  root.querySelectorAll<HTMLSelectElement>(
    'select[aria-label^="Agregar ejercicio de catálogo al bloque"]',
  ).forEach((select) => {
    if (select.dataset.enhancedBlankExercise === "true") return;
    select.dataset.enhancedBlankExercise = "true";
    select.hidden = true;

    const button = document.createElement("button");
    button.type = "button";
    button.className = select.className;
    button.textContent = "+ Agregar ejercicio";
    button.setAttribute("aria-label", "Agregar ejercicio vacío");
    button.addEventListener("click", () => {
      const firstExercise = Array.from(select.options).find((option) => option.value);
      if (!firstExercise) return;
      const block = select.closest(".workout-block-card");
      select.value = firstExercise.value;
      select.dispatchEvent(new Event("change", { bubbles: true }));
      select.value = "";
      if (block) focusNewestExercise(block);
    });
    select.insertAdjacentElement("afterend", button);
  });
}

function simplifyPdfActions(root: ParentNode) {
  root.querySelectorAll<HTMLButtonElement>("button").forEach((button) => {
    const text = button.textContent?.trim() ?? "";
    if (text.includes("PDF Claro")) {
      button.hidden = true;
      const separator = button.nextElementSibling;
      if (separator instanceof HTMLElement && separator.textContent?.trim() === "|") {
        separator.hidden = true;
      }
      return;
    }
    if (text.includes("PDF Oscuro")) {
      button.textContent = button.disabled ? "Descargando…" : "Descargar PDF";
      button.title = "Descargar rutina en PDF";
    }
  });
}

function enhance(root: ParentNode) {
  enhanceExerciseActions(root);
  enhanceAdvancedFields(root);
  simplifyPdfActions(root);
}

export function CoachesExperienceEnhancer() {
  useEffect(() => {
    const run = () => enhance(document);
    const initial = window.setTimeout(run, 0);

    const onClick = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;

      const shouldRefresh = Boolean(
        target.closest('nav[aria-label="Secciones de Coaches"]')
        || target.closest(".workout-builder")
        || target.closest(".rounded-xl"),
      );
      if (!shouldRefresh) return;
      window.setTimeout(run, 0);
    };

    document.addEventListener("click", onClick);
    return () => {
      window.clearTimeout(initial);
      document.removeEventListener("click", onClick);
    };
  }, []);

  return null;
}
