package com.gymos.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.infrastructure.CatalogProductCommandRepository;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InventoryPaginationIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

	private InventoryStockQueryRepository stockQueries;
	private InventoryMovementQueryRepository movementQueries;
	private InventoryStockCommandRepository stockCommands;
	private InventoryMovementCommandRepository movementCommands;
	private CatalogProductCommandRepository products;
	private JdbcClient jdbcClient;
	private UUID organizationId;
	private UUID branchId;
	private UUID secondBranchId;
	private UUID userId;
	private UUID waterId;
	private UUID towelId;

	@BeforeEach
	void setUp() {
		var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbcClient = JdbcClient.create(dataSource);
		stockQueries = new InventoryStockQueryRepository(jdbcClient);
		movementQueries = new InventoryMovementQueryRepository(jdbcClient);
		stockCommands = new InventoryStockCommandRepository(jdbcClient);
		movementCommands = new InventoryMovementCommandRepository(jdbcClient);
		products = new CatalogProductCommandRepository(jdbcClient);
		organizationId = UUID.randomUUID();
		branchId = UUID.randomUUID();
		secondBranchId = UUID.randomUUID();
		userId = UUID.randomUUID();
		waterId = UUID.randomUUID();
		towelId = UUID.randomUUID();
		insertOrganization();
		insertBranch(branchId, "CENTRO");
		insertBranch(secondBranchId, "NORTE");
		createProduct(waterId, "AGUA-1", "Agua mineral");
		createProduct(towelId, "TOALLA-1", "Toalla");
		attachProduct(branchId, waterId);
		attachProduct(secondBranchId, waterId);
		attachProduct(branchId, towelId);
		setStock(branchId, waterId, 8);
		setStock(branchId, towelId, 3);
		setStock(secondBranchId, waterId, 2);
	}

	@Test
	void stockSearchFiltersOrdersAndPaginatesWithinBranch() {
		var filtered = stockQueries.search(organizationId, branchId,
				new InventoryStockSearchCriteria("agua", 0, 25, "name,asc"));
		assertThat(filtered.total()).isEqualTo(1);
		assertThat(filtered.items()).extracting(InventoryRepository.InventoryStockRecord::productId)
				.containsExactly(waterId);
		assertThat(filtered.items().get(0).quantityOnHand()).isEqualTo(8);

		var firstPage = stockQueries.search(organizationId, branchId,
				new InventoryStockSearchCriteria(null, 0, 1, "quantity,desc"));
		var secondPage = stockQueries.search(organizationId, branchId,
				new InventoryStockSearchCriteria(null, 1, 1, "quantity,desc"));
		assertThat(firstPage.total()).isEqualTo(2);
		assertThat(firstPage.items()).extracting(InventoryRepository.InventoryStockRecord::productId)
				.containsExactly(waterId);
		assertThat(secondPage.items()).extracting(InventoryRepository.InventoryStockRecord::productId)
				.containsExactly(towelId);
	}

	@Test
	void stockQuantityAndAvailabilityAreIsolatedByBranch() {
		var center = stockQueries.stockDetail(organizationId, branchId, waterId).orElseThrow();
		var north = stockQueries.stockDetail(organizationId, secondBranchId, waterId).orElseThrow();
		assertThat(center.quantityOnHand()).isEqualTo(8);
		assertThat(north.quantityOnHand()).isEqualTo(2);
		assertThat(stockQueries.stockDetail(organizationId, secondBranchId, towelId)).isEmpty();
	}

	@Test
	void movementSearchPaginatesAndFiltersByProductAndBranch() {
		UUID centerStockId = stockCommands.stockIdentity(organizationId, branchId, waterId).orElseThrow();
		UUID northStockId = stockCommands.stockIdentity(organizationId, secondBranchId, waterId).orElseThrow();
		OffsetDateTime now = OffsetDateTime.now();
		movementCommands.createMovement(organizationId, branchId, centerStockId, waterId, "PURCHASE",
				8, 0, 8, 100L, "TEST", UUID.randomUUID(), "Centro", now, userId, UUID.randomUUID());
		movementCommands.createMovement(organizationId, secondBranchId, northStockId, waterId, "PURCHASE",
				2, 0, 2, 100L, "TEST", UUID.randomUUID(), "Norte", now, userId, UUID.randomUUID());

		var page = movementQueries.search(organizationId, branchId,
				new InventoryMovementSearchCriteria(waterId, 0, 10));
		assertThat(page.total()).isEqualTo(1);
		assertThat(page.items()).extracting(InventoryRepository.InventoryMovementRecord::reason)
				.containsExactly("Centro");
	}

	private void createProduct(UUID productId, String sku, String name) {
		products.create(new ProductMutation(
				organizationId, productId, sku, name, null, "PHYSICAL", 1_000,
				"MXN", true, "ACTIVE", null, null, null, null, true, 0, null,
				OffsetDateTime.now(), userId));
	}

	private void attachProduct(UUID branch, UUID productId) {
		products.attachBranch(organizationId, branch, productId, userId, OffsetDateTime.now());
	}

	private void setStock(UUID branch, UUID productId, int quantity) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID stockId = stockCommands.ensureStock(organizationId, branch, productId, userId, now);
		stockCommands.update(stockId, quantity, 0, null, userId, now);
	}

	private void insertOrganization() {
		jdbcClient.sql("""
				INSERT INTO organization (
				 id, legal_name, trade_name, status, timezone, default_currency, created_at, updated_at, version)
				VALUES (:id, 'Inventory Test', 'Inventory Test', 'ACTIVE', 'America/Mexico_City', 'MXN', now(), now(), 0)
				""").param("id", organizationId).update();
	}

	private void insertBranch(UUID id, String code) {
		jdbcClient.sql("""
				INSERT INTO branch (
				 id, organization_id, name, code, status, timezone, created_at, updated_at, version)
				VALUES (:id, :organizationId, :name, :code, 'ACTIVE', 'America/Mexico_City', now(), now(), 0)
				""")
				.param("id", id).param("organizationId", organizationId)
				.param("name", code).param("code", code).update();
	}
}
