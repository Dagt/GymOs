"use client";

import { useEffect } from "react";

function setReactInputValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(
    window.HTMLInputElement.prototype,
    "value",
  )?.set;
  setter?.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
  input.dispatchEvent(new Event("change", { bubbles: true }));
}

function findFieldLabel(input: HTMLInputElement): string {
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
    const title = label.querySelector(":scope > span");
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

    const exerciseInput = Array.from(card.querySelectorAll<HTMLInputElement>("input"))
      .find((input) => findFieldLabel(input) === "Ejercicio");
    if (!exerciseInput) return;

    setReactInputValue(exerciseInput, "");
    exerciseInput.focus();
  }, 0);
}

function replaceCatalogSelectors(root: ParentNode) {
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
      const firstCatalogOption = Array.from(select.options).find((option) => option.value);
      if (!firstCatalogOption) return;
      select.value = firstCatalogOption.value;
      select.dispatchEvent(new Event("change", { bubbles: true }));
      const block = select.closest(".workout-block-card");
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
    if (text.includes("PDF Oscuro") || text === "Descargar PDF") {
      button.textContent = button.disabled ? "Descargando…" : "Descargar PDF";
      button.title = "Descargar rutina en PDF";
    }
  });
}

function enhance(root: ParentNode) {
  replaceCatalogSelectors(root);
  enhanceAdvancedFields(root);
  simplifyPdfActions(root);
}

export function CoachesExperienceEnhancer() {
  useEffect(() => {
    enhance(document);
    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLElement) enhance(node);
        });
      }
      simplifyPdfActions(document);
    });
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return null;
}
