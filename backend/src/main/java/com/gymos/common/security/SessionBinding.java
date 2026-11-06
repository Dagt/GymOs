package com.gymos.common.security;

import java.util.UUID;

public record SessionBinding(UUID id, long revision, String authorityFingerprint) {
}
