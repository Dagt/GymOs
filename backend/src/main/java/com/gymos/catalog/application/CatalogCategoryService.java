package com.gymos.catalog.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.application.command.CategoryCommand;
import com.gymos.catalog.application.port.CatalogCategoryCommandPort;
import com.gymos.catalog.application.port.CatalogCategoryCommandPort.CategoryMutation;
import com.gymos.catalog.application.port.CatalogCategoryQueryPort;
import com.gymos.catalog.application.query.CategoryView;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogCategoryService {
	private final CatalogCategoryQueryPort queries;
	private final CatalogCategoryCommandPort commands;
	private final AuditService auditService;

	CatalogCategoryService(CatalogCategoryQueryPort queries, CatalogCategoryCommandPort commands, AuditService auditService) {
		this.queries = queries;
		this.commands = commands;
		this.auditService = auditService;
	}

	List<CategoryView> categories(GymOsPrincipal principal, boolean includeInactive) {
		CatalogAccessGuard.requirePrincipal(principal);
		return queries.listCategories(principal.organizationId(), includeInactive);
	}

	CategoryView create(GymOsPrincipal principal, CategoryCommand command, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		validate(command, principal.organizationId(), null);
		UUID id = UUID.randomUUID();
		String slug = slug(command.name());
		if (queries.slugExists(principal.organizationId(), slug, null)) throw duplicate();
		commands.createCategory(mutation(principal, id, command, slug));
		auditService.record(principal, "CATEGORY_CREATED", "CATALOG_CATEGORY", id, "BACKOFFICE", null, correlationId);
		return require(principal.organizationId(), id);
	}

	CategoryView update(GymOsPrincipal principal, UUID categoryId, CategoryCommand command, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		require(principal.organizationId(), categoryId);
		validate(command, principal.organizationId(), categoryId);
		String slug = slug(command.name());
		if (queries.slugExists(principal.organizationId(), slug, categoryId)) throw duplicate();
		if (commands.updateCategory(mutation(principal, categoryId, command, slug)) != 1) throw notFound();
		auditService.record(principal, "CATEGORY_UPDATED", "CATALOG_CATEGORY", categoryId, "BACKOFFICE", null, correlationId);
		return require(principal.organizationId(), categoryId);
	}

	void archive(GymOsPrincipal principal, UUID categoryId, String correlationId) {
		CatalogAccessGuard.requirePrincipal(principal);
		require(principal.organizationId(), categoryId);
		if (queries.hasActiveChildren(principal.organizationId(), categoryId)) {
			throw new BusinessException("CATEGORY_HAS_ACTIVE_CHILDREN", "Desactiva o reasigna las subcategorias antes de archivar.");
		}
		if (queries.hasActiveProducts(principal.organizationId(), categoryId)) {
			throw new BusinessException("CATEGORY_HAS_ACTIVE_PRODUCTS", "Reasigna los productos activos antes de archivar la categoria.");
		}
		if (commands.archiveCategory(principal.organizationId(), categoryId, principal.userId(), OffsetDateTime.now()) != 1) throw notFound();
		auditService.record(principal, "CATEGORY_ARCHIVED", "CATALOG_CATEGORY", categoryId, "BACKOFFICE", null, correlationId);
	}

	private void validate(CategoryCommand command, UUID organizationId, UUID categoryId) {
		if (command.name() == null || command.name().isBlank()) throw new BusinessException("CATEGORY_NAME_REQUIRED", "El nombre de la categoria es obligatorio.");
		if (command.displayOrder() < 0) throw new BusinessException("CATEGORY_DISPLAY_ORDER_INVALID", "El orden no puede ser negativo.");
		String status = normalizeStatus(command.status());
		if (!List.of("ACTIVE", "INACTIVE").contains(status)) throw new BusinessException("CATEGORY_STATUS_INVALID", "El estado de la categoria no es valido.");
		if (categoryId != null && categoryId.equals(command.parentId())) throw new BusinessException("CATEGORY_PARENT_INVALID", "Una categoria no puede ser su propia categoria padre.");
		if (command.parentId() != null && queries.findCategory(organizationId, command.parentId()).filter(parent -> "ACTIVE".equals(parent.status())).isEmpty()) {
			throw new BusinessException("CATEGORY_PARENT_NOT_AVAILABLE", "La categoria padre no existe o esta inactiva.");
		}
		if (categoryId != null && queries.wouldCreateCycle(organizationId, categoryId, command.parentId())) {
			throw new BusinessException("CATEGORY_CYCLE_NOT_ALLOWED", "La jerarquia de categorias no puede contener ciclos.");
		}
	}

	private CategoryMutation mutation(GymOsPrincipal principal, UUID categoryId, CategoryCommand command, String slug) {
		return new CategoryMutation(principal.organizationId(), categoryId, command.parentId(), command.name().trim(), slug,
				CatalogBenefitsSerializer.blankToNull(command.description()), normalizeStatus(command.status()), command.displayOrder(),
				OffsetDateTime.now(), principal.userId());
	}

	private CategoryView require(UUID organizationId, UUID categoryId) { return queries.findCategory(organizationId, categoryId).orElseThrow(this::notFound); }
	private String slug(String value) {
		String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String result = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (result.isBlank()) throw new BusinessException("CATEGORY_NAME_INVALID", "El nombre debe contener letras o numeros.");
		return result;
	}
	private String normalizeStatus(String status) { return status == null ? "" : status.trim().toUpperCase(Locale.ROOT); }
	private BusinessException notFound() { return new BusinessException("CATEGORY_NOT_FOUND", "La categoria no existe."); }
	private BusinessException duplicate() { return new BusinessException("CATEGORY_ALREADY_EXISTS", "Ya existe una categoria con ese nombre."); }
}
