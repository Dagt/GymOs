package com.gymos.access.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PhysicalConnectorProperties.class)
class PhysicalAccessConfig {
}
