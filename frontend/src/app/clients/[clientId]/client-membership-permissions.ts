export type ClientMembershipCapabilities = {
  canChange: boolean;
  canEnroll: boolean;
  canManage: boolean;
  canRenew: boolean;
};

type PermissionSubject = {
  permissions: string[];
  platformAdmin?: boolean;
};

export function resolveClientMembershipCapabilities(
  subject?: PermissionSubject | null,
): ClientMembershipCapabilities {
  const permissions = new Set(subject?.permissions ?? []);
  const platformAdmin = Boolean(subject?.platformAdmin || permissions.has("platform.admin"));
  const has = (permission: string) => platformAdmin || permissions.has(permission);
  const canEnroll = has("sale.create");
  const canRenew = has("membership.renew") || canEnroll;
  const canChange = has("membership.change-plan") || canEnroll;

  return {
    canChange,
    canEnroll,
    canManage: canEnroll || canRenew || canChange,
    canRenew,
  };
}
