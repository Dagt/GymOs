package com.gymos.payments.api;

import java.util.UUID;

public interface CardPresentGateway {
    String provider();
    boolean available(UUID organizationId, UUID branchId);
    CreateOrderResult createOrder(CreateOrderRequest request);
    OrderStatusResult getOrder(UUID organizationId, UUID branchId, String externalOrderId);
    void cancel(UUID organizationId, UUID branchId, String externalOrderId);

    record CreateOrderRequest(
            UUID organizationId,
            UUID branchId,
            UUID saleId,
            long amountCents,
            String currency,
            String description,
            String idempotencyKey,
            String terminalId
    ) {}

    record CreateOrderResult(String externalOrderId, String status, String terminalId) {}
    record OrderStatusResult(String externalOrderId, String status, long amountCents, String currency) {}
}
