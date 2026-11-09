package com.gymos.payments.api.events;

import java.util.UUID;

public record PointPaymentClosed(UUID organizationId, UUID branchId, UUID saleId, UUID paymentId, String status) {}
