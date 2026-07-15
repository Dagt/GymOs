package com.gymos.sales.api.operations;

public interface SalesOperations {
	MembershipSaleResult createMembershipRenewal(CreateMembershipRenewalSaleCommand command);

	MembershipSaleResult createMembershipPlanChange(CreateMembershipPlanChangeSaleCommand command);
}
