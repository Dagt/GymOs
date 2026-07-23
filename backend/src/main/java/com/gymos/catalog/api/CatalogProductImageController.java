package com.gymos.catalog.api;

import com.gymos.catalog.application.CatalogService;
import com.gymos.catalog.application.command.UploadProductImageCommand;
import com.gymos.catalog.application.query.ProductSummaryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/catalog/products/{productId}/image")
class CatalogProductImageController {
	private final CatalogService catalogService;

	CatalogProductImageController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ProductSummaryView uploadProductImage(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId, @RequestPart("file") MultipartFile file,
			HttpServletRequest servletRequest) {
		try {
			return catalogService.uploadProductImage(principal, productId,
					new UploadProductImageCommand(file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()),
					CatalogRequestCorrelation.from(servletRequest));
		} catch (IOException exception) {
			throw new BusinessException("PRODUCT_IMAGE_INVALID", "No fue posible leer la imagen.");
		}
	}

	@DeleteMapping
	ProductSummaryView removeProductImage(@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID productId, HttpServletRequest servletRequest) {
		return catalogService.removeProductImage(principal, productId, CatalogRequestCorrelation.from(servletRequest));
	}
}
