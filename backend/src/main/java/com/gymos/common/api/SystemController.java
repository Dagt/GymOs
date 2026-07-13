package com.gymos.common.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
	@GetMapping("/info")
	Map<String, Object> info() {
		return Map.of(
				"name", "GymOS",
				"status", "BOOTSTRAPPED",
				"timestamp", Instant.now().toString()
		);
	}
}
