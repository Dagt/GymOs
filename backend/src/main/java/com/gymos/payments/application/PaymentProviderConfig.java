package com.gymos.payments.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentProviderProperties.class)
class PaymentProviderConfig {
}
