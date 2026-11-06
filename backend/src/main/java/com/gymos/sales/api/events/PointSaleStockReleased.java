package com.gymos.sales.api.events;

import java.util.UUID;

public record PointSaleStockReleased(UUID organizationId, UUID branchId, UUID saleId) {}
