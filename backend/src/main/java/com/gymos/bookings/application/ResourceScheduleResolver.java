package com.gymos.bookings.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.bookings.infrastructure.BookingRepository.ResourceRecord;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ResourceScheduleResolver {
	private final ObjectMapper objectMapper;

	ResourceScheduleResolver(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	Optional<ResourceHours> resolve(ResourceRecord resource, LocalDate date) {
		if (resource.notes() == null || resource.notes().isBlank()) {
			return Optional.of(defaultHours(resource));
		}
		try {
			JsonNode schedule = objectMapper.readTree(resource.notes()).path("schedule");
			if (!schedule.isArray()) {
				return Optional.of(defaultHours(resource));
			}
			String weekday = date.getDayOfWeek().name();
			for (JsonNode item : schedule) {
				if (weekday.equalsIgnoreCase(item.path("day").asText())) {
					return Optional.of(new ResourceHours(
							LocalTime.parse(item.path("opensAt").asText()),
							LocalTime.parse(item.path("closesAt").asText())
					));
				}
			}
			return Optional.empty();
		} catch (Exception ignored) {
			return Optional.of(defaultHours(resource));
		}
	}

	private ResourceHours defaultHours(ResourceRecord resource) {
		return new ResourceHours(resource.opensAt(), resource.closesAt());
	}

	record ResourceHours(LocalTime opensAt, LocalTime closesAt) {
	}
}
