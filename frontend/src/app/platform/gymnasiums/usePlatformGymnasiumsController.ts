import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { type FieldErrorMap, normalizePhone, normalizeRfc } from "@/components/FormFields";
import {
  createBranch,
  createOrganization,
  getOrganizationBranches,
  getOrganizations,
  getStoredSession,
  selectBranch,
  selectOrganization,
  storeSession,
  uploadManagedOrganizationLogo,
  updateOrganizationStatus,
  type BranchSummary,
  type CreateBranchPayload,
  type CreateOrganizationPayload,
  type ManagedOrganizationSummary,
  type StoredSession,
} from "@/lib/api";
import { getPlatformCustomers, updatePlatformCustomer } from "@/lib/api/platform-customers";
import type {
  PlatformCustomer,
  PlatformCustomerPortfolio,
  PlatformCustomerUpdatePayload,
} from "@/lib/api/platform-customer-types";
import {
  emptyBranchForm,
  emptyOrganizationForm,
  validateBranch,
  validateOrganization,
} from "./PlatformGymnasiumForms";

export function usePlatformGymnasiumsController() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organizations, setOrganizations] = useState<ManagedOrganizationSummary[]>([]);
  const [portfolio, setPortfolio] = useState<PlatformCustomerPortfolio | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [query, setQuery] = useState("");
  const [lifecycleFilter, setLifecycleFilter] = useState("ALL");
  const [showCreate, setShowCreate] = useState(false);
  const [showCreateBranch, setShowCreateBranch] = useState(false);
  const [showCustomerAccount, setShowCustomerAccount] = useState(false);
  const [form, setForm] = useState<CreateOrganizationPayload>(emptyOrganizationForm);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [branchForm, setBranchForm] = useState<CreateBranchPayload>(emptyBranchForm);
  const [customerForm, setCustomerForm] = useState<PlatformCustomerUpdatePayload | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [branchFieldErrors, setBranchFieldErrors] = useState<FieldErrorMap>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [selectingId, setSelectingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const accessToken = session?.accessToken;

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [availableOrganizations, customerPortfolio] = await Promise.all([
          getOrganizations(stored.accessToken),
          getPlatformCustomers(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganizations(availableOrganizations);
        setPortfolio(customerPortfolio);
        setSelectedOrganizationId((current) => {
          const requestedId = new URLSearchParams(window.location.search).get("organization") ?? "";
          const preferredId = current || requestedId || stored.user.organizationId;
          return availableOrganizations.some((organization) => organization.id === preferredId)
            ? preferredId
            : availableOrganizations[0]?.id || "";
        });
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar gimnasios.");
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
  }, []);

  useEffect(() => {
    let active = true;

    async function loadBranchesForSelection() {
      if (!accessToken || !selectedOrganizationId) {
        setBranches([]);
        return;
      }
      setBranches([]);
      setBranchesLoading(true);
      setError(null);
      try {
        const selectedBranches = await getOrganizationBranches(accessToken, selectedOrganizationId);
        if (active) {
          setBranches(selectedBranches);
        }
      } catch (exception) {
        if (active) {
          setBranches([]);
          setError(
            exception instanceof Error
              ? exception.message
              : "No fue posible cargar sucursales del gimnasio seleccionado.",
          );
        }
      } finally {
        if (active) {
          setBranchesLoading(false);
        }
      }
    }

    void loadBranchesForSelection();

    return () => {
      active = false;
    };
  }, [accessToken, selectedOrganizationId]);

  const filteredOrganizations = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    const customers = new Map(
      (portfolio?.customers ?? []).map((customer) => [customer.organizationId, customer]),
    );
    return organizations.filter((item) => {
      const customer = customers.get(item.id);
      const matchesQuery = !normalized || [
        item.tradeName,
        item.legalName,
        item.taxId ?? "",
        customer?.accountOwner ?? "",
        customer?.billingEmail ?? "",
      ].some((value) => value.toLowerCase().includes(normalized));
      return matchesQuery
        && (lifecycleFilter === "ALL" || customer?.lifecycleStatus === lifecycleFilter);
    });
  }, [lifecycleFilter, organizations, portfolio, query]);

  const selectedOrganization =
    organizations.find((item) => item.id === selectedOrganizationId) ?? null;
  const selectedCustomer = portfolio?.customers.find(
    (customer) => customer.organizationId === selectedOrganizationId,
  ) ?? null;
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectedIsActive = Boolean(
    session && selectedOrganization?.id === session.user.organizationId && session.user.supportMode,
  );

  async function refreshBranches(activeToken: string, organizationId: string): Promise<BranchSummary[]> {
    const availableBranches = await getOrganizationBranches(activeToken, organizationId);
    setBranches(availableBranches);
    return availableBranches;
  }

  async function refreshPortfolio(activeToken: string) {
    const customerPortfolio = await getPlatformCustomers(activeToken);
    setPortfolio(customerPortfolio);
    return customerPortfolio;
  }

  async function ensureSupport(organizationId: string): Promise<StoredSession | null> {
    if (!session) {
      return null;
    }
    setSelectingId(organizationId);
    setError(null);
    setMessage(null);
    try {
      let nextSession: StoredSession = session;
      if (session.user.organizationId !== organizationId || !session.user.supportMode) {
        const supportResponse = await selectOrganization(session.accessToken, organizationId);
        storeSession(supportResponse);
        setSession(supportResponse);
        nextSession = supportResponse;
      }
      setSelectedOrganizationId(organizationId);
      await refreshBranches(nextSession.accessToken, organizationId);
      if (!session.user.supportMode || session.user.organizationId !== organizationId) {
        setMessage("Modo soporte iniciado para este gimnasio.");
      }
      return nextSession;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible entrar en soporte.");
      return null;
    } finally {
      setSelectingId(null);
    }
  }

  async function activateOrganization(organizationId: string) {
    const nextSession = await ensureSupport(organizationId);
    if (!nextSession) {
      return;
    }
    const availableBranches = await refreshBranches(nextSession.accessToken, organizationId);
    if (!nextSession.user.branchId && availableBranches.length === 1) {
      await activateBranch(availableBranches[0].id, false);
    }
  }

  async function activateBranch(branchId: string, navigate = true) {
    if (!session || !selectedOrganization) {
      return;
    }
    setSelectingId(branchId);
    setError(null);
    setMessage(null);
    try {
      const supportSession = await ensureSupport(selectedOrganization.id);
      if (!supportSession) {
        return;
      }
      if (supportSession.user.branchId === branchId) {
        if (navigate) {
          router.push("/dashboard");
        }
        return;
      }
      const nextSession = await selectBranch(supportSession.accessToken, branchId);
      storeSession(nextSession);
      setSession(nextSession);
      await refreshBranches(nextSession.accessToken, selectedOrganization.id);
      if (navigate) {
        router.push("/dashboard");
      } else {
        setMessage("Sucursal lista para soporte.");
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible seleccionar sucursal.");
    } finally {
      setSelectingId(null);
    }
  }

  async function goToTenantSection(path: string) {
    if (!selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (nextSession) {
      router.push(path);
    }
  }

  async function openBranchDrawer() {
    if (!selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (nextSession) {
      setBranchForm(emptyBranchForm);
      setBranchFieldErrors({});
      setShowCreateBranch(true);
    }
  }

  async function submitOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    const errors = validateOrganization(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = {
        ...form,
        taxId: normalizeRfc(form.taxId ?? ""),
        contactPhone: normalizePhone(form.contactPhone ?? ""),
      };
      const created = await createOrganization(session.accessToken, payload);
      let persistedOrganization = created;
      let logoUploadError: string | null = null;
      if (logoFile) {
        try {
          const branded = await uploadManagedOrganizationLogo(
            session.accessToken,
            created.id,
            logoFile,
          );
          persistedOrganization = { ...created, ...branded };
        } catch (exception) {
          logoUploadError = exception instanceof Error
            ? exception.message
            : "No fue posible guardar el logo seleccionado.";
        }
      }
      setOrganizations((current) =>
        [...current, persistedOrganization]
          .sort((left, right) => left.tradeName.localeCompare(right.tradeName)),
      );
      setForm(emptyOrganizationForm);
      setLogoFile(null);
      setShowCreate(false);
      setSelectedOrganizationId(created.id);
      setBranches([]);
      await refreshPortfolio(session.accessToken);
      if (logoUploadError) {
        setError(`El gimnasio se creo correctamente, pero el logo no pudo guardarse. ${logoUploadError}`);
        setMessage("Gimnasio creado. Puedes volver a cargar el logo desde su detalle.");
      } else {
        setMessage(
          logoFile
            ? "Gimnasio y logo creados. Ahora agrega sus sucursales y usuarios."
            : "Gimnasio creado. Ahora crea sus sucursales, marca y usuarios desde su detalle.",
        );
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  async function submitBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedOrganization) {
      return;
    }
    const nextSession = await ensureSupport(selectedOrganization.id);
    if (!nextSession) {
      return;
    }
    const errors = validateBranch(branchForm);
    setBranchFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createBranch(nextSession.accessToken, {
        ...branchForm,
        phone: normalizePhone(branchForm.phone ?? ""),
      });
      const selectedBranches = await getOrganizationBranches(nextSession.accessToken, selectedOrganization.id);
      setBranches(selectedBranches);
      setOrganizations((current) =>
        current.map((organization) =>
          organization.id === selectedOrganization.id
            ? {
                ...organization,
                branchCount: organization.branchCount + 1,
                activeBranchCount: organization.activeBranchCount + 1,
              }
            : organization,
        ),
      );
      await refreshPortfolio(nextSession.accessToken);
      setBranchForm(emptyBranchForm);
      setShowCreateBranch(false);
      setMessage("Sucursal creada. Ya puedes seleccionarla o asignar usuarios.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la sucursal.");
    } finally {
      setSaving(false);
    }
  }

  function openCreateOrganization() {
    setForm(emptyOrganizationForm);
    setLogoFile(null);
    setFieldErrors({});
    setError(null);
    setMessage(null);
    setShowCreate(true);
  }

  function closeCreateOrganization() {
    setLogoFile(null);
    setFieldErrors({});
    setShowCreate(false);
  }

  function openCustomerAccount() {
    if (!selectedCustomer) return;
    setCustomerForm(toCustomerForm(selectedCustomer));
    setError(null);
    setMessage(null);
    setShowCustomerAccount(true);
  }

  function closeCustomerAccount() {
    if (saving) return;
    setShowCustomerAccount(false);
    setCustomerForm(null);
  }

  async function submitCustomerAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedCustomer || !customerForm) return;
    if (customerForm.renewalOn && customerForm.contractStartedOn
        && customerForm.renewalOn < customerForm.contractStartedOn) {
      setError("La renovación no puede ser anterior al inicio del contrato.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updatePlatformCustomer(
        session.accessToken,
        selectedCustomer.organizationId,
        customerForm,
      );
      setPortfolio((current) => current ? {
        ...current,
        customers: current.customers.map((customer) =>
          customer.organizationId === updated.organizationId ? updated : customer),
      } : current);
      await refreshPortfolio(session.accessToken);
      setCustomerForm(toCustomerForm(updated));
      setShowCustomerAccount(false);
      setMessage("Expediente comercial actualizado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar el expediente comercial.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleOrganizationStatus() {
    if (!session || !selectedOrganization) {
      return;
    }
    const nextStatus = selectedOrganization.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    const confirmed = window.confirm(
      nextStatus === "ACTIVE"
        ? `Quieres reactivar ${selectedOrganization.tradeName}? Sus usuarios podran volver a operar.`
        : `Quieres suspender ${selectedOrganization.tradeName}? Sus usuarios no podran iniciar sesion mientras este suspendido.`,
    );
    if (!confirmed) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const updated = await updateOrganizationStatus(session.accessToken, selectedOrganization.id, nextStatus);
      setOrganizations((current) =>
        current.map((organization) => (organization.id === updated.id ? updated : organization)),
      );
      await refreshPortfolio(session.accessToken);
      setMessage(
        nextStatus === "ACTIVE"
          ? "Gimnasio reactivado."
          : "Gimnasio suspendido. Sus usuarios tenant no podran iniciar sesion.",
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado del gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  return {
    activeBranch,
    branchFieldErrors,
    branchForm,
    branches,
    branchesLoading,
    error,
    fieldErrors,
    filteredOrganizations,
    form,
    logoFile,
    loading,
    lifecycleFilter,
    message,
    organizations,
    portfolio,
    query,
    saving,
    selectedIsActive,
    selectedCustomer,
    selectedOrganization,
    selectingId,
    session,
    showCreate,
    showCreateBranch,
    showCustomerAccount,
    customerForm,
    activateBranch,
    activateOrganization,
    goToTenantSection,
    openBranchDrawer,
    openCustomerAccount,
    openCreateOrganization,
    closeCreateOrganization,
    closeCustomerAccount,
    setBranchForm,
    setForm,
    setLogoFile,
    setCustomerForm,
    setLifecycleFilter,
    setQuery,
    setSelectedOrganizationId,
    setShowCreateBranch,
    submitBranch,
    submitCustomerAccount,
    submitOrganization,
    toggleOrganizationStatus,
  };
}

function toCustomerForm(customer: PlatformCustomer): PlatformCustomerUpdatePayload {
  return {
    lifecycleStatus: customer.lifecycleStatus,
    accountOwner: customer.accountOwner,
    billingEmail: customer.billingEmail,
    billingPhone: customer.billingPhone,
    contractStartedOn: customer.contractStartedOn,
    renewalOn: customer.renewalOn,
    paymentTermsDays: customer.paymentTermsDays,
    nextFollowUpOn: customer.nextFollowUpOn,
    notes: customer.notes,
    expectedVersion: customer.version,
  };
}
