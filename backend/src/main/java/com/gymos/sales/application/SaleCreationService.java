package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.events.SaleCreatedV1;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.infrastructure.SaleCommandRepository;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleItemRecord;
import com.gymos.sales.infrastructure.SaleCommandRepository.SaleRecord;
import com.gymos.sales.infrastructure.SalesRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class SaleCreationService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private final SalesRepository salesRepository;
	private final SaleLineResolver saleLineResolver;
	private final SaleCommandRepository commandRepository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	SaleCreationService(SalesRepository salesRepository, SaleLineResolver saleLineResolver, SaleCommandRepository commandRepository,
			AuditService auditService, ApplicationEventPublisher eventPublisher) {
		this.salesRepository = salesRepository;
		this.saleLineResolver = saleLineResolver;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	CreationResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		String requestHash = SalesIdempotency.saleRequestHash(request);
		String idempotencyKey = SalesIdempotency.blankToNull(request.idempotencyKey());
		if (idempotencyKey != null) {
			var existing = salesRepository.saleHeaderByIdempotencyKey(principal.organizationId(), idempotencyKey);
			if (existing.isPresent()) {
				SalesIdempotency.ensureSameRequest(salesRepository.saleRequestHashByIdempotencyKey(principal.organizationId(), idempotencyKey).orElse(null), requestHash);
				return new CreationResult(existing.get().id(), false);
			}
		}
		if (request.clientId() != null && !salesRepository.clientExists(principal.organizationId(), request.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		var resolved = saleLineResolver.resolve(principal.organizationId(), request.clientId(), request.items(), request.discountCents());
		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		commandRepository.createSale(new SaleRecord(saleId, principal.organizationId(), principal.branchId(), request.clientId(), saleNumber,
				resolved.subtotalCents(), resolved.discountCents(), resolved.totalCents(), resolved.currency(),
				SalesIdempotency.blankToNull(request.notes()), now, principal.userId(), idempotencyKey, requestHash));
		commandRepository.createItems(principal.organizationId(), saleId, resolved.lines().stream()
				.map(line -> new SaleItemRecord(line.productId(), line.itemType(), line.name(), line.quantity(), line.unitPriceCents(), line.discountCents(), line.totalCents()))
				.toList(), now);
		auditService.record(principal, "SALE_CREATED", "SALE", saleId, "POS", null, correlationId);
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, principal.organizationId(), principal.branchId(), saleId,
				saleNumber, null, resolved.totalCents(), resolved.currency(), now));
		return new CreationResult(saleId, true);
	}

	record CreationResult(UUID saleId, boolean created) {
	}
}
