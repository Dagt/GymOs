"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createBooking,
  createResource,
  getBookings,
  getResourceAvailability,
  getResources,
  getStoredSession,
  linkBookingPayment,
  searchClients,
  transitionBooking,
  type AvailabilitySlot,
  type BookingSummary,
  type ClientSummary,
  type ResourceSummary,
  type StoredSession,
} from "@/lib/api";

export default function BookingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [resources, setResources] = useState<ResourceSummary[]>([]);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [availability, setAvailability] = useState<AvailabilitySlot[]>([]);
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [resourceId, setResourceId] = useState("");
  const [clientId, setClientId] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [notes, setNotes] = useState("");
  const [resourceName, setResourceName] = useState("");
  const [paymentBookingId, setPaymentBookingId] = useState("");
  const [paymentSaleId, setPaymentSaleId] = useState("");
  const [paymentStatus, setPaymentStatus] = useState<"PENDING" | "PAID" | "EXPIRED" | "CANCELLED">("PAID");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedResources, loadedBookings, loadedClients] = await Promise.all([
          getResources(stored.accessToken),
          getBookings(stored.accessToken, date),
          searchClients(stored.accessToken, "", "ACTIVE"),
        ]);
        if (!active) {
          return;
        }
        setResources(loadedResources);
        setBookings(loadedBookings);
        setClients(loadedClients);
        setResourceId(loadedResources[0]?.id ?? "");
        setClientId(loadedClients[0]?.id ?? "");
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar reservas.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [date]);

  useEffect(() => {
    if (!session || !resourceId) {
      return;
    }
    let active = true;
    getResourceAvailability(session.accessToken, resourceId, date)
      .then((slots) => {
        if (active) {
          setAvailability(slots);
        }
      })
      .catch(() => {
        if (active) {
          setAvailability([]);
        }
      });
    return () => {
      active = false;
    };
  }, [date, resourceId, session]);

  const activeResource = useMemo(
    () => resources.find((resource) => resource.id === resourceId) ?? null,
    [resources, resourceId],
  );

  async function refresh(stored = session) {
    if (!stored) {
      return;
    }
    const [loadedResources, loadedBookings] = await Promise.all([
      getResources(stored.accessToken),
      getBookings(stored.accessToken, date),
    ]);
    setResources(loadedResources);
    setBookings(loadedBookings);
  }

  async function submitBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createBooking(session.accessToken, {
        resourceId,
        clientId,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        notes,
      });
      await refresh();
      setMessage("Reserva creada.");
      setNotes("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear reserva.");
    } finally {
      setSaving(false);
    }
  }

  async function submitResource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createResource(session.accessToken, {
        name: resourceName,
        resourceType: "ROOM",
        capacity: 1,
        slotMinutes: 60,
        opensAt: "08:00",
        closesAt: "20:00",
      });
      await refresh();
      setResourceId(created.id);
      setResourceName("");
      setMessage("Recurso creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear recurso.");
    } finally {
      setSaving(false);
    }
  }

  async function runAction(bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") {
    if (!session) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await transitionBooking(session.accessToken, bookingId, action);
      await refresh();
      setMessage("Reserva actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar reserva.");
    }
  }

  async function submitBookingPayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !paymentBookingId || !paymentSaleId.trim()) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await linkBookingPayment(session.accessToken, paymentBookingId, {
        saleId: paymentSaleId.trim(),
        status: paymentStatus,
      });
      await refresh();
      setPaymentBookingId("");
      setPaymentSaleId("");
      setPaymentStatus("PAID");
      setMessage("Pago de reserva vinculado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible vincular pago.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm">Cargando reservas...</p>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6">
          <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
          <Link className="mt-5 inline-flex h-11 items-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white" href="/login">
            Iniciar sesion
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">Operacion</p>
            <h1 className="mt-1 text-3xl font-semibold">Recursos y reservas</h1>
          </div>
          <Link className="rounded-md border border-[#cdd5ca] px-4 py-2 text-sm font-semibold" href="/reception">
            Recepcion
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[0.9fr_1.3fr]">
        <aside className="grid gap-5">
          {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{error}</p> : null}
          {message ? <p className="rounded-md border border-[#b8d9c3] bg-[#f0fbf3] px-3 py-2 text-sm text-[#2d6843]">{message}</p> : null}

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitBooking}>
            <h2 className="text-xl font-semibold">Nueva reserva</h2>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Fecha
              <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setDate(event.target.value)} type="date" value={date} />
            </label>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Recurso
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceId(event.target.value)} value={resourceId}>
                {resources.map((resource) => (
                  <option key={resource.id} value={resource.id}>{resource.name}</option>
                ))}
              </select>
            </label>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Cliente
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setClientId(event.target.value)} value={clientId}>
                {clients.map((client) => (
                  <option key={client.id} value={client.id}>{client.firstName} {client.lastName}</option>
                ))}
              </select>
            </label>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="grid gap-2 text-sm font-medium">
                Inicio
                <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setStartsAt(event.target.value)} required type="datetime-local" value={startsAt} />
              </label>
              <label className="grid gap-2 text-sm font-medium">
                Fin
                <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setEndsAt(event.target.value)} required type="datetime-local" value={endsAt} />
              </label>
            </div>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Notas
              <textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2" onChange={(event) => setNotes(event.target.value)} value={notes} />
            </label>
            <button className="mt-5 h-11 w-full rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !resourceId || !clientId} type="submit">
              Crear reserva
            </button>
          </form>

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitResource}>
            <h2 className="text-xl font-semibold">Alta rapida de recurso</h2>
            <input className="mt-4 h-11 w-full rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceName(event.target.value)} placeholder="Nombre del recurso" required value={resourceName} />
            <button className="mt-4 h-11 w-full rounded-md border border-[#51735f] px-4 text-sm font-semibold text-[#13251c]" disabled={saving} type="submit">
              Crear recurso
            </button>
          </form>

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitBookingPayment}>
            <h2 className="text-xl font-semibold">Pago de reserva</h2>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Reserva
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setPaymentBookingId(event.target.value)} value={paymentBookingId}>
                <option value="">Selecciona reserva</option>
                {bookings.map((booking) => (
                  <option key={booking.id} value={booking.id}>
                    {booking.clientName} - {formatTime(booking.startsAt)}
                  </option>
                ))}
              </select>
            </label>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              ID de venta POS
              <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setPaymentSaleId(event.target.value)} placeholder="UUID de venta" value={paymentSaleId} />
            </label>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Estado
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setPaymentStatus(event.target.value as typeof paymentStatus)} value={paymentStatus}>
                <option value="PAID">Pagada</option>
                <option value="PENDING">Pendiente</option>
                <option value="EXPIRED">Expirada</option>
                <option value="CANCELLED">Cancelada</option>
              </select>
            </label>
            <button className="mt-5 h-11 w-full rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !paymentBookingId || !paymentSaleId.trim()} type="submit">
              Vincular pago
            </button>
          </form>
        </aside>

        <section className="grid gap-5">
          <div className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Disponibilidad {activeResource ? `- ${activeResource.name}` : ""}</h2>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {availability.map((slot) => (
                <button
                  className={`rounded-md border px-3 py-3 text-left text-sm ${slot.available ? "border-[#b8d9c3] bg-[#f0fbf3]" : "border-[#e0c4c4] bg-[#fff6f6]"}`}
                  key={slot.startsAt}
                  onClick={() => {
                    setStartsAt(toLocalInput(slot.startsAt));
                    setEndsAt(toLocalInput(slot.endsAt));
                  }}
                  type="button"
                >
                  <span className="block font-semibold">{formatTime(slot.startsAt)} - {formatTime(slot.endsAt)}</span>
                  <span className="mt-1 block text-xs">{slot.reserved}/{slot.capacity} reservado</span>
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Agenda del dia</h2>
            <div className="mt-4 grid gap-3">
              {bookings.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin reservas para esta fecha.</p> : null}
              {bookings.map((booking) => (
                <article className="rounded-md border border-[#d8ddd2] p-4" key={booking.id}>
                  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                      <p className="font-semibold">{booking.resourceName}</p>
                      <p className="mt-1 text-sm text-[#5d6b63]">{booking.clientName} - {formatTime(booking.startsAt)} a {formatTime(booking.endsAt)}</p>
                      <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-[#51735f]">{booking.status}</p>
                      {booking.paymentStatus ? (
                        <p className="mt-1 text-xs text-[#6f7a73]">
                          Pago {booking.paymentStatus} {booking.saleNumber ? `- ${booking.saleNumber}` : ""}
                        </p>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <ActionButton action="confirm" booking={booking} onRun={runAction} />
                      <ActionButton action="check-in" booking={booking} onRun={runAction} />
                      <ActionButton action="complete" booking={booking} onRun={runAction} />
                      <ActionButton action="cancel" booking={booking} onRun={runAction} />
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>
      </section>
    </main>
  );
}

function ActionButton({ action, booking, onRun }: { action: "confirm" | "check-in" | "complete" | "cancel"; booking: BookingSummary; onRun: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void }) {
  const labels = { confirm: "Confirmar", "check-in": "Check-in", complete: "Completar", cancel: "Cancelar" };
  const disabled = booking.status === "COMPLETED" || booking.status === "CANCELLED";
  return (
    <button className="h-9 rounded-md border border-[#cdd5ca] px-3 text-xs font-semibold disabled:opacity-50" disabled={disabled} onClick={() => onRun(booking.id, action)} type="button">
      {labels[action]}
    </button>
  );
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function toLocalInput(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}
