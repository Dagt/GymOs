package com.gymos.sales.application;

import com.gymos.common.api.BusinessException;
import com.gymos.sales.application.result.SalesReportResult;
import com.gymos.sales.infrastructure.SalesReadRepository;
import com.gymos.sales.infrastructure.SalesReportRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SalesReportingQuery {
	private final SalesReadRepository salesReadRepository;
	private final SalesReportRepository salesReportRepository;

	SalesReportingQuery(SalesReadRepository salesReadRepository, SalesReportRepository salesReportRepository) {
		this.salesReadRepository = salesReadRepository;
		this.salesReportRepository = salesReportRepository;
	}

	SalesReportResult report(UUID organizationId, UUID branchId, LocalDate fromDate, LocalDate toDate) {
		LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
		LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusDays(6) : fromDate;
		if (resolvedFrom.isAfter(resolvedTo)) {
			throw new BusinessException("REPORT_DATE_RANGE_INVALID", "La fecha inicial no puede ser posterior a la fecha final.");
		}
		if (resolvedFrom.plusDays(370).isBefore(resolvedTo)) {
			throw new BusinessException("REPORT_DATE_RANGE_TOO_LARGE", "El reporte no puede cubrir mas de 370 dias.");
		}

		OffsetDateTime from = resolvedFrom.atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime toExclusive = resolvedTo.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
		var sales = salesReportRepository.salesTotals(organizationId, branchId, from, toExclusive);
		return new SalesReportResult(
				resolvedFrom,
				resolvedTo,
				sales.currency(),
				sales,
				salesReportRepository.paymentTotals(organizationId, branchId, from, toExclusive),
				salesReportRepository.salesByStatus(organizationId, branchId, from, toExclusive),
				salesReportRepository.paymentsByMethod(organizationId, branchId, from, toExclusive),
				salesReadRepository.recentSales(organizationId, branchId)
		);
	}
}
