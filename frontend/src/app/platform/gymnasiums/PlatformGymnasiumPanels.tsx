import type { BranchSummary } from "@/lib/api";
import type {
  PlatformCustomer,
  PlatformOrganizationSummary,
  PlatformOwnerAccess,
} from "@/lib/api/platform-customer-types";
import { customerHealthPresentation, lifecycleLabel } from "../platform-portfolio";
import {
  deliveryStatusLabel,
  invitationActionLabel,
  invitationStatusLabel,
} from "./owner-invitation";

type PlatformGymnasiumListProps = {
  filteredOrganizations: PlatformOrganizationSummary[];
  organizationsCount: number;
  query: string;
  selectedOrganizationId: string;
  customers: PlatformCustomer[];
  lifecycleFilter: string;
  onCreateOrganization: () => void;
  onQueryChange: (value: string) => void;
  onLifecycleFilterChange: (value: string) => void;
  onSelectOrganization: (organizationId: string) => void;
};

type PlatformGymnasiumDetailProps = {
  activeBranchId?: string | null;
  branches: BranchSummary[];
  branchesLoading: boolean;
  saving: boolean;
  selectedIsActive: boolean;
  selectedOrganization: PlatformOrganizationSummary | null;
  selectingId: string | null;
  selectedCustomer: PlatformCustomer | null;
  onActivateBranch: (branchId: string) => void;
  onActivateOrganization: (organizationId: string) => void;
  onCreateBranch: () => void;
  onEditCustomerAccount: () => void;
  onGoToTenantSection: (path: string) => void;
  onReissueOwnerInvitation: () => void;
  onToggleOrganizationStatus: () => void;
};

export function PlatformGymnasiumList({
  filteredOrganizations,
  customers,
  lifecycleFilter,
  organizationsCount,
  query,
  selectedOrganizationId,
  onCreateOrganization,
  onQueryChange,
  onLifecycleFilterChange,
  onSelectOrganization,
}: PlatformGymnasiumListProps) {
  return (
    <section className="platform-list-panel">
      <div className="platform-list-header">
        <div>
          <p className="app-eyebrow">Gimnasios</p>
          <h2>{organizationsCount} clientes registrados</h2>
        </div>
        <button className="app-button" onClick={onCreateOrganization} type="button">
          Nuevo gimnasio
        </button>
      </div>
      <div className="platform-list-filters">
        <input
          className="app-input"
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Nombre, RFC, responsable o cobranza"
          value={query}
        />
        <select className="app-input" onChange={(event) => onLifecycleFilterChange(event.target.value)} value={lifecycleFilter}>
          <option value="ALL">Todas las etapas</option>
          <option value="ONBOARDING">Implementación</option>
          <option value="ACTIVE">Clientes activos</option>
          <option value="AT_RISK">En riesgo</option>
          <option value="OFFBOARDING">En salida</option>
          <option value="FORMER">Clientes anteriores</option>
        </select>
      </div>
      <div className="platform-tenant-list">
        {filteredOrganizations.map((item) => {
          const customer = customers.find((candidate) => candidate.organizationId === item.id);
          const health = customer ? customerHealthPresentation(customer.healthStatus) : null;
          return <button
            className={item.id === selectedOrganizationId ? "active" : undefined}
            key={item.id}
            onClick={() => onSelectOrganization(item.id)}
            type="button"
          >
            <span>{health?.label ?? item.status}</span>
            <strong>{item.tradeName}</strong>
            <small>
              {customer
                ? `${lifecycleLabel(customer.lifecycleStatus)} · ${item.activeBranchCount} ${item.activeBranchCount === 1 ? "sede activa" : "sedes activas"}`
                : item.activeBranchCount === 0
                  ? "Sin sucursales activas"
                  : `${item.activeBranchCount} sucursales activas`}
            </small>
          </button>;
        })}
        {filteredOrganizations.length === 0 ? (
          <div className="admin-empty-card">No hay clientes que coincidan con los filtros.</div>
        ) : null}
      </div>
    </section>
  );
}

