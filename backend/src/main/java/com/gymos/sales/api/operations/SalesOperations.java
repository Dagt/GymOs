package com.gymos.sales.api.operations;

public interface SalesOperations {
	MembershipSaleResult createMembershipEnrollment(CreateMembershipEnrollmentSaleCommand command);

	MembershipSaleResult createMembershipRenewal(CreateMembershipRenewalSaleCommand command);

	MembershipSaleResult createMembershipPlanChange(CreateMembershipPlanChangeSaleCommand command);

	VisitPackageSaleResult createVisitPackagePurchase(CreateVisitPackageSaleCommand command);
}
