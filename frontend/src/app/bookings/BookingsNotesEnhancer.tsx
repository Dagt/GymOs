"use client";

import { useCallback, useEffect, useState } from "react";
import { getBookings, getStoredSession, type BookingSummary } from "@/lib/api";

type NoteDialog = { clientName: string; note: string } | null;

export default function BookingsNotesEnhancer() {
  const [dialog, setDialog] = useState<NoteDialog>(null);

  const enhance = useCallback(async () => {
    const session = getStoredSession();
    const dateInput = document.querySelector<HTMLInputElement>('form input[type="date"]');
    if (!session || !dateInput?.value) return;

    const bookings = (await getBookings(session.accessToken, dateInput.value).catch(() => []))
      .filter((booking) => booking.status !== "CANCELLED")
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt));

    const usedBookingIds = new Set<string>();
    const resourceCards = Array.from(document.querySelectorAll<HTMLElement>("article.snap-start"));

    for (const resourceCard of resourceCards) {
      const resourceName = resourceCard.querySelector("p")?.textContent?.trim() ?? "";
      const matching = bookings.filter((booking) => booking.resourceName === resourceName);
      const cards = Array.from(resourceCard.querySelectorAll<HTMLElement>("article[style]"));

      cards.forEach((card, index) => {
        if (card.querySelector("[data-booking-note-button]")) return;

        const cardText = card.textContent ?? "";
        const booking = matching.find((candidate) => {
          if (usedBookingIds.has(candidate.id)) return false;
          return cardText.includes(candidate.clientName)
            && cardText.includes(formatTime(candidate.startsAt))
            && cardText.includes(formatTime(candidate.endsAt));
        }) ?? matching.find((candidate) => !usedBookingIds.has(candidate.id)) ?? matching[index];

        if (booking) usedBookingIds.add(booking.id);

        const cancel = Array.from(card.querySelectorAll("button")).find((button) => button.textContent?.toLowerCase().includes("cancelar"));
        if (cancel) cancel.textContent = "Cancelar";

        const actions = document.createElement("div");
        actions.className = "booking-card-actions";
        actions.dataset.bookingActions = "true";

        if (cancel?.parentElement) {
          cancel.parentElement.insertBefore(actions, cancel);
          actions.appendChild(cancel);
        } else {
          card.appendChild(actions);
        }

        const noteButton = document.createElement("button");
        noteButton.type = "button";
        noteButton.dataset.bookingNoteButton = "true";
        noteButton.className = "booking-note-button";
        noteButton.textContent = "Ver nota";
        noteButton.disabled = !booking?.notes?.trim();
        noteButton.title = booking?.notes?.trim() ? "Ver nota de la reserva" : "Esta reserva no tiene nota";
        noteButton.onclick = () => setDialog({
          clientName: booking?.clientName ?? "Reserva",
          note: booking?.notes?.trim() || "Esta reserva no tiene una nota registrada.",
        });
        actions.appendChild(noteButton);
      });
    }
  }, []);

  useEffect(() => {
    let timer = window.setTimeout(() => void enhance(), 350);
    const observer = new MutationObserver(() => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => void enhance(), 150);
    });
    observer.observe(document.body, { childList: true, subtree: true });
    const onChange = () => { window.clearTimeout(timer); timer = window.setTimeout(() => void enhance(), 100); };
    document.addEventListener("change", onChange);
    return () => {
      window.clearTimeout(timer);
      observer.disconnect();
      document.removeEventListener("change", onChange);
    };
  }, [enhance]);

  if (!dialog) return null;
  return (
    <div aria-modal="true" className="fixed inset-0 z-[120] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={() => setDialog(null)} role="dialog">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-[var(--brand-surface)] text-2xl">📝</div>
        <h2 className="mt-4 text-center text-xl font-bold text-[#243229]">Nota de la reserva</h2>
        <p className="mt-1 text-center text-sm font-semibold text-[#5d6b63]">{dialog.clientName}</p>
        <p className="mt-4 whitespace-pre-wrap rounded-lg border border-[#d8ddd2] bg-[#f8faf8] p-4 text-sm leading-6 text-[#243229]">{dialog.note}</p>
        <button autoFocus className="mt-6 h-10 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white" onClick={() => setDialog(null)} type="button">Cerrar</button>
      </div>
    </div>
  );
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
