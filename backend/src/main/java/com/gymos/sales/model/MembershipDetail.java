package com.gymos.sales.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipDetail(UUID id, UUID clientId, UUID planId, String planName, String status, OffsetDateTime activatedAt, LocalDate startsOn, LocalDate endsOn, LocalDate graceEndsOn) {
}
