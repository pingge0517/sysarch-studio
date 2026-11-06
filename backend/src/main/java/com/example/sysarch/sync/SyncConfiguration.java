package com.example.sysarch.sync;

import com.example.sysarch.sync.consistency.ConsistencyProperties;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.TokenBucket;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Polarion sync beans (T7). Scheduling runs the audit and idempotency retention in {@link SyncStore}. */
@Configuration
@EnableConfigurationProperties({ SyncProperties.class, ConsistencyProperties.class })
@EnableScheduling
public class SyncConfiguration {

    /** One client, so one rate limit for every call this service makes to Polarion. */
    @Bean
    public PolarionClient polarionClient(SyncProperties properties, ObjectMapper json) {
        return new PolarionClient(properties.getBaseUrl(), new TokenBucket(properties.getRateLimitRps()), properties.getMaxRetries(), json,
                duration -> Thread.sleep(duration.toMillis()));
    }
}
