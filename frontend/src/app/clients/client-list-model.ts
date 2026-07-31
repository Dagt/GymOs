type ClientMembershipProjection = {
  status: string;
  membershipStatus: string | null;
};

export function clientMembershipStatus(client: ClientMembershipProjection) {
  if (client.status !== "ACTIVE") {
    return {
      label: "INACTIVO",
      className: "border-[#E7B9B2] bg-[#F3D9D5] text-[#8A3F38]",
    };
  }
  if (client.membershipStatus === "ACTIVE") {
    return {
      label: "ACTIVA",
      className: "border-[#C9D7A3] bg-[#E4EDCF] text-[#53682F]",
    };
  }
  if (client.membershipStatus === "EXPIRED") {
    return {
      label: "VENCIDA",
      className: "border-[#E7B9B2] bg-[#F3D9D5] text-[#8A3F38]",
    };
  }
  return {
    label: "SIN MEMBRESÍA",
    className: "border-stone-200 bg-stone-100 text-stone-600",
  };
}
