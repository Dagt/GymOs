package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.sales.api.events.PaymentCompletedLineItemV1;
import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.api.events.PaymentRecordedV1;
import com.gymos.sales.api.events.PaymentRejectedV1;
import com.gymos.sales.api.events.SaleCreatedV1;
import com.gymos.sales.api.operations.CreateMembershipPlanChangeSaleCommand;
import com.gymos.sales.api.operations.CreateMembershipRenewalSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.application.command.CreateSaleCommand;
import com.gymos.sales.application.command.ManualPaymentCommand;
import com.gymos.sales.application.command.RequestRefundCommand;
import com.gymos.sales.application.result.SaleDetailResult;
import com.gymos.sales.application.result.SalesReportResult;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.ProductForSale;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import com.gymos.sales.infrastructure.SalesRepository.SaleSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalesService implements SalesOperations {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final JdbcClient jdbcClient;
	private final SalesRepository salesRepository;
	private final AuditService auditService;
	private final ObjectStorageService objectStorageService;
	private final ApplicationEventPublisher eventPublisher;

	public SalesService(
			JdbcClient jdbcClient,
			SalesRepository salesRepository,
			AuditService auditService,
			ObjectStorageService objectStorageService,
			ApplicationEventPublisher eventPublisher
	) {
		this.jdbcClient = jdbcClient;
		this.salesRepository = salesRepository;
		this.auditService = auditService;
		this.objectStorageService = objectStorageService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public List<SaleSummary> recent(GymOsPrincipal principal) {
		requireBranch(principal);
		return salesRepository.recentSales(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public SalesReportResult report(GymOsPrincipal principal, LocalDate fromDate, LocalDate toDate) {
		requireBranch(principal);
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
		var sales = salesRepository.salesReportTotals(principal.organizationId(), principal.branchId(), from, toExclusive);
		return new SalesReportResult(
				resolvedFrom,
				resolvedTo,
				sales.currency(),
				sales,
				salesRepository.paymentReportTotals(principal.organizationId(), principal.branchId(), from, toExclusive),
				salesRepository.salesByStatus(principal.organizationId(), principal.branchId(), from, toExclusive),
				salesRepository.paymentsByMethod(principal.organizationId(), principal.branchId(), from, toExclusive),
				salesRepository.recentSales(principal.organizationId(), principal.branchId())
		);
	}

	@Transactional(readOnly = true)
	public SaleDetailResult detail(GymOsPrincipal principal, UUID saleId) {
		requirePrincipal(principal);
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		return new SaleDetailResult(
				sale,
				salesRepository.saleItems(principal.organizationId(), saleId),
				salesRepository.payments(principal.organizationId(), saleId),
				salesRepository.refunds(principal.organizationId(), saleId),
				salesRepository.membershipsForSale(principal.organizationId(), saleId)
		);
	}

	@Transactional
	public SaleDetailResult create(GymOsPrincipal principal, CreateSaleCommand request, String correlationId) {
		requireBranch(principal);
		String idempotencyHash = saleRequestHash(request);
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			String key = request.idempotencyKey().trim();
			var existing = salesRepository.saleHeaderByIdempotencyKey(principal.organizationId(), key);
			if (existing.isPresent()) {
				ensureSameIdempotencyRequest(
						salesRepository.saleRequestHashByIdempotencyKey(principal.organizationId(), key).orElse(null),
						idempotencyHash
				);
				return detail(principal, existing.get().id());
			}
		}
		if (request.clientId() != null && !salesRepository.clientExists(principal.organizationId(), request.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		if (request.items().isEmpty()) {
			throw new BusinessException("SALE_EMPTY", "La venta necesita al menos un item.");
		}

		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		long subtotal = 0;
		long itemDiscounts = 0;
		String currency = null;
		boolean containsMembershipPlan = false;

		for (var item : request.items()) {
			ProductForSale product = salesRepository.productForSale(principal.organizationId(), item.productId())
					.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Un producto no existe o no esta activo."));
			if ("MEMBERSHIP_PLAN".equals(product.productType())) {
				containsMembershipPlan = true;
			}
			if (currency == null) {
				currency = product.currency();
			}
			if (!currency.equals(product.currency())) {
				throw new BusinessException("SALE_MIXED_CURRENCY", "Todos los items deben usar la misma moneda.");
			}
			long lineSubtotal = product.priceCents() * item.quantity();
			long discount = Math.max(0, item.discountCents());
			if (discount > lineSubtotal) {
				throw new BusinessException("SALE_ITEM_DISCOUNT_INVALID", "El descuento de un item no puede superar su subtotal.");
			}
			subtotal += lineSubtotal;
			itemDiscounts += discount;
		}

		long headerDiscount = Math.max(0, request.discountCents());
		long totalDiscount = itemDiscounts + headerDiscount;
		if (totalDiscount > subtotal) {
			throw new BusinessException("SALE_DISCOUNT_INVALID", "El descuento total no puede superar el subtotal.");
		}
		if (containsMembershipPlan && request.clientId() == null) {
			throw new BusinessException("SALE_CLIENT_REQUIRED_FOR_PLAN", "Una venta de membresia necesita un cliente.");
		}
		long total = subtotal - totalDiscount;

		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PENDING_PAYMENT',
				    :subtotalCents, :discountCents, :totalCents, 0, :balanceCents,
				    :currency, :notes, :soldAt, :createdAt, :updatedAt, :createdBy, :updatedBy, 0,
				    :idempotencyKey, :idempotencyRequestHash
				)
				""")
				.param("id", saleId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("clientId", request.clientId())
				.param("saleNumber", saleNumber)
				.param("subtotalCents", subtotal)
				.param("discountCents", totalDiscount)
				.param("totalCents", total)
				.param("balanceCents", total)
				.param("currency", currency)
				.param("notes", blankToNull(request.notes()))
				.param("soldAt", now)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.param("idempotencyKey", blankToNull(request.idempotencyKey()))
				.param("idempotencyRequestHash", idempotencyHash)
				.update();

		for (var item : request.items()) {
			ProductForSale product = salesRepository.productForSale(principal.organizationId(), item.productId()).orElseThrow();
			long lineSubtotal = product.priceCents() * item.quantity();
			long discount = Math.max(0, item.discountCents());
			jdbcClient.sql("""
					INSERT INTO sale_item (
					    id, sale_id, organization_id, product_id, item_type, name, quantity,
					    unit_price_cents, discount_cents, total_cents, created_at
					)
					VALUES (
					    :id, :saleId, :organizationId, :productId, :itemType, :name, :quantity,
					    :unitPriceCents, :discountCents, :totalCents, :createdAt
					)
					""")
					.param("id", UUID.randomUUID())
					.param("saleId", saleId)
					.param("organizationId", principal.organizationId())
					.param("productId", product.id())
					.param("itemType", product.productType())
					.param("name", product.name())
					.param("quantity", item.quantity())
					.param("unitPriceCents", product.priceCents())
					.param("discountCents", discount)
					.param("totalCents", lineSubtotal - discount)
					.param("createdAt", now)
					.update();
		}

		auditService.record(principal, "SALE_CREATED", "SALE", saleId, "POS", null, correlationId);
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, principal.organizationId(), principal.branchId(), saleId, saleNumber, null, total, currency, now));
		if (request.payment() != null) {
			addPaymentInternal(principal, saleId, request.payment(), correlationId);
		}
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Override
	@Transactional
	public com.gymos.sales.api.operations.MembershipSaleResult createMembershipRenewal(CreateMembershipRenewalSaleCommand command) {
		return createMembershipSale(
				command.organizationId(),
				command.branchId(),
				command.actorUserId(),
				command.clientId(),
				command.sourceMembershipId(),
				command.planId(),
				command.productId(),
				command.planName(),
				command.priceCents(),
				command.currency(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				command.idempotencyKey(),
				"MEMBERSHIP_RENEWAL",
				command.correlationId()
		);
	}

	@Override
	@Transactional
	public com.gymos.sales.api.operations.MembershipSaleResult createMembershipPlanChange(CreateMembershipPlanChangeSaleCommand command) {
		return createMembershipSale(
				command.organizationId(),
				command.branchId(),
				command.actorUserId(),
				command.clientId(),
				command.sourceMembershipId(),
				command.targetPlanId(),
				command.productId(),
				command.planName(),
				command.priceCents(),
				command.currency(),
				command.method(),
				command.reference(),
				command.validateNow(),
				command.notes(),
				command.idempotencyKey(),
				"MEMBERSHIP_PLAN_CHANGE",
				command.correlationId()
		);
	}

	private com.gymos.sales.api.operations.MembershipSaleResult createMembershipSale(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			UUID clientId,
			UUID sourceMembershipId,
			UUID targetPlanId,
			UUID productId,
			String planName,
			long priceCents,
			String currency,
			String method,
			String reference,
			boolean validateNow,
			String notes,
			String idempotencyKey,
			String businessContext,
			String correlationId
	) {
		String idempotencyHash = membershipSaleRequestHash(clientId, sourceMembershipId, targetPlanId, productId, priceCents, currency, method, reference, validateNow, notes, businessContext);
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			String key = idempotencyKey.trim();
			var existing = salesRepository.saleHeaderByIdempotencyKey(organizationId, key);
			if (existing.isPresent()) {
				ensureSameIdempotencyRequest(
						salesRepository.saleRequestHashByIdempotencyKey(organizationId, key).orElse(null),
						idempotencyHash
				);
				return toMembershipSaleResult(existing.get());
			}
		}

		OffsetDateTime now = OffsetDateTime.now();
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		String saleNumber = "S-" + now.format(SALE_NUMBER_FORMAT) + "-" + saleId.toString().substring(0, 6).toUpperCase();
		String normalizedMethod = method.trim().toUpperCase();
		boolean completed = validateNow || "CASH".equals(normalizedMethod);
		String paymentStatus = completed ? "COMPLETED" : "PENDING_VALIDATION";
		String saleStatus = completed ? "PAID" : "PENDING_PAYMENT";
		long paidCents = completed ? priceCents : 0;
		long balanceCents = completed ? 0 : priceCents;

		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash, business_context, context_membership_id, context_target_plan_id
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, :status,
				    :subtotalCents, 0, :totalCents, :paidCents, :balanceCents,
				    :currency, :notes, :soldAt, :createdAt, :updatedAt, :createdBy, :updatedBy, 0,
				    :idempotencyKey, :idempotencyRequestHash, :businessContext, :contextMembershipId, :contextTargetPlanId
				)
				""")
				.param("id", saleId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("clientId", clientId)
				.param("saleNumber", saleNumber)
				.param("status", saleStatus)
				.param("subtotalCents", priceCents)
				.param("totalCents", priceCents)
				.param("paidCents", paidCents)
				.param("balanceCents", balanceCents)
				.param("currency", currency)
				.param("notes", blankToNull(notes))
				.param("soldAt", now)
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.param("idempotencyKey", blankToNull(idempotencyKey))
				.param("idempotencyRequestHash", idempotencyHash)
				.param("businessContext", businessContext)
				.param("contextMembershipId", sourceMembershipId)
				.param("contextTargetPlanId", targetPlanId)
				.update();

		jdbcClient.sql("""
				INSERT INTO sale_item (
				    id, sale_id, organization_id, product_id, item_type, name, quantity,
				    unit_price_cents, discount_cents, total_cents, created_at
				)
				VALUES (
				    :id, :saleId, :organizationId, :productId, 'MEMBERSHIP_PLAN', :name, 1,
				    :unitPriceCents, 0, :totalCents, :createdAt
				)
				""")
				.param("id", UUID.randomUUID())
				.param("saleId", saleId)
				.param("organizationId", organizationId)
				.param("productId", productId)
				.param("name", planName)
				.param("unitPriceCents", priceCents)
				.param("totalCents", priceCents)
				.param("createdAt", now)
				.update();

		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :amountCents, :currency, :reference, NULL, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0,
				    :idempotencyKey, :idempotencyRequestHash
				)
				""")
				.param("id", paymentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("saleId", saleId)
				.param("clientId", clientId)
				.param("method", normalizedMethod)
				.param("status", paymentStatus)
				.param("amountCents", priceCents)
				.param("currency", currency)
				.param("reference", blankToNull(reference))
				.param("receivedAt", now)
				.param("validatedAt", completed ? now : null)
				.param("validatedBy", completed ? actorUserId : null)
				.param("notes", blankToNull(notes))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.param("idempotencyKey", blankToNull(idempotencyKey == null ? null : idempotencyKey + ":payment"))
				.param("idempotencyRequestHash", paymentRequestHash(method, priceCents, reference, null, validateNow, notes))
				.update();

		auditService.recordSystem(organizationId, branchId, actorUserId, "SALE_CREATED", "SALE", saleId, businessContext, null, correlationId);
		auditService.recordSystem(organizationId, branchId, actorUserId, "PAYMENT_RECORDED", "PAYMENT", paymentId, businessContext, null, correlationId);
		eventPublisher.publishEvent(new SaleCreatedV1("sale-created:" + saleId, organizationId, branchId, saleId, saleNumber, businessContext, priceCents, currency, now));
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, organizationId, branchId, saleId, paymentId, normalizedMethod, paymentStatus, priceCents, currency, now));
		if (completed) {
			publishPaymentCompletedIfNeeded(organizationId, saleId);
		}
		SaleHeader sale = salesRepository.saleHeader(organizationId, saleId).orElseThrow();
		return toMembershipSaleResult(sale);
	}

	@Transactional
	public SaleDetailResult addPayment(
			GymOsPrincipal principal,
			UUID saleId,
			ManualPaymentCommand request,
			String correlationId
	) {
		requireBranch(principal);
		ensureSale(principal, saleId);
		addPaymentInternal(principal, saleId, request, correlationId);
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResult uploadReceipt(
			GymOsPrincipal principal,
			UUID paymentId,
			MultipartFile file,
			String correlationId
	) {
		requireBranch(principal);
		if (file == null || file.isEmpty()) {
			throw new BusinessException("PAYMENT_RECEIPT_REQUIRED", "Selecciona un archivo de comprobante.");
		}
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		SaleHeader sale = ensureSale(principal, saleId);
		String objectKey = receiptObjectKey(principal.organizationId(), saleId, paymentId, file.getOriginalFilename());
		try {
			objectStorageService.putReceipt(objectKey, file.getContentType(), file.getSize(), file.getInputStream());
		} catch (IOException exception) {
			throw new BusinessException("PAYMENT_RECEIPT_READ_FAILED", "No fue posible leer el comprobante.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE payment
				SET receipt_object_key = :receiptObjectKey,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND sale_id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("receiptObjectKey", objectKey)
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("paymentId", paymentId)
				.param("saleId", sale.id())
				.update();
		auditService.record(principal, "PAYMENT_RECEIPT_UPLOADED", "PAYMENT", paymentId, "POS", null, correlationId);
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResult validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
		requireBranch(principal);
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		PaymentDetail payment = salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		if ("COMPLETED".equals(payment.status())) {
			return detail(principal, saleId);
		}
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE payment
				SET status = 'COMPLETED',
				    validated_at = :validatedAt,
				    validated_by = :validatedBy,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("validatedAt", now)
				.param("validatedBy", principal.userId())
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("paymentId", paymentId)
				.update();
		auditService.record(principal, "PAYMENT_VALIDATED", "PAYMENT", paymentId, "POS", null, correlationId);
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResult rejectPayment(
			GymOsPrincipal principal,
			UUID paymentId,
			String reason,
			String correlationId
	) {
		requireBranch(principal);
		UUID saleId = salesRepository.saleIdForPayment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		PaymentDetail payment = salesRepository.payment(principal.organizationId(), paymentId)
				.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
		if ("COMPLETED".equals(payment.status())) {
			throw new BusinessException("PAYMENT_ALREADY_COMPLETED", "No puedes rechazar un pago completado.");
		}
		if ("REJECTED".equals(payment.status())) {
			return detail(principal, saleId);
		}
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE payment
				SET status = 'REJECTED',
				    notes = :notes,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :paymentId
				  AND deleted_at IS NULL
				""")
				.param("notes", blankToNull(reason))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("paymentId", paymentId)
				.update();
		auditService.record(principal, "PAYMENT_REJECTED", "PAYMENT", paymentId, "POS", reason, correlationId);
		eventPublisher.publishEvent(new PaymentRejectedV1("payment-rejected:" + paymentId, principal.organizationId(), principal.branchId(), saleId, paymentId, reason, OffsetDateTime.now()));
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResult requestRefund(
			GymOsPrincipal principal,
			UUID saleId,
			RequestRefundCommand request,
			String correlationId
	) {
		requireBranch(principal);
		SaleHeader sale = ensureSale(principal, saleId);
		if (!principal.branchId().equals(sale.branchId())) {
			throw new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible en esta sucursal.");
		}
		if (request.amountCents() <= 0) {
			throw new BusinessException("REFUND_AMOUNT_INVALID", "El reembolso debe ser mayor a cero.");
		}
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			String key = request.idempotencyKey().trim();
			var existing = salesRepository.refundByIdempotencyKey(principal.organizationId(), key);
			if (existing.isPresent()) {
				var refund = existing.get();
				if (!refund.saleId().equals(saleId)
						|| refund.amountCents() != request.amountCents()
						|| (request.paymentId() != null && !request.paymentId().equals(refund.paymentId()))) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otro reembolso.");
				}
				return detail(principal, refund.saleId());
			}
		}
		if (request.paymentId() != null) {
			PaymentDetail payment = salesRepository.payment(principal.organizationId(), request.paymentId())
					.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
			UUID paymentSaleId = salesRepository.saleIdForPayment(principal.organizationId(), request.paymentId())
					.orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "El pago no existe o no esta disponible."));
			if (!paymentSaleId.equals(saleId)) {
				throw new BusinessException("PAYMENT_SALE_MISMATCH", "El pago no pertenece a la venta.");
			}
			if (!"COMPLETED".equals(payment.status())) {
				throw new BusinessException("PAYMENT_NOT_COMPLETED", "Solo puedes reembolsar pagos completados.");
			}
			if (request.amountCents() > payment.amountCents()) {
				throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "El reembolso no puede superar el pago seleccionado.");
			}
		}
		long alreadyRefunded = salesRepository.processedRefundTotal(principal.organizationId(), saleId);
		long refundable = sale.paidCents() - alreadyRefunded;
		if (request.amountCents() > refundable) {
			throw new BusinessException("REFUND_EXCEEDS_REFUNDABLE_BALANCE", "El reembolso supera el saldo pagado disponible.");
		}
		UUID refundId = salesRepository.createProcessedRefund(
				principal.organizationId(),
				principal.branchId(),
				saleId,
				request.paymentId(),
				request.amountCents(),
				sale.currency(),
				request.reason(),
				blankToNull(request.idempotencyKey()),
				principal.userId()
		);
		auditService.record(principal, "SALE_REFUND_PROCESSED", "SALE_REFUND", refundId, "POS", request.reason(), correlationId);
		return detail(principal, saleId);
	}

	private void addPaymentInternal(
			GymOsPrincipal principal,
			UUID saleId,
			ManualPaymentCommand request,
			String correlationId
	) {
		SaleHeader sale = ensureSale(principal, saleId);
		String idempotencyHash = paymentRequestHash(
				request.method(),
				request.amountCents(),
				request.reference(),
				request.receiptObjectKey(),
				request.validateNow(),
				request.notes()
		);
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			String key = request.idempotencyKey().trim();
			UUID existingSaleId = paymentSaleByIdempotencyKey(principal.organizationId(), key);
			if (existingSaleId != null) {
				if (!existingSaleId.equals(saleId)) {
					throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada en otra venta.");
				}
				ensureSameIdempotencyRequest(
						salesRepository.paymentRequestHashByIdempotencyKey(principal.organizationId(), key).orElse(null),
						idempotencyHash
				);
				return;
			}
		}
		if (request.amountCents() <= 0) {
			throw new BusinessException("PAYMENT_AMOUNT_INVALID", "El pago debe ser mayor a cero.");
		}
		String method = request.method().trim().toUpperCase();
		String status = request.validateNow() || "CASH".equals(method) ? "COMPLETED" : "PENDING_VALIDATION";
		OffsetDateTime now = OffsetDateTime.now();
		UUID paymentId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO payment (
				    id, organization_id, branch_id, sale_id, client_id, method, status,
				    amount_cents, currency, reference, receipt_object_key, received_at,
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version,
				    idempotency_key, idempotency_request_hash
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :amountCents, :currency, :reference, :receiptObjectKey, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0,
				    :idempotencyKey, :idempotencyRequestHash
				)
				""")
				.param("id", paymentId)
				.param("organizationId", principal.organizationId())
				.param("branchId", principal.branchId())
				.param("saleId", saleId)
				.param("clientId", sale.clientId())
				.param("method", method)
				.param("status", status)
				.param("amountCents", request.amountCents())
				.param("currency", sale.currency())
				.param("reference", blankToNull(request.reference()))
				.param("receiptObjectKey", blankToNull(request.receiptObjectKey()))
				.param("receivedAt", now)
				.param("validatedAt", "COMPLETED".equals(status) ? now : null)
				.param("validatedBy", "COMPLETED".equals(status) ? principal.userId() : null)
				.param("notes", blankToNull(request.notes()))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.param("idempotencyKey", blankToNull(request.idempotencyKey()))
				.param("idempotencyRequestHash", idempotencyHash)
				.update();
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "POS", null, correlationId);
		eventPublisher.publishEvent(new PaymentRecordedV1("payment-recorded:" + paymentId, principal.organizationId(), principal.branchId(), saleId, paymentId, method, status, request.amountCents(), sale.currency(), now));
	}

	private UUID paymentSaleByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return jdbcClient.sql("""
				SELECT sale_id
				FROM payment
				WHERE organization_id = :organizationId
				  AND idempotency_key = :idempotencyKey
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("idempotencyKey", idempotencyKey)
				.query(UUID.class)
				.optional()
				.orElse(null);
	}

	private void recalculateSale(UUID organizationId, UUID saleId, UUID userId) {
		SaleHeader sale = salesRepository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		Long paid = jdbcClient.sql("""
				SELECT COALESCE(SUM(amount_cents), 0)
				FROM payment
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				  AND status = 'COMPLETED'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query(Long.class)
				.single();
		long balance = Math.max(0, sale.totalCents() - paid);
		String status = paid >= sale.totalCents() ? "PAID" : (paid > 0 ? "PARTIALLY_PAID" : "PENDING_PAYMENT");
		jdbcClient.sql("""
				UPDATE sale
				SET status = :status,
				    paid_cents = :paidCents,
				    balance_cents = :balanceCents,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("paidCents", paid)
				.param("balanceCents", balance)
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", userId)
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.update();
		if ("PAID".equals(status)) {
			publishPaymentCompletedIfNeeded(organizationId, saleId);
		}
	}

	private void publishPaymentCompletedIfNeeded(UUID organizationId, UUID saleId) {
		OffsetDateTime now = OffsetDateTime.now();
		int updated = jdbcClient.sql("""
				UPDATE sale
				SET payment_completed_at = :paymentCompletedAt
				WHERE organization_id = :organizationId
				  AND id = :saleId
				  AND payment_completed_at IS NULL
				  AND deleted_at IS NULL
				""")
				.param("paymentCompletedAt", now)
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.update();
		if (updated == 0) {
			return;
		}
		eventPublisher.publishEvent(paymentCompletedEvent(organizationId, saleId, now));
	}

	private PaymentCompletedV1 paymentCompletedEvent(UUID organizationId, UUID saleId, OffsetDateTime occurredAt) {
		SaleHeader sale = salesRepository.saleHeader(organizationId, saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		SaleContext context = saleContext(organizationId, saleId);
		return new PaymentCompletedV1(
				"payment-completed:" + saleId,
				organizationId,
				sale.branchId(),
				saleId,
				sale.clientId(),
				sale.saleNumber(),
				context.businessContext(),
				context.contextMembershipId(),
				context.contextTargetPlanId(),
				paymentCompletedItems(organizationId, saleId),
				occurredAt
		);
	}

	private SaleContext saleContext(UUID organizationId, UUID saleId) {
		return jdbcClient.sql("""
				SELECT business_context, context_membership_id, context_target_plan_id
				FROM sale
				WHERE organization_id = :organizationId
				  AND id = :saleId
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new SaleContext(
						resultSet.getString("business_context"),
						resultSet.getObject("context_membership_id", UUID.class),
						resultSet.getObject("context_target_plan_id", UUID.class)
				))
				.single();
	}

	private List<PaymentCompletedLineItemV1> paymentCompletedItems(UUID organizationId, UUID saleId) {
		return new ArrayList<>(jdbcClient.sql("""
				SELECT product_id, item_type, quantity
				FROM sale_item
				WHERE organization_id = :organizationId
				  AND sale_id = :saleId
				""")
				.param("organizationId", organizationId)
				.param("saleId", saleId)
				.query((resultSet, rowNumber) -> new PaymentCompletedLineItemV1(
						resultSet.getObject("product_id", UUID.class),
						resultSet.getString("item_type"),
						resultSet.getInt("quantity")
				))
				.list());
	}

	private com.gymos.sales.api.operations.MembershipSaleResult toMembershipSaleResult(SaleHeader sale) {
		return new com.gymos.sales.api.operations.MembershipSaleResult(
				sale.id(),
				sale.saleNumber(),
				sale.status(),
				sale.totalCents(),
				sale.paidCents(),
				sale.balanceCents(),
				sale.currency(),
				"PAID".equals(sale.status())
		);
	}

	private record SaleContext(String businessContext, UUID contextMembershipId, UUID contextTargetPlanId) {
	}

	private SaleHeader ensureSale(GymOsPrincipal principal, UUID saleId) {
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		if (!sale.branchId().equals(principal.branchId())) {
			throw new BusinessException("SALE_BRANCH_MISMATCH", "La venta pertenece a otra sucursal.");
		}
		return sale;
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private void requireBranch(GymOsPrincipal principal) {
		requirePrincipal(principal);
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void ensureSameIdempotencyRequest(String existingHash, String requestedHash) {
		if (existingHash != null && requestedHash != null && !existingHash.equals(requestedHash)) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave de idempotencia ya fue usada con una solicitud diferente.");
		}
	}

	private String saleRequestHash(CreateSaleCommand request) {
		StringBuilder value = new StringBuilder()
				.append("client=").append(request.clientId()).append('|')
				.append("discount=").append(request.discountCents()).append('|')
				.append("notes=").append(normalized(request.notes())).append('|');
		request.items().stream()
				.sorted(java.util.Comparator.comparing(item -> item.productId().toString()))
				.forEach(item -> value
						.append("item=").append(item.productId())
						.append(',').append(item.quantity())
						.append(',').append(item.discountCents())
						.append('|'));
		if (request.payment() != null) {
			value.append("payment=").append(paymentRequestHash(
					request.payment().method(),
					request.payment().amountCents(),
					request.payment().reference(),
					request.payment().receiptObjectKey(),
					request.payment().validateNow(),
					request.payment().notes()
			));
		}
		return sha256(value.toString());
	}

	private String membershipSaleRequestHash(
			UUID clientId,
			UUID sourceMembershipId,
			UUID targetPlanId,
			UUID productId,
			long priceCents,
			String currency,
			String method,
			String reference,
			boolean validateNow,
			String notes,
			String businessContext
	) {
		return sha256(String.join("|",
				"client=" + clientId,
				"sourceMembership=" + sourceMembershipId,
				"targetPlan=" + targetPlanId,
				"product=" + productId,
				"price=" + priceCents,
				"currency=" + normalized(currency),
				"method=" + normalized(method).toUpperCase(),
				"reference=" + normalized(reference),
				"validateNow=" + validateNow,
				"notes=" + normalized(notes),
				"context=" + businessContext
		));
	}

	private String paymentRequestHash(
			String method,
			long amountCents,
			String reference,
			String receiptObjectKey,
			boolean validateNow,
			String notes
	) {
		return sha256(String.join("|",
				"method=" + normalized(method).toUpperCase(),
				"amount=" + amountCents,
				"reference=" + normalized(reference),
				"receipt=" + normalized(receiptObjectKey),
				"validateNow=" + validateNow,
				"notes=" + normalized(notes)
		));
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}

	private String normalized(String value) {
		return value == null ? "" : value.trim();
	}

	private String receiptObjectKey(UUID organizationId, UUID saleId, UUID paymentId, String originalFilename) {
		String filename = originalFilename == null || originalFilename.isBlank() ? "receipt.bin" : originalFilename;
		String safeFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
		return "receipts/" + organizationId + "/" + saleId + "/" + paymentId + "/" + System.currentTimeMillis() + "-" + safeFilename;
	}
}
