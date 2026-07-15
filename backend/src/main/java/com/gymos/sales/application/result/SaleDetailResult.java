package com.gymos.sales.application.result;

import com.gymos.sales.infrastructure.SalesRepository.MembershipDetail;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import com.gymos.sales.infrastructure.SalesRepository.SaleItemDetail;
import java.util.List;

public record SaleDetailResult(
		SaleHeader sale,
		List<SaleItemDetail> items,
		List<PaymentDetail> payments,
		List<MembershipDetail> memberships
) {
}
