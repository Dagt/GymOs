package com.gymos.sales.api.events;

import java.util.List;
import java.util.UUID;

public record PointSaleStockRequested(UUID organizationId, UUID branchId, UUID saleId, List<PaymentCompletedLineItemV1> items) {}
