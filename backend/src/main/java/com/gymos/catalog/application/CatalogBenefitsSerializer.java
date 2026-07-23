package com.gymos.catalog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CatalogBenefitsSerializer {
	private final ObjectMapper objectMapper;

	CatalogBenefitsSerializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String normalizeJson(String value) {
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

	String textToJson(String benefits) {
		String normalized = blankToNull(benefits);
		if (normalized == null) {
			return "{}";
		}
		if (normalized.startsWith("{") || normalized.startsWith("[")) {
			return normalizeJson(normalized);
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

	static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
