package com.gymos.sales.application.result;

import com.gymos.sales.model.PaymentMethodBreakdown;
import com.gymos.sales.model.PaymentReportTotals;
import com.gymos.sales.model.SaleBusinessContextBreakdown;
import com.gymos.sales.model.SaleStatusBreakdown;
import com.gymos.sales.model.SaleSummary;
import com.gymos.sales.model.SalesReportTotals;
import java.time.LocalDate;
import java.util.List;

public record SalesReportResult(
		LocalDate fromDate,
		LocalDate toDate,
		String currency,
		SalesReportTotals sales,
		PaymentReportTotals payments,
		List<SaleStatusBreakdown> salesByStatus,
		List<SaleBusinessContextBreakdown> salesByBusinessContext,
		List<PaymentMethodBreakdown> paymentsByMethod,
		List<SaleSummary> recentSales
) {
}
