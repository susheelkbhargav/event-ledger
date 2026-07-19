package com.eventledger.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "account-service")
public record AccountServiceProps(String baseUrl, Duration connectTimeout, Duration readTimeout) {}
