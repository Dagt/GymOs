package com.gymos.catalog.api;

import com.gymos.catalog.application.command.CreateProductVariantsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record CreateProductVariantsRequest(
		UUID familyId,
		@Size(max = 160) String familyName,
		@Size(max = 160) String brand,
		@Size(max = 2000) String description,
		@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
		boolean taxable,
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
		@Size(max = 40) String unitOfMeasure,
		boolean inventoryTracked,
		@Min(0) int displayOrder,
		@Size(max = 160) String supplierName,
		UUID categoryId,
		@Size(max = 20) List<@Size(max = 80) String> tags,
		@NotEmpty @Size(max = 50) List<@Valid VariantRequest> variants
) {
	CreateProductVariantsCommand toCommand() {
		return new CreateProductVariantsCommand(
				familyId, familyName, brand, description, currency, taxable, status, unitOfMeasure,
				inventoryTracked, displayOrder, supplierName, categoryId, tags == null ? List.of() : tags,
				variants.stream().map(VariantRequest::toCommand).toList()
		);
	}

	record VariantRequest(
			@NotBlank @Size(max = 160) String variantName,
			@NotBlank @Size(max = 64) String sku,
			@Size(max = 160) String presentation,
			@Min(0) long priceCents,
			@Size(max = 20) List<@Size(max = 128) String> barcodes,
			@Min(0) Long referenceCostCents,
			@Min(0) Long promotionalPriceCents,
			OffsetDateTime promotionStartsAt,
			OffsetDateTime promotionEndsAt
	) {
		CreateProductVariantsCommand.Variant toCommand() {
			return new CreateProductVariantsCommand.Variant(
					variantName, sku, presentation, priceCents, barcodes == null ? List.of() : barcodes,
					referenceCostCents, promotionalPriceCents, promotionStartsAt, promotionEndsAt
			);
		}
	}
}
