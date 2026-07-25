package com.gymos.inventory.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.inventory.application.InventoryService;
import com.gymos.inventory.application.query.InventoryBranchSettingsView;
import com.gymos.inventory.application.query.InventoryPhysicalCountView;
import com.gymos.inventory.application.query.InventorySummaryView;
import com.gymos.inventory.application.query.InventoryTransferView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class InventoryControllerTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private InventoryService inventoryService;

	@MockitoBean
	private JwtService jwtService;

	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"admin", "Admin", Set.of("inventory.read", "inventory.manage")
		);
	}

	@Test
	void getSummaryReturnsValuationAndMetrics() throws Exception {
		when(inventoryService.summary(any())).thenReturn(
				new InventorySummaryView(150000L, 25L, 3L, 1L, true)
		);

		mockMvc.perform(get("/api/v1/inventory/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalValuationCents").value(150000))
				.andExpect(jsonPath("$.totalTrackedProducts").value(25))
				.andExpect(jsonPath("$.lowStockCount").value(3))
				.andExpect(jsonPath("$.allowNegativeStock").value(true));
	}

	@Test
	void updateBranchSettingsChangesPolicy() throws Exception {
		when(inventoryService.updateBranchSettings(any(), eq(true), any())).thenReturn(
				new InventoryBranchSettingsView(principal.branchId(), true)
		);

		mockMvc.perform(put("/api/v1/inventory/branch-settings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InventoryController.UpdateBranchSettingsRequest(true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allowNegativeStock").value(true));

		verify(inventoryService).updateBranchSettings(any(), eq(true), any());
	}

	@Test
	void createPhysicalCountEndpoints() throws Exception {
		UUID countId = UUID.randomUUID();
		when(inventoryService.createPhysicalCount(any(), any(), any())).thenReturn(
				new InventoryPhysicalCountView(countId, principal.organizationId(), principal.branchId(),
						"IN_PROGRESS", "Cierre de mes", OffsetDateTime.now(), OffsetDateTime.now(),
						principal.userId(), null, null, List.of())
		);

		mockMvc.perform(post("/api/v1/inventory/physical-counts")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InventoryController.CreatePhysicalCountRequest("Cierre de mes"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(countId.toString()))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void createTransferEndpoint() throws Exception {
		UUID transferId = UUID.randomUUID();
		UUID destBranchId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();

		when(inventoryService.createTransfer(any(), eq(destBranchId), any(), any(), any())).thenReturn(
				new InventoryTransferView(transferId, principal.organizationId(), principal.branchId(), "Origen",
						destBranchId, "Destino", "REQUESTED", "Envio insumos", OffsetDateTime.now(),
						OffsetDateTime.now(), principal.userId(), null, null, null, null, List.of())
		);

		var itemReq = new InventoryController.TransferItemApiRequest(productId, 5);
		var request = new InventoryController.CreateTransferRequest(destBranchId, "Envio insumos", List.of(itemReq));

		mockMvc.perform(post("/api/v1/inventory/transfers")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId.toString()))
				.andExpect(jsonPath("$.status").value("REQUESTED"));
	}
}
