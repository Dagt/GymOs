package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import com.gymos.catalog.application.command.CreatePlanCommand;
import com.gymos.catalog.application.command.ProductCommand;
import com.gymos.catalog.application.command.UpdateMembershipPlanCommand;
import com.gymos.catalog.infrastructure.CatalogRepository;
import com.gymos.catalog.infrastructure.CatalogRepository.PlanSummary;
import com.gymos.catalog.infrastructure.CatalogRepository.ProductSummary;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
	private final JdbcClient jdbcClient;
	private final CatalogRepository catalogRepository;
	private final AuditService auditService;
	private final ObjectMapper objectMapper;

	public CatalogService(JdbcClient jdbcClient, CatalogRepository catalogRepository, AuditService auditService, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.catalogRepository = catalogRepository;
		this.auditService = auditService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<ProductSummary> products(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return catalogRepository.products(principal.organizationId());
	}

	@Transactional
	public ProductSummary createProduct(GymOsPrincipal principal, ProductCommand request, String correlationId) {
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
	public PlanSummary createPlan(GymOsPrincipal principal, CreatePlanCommand request, String correlationId) {
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
				    multi_branch_access, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :productId, :durationDays, :graceDays, CAST(:benefitsJson AS jsonb),
				    :multiBranchAccess, :status, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", planId)
				.param("organizationId", principal.organizationId())
				.param("productId", request.productId())
				.param("durationDays", request.durationDays())
				.param("graceDays", request.graceDays())
				.param("benefitsJson", normalizeBenefitsJson(request.benefitsJson()))
				.param("multiBranchAccess", request.multiBranchAccess())
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

	@Transactional
	public PlanSummary createMembershipPlan(GymOsPrincipal principal, CreateMembershipPlanCommand request, String correlationId) {
		requirePrincipal(principal);
		String sku = request.sku().trim().toUpperCase();
		if (catalogRepository.skuExists(principal.organizationId(), sku, null)) {
			throw new BusinessException("PRODUCT_SKU_ALREADY_EXISTS", "Ya existe un producto o plan con ese codigo.");
		}

		ProductSummary product = createProduct(
				principal,
				new ProductCommand(
						sku,
						request.name(),
						request.description(),
						"MEMBERSHIP_PLAN",
						request.priceCents(),
						request.currency(),
						true,
						request.status()
				),
				correlationId
		);
		return createPlan(
				principal,
				new CreatePlanCommand(
						product.id(),
						request.durationDays(),
						request.graceDays(),
						benefitsTextToJson(request.benefits()),
						request.multiBranchAccess(),
						request.status()
				),
				correlationId
		);
	}

	@Transactional
	public PlanSummary updateMembershipPlan(GymOsPrincipal principal, UUID planId, UpdateMembershipPlanCommand request, String correlationId) {
		requirePrincipal(principal);
		PlanSummary existing = catalogRepository.plan(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "El plan no esta disponible."));
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE catalog_product
				SET name = :name,
				    description = :description,
				    price_cents = :priceCents,
				    currency = :currency,
				    status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :productId
				  AND deleted_at IS NULL
				""")
				.param("name", request.name().trim())
				.param("description", blankToNull(request.description()))
				.param("priceCents", request.priceCents())
				.param("currency", request.currency().trim().toUpperCase())
				.param("status", request.status().trim().toUpperCase())
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("productId", existing.productId())
				.update();
		jdbcClient.sql("""
				UPDATE membership_plan
				SET duration_days = :durationDays,
				    grace_days = :graceDays,
				    benefits_json = CAST(:benefitsJson AS jsonb),
				    multi_branch_access = :multiBranchAccess,
				    status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :planId
				  AND deleted_at IS NULL
				""")
				.param("durationDays", request.durationDays())
				.param("graceDays", request.graceDays())
				.param("benefitsJson", benefitsTextToJson(request.benefits()))
				.param("multiBranchAccess", request.multiBranchAccess())
				.param("status", request.status().trim().toUpperCase())
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("organizationId", principal.organizationId())
				.param("planId", planId)
				.update();
		auditService.record(principal, "PLAN_UPDATED", "MEMBERSHIP_PLAN", planId, "BACKOFFICE", null, correlationId);
		return catalogRepository.plan(principal.organizationId(), planId)
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

	private String normalizeBenefitsJson(String value) {
		String normalized = blankToNull(value);
		if (normalized == null) {
			return "{}";
		}
		try {
			objectMapper.readTree(normalized);
			return normalized;
		} catch (JsonProcessingException exception) {
			throw new BusinessException("INVALID_PLAN_BENEFITS", "Los beneficios del plan no tienen formato valido.");
		}
	}

	private String benefitsTextToJson(String benefits) {
		String normalized = blankToNull(benefits);
		if (normalized == null) {
			return "{}";
		}
		if (normalized.startsWith("{") || normalized.startsWith("[")) {
			return normalizeBenefitsJson(normalized);
		}
		List<String> included = Arrays.stream(normalized.split("[,\\n]"))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.toList();
		try {
			return objectMapper.writeValueAsString(Map.of("included", included));
		} catch (JsonProcessingException exception) {
			throw new BusinessException("INVALID_PLAN_BENEFITS", "No fue posible guardar los beneficios del plan.");
		}
	}
}
