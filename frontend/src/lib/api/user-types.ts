export type RoleOption = {
  id: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissionCount: number;
};

export type InternalUserSummary = {
  id: string;
  fullName: string;
  username: string;
  email: string | null;
  phone: string | null;
  status: string;
  roles: string[];
  branches: string[];
};

export type CreateInternalUserPayload = {
  firstName: string;
  lastName: string;
  maternalLastName?: string | null;
  email?: string | null;
  phone?: string | null;
  username: string;
  temporaryPassword: string;
  roleId: string;
  branchIds: string[];
};
