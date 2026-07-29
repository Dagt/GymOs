"use client";

import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  BookingDayDetail,
  BookingNoteModal,
  BookingNoticeModal,
  BookingSummaryPanel,
  BookingsMonthCalendar,
} from "./components";
import { BookingDrawerForm, ResourceDrawerForm } from "./forms";
import { useBookingsController } from "./useBookingsController";

export default function BookingsPage() {
  const controller = useBookingsController();

  if (controller.loading) return <LoadingScreen text="Cargando reservas..." />;
  if (!controller.session) return <AuthRequiredCard message="Necesitas iniciar sesion para administrar reservas." title="Inicia sesion" />;

  return (
    <AppShell
      branchName={controller.activeBranch?.name}
      eyebrow="Operacion"
      mode={controller.activeBranch ? "operation" : undefined}
      organizationName={
        controller.organization?.brandName
        || controller.organization?.tradeName
        || controller.session.user.brandName
        || controller.session.user.organizationName
      }
      session={controller.session}
      subtitle="Agenda mensual con detalle diario y reservaciones incluidas en membresias."
      title="Recursos y reservas"
    >
      {controller.notice ? <BookingNoticeModal notice={controller.notice} onClose={() => controller.setNotice(null)} /> : null}
      {controller.noteDialog ? <BookingNoteModal dialog={controller.noteDialog} onClose={() => controller.setNoteDialog(null)} /> : null}

      <section className={`grid gap-6 xl:grid-cols-[410px_minmax(0,1fr)] ${controller.canManageResources ? "" : "bookings-operational-layout"}`}>
        <aside className="grid content-start gap-5">
          {controller.selectedDateIsPast ? (
            <p className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-2 text-sm text-[var(--brand-muted)]">
              Este dia ya paso. Puedes consultar el detalle historico, pero no crear nuevas reservas.
            </p>
          ) : null}

          <section className="app-card p-5">
            <p className="app-eyebrow">Acciones</p>
            <h2 className="app-card-title">Agenda del dia</h2>
            <p className="app-card-copy">Selecciona un dia en el calendario y abre solo la accion que necesitas.</p>
            <div className="mt-5 grid gap-3">
              <button
                className="app-button"
                disabled={controller.selectedDateIsPast || controller.resources.length === 0}
                onClick={() => controller.setShowBookingDrawer(true)}
                type="button"
              >
                Nueva reserva
              </button>
              {controller.canManageResources ? (
                <button className="admin-secondary-link" onClick={controller.openCreateResourceDrawer} type="button">
                  Crear recurso
                </button>
              ) : null}
            </div>
          </section>

          <BookingSummaryPanel
            canManageResources={controller.canManageResources}
            onDeleteResource={controller.deleteReservableResource}
            onEditResource={controller.openEditResourceDrawer}
            openResourceCount={controller.openResourceCount}
            resources={controller.selectedDayResourceSummary}
            selectedDate={controller.startDate}
          />

          <BookingDrawer
            controller={controller}
            onClose={() => controller.setShowBookingDrawer(false)}
            open={controller.showBookingDrawer}
          />

          {controller.canManageResources ? (
            <ResourceDrawer
              controller={controller}
              onClose={() => controller.setShowResourceDrawer(false)}
              open={controller.showResourceDrawer}
            />
          ) : null}
        </aside>

        <BookingsMonthCalendar
          activeBookings={controller.activeBookings}
          calendarDate={controller.calendarDate}
          className={controller.canManageResources ? "" : "bookings-operational-agenda"}
          getResourceColor={controller.resourceColor}
          monthDates={controller.monthDates}
          onGoToToday={controller.goToToday}
          onMoveMonth={controller.moveCalendarMonth}
          onSelectDate={controller.selectCalendarDate}
          onSelectResource={controller.setResourceId}
          resourceId={controller.resourceId}
          resources={controller.resources}
          selectedDate={controller.startDate}
          today={controller.today}
          weekdays={controller.weekdays}
        />

        {controller.resources.length > 0 ? (
          <BookingDayDetail
            bookings={controller.selectedDateBookings}
            className="bookings-full-width-detail"
            getResourceColor={controller.resourceColor}
            getResourceHours={(resource) => controller.resourceHoursForDay(resource, controller.selectedWeekday)}
            onCancel={controller.cancelBooking}
            onShowNote={controller.openNoteDialog}
            resources={controller.resources}
            selectedDate={controller.startDate}
          />
        ) : null}
      </section>
    </AppShell>
  );
}

type BookingsController = ReturnType<typeof useBookingsController>;

function BookingDrawer({
  controller,
  onClose,
  open,
}: {
  controller: BookingsController;
  onClose: () => void;
  open: boolean;
}) {
  return (
    <AppDrawer
      description="Busca al socio, elige el recurso y confirma el horario de inicio."
      onClose={onClose}
      open={open}
      title="Nueva reserva"
    >
      <BookingDrawerForm
        activeResource={controller.activeResource}
        clientQuery={controller.clientQuery}
        clientResults={controller.clientResults}
        computedEnd={controller.computedEnd}
        formatDateTime={controller.formatDateTime}
        isValidTimeInput={controller.isValidTimeInput}
        minimumBookingTime={controller.minimumBookingTime}
        notes={controller.notes}
        onClientQueryChange={controller.updateClientQuery}
        onNotesChange={controller.setNotes}
        onSelectCalendarDate={controller.selectCalendarDate}
        onSelectClient={controller.selectClient}
        onSelectResource={controller.setResourceId}
        onStartTimeChange={controller.setStartTime}
        onSubmit={controller.submitBooking}
        resourceId={controller.resourceId}
        resources={controller.resources}
        saving={controller.saving}
        selectedClient={controller.selectedClient}
        selectedDateIsPast={controller.selectedDateIsPast}
        selectedStartIsPast={controller.selectedStartIsPast}
        startDate={controller.startDate}
        startTime={controller.startTime}
        today={controller.today}
      />
    </AppDrawer>
  );
}

function ResourceDrawer({
  controller,
  onClose,
  open,
}: {
  controller: BookingsController;
  onClose: () => void;
  open: boolean;
}) {
  return (
    <AppDrawer
      description="Define horarios, duracion y membresias permitidas para un recurso reservable."
      onClose={onClose}
      open={open}
      title={controller.editingResourceId ? "Editar recurso" : "Crear recurso"}
    >
      <ResourceDrawerForm
        durationUnit={controller.durationUnit}
        durationValue={controller.durationValue}
        onAllowedPlanIdsChange={controller.setResourceAllowedPlanIds}
        onDurationUnitChange={controller.setDurationUnit}
        onDurationValueChange={controller.setDurationValue}
        onResourceNameChange={controller.setResourceName}
        onSubmit={controller.submitResource}
        onUpdateResourceDay={controller.updateResourceDay}
        plans={controller.plans}
        resourceAllowedPlanIds={controller.resourceAllowedPlanIds}
        resourceName={controller.resourceName}
        resourceSchedule={controller.resourceSchedule}
        saving={controller.saving}
        submitLabel={controller.editingResourceId ? "Guardar recurso" : "Crear recurso"}
      />
    </AppDrawer>
  );
}

