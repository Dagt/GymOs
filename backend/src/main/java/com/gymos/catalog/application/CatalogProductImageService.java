package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.UploadProductImageCommand;
import com.gymos.catalog.application.port.CatalogImageStoragePort;
import com.gymos.catalog.application.port.CatalogProductCommandPort;
import com.gymos.catalog.application.port.CatalogProductQueryPort;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogProductImageService {
	private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
	private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final CatalogProductQueryPort products;
	private final CatalogProductCommandPort commands;
	private final CatalogImageStoragePort storage;
	private final AuditService auditService;

	CatalogProductImageService(
			CatalogProductQueryPort products,
			CatalogProductCommandPort commands,
			CatalogImageStoragePort storage,
			AuditService auditService
	) {
		this.products = products;
		this.commands = commands;
		this.storage = storage;
		this.auditService = auditService;
	}

	ProductSummaryView upload(
			GymOsPrincipal principal,
			UUID productId,
			UploadProductImageCommand command,
			String correlationId
	) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = require(principal.organizationId(), productId);
		validate(command);
		String objectKey = objectKey(principal.organizationId(), productId, command.filename());
		storage.store(objectKey, command.contentType(), command.size(), command.content());
		try {
			if (commands.updateImageKey(
					principal.organizationId(), productId, objectKey, principal.userId(), OffsetDateTime.now()) != 1) {
				throw notFound();
			}
			if (product.imageObjectKey() != null) {
				storage.delete(product.imageObjectKey());
			}
		} catch (RuntimeException exception) {
			storage.delete(objectKey);
			throw exception;
		}
		auditService.record(
				principal, "PRODUCT_IMAGE_UPDATED", "CATALOG_PRODUCT", productId,
				"BACKOFFICE", null, correlationId);
		return require(principal.organizationId(), productId);
	}

	ProductSummaryView remove(GymOsPrincipal principal, UUID productId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		ProductSummaryView product = require(principal.organizationId(), productId);
		if (product.imageObjectKey() == null) {
			return product;
		}
		if (commands.updateImageKey(
				principal.organizationId(), productId, null, principal.userId(), OffsetDateTime.now()) != 1) {
			throw notFound();
		}
		storage.delete(product.imageObjectKey());
		auditService.record(
				principal, "PRODUCT_IMAGE_REMOVED", "CATALOG_PRODUCT", productId,
				"BACKOFFICE", null, correlationId);
		return require(principal.organizationId(), productId);
	}

	private void validate(UploadProductImageCommand command) {
		if (command.content() == null || command.size() <= 0) {
			throw new BusinessException("PRODUCT_IMAGE_INVALID", "Selecciona una imagen.");
		}
		if (command.size() > MAX_IMAGE_SIZE) {
			throw new BusinessException("PRODUCT_IMAGE_INVALID", "La imagen no puede superar 5 MB.");
		}
		String contentType = command.contentType() == null
				? ""
				: command.contentType().toLowerCase(Locale.ROOT);
		if (!CONTENT_TYPES.contains(contentType)) {
			throw new BusinessException("PRODUCT_IMAGE_INVALID", "Usa una imagen JPG, PNG o WebP.");
		}
	}

	private String objectKey(UUID organizationId, UUID productId, String filename) {
		String safeName = filename == null || filename.isBlank() ? "product-image" : filename;
		safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
		return "catalog/" + organizationId + "/" + productId + "/" + UUID.randomUUID() + "-" + safeName;
	}

	private ProductSummaryView require(UUID organizationId, UUID productId) {
		return products.findProduct(organizationId, productId).orElseThrow(this::notFound);
	}

	private BusinessException notFound() {
		return new BusinessException("PRODUCT_NOT_FOUND", "El producto no existe o fue archivado.");
	}
}
