package com.gymos.catalog.api;

import com.gymos.catalog.application.command.CategoryCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(
		UUID parentId,
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description,
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
		@Min(0) int displayOrder
) {
	CategoryCommand toCommand() {
		return new CategoryCommand(parentId, name, description, status, displayOrder);
	}
}
