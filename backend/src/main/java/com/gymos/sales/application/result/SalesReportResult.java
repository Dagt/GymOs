package com.gymos.sales.application.result;

import com.gymos.sales.infrastructure.SalesRepository.PaymentMethodBreakdown;
import com.gymos.sales.infrastructure.SalesRepository.PaymentReportTotals;
import com.gymos.sales.infrastructure.SalesRepository.SaleStatusBreakdown;
import com.gymos.sales.infrastructure.SalesRepository.SaleSummary;
import com.gymos.sales.infrastructure.SalesRepository.SalesReportTotals;
import java.time.LocalDate;
import java.util.List;

public record SalesReportResult(
		LocalDate fromDate,
		LocalDate toDate,
		String currency,
		SalesReportTotals sales,
		PaymentReportTotals payments,
		List<SaleStatusBreakdown> salesByStatus,
		List<PaymentMethodBreakdown> paymentsByMethod,
		List<SaleSummary> recentSales
) {
}