export function PlatformGymnasiumDetail({
  activeBranchId,
  branches,
  branchesLoading,
  saving,
  selectedIsActive,
  selectedOrganization,
  selectingId,
  selectedCustomer,
  onActivateBranch,
  onActivateOrganization,
  onCreateBranch,
  onEditCustomerAccount,
  onGoToTenantSection,
  onReissueOwnerInvitation,
  onToggleOrganizationStatus,
}: PlatformGymnasiumDetailProps) {
  return (
    <section className="platform-detail-panel">
      {selectedOrganization ? (
        <>
          <header className="platform-detail-header">
            <div>
              <p className="app-eyebrow">{selectedIsActive ? "En soporte ahora" : "Cliente seleccionado"}</p>
              <h2>{selectedOrganization.tradeName}</h2>
              <span>{selectedOrganization.legalName}</span>
            </div>
            <div className="platform-header-actions">
              <button className="app-button-secondary" onClick={onEditCustomerAccount} type="button">Editar expediente</button>
              <button className="app-button" disabled={selectingId !== null} onClick={() => onActivateOrganization(selectedOrganization.id)} type="button">
                {selectedIsActive ? "Actualizar soporte" : "Entrar en soporte"}
              </button>
            </div>
          </header>

          <div className="platform-facts">
            <article><span>RFC</span><strong>{selectedOrganization.taxId || "Sin dato"}</strong></article>
            <article><span>Contacto</span><strong>{selectedOrganization.contactEmail || "Sin email"}</strong></article>
            <article><span>Sucursales</span><strong>{selectedOrganization.branchCount}</strong></article>
            <article><span>Marca</span><strong>{selectedOrganization.brandName || selectedOrganization.tradeName}</strong></article>
          </div>

          {selectedCustomer ? (
            <section className="platform-customer-account">
              <header>
                <div>
                  <span>Expediente comercial</span>
                  <h3>{lifecycleLabel(selectedCustomer.lifecycleStatus)}</h3>
                </div>
                <span className={`platform-status-badge ${customerHealthPresentation(selectedCustomer.healthStatus).tone}`}>
                  {customerHealthPresentation(selectedCustomer.healthStatus).label}
                </span>
              </header>
              <dl>
                <div><dt>Responsable GymFort</dt><dd>{selectedCustomer.accountOwner || "Por asignar"}</dd></div>
                <div><dt>Contacto de cobranza</dt><dd>{selectedCustomer.billingEmail || "Sin correo"}</dd></div>
                <div><dt>Renovación</dt><dd>{selectedCustomer.renewalOn ? formatDate(selectedCustomer.renewalOn) : "Sin fecha"}</dd></div>
                <div><dt>Próximo seguimiento</dt><dd>{selectedCustomer.nextFollowUpOn ? formatDate(selectedCustomer.nextFollowUpOn) : "Sin seguimiento"}</dd></div>
                <div><dt>MRR</dt><dd>{formatCurrency(selectedCustomer.monthlyRecurringRevenue)}</dd></div>
                <div><dt>Cobranza</dt><dd>{selectedCustomer.overdueBranchCount ? `${selectedCustomer.overdueBranchCount} ${selectedCustomer.overdueBranchCount === 1 ? "sucursal vencida" : "sucursales vencidas"}` : "Al corriente"}</dd></div>
              </dl>
              {selectedCustomer.notes ? <p>{selectedCustomer.notes}</p> : null}
            </section>
          ) : null}

          {selectedCustomer?.ownerAccess ? (
            <OwnerInvitationPanel
              ownerAccess={selectedCustomer.ownerAccess}
              onReissue={onReissueOwnerInvitation}
              saving={saving}
            />
          ) : null}

          <section className="platform-status-panel">
            <div>
              <span>Estado comercial</span>
              <strong>{selectedOrganization.status === "ACTIVE" ? "Activo" : selectedOrganization.status === "SUSPENDED" ? "Suspendido" : "Inactivo"}</strong>
              <p>{selectedOrganization.status === "ACTIVE" ? "El gimnasio puede operar normalmente." : "El acceso de usuarios tenant queda bloqueado mientras este suspendido o inactivo."}</p>
            </div>
            <button className="app-button-secondary" disabled={saving} onClick={onToggleOrganizationStatus} type="button">
              {selectedOrganization.status === "ACTIVE" ? "Suspender gimnasio" : "Reactivar gimnasio"}
            </button>
          </section>

          <PlatformBranchPanel
            activeBranchId={activeBranchId}
            branches={branches}
            branchesLoading={branchesLoading}
            selectedIsActive={selectedIsActive}
            selectingId={selectingId}
            onActivateBranch={onActivateBranch}
            onCreateBranch={onCreateBranch}
          />

          <div className="platform-actions">
            <div className="platform-action-group">
              <span>Administración del cliente</span>
              <button className="admin-secondary-link" disabled={selectingId !== null} onClick={() => onGoToTenantSection("/settings/organization")} type="button">
                Datos, marca y sucursales
              </button>
              <button className="admin-secondary-link" disabled={selectingId !== null} onClick={() => onGoToTenantSection("/users")} type="button">
                Equipo y accesos
              </button>
            </div>
          </div>
        </>
      ) : (
        <section className="admin-empty-card">
          <strong>No hay gimnasios</strong>
          <span>Crea el primer gimnasio para empezar.</span>
        </section>
      )}
    </section>
  );
}

