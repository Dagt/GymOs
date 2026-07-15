package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.CatalogController.CreatePlanRequest;
import com.gymos.catalog.api.CatalogController.ProductRequest;
import com.gymos.catalog.infrastructure.CatalogRepository;
import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final JdbcClient jdbcClient;
	private final CatalogRepository catalogRepository;
	private final AuditService auditService;

	public CatalogService(JdbcClient jdbcClient, CatalogRepository catalogRepository, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.catalogRepository = catalogRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ProductSummary> products(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return catalogRepository.products(principal.organizationId());
	}

	@Transactional
	public ProductSummary createProduct(GymOsPrincipal principal, ProductRequest request, String correlationId) {
		requirePrincipal(principal);
		String sku = request.sku().trim().toUpperCase();
		if (catalogRepository.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto con ese SKU.");
		}
		UUID productId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO catalog_product (
				    id, organization_id, sku, name, description, product_type, price_cents,
				    currency, taxable, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :sku, :name, :description, :productType, :priceCents,
				    :currency, :taxable, :status, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", productId)
				.param("organizationId", principal.organizationId())
				.param("sku", sku)
				.param("name", request.name().trim())
				.param("description", blankToNull(request.description()))
				.param("productType", request.productType().trim().toUpperCase())
				.param("priceCents", request.priceCents())
				.param("currency", request.currency().trim().toUpperCase())
				.param("taxable", request.taxable())
				.param("status", request.status().trim().toUpperCase())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "PRODUCT_CREATED", "CATALOG_PRODUCT", productId, "BACKOFFICE", null, correlationId);
		return catalogRepository.product(principal.organizationId(), productId)
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
	}

	@Transactional(readOnly = true)
	public List<PlanSummary> plans(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return catalogRepository.plans(principal.organizationId());
	}

	@Transactional
	public PlanSummary createPlan(GymOsPrincipal principal, CreatePlanRequest request, String correlationId) {
		requirePrincipal(principal);
		ProductSummary product = catalogRepository.product(principal.organizationId(), request.productId())
				.orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "El producto no esta disponible."));
		if (!"MEMBERSHIP_PLAN".equals(product.productType())) {
			throw new BusinessException("PRODUCT_NOT_MEMBERSHIP_PLAN", "El producto debe ser de tipo MEMBERSHIP_PLAN.");
		}
		UUID planId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO membership_plan (
				    id, organization_id, product_id, duration_days, grace_days, benefits_json,
				    status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    :status, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", planId)
				.param("organizationId", principal.organizationId())
				.param("productId", request.productId())
				.param("durationDays", request.durationDays())
				.param("graceDays", request.graceDays())
				.param("benefitsJson", blankToNull(request.benefitsJson()) == null ? "{}" : request.benefitsJson())
				.param("status", request.status().trim().toUpperCase())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "PLAN_CREATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return catalogRepository.plans(principal.organizationId()).stream()
				.filter(plan -> plan.id().equals(planId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
