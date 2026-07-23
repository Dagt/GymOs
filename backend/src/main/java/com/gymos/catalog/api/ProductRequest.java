package com.gymos.catalog.api;

import com.gymos.catalog.application.command.ProductCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
		@NotBlank @Size(max = 64) String sku,
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description,
		@NotBlank @Pattern(regexp = "PHYSICAL|SERVICE|MEMBERSHIP_PLAN") String productType,
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
		@Size(max = 500) String imageObjectKey,
		UUID categoryId,
		@Size(max = 20) List<@Size(max = 80) String> tags
) {
	ProductCommand toCommand() {
		return new ProductCommand(
				sku, name, description, productType, priceCents, currency, taxable, status,
				barcode, brand, presentation, unitOfMeasure, inventoryTracked, displayOrder,
				imageObjectKey, categoryId, tags
		);
	}
}
