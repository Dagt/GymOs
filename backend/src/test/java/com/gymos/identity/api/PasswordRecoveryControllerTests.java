package com.gymos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gymos.common.api.BusinessException;
import com.gymos.common.api.GlobalExceptionHandler;
import com.gymos.common.security.InMemoryRateLimiter;
import com.gymos.identity.application.PasswordRecoveryProperties;
import com.gymos.identity.application.PasswordRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordRecoveryControllerTests {
    @Test void invalidAttemptsAreRateLimitedWithoutReturningTokenOrPassword() throws Exception {
        var service = mock(PasswordRecoveryService.class);
        doThrow(new BusinessException("PASSWORD_RESET_INVALID", "El enlace no es válido, ya se utilizó o venció."))
                .when(service).consume(anyString(), anyString(), anyString());
        var controller = new PasswordRecoveryController(service, new InMemoryRateLimiter(),
                request -> "127.0.0.1", new PasswordRecoveryProperties(60, 60, 2));
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        for (int attempt = 0; attempt < 4; attempt++) {
            var response = mvc.perform(post("/api/v1/auth/password-recovery/accept").contentType("application/json")
                    .content("{\"token\":\"invalid-private-token\",\"password\":\"PrivatePassword123!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(attempt < 2 ? "PASSWORD_RESET_INVALID" : "RATE_LIMIT_EXCEEDED"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("invalid-private-token", "PrivatePassword123!");
        }
        verify(service, times(2)).consume(anyString(), anyString(), anyString());
    }

    @Test void successfulRecoveryIsNotCacheableAndTransportObjectIsRedacted() throws Exception {
        var controller = new PasswordRecoveryController(mock(PasswordRecoveryService.class), new InMemoryRateLimiter(),
                request -> "127.0.0.2", new PasswordRecoveryProperties(60, 60, 10));
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/password-recovery/accept").contentType("application/json")
                .content("{\"token\":\"private-token\",\"password\":\"PrivatePassword123!\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("PASSWORD_CHANGED"));
        assertThat(new PasswordRecoveryController.RecoveryRequest("private-token", "PrivatePassword123!").toString())
                .doesNotContain("private-token", "PrivatePassword123!");
    }
}
