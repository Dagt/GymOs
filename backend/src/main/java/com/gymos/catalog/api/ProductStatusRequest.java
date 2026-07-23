package com.gymos.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProductStatusRequest(
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
}
