package com.gymos.sales.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.SalesController.CreateSaleRequest;
import com.gymos.sales.api.SalesController.ManualPaymentRequest;
import com.gymos.sales.api.SalesController.SaleDetailResponse;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.PaymentDetail;
import com.gymos.sales.infrastructure.SalesRepository.ProductForSale;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import com.gymos.sales.infrastructure.SalesRepository.SaleSummary;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesService {
	private static final DateTimeFormatter SALE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final JdbcClient jdbcClient;
	private final SalesRepository salesRepository;
	private final AuditService auditService;

	public SalesService(JdbcClient jdbcClient, SalesRepository salesRepository, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.salesRepository = salesRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<SaleSummary> recent(GymOsPrincipal principal) {
		requireBranch(principal);
		return salesRepository.recentSales(principal.organizationId(), principal.branchId());
	}

	@Transactional(readOnly = true)
	public SaleDetailResponse detail(GymOsPrincipal principal, UUID saleId) {
		requirePrincipal(principal);
		SaleHeader sale = salesRepository.saleHeader(principal.organizationId(), saleId)
				.orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible."));
		return new SaleDetailResponse(
				sale,
				salesRepository.saleItems(principal.organizationId(), saleId),
				salesRepository.payments(principal.organizationId(), saleId)
		);
	}

	@Transactional
	public SaleDetailResponse create(GymOsPrincipal principal, CreateSaleRequest request, String correlationId) {
		requireBranch(principal);
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

		for (var item : request.items()) {
			ProductForSale product = salesRepository.productForSale(principal.organizationId(), item.productId())
					.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Un producto no existe o no esta activo."));
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
		long total = subtotal - totalDiscount;

		jdbcClient.sql("""
				INSERT INTO sale (
				    id, organization_id, branch_id, client_id, sale_number, status,
				    subtotal_cents, discount_cents, total_cents, paid_cents, balance_cents,
				    currency, notes, sold_at, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :clientId, :saleNumber, 'PENDING_PAYMENT',
				    :subtotalCents, :discountCents, :totalCents, 0, :balanceCents,
				    :currency, :notes, :soldAt, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
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
		if (request.payment() != null) {
			addPaymentInternal(principal, saleId, request.payment(), correlationId);
		}
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResponse addPayment(
			GymOsPrincipal principal,
			UUID saleId,
			ManualPaymentRequest request,
			String correlationId
	) {
		requireBranch(principal);
		ensureSale(principal, saleId);
		addPaymentInternal(principal, saleId, request, correlationId);
		recalculateSale(principal.organizationId(), saleId, principal.userId());
		return detail(principal, saleId);
	}

	@Transactional
	public SaleDetailResponse validatePayment(GymOsPrincipal principal, UUID paymentId, String correlationId) {
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

	private void addPaymentInternal(
			GymOsPrincipal principal,
			UUID saleId,
			ManualPaymentRequest request,
			String correlationId
	) {
		SaleHeader sale = ensureSale(principal, saleId);
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
				    validated_at, validated_by, notes, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :saleId, :clientId, :method, :status,
				    :amountCents, :currency, :reference, :receiptObjectKey, :receivedAt,
				    :validatedAt, :validatedBy, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
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
				.update();
		auditService.record(principal, "PAYMENT_RECORDED", "PAYMENT", paymentId, "POS", null, correlationId);
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
}
