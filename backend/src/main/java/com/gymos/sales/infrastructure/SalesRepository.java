package com.gymos.sales.infrastructure;

import com.gymos.sales.model.MembershipPlanForSale;
import com.gymos.sales.model.PaymentDetail;
import com.gymos.sales.model.ProductForSale;
import com.gymos.sales.model.RefundDetail;
import com.gymos.sales.model.SaleHeader;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SalesRepository {
	private final SalesCatalogLookupRepository catalogLookup;
	private final SalesClientLookupRepository clientLookup;
	private final SalesRecordLookupRepository recordLookup;
	private final SalesIdempotencyLookupRepository idempotencyLookup;
	private final SalesPaymentLookupRepository paymentLookup;
	private final SalesRefundLookupRepository refundLookup;
	private final SalesMembershipLookupRepository membershipLookup;

	public SalesRepository(
			SalesCatalogLookupRepository catalogLookup,
			SalesClientLookupRepository clientLookup,
			SalesRecordLookupRepository recordLookup,
			SalesIdempotencyLookupRepository idempotencyLookup,
			SalesPaymentLookupRepository paymentLookup,
			SalesRefundLookupRepository refundLookup,
			SalesMembershipLookupRepository membershipLookup
	) {
		this.catalogLookup = catalogLookup;
		this.clientLookup = clientLookup;
		this.recordLookup = recordLookup;
		this.idempotencyLookup = idempotencyLookup;
		this.paymentLookup = paymentLookup;
		this.refundLookup = refundLookup;
		this.membershipLookup = membershipLookup;
	}

	public Optional<ProductForSale> productForSale(UUID organizationId, UUID productId) {
		return catalogLookup.productForSale(organizationId, productId);
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		return clientLookup.exists(organizationId, clientId);
	}

	public Optional<SaleHeader> saleHeader(UUID organizationId, UUID saleId) {
		return recordLookup.saleHeader(organizationId, saleId);
	}

	public Optional<SaleHeader> saleHeaderByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return recordLookup.saleHeaderByIdempotencyKey(organizationId, idempotencyKey);
	}

	public Optional<String> saleRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyLookup.saleRequestHashByIdempotencyKey(organizationId, idempotencyKey);
	}

	public Optional<String> paymentRequestHashByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return idempotencyLookup.paymentRequestHashByIdempotencyKey(organizationId, idempotencyKey);
	}

	public Optional<RefundDetail> refundByIdempotencyKey(UUID organizationId, String idempotencyKey) {
		return refundLookup.byIdempotencyKey(organizationId, idempotencyKey);
	}

	public long processedRefundTotal(UUID organizationId, UUID saleId) {
		return refundLookup.processedTotal(organizationId, saleId);
	}

	public Optional<PaymentDetail> payment(UUID organizationId, UUID paymentId) {
		return paymentLookup.payment(organizationId, paymentId);
	}

	public List<MembershipPlanForSale> membershipPlansForSale(UUID organizationId, UUID saleId) {
		return membershipLookup.membershipPlansForSale(organizationId, saleId);
	}

	public boolean membershipExistsForSalePlan(UUID organizationId, UUID saleId, UUID planId) {
		return membershipLookup.membershipExistsForSalePlan(organizationId, saleId, planId);
	}

	public Optional<LocalDate> latestMembershipEnd(UUID organizationId, UUID clientId, UUID planId) {
		return membershipLookup.latestMembershipEnd(organizationId, clientId, planId);
	}

	public Optional<UUID> saleIdForPayment(UUID organizationId, UUID paymentId) {
		return paymentLookup.saleIdForPayment(organizationId, paymentId);
	}
}
