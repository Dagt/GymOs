package com.gymos.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.catalog.application.port.CatalogProductCommandPort.ProductMutation;
import com.gymos.catalog.infrastructure.CatalogProductCommandRepository;
import com.gymos.inventory.application.query.InventoryMovementSearchCriteria;
import com.gymos.inventory.application.query.InventoryHistorySearchCriteria;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventoryTransferView;
import com.gymos.inventory.application.query.InventoryStockSearchCriteria;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
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
	void stockSearchFindsProductByActiveAlternateBarcode() {
		products.replaceBarcodes(organizationId, waterId,
				List.of("750000000001", "750000000099"), true, userId, OffsetDateTime.now());

		var result = stockQueries.search(organizationId, branchId,
				new InventoryStockSearchCriteria("750000000099", 0, 25, "name,asc"));

		assertThat(result.total()).isEqualTo(1);
		assertThat(result.items()).extracting(InventoryRepository.InventoryStockRecord::productId)
				.containsExactly(waterId);
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

	@Test
	void stockAndLowStockPagesCanReachMoreThanOneHundredProducts() {
		for (int index = 0; index < 127; index++) {
			UUID id = UUID.randomUUID();
			createProduct(id, "PAG-" + index, "Producto paginado " + index);
			attachProduct(branchId, id);
			UUID stockId = stockCommands.ensureStock(organizationId, branchId, id, userId, OffsetDateTime.now());
			stockCommands.update(stockId, 2, 5, 100L, userId, OffsetDateTime.now());
		}
		List<UUID> visited = new ArrayList<>();
		for (int page = 0; page < 6; page++) {
			var result = stockQueries.search(organizationId, branchId, new InventoryStockSearchCriteria("paginado", page, 25, "name,asc", true));
			assertThat(result.total()).isEqualTo(127);
			assertThat(result.items()).hasSize(page == 5 ? 2 : 25);
			visited.addAll(result.items().stream().map(InventoryRepository.InventoryStockRecord::productId).toList());
		}
		assertThat(visited).hasSize(127).doesNotHaveDuplicates();
		assertThat(stockQueries.search(organizationId, secondBranchId, new InventoryStockSearchCriteria(null, 0, 25, "name,asc", true)).total()).isZero();
		assertThat(stockQueries.search(UUID.randomUUID(), branchId, new InventoryStockSearchCriteria(null, 0, 25, "name,asc", true)).total()).isZero();
		assertThat(stockQueries.search(organizationId, branchId, new InventoryStockSearchCriteria("PAG-126", 0, 25, "name,asc")).items())
				.extracting(InventoryRepository.InventoryStockRecord::sku).containsExactly("PAG-126");
		assertThat(stockQueries.search(organizationId, branchId, new InventoryStockSearchCriteria(null, Integer.MAX_VALUE, 100, "name,asc")).items()).isEmpty();
	}

	@Test
	void movementTextAndTypeFiltersApplyBeforePagingAndCounting() {
		UUID stockId = stockCommands.stockIdentity(organizationId, branchId, waterId).orElseThrow();
		OffsetDateTime now = OffsetDateTime.now();
		for (int index = 0; index < 127; index++) {
			movementCommands.createMovement(organizationId, branchId, stockId, waterId, index % 2 == 0 ? "PURCHASE" : "WASTE",
					1, 0, 1, 100L, "TEST", UUID.randomUUID(), index == 121 ? "Revisión excepcional" : "Normal",
					now.minusSeconds(index), userId, UUID.randomUUID());
		}
		UUID northStockId = stockCommands.stockIdentity(organizationId, secondBranchId, waterId).orElseThrow();
		movementCommands.createMovement(organizationId, secondBranchId, northStockId, waterId, "WASTE", 1, 0, 1, 100L,
				"TEST", UUID.randomUUID(), "Revisión excepcional", now, userId, UUID.randomUUID());

		var first = movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(null, 0, 50));
		var last = movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(null, 2, 50));
		assertThat(first.total()).isEqualTo(127);
		assertThat(last.items()).hasSize(27);
		assertThat(first.items()).extracting(InventoryRepository.InventoryMovementRecord::reason).doesNotContain("Revisión excepcional");
		var filtered = movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(waterId, 0, 25, " EXCEPCIONAL ", " waste "));
		assertThat(filtered.total()).isEqualTo(1);
		assertThat(filtered.items()).extracting(InventoryRepository.InventoryMovementRecord::reason).containsExactly("Revisión excepcional");
		assertThat(movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(null, 0, 25, "AGUA-1", "WASTE")).total()).isEqualTo(63);
		assertThat(movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(null, 0, 25, "mineral", null)).total()).isEqualTo(127);
		assertThat(movementQueries.search(organizationId, branchId, new InventoryMovementSearchCriteria(towelId, 0, 25, null, "WASTE")).total()).isZero();
		assertThat(movementQueries.search(UUID.randomUUID(), branchId, new InventoryMovementSearchCriteria(null, 0, 25)).total()).isZero();
	}

	@Test
	void countAndTransferHistoriesAreCompleteAndActiveIndicatorsIgnorePagePosition() {
		var counts = new InventoryPhysicalCountRepository(jdbcClient);
		var transfers = new InventoryTransferRepository(jdbcClient);
		OffsetDateTime now = OffsetDateTime.now();
		UUID oldestCount = null;
		UUID oldestTransfer = null;
		for (int index = 0; index < 127; index++) {
			UUID count = counts.create(organizationId, branchId, "Conteo " + index, userId, now.minusSeconds(index));
			UUID transfer = transfers.create(organizationId, branchId, secondBranchId, "Transferencia " + index, userId, now.minusSeconds(index));
			if (index < 126) {
				counts.updateStatus(organizationId, branchId, count, "CANCELLED", userId, now);
				transfers.updateStatus(organizationId, transfer, "CANCELLED", userId, now);
			} else { oldestCount = count; oldestTransfer = transfer; }
		}
		counts.create(organizationId, secondBranchId, "Otra sucursal", userId, now);
		UUID thirdBranchId = UUID.randomUUID();
		insertBranch(thirdBranchId, "TERCERA");
		transfers.create(organizationId, secondBranchId, thirdBranchId, "Fuera de alcance", userId, now);
		List<UUID> countIds = new ArrayList<>();
		List<UUID> transferIds = new ArrayList<>();
		for (int page = 0; page < 6; page++) {
			var criteria = new InventoryHistorySearchCriteria(page, 25, false);
			var countPage = counts.search(organizationId, branchId, criteria);
			var transferPage = transfers.search(organizationId, branchId, criteria);
			assertThat(countPage.totalElements()).isEqualTo(127);
			assertThat(transferPage.totalElements()).isEqualTo(127);
			assertThat(countPage.totalPages()).isEqualTo(6);
			assertThat(transferPage.items()).hasSize(page == 5 ? 2 : 25);
			countIds.addAll(countPage.items().stream().map(InventoryPhysicalCountView::id).toList());
			transferIds.addAll(transferPage.items().stream().map(InventoryTransferView::id).toList());
		}
		assertThat(countIds).hasSize(127).doesNotHaveDuplicates().contains(oldestCount);
		assertThat(transferIds).hasSize(127).doesNotHaveDuplicates().contains(oldestTransfer);
		var active = new InventoryHistorySearchCriteria(0, 1, true);
		assertThat(counts.search(organizationId, branchId, active).items()).extracting(InventoryPhysicalCountView::id).containsExactly(oldestCount);
		assertThat(transfers.search(organizationId, branchId, active).items()).extracting(InventoryTransferView::id).containsExactly(oldestTransfer);
		assertThat(counts.search(organizationId, branchId, active).totalElements()).isEqualTo(1);
		assertThat(transfers.search(organizationId, secondBranchId, active).totalElements()).isEqualTo(2);
		assertThat(counts.search(UUID.randomUUID(), branchId, active).totalElements()).isZero();
		assertThat(transfers.search(UUID.randomUUID(), branchId, active).totalElements()).isZero();
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
