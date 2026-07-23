package com.gymos.catalog.api;

import com.gymos.common.api.Correlation;
import jakarta.servlet.http.HttpServletRequest;

final class CatalogRequestCorrelation {
	private CatalogRequestCorrelation() {
	}

	static String from(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}
}
