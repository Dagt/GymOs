package com.gymos.sales.application.result;

import com.gymos.sales.model.AppliedBenefitDetail;
import com.gymos.sales.model.MembershipDetail;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import com.gymos.sales.model.SaleItemDetail;
import java.util.List;
import java.util.UUID;

public record SaleDetailResult(
		SaleHeader sale,
		List<SaleItemDetail> items,
		List<AppliedBenefitDetail> appliedBenefits,
		List<PaymentDetail> payments,
		List<RefundDetail> refunds,
		List<MembershipDetail> memberships,
		UUID operationPaymentId
) {
	public SaleDetailResult(SaleHeader sale, List<SaleItemDetail> items, List<AppliedBenefitDetail> appliedBenefits,
			List<PaymentDetail> payments, List<RefundDetail> refunds, List<MembershipDetail> memberships) {
		this(sale, items, appliedBenefits, payments, refunds, memberships, null);
	}

	public SaleDetailResult withOperationPayment(UUID paymentId) {
		return new SaleDetailResult(sale, items, appliedBenefits, payments, refunds, memberships, paymentId);
	}
}