function OwnerInvitationPanel({
  ownerAccess,
  saving,
  onReissue,
}: {
  ownerAccess: PlatformOwnerAccess;
  saving: boolean;
  onReissue: () => void;
}) {
  const accountActive = ownerAccess.accountStatus === "ACTIVE";
  const canReissue = ownerAccess.accountStatus === "INVITED";
  return (
    <section className="platform-owner-access-panel">
      <header>
        <div>
          <span>Acceso del dueño</span>
          <h3>{ownerAccess.ownerName}</h3>
          <p>{ownerAccess.ownerEmail}</p>
        </div>
        <span className={`platform-status-badge ${accountActive ? "success" : "warning"}`}>
          {accountActive ? "Cuenta activada" : invitationStatusLabel(ownerAccess.invitationStatus)}
        </span>
      </header>
      <div className="platform-owner-access-details">
        <div>
          <span>Correo de invitación</span>
          <strong>{deliveryStatusLabel(ownerAccess.emailDeliveryStatus)}</strong>
        </div>
        <div>
          <span>Vigencia del enlace</span>
          <strong>{ownerAccess.invitationExpiresAt
            ? formatDateTime(ownerAccess.invitationExpiresAt)
            : "Sin invitación vigente"}</strong>
        </div>
      </div>
      {canReissue ? (
        <div className="platform-owner-access-actions">
          <p>Al generar una nueva invitación, cualquier enlace anterior deja de funcionar.</p>
          <button className="app-button-secondary" disabled={saving} onClick={onReissue} type="button">
            {invitationActionLabel(ownerAccess.invitationStatus)}
          </button>
        </div>
      ) : null}
    </section>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", maximumFractionDigits: 0 }).format(value);
}

function formatDate(value: string) {
  const [year, month, day] = value.split("-");
  return year && month && day ? `${day}/${month}/${year}` : value;
}

function PlatformBranchPanel({
  activeBranchId,
  branches,
  branchesLoading,
  selectedIsActive,
  selectingId,
  onActivateBranch,
  onCreateBranch,
}: {
  activeBranchId?: string | null;
  branches: BranchSummary[];
  branchesLoading: boolean;
  selectedIsActive: boolean;
  selectingId: string | null;
  onActivateBranch: (branchId: string) => void;
  onCreateBranch: () => void;
}) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-header">
        <div>
          <p>Sucursales</p>
          <h2>{selectedIsActive ? "Sucursales para soporte" : "Sucursales registradas"}</h2>
        </div>
        <button className="admin-text-button" disabled={selectingId !== null} onClick={onCreateBranch} type="button">
          Crear sucursal
        </button>
      </div>
      {branchesLoading ? (
        <p className="admin-muted-text">Cargando sucursales...</p>
      ) : branches.length > 0 ? (
        <div className="platform-table">
          <div className="platform-table-row header">
            <span>Código</span>
            <span>Sucursal</span>
            <span>Teléfono</span>
            <span>Acción</span>
          </div>
          {branches.map((branch) => (
            <div className="platform-table-row" key={branch.id}>
              <span>{branch.code}</span>
              <strong>{branch.name}</strong>
              <span>{branch.phone || "Sin telefono"}</span>
              <button disabled={selectingId !== null} onClick={() => onActivateBranch(branch.id)} type="button">
                {branch.id === activeBranchId && selectedIsActive ? "Abrir soporte" : "Entrar"}
              </button>
            </div>
          ))}
        </div>
      ) : (
        <section className="admin-empty-card">
          <strong>Este gimnasio todavia no tiene sucursales</strong>
          <span>Crea al menos una sucursal para poder seleccionar contexto operativo, usuarios y recepcion.</span>
        </section>
      )}
    </section>
  );
}
