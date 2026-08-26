package com.gymos.identity.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({IdentityProperties.class, PasswordRecoveryProperties.class})
class IdentityConfig {
}
