export type UserConsole = "PLATFORM_OWNER" | "TENANT_OWNER" | "TENANT_MANAGER" | "RECEPTION" | "COACH";
export type AccountScope = "PLATFORM" | "TENANT";

export type CurrentUser = {
  userId: string;
  accountScope: AccountScope;
  organizationId: string | null;
  branchId: string | null;
  username: string;
  fullName: string;
  permissions: string[];
  roles: string[];
  platformAdmin: boolean;
  supportMode: boolean;
  supportExpiresAt: string | null;
  console: UserConsole;
  organizationName?: string | null;
  brandName?: string | null;
  logoUrl?: string | null;
  primaryColor?: string | null;
  accentColor?: string | null;
  surfaceColor?: string | null;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: CurrentUser;
};

export type StoredSession = {
  accessToken: string;
  user: CurrentUser;
};
