package com.gymos.sales.model;

import java.util.UUID;

public record ProductForSale(UUID id, String name, String productType, long priceCents, String currency) {
}
