import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { type FieldErrorMap, normalizePhone, normalizeRfc } from "@/components/FormFields";
import {
  createBranch,
  getOrganizationBranches,
  getStoredSession,
  selectBranch,
  selectOrganization,
  uploadManagedOrganizationLogo,
  updateOrganizationStatus,
  type BranchSummary,
  type CreateBranchPayload,
  type StoredSession,
} from "@/lib/api";
import {
  createPlatformCustomer,
  getPlatformCustomers,
  reissuePlatformOwnerInvitation,
  updatePlatformCustomer,
} from "@/lib/api/platform-customers";
import type {
  PlatformCustomer,
	PlatformCustomerCreatePayload,
  PlatformOrganizationSummary,
  PlatformCustomerPortfolio,
  PlatformCustomerUpdatePayload,
	ProvisionedPlatformCustomer,
} from "@/lib/api/platform-customer-types";
import {
  emptyBranchForm,
  emptyOrganizationForm,
  validateBranch,
  validateOrganization,
} from "./PlatformGymnasiumForms";
import { invitationSuccessMessage } from "./owner-invitation";

export function usePlatformGymnasiumsController() {
  const router = useRouter();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [portfolio, setPortfolio] = useState<PlatformCustomerPortfolio | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [selectedOrganizationId, setSelectedOrganizationId] = useState("");
  const [query, setQuery] = useState("");
  const [lifecycleFilter, setLifecycleFilter] = useState("ALL");
  const [showCreate, setShowCreate] = useState(false);
  const [showCreateBranch, setShowCreateBranch] = useState(false);
  const [showCustomerAccount, setShowCustomerAccount] = useState(false);
	const [showSupportReason, setShowSupportReason] = useState(false);
	const [supportReason, setSupportReason] = useState("Configuración solicitada por el cliente");
	const [supportReasonError, setSupportReasonError] = useState<string | null>(null);
	const supportReasonResolver = useRef<((reason: string | null) => void) | null>(null);
	const [form, setForm] = useState<PlatformCustomerCreatePayload>(emptyOrganizationForm);
	const [provisionedInvitation, setProvisionedInvitation] = useState<ProvisionedPlatformCustomer | null>(null);
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
        const customerPortfolio = await getPlatformCustomers(stored.accessToken);
        if (!active) {
          return;
        }
        setPortfolio(customerPortfolio);
        setSelectedOrganizationId((current) => {
          const requestedId = new URLSearchParams(window.location.search).get("organization") ?? "";
		  const preferredId = current || requestedId || stored.user.organizationId || "";
          return customerPortfolio.customers.some(
            (customer) => customer.organizationId === preferredId,
          )
            ? preferredId
            : customerPortfolio.customers[0]?.organizationId || "";
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

  const organizations = useMemo<PlatformOrganizationSummary[]>(
    () => (portfolio?.customers ?? []).map(toPlatformOrganization),
    [portfolio],
  );

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

  async function refreshPortfolioAfterCommittedAction(activeToken: string) {
    try {
      await refreshPortfolio(activeToken);
      return null;
    } catch (exception) {
      return exception instanceof Error
        ? exception.message
        : "No fue posible actualizar el portafolio.";
    }
  }

	function requestSupportReason(): Promise<string | null> {
		setSupportReason("Configuración solicitada por el cliente");
		setSupportReasonError(null);
		setShowSupportReason(true);
		return new Promise((resolve) => {
			supportReasonResolver.current = resolve;
		});
	}

	function closeSupportReason() {
		supportReasonResolver.current?.(null);
		supportReasonResolver.current = null;
		setShowSupportReason(false);
		setSupportReasonError(null);
	}

	function submitSupportReason(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		const normalized = supportReason.trim();
		if (normalized.length < 8) {
			setSupportReasonError("Describe el motivo con al menos 8 caracteres.");
			return;
		}
		supportReasonResolver.current?.(normalized);
		supportReasonResolver.current = null;
		setShowSupportReason(false);
		setSupportReasonError(null);
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
		const reason = await requestSupportReason();
		if (!reason) {
		  setSelectingId(null);
		  return null;
		}
		const supportResponse = await selectOrganization(session.accessToken, organizationId, reason);
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
		if (nextSession.user.branchId) {
			router.push("/dashboard");
			return;
		}
		if (availableBranches.length === 1) {
			const selectedSession = await selectBranch(nextSession.accessToken, availableBranches[0].id);
			setSession(selectedSession);
			router.push("/dashboard");
			return;
		}
		setBranchForm(emptyBranchForm);
		setBranchFieldErrors({});
		setShowCreateBranch(true);
		setMessage("Este gimnasio necesita una sucursal antes de abrir su operación.");
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
		branchPhone: normalizePhone(form.branchPhone),
		ownerPhone: normalizePhone(form.ownerPhone),
      };
	  const provisioned = await createPlatformCustomer(session.accessToken, payload);
	  let logoUploadError: string | null = null;
      if (logoFile) {
        try {
		  await uploadManagedOrganizationLogo(
            session.accessToken,
			provisioned.customer.organizationId,
            logoFile,
          );
        } catch (exception) {
          logoUploadError = exception instanceof Error
            ? exception.message
            : "No fue posible guardar el logo seleccionado.";
        }
      }
      setForm(emptyOrganizationForm);
      setLogoFile(null);
      setShowCreate(false);
	  setProvisionedInvitation(provisioned);
	  setSelectedOrganizationId(provisioned.customer.organizationId);
      setBranches([]);
      const portfolioRefreshError = await refreshPortfolioAfterCommittedAction(session.accessToken);
      const completedWithWarnings = Boolean(logoUploadError || portfolioRefreshError);
      if (completedWithWarnings) {
        const warnings = [
          logoUploadError ? `El logo no pudo guardarse: ${logoUploadError}` : null,
          portfolioRefreshError
            ? `El portafolio no pudo actualizarse todavía: ${portfolioRefreshError}`
            : null,
        ].filter(Boolean).join(" ");
        setError(`El gimnasio se creó correctamente. ${warnings}`);
      }
	  setMessage(invitationSuccessMessage(
		provisioned.emailDeliveryStatus,
		Boolean(logoUploadError),
	  ));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear el gimnasio.");
    } finally {
      setSaving(false);
    }
  }

  async function reissueOwnerInvitation() {
    if (!session || !selectedCustomer?.ownerAccess) return;
    const ownerAccess = selectedCustomer.ownerAccess;
    if (ownerAccess.invitationStatus === "PENDING") {
      const confirmed = window.confirm(
        "Se invalidará el enlace anterior y se enviará uno nuevo. ¿Quieres continuar?",
      );
      if (!confirmed) return;
    }

    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const invitation = await reissuePlatformOwnerInvitation(
        session.accessToken,
        selectedCustomer.organizationId,
      );
      setProvisionedInvitation({
        customer: selectedCustomer,
        invitationId: invitation.invitationId,
        ownerName: invitation.ownerName,
        ownerEmail: invitation.ownerEmail,
        invitationUrl: invitation.invitationUrl,
        invitationExpiresAt: invitation.invitationExpiresAt,
        emailDeliveryStatus: invitation.emailDeliveryStatus,
      });
      setMessage(invitationSuccessMessage(invitation.emailDeliveryStatus, false, true));
      const portfolioRefreshError = await refreshPortfolioAfterCommittedAction(session.accessToken);
      if (portfolioRefreshError) {
        setError(
          `La nueva invitación se generó correctamente, pero el portafolio no pudo actualizarse todavía: ${portfolioRefreshError}`,
        );
      }
    } catch (exception) {
      setError(exception instanceof Error
        ? exception.message
        : "No fue posible generar una nueva invitación.");
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
		const createdBranch = await createBranch(nextSession.accessToken, {
        ...branchForm,
        phone: normalizePhone(branchForm.phone ?? ""),
      });
		const selectedSession = await selectBranch(nextSession.accessToken, createdBranch.id);
		setSession(selectedSession);
      setBranchForm(emptyBranchForm);
      setShowCreateBranch(false);
		router.push("/settings/organization");
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
	setProvisionedInvitation(null);
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
      await updateOrganizationStatus(session.accessToken, selectedOrganization.id, nextStatus);
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
	provisionedInvitation,
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
	showSupportReason,
	supportReason,
	supportReasonError,
    customerForm,
    activateBranch,
    activateOrganization,
    goToTenantSection,
    openBranchDrawer,
    openCustomerAccount,
    openCreateOrganization,
	reissueOwnerInvitation,
    closeCreateOrganization,
    closeCustomerAccount,
	closeSupportReason,
    setBranchForm,
    setForm,
    setLogoFile,
    setCustomerForm,
    setLifecycleFilter,
    setQuery,
    setSelectedOrganizationId,
    setShowCreateBranch,
	setProvisionedInvitation,
	setSupportReason,
    submitBranch,
    submitCustomerAccount,
    submitOrganization,
	submitSupportReason,
    toggleOrganizationStatus,
  };
}

function toPlatformOrganization(customer: PlatformCustomer): PlatformOrganizationSummary {
  return {
    id: customer.organizationId,
    legalName: customer.legalName,
    tradeName: customer.tradeName,
    taxId: customer.taxId,
    status: customer.organizationStatus,
    contactEmail: customer.contactEmail,
    contactPhone: customer.contactPhone,
    brandName: customer.brandName,
    logoUrl: customer.logoUrl,
    branchCount: customer.branchCount,
    activeBranchCount: customer.activeBranchCount,
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
