export type OrganizationSummary = {
  id: string;
  legalName: string;
  tradeName: string;
  taxId: string | null;
  status: string;
  timezone: string;
  defaultCurrency: string;
  contactEmail: string | null;
  contactPhone: string | null;
  brandName: string | null;
  logoUrl: string | null;
  primaryColor: string | null;
  accentColor: string | null;
  surfaceColor: string | null;
};

export type ManagedOrganizationSummary = OrganizationSummary & {
  branchCount: number;
  activeBranchCount: number;
};

export type BranchSummary = {
  id: string;
  name: string;
  code: string;
  status: string;
  timezone: string;
  phone: string | null;
  accessScope: string;
};

export type OrganizationUpdatePayload = {
  legalName: string;
  tradeName: string;
  taxId?: string | null;
  timezone: string;
  defaultCurrency: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
  brandName?: string | null;
  logoUrl?: string | null;
  primaryColor?: string | null;
  accentColor?: string | null;
  surfaceColor?: string | null;
};

export type CreateOrganizationPayload = OrganizationUpdatePayload & {
  firstBranchName?: string | null;
  firstBranchCode?: string | null;
  firstBranchPhone?: string | null;
};

export type BranchUpdatePayload = {
  name: string;
  code: string;
  timezone: string;
  phone?: string | null;
};

export type CreateBranchPayload = BranchUpdatePayload;
