package com.gymos.clients.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gymos.clients.application.ClientService;
import com.gymos.common.api.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientPaginationControllerTests {
	@Autowired private MockMvc mockMvc;
	@MockitoBean private ClientService service;

	@Test
	@WithMockUser(authorities = "client.read")
	void directoryReturnsMetadataWhileQuickSearchKeepsArrayContract() throws Exception {
		when(service.searchPage(any(), eq("Ana"), eq("ACTIVE"), eq(2), eq(25)))
				.thenReturn(PageResponse.of(List.of(), 2, 25, 127));
		when(service.search(any(), any(), any())).thenReturn(List.of());
		mockMvc.perform(get("/api/v1/clients/search").param("query", "Ana").param("status", "ACTIVE").param("page", "2").param("size", "25"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.totalElements").value(127)).andExpect(jsonPath("$.totalPages").value(6));
		mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
	}

	@Test
	@WithMockUser(authorities = "inventory.read")
	void paginatedDirectoryDoesNotBypassClientPermission() throws Exception {
		mockMvc.perform(get("/api/v1/clients/search")).andExpect(status().isForbidden());
	}

	@Test
	void paginatedDirectoryRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/clients/search")).andExpect(status().isUnauthorized());
	}
}
