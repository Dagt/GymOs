import type { BranchSummary, ManagedOrganizationSummary } from "@/lib/api";

type PlatformGymnasiumListProps = {
  filteredOrganizations: ManagedOrganizationSummary[];
  organizationsCount: number;
  query: string;
  selectedOrganizationId: string;
  onCreateOrganization: () => void;
  onQueryChange: (value: string) => void;
  onSelectOrganization: (organizationId: string) => void;
};

type PlatformGymnasiumDetailProps = {
  activeBranchId?: string | null;
  branches: BranchSummary[];
  branchesLoading: boolean;
  saving: boolean;
  selectedIsActive: boolean;
  selectedOrganization: ManagedOrganizationSummary | null;
  selectingId: string | null;
  onActivateBranch: (branchId: string) => void;
  onActivateOrganization: (organizationId: string) => void;
  onCreateBranch: () => void;
  onGoToTenantSection: (path: string) => void;
  onToggleOrganizationStatus: () => void;
};

export function PlatformGymnasiumList({
  filteredOrganizations,
  organizationsCount,
  query,
  selectedOrganizationId,
  onCreateOrganization,
  onQueryChange,
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
      <input
        className="app-input"
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder="Buscar por nombre o RFC"
        value={query}
      />
      <div className="platform-tenant-list">
        {filteredOrganizations.map((item) => (
          <button
            className={item.id === selectedOrganizationId ? "active" : undefined}
            key={item.id}
            onClick={() => onSelectOrganization(item.id)}
            type="button"
          >
            <span>{item.status}</span>
            <strong>{item.tradeName}</strong>
            <small>
              {item.activeBranchCount === 0
                ? "Sin sucursales activas"
                : `${item.activeBranchCount} sucursales activas`}
            </small>
          </button>
        ))}
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
  onActivateBranch,
  onActivateOrganization,
  onCreateBranch,
  onGoToTenantSection,
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
            <button className="app-button" disabled={selectingId !== null} onClick={() => onActivateOrganization(selectedOrganization.id)} type="button">
              {selectedIsActive ? "Actualizar soporte" : "Entrar en soporte"}
            </button>
          </header>

          <div className="platform-facts">
            <article><span>RFC</span><strong>{selectedOrganization.taxId || "Sin dato"}</strong></article>
            <article><span>Contacto</span><strong>{selectedOrganization.contactEmail || "Sin email"}</strong></article>
            <article><span>Sucursales</span><strong>{selectedOrganization.branchCount}</strong></article>
            <article><span>Marca</span><strong>{selectedOrganization.brandName || selectedOrganization.tradeName}</strong></article>
          </div>

          <section className="platform-status-panel">
            <div>
              <span>Estado comercial</span>
              <strong>{selectedOrganization.status}</strong>
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
              <span>Administracion del cliente</span>
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
            <span>Codigo</span>
            <span>Sucursal</span>
            <span>Telefono</span>
            <span>Accion</span>
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
