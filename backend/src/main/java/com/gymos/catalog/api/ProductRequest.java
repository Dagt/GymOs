package com.gymos.catalog.api;

import com.gymos.catalog.application.command.ProductCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
		@NotBlank @Size(max = 64) String sku,
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description,
		@NotBlank @Pattern(regexp = "PHYSICAL|SERVICE") String productType,
		@Pattern(regexp = "GENERAL_PRODUCT|GENERAL_SERVICE|VISIT_PASS|BOOKING|ROUTINE|CONSULTATION|WELLNESS_SERVICE") String commercialSubtype,
		@Min(0) long priceCents,
		@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
		boolean taxable,
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
		@Size(max = 128) String barcode,
		@Size(max = 160) String brand,
		@Size(max = 160) String presentation,
		@Size(max = 40) String unitOfMeasure,
		boolean inventoryTracked,
		@Min(0) int displayOrder,
		@Size(max = 160) String supplierName,
		@Min(0) Long referenceCostCents,
		@Min(0) Long promotionalPriceCents,
		OffsetDateTime promotionStartsAt,
		OffsetDateTime promotionEndsAt,
		UUID categoryId,
		@Size(max = 20) List<@Size(max = 80) String> tags,
		@Min(0) Long version
) {
	ProductCommand toCommand() {
		return new ProductCommand(
				sku, name, description, productType, resolvedSubtype(), priceCents, currency, taxable, status,
				barcode, brand, presentation, unitOfMeasure, inventoryTracked, displayOrder,
				supplierName, referenceCostCents, promotionalPriceCents, promotionStartsAt, promotionEndsAt,
				categoryId, tags, version
		);
	}

	private String resolvedSubtype() {
		if (commercialSubtype != null && !commercialSubtype.isBlank()) return commercialSubtype;
		if ("SERVICE".equals(productType)) return "GENERAL_SERVICE";
		return "GENERAL_PRODUCT";
	}
}
