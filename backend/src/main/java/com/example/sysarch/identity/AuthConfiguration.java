package com.example.sysarch.identity;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** P2 T8: {@code sysarch.auth} and the session cookie signer. */
@Configuration
@EnableConfigurationProperties({ AuthProperties.class, com.example.sysarch.config.FeatureProperties.class })
public class AuthConfiguration {

    @Bean
    public SessionCookies sessionCookies(AuthProperties properties) {
        return new SessionCookies(properties.getSessionSecret(), Clock.systemUTC());
    }

    /** Embedding in Polarion: assertions signed by the SysArch extension in Polarion. */
    @Bean
    public PolarionSso polarionSso(AuthProperties properties) {
        return new PolarionSso(properties.getPolarionSsoSecret(), properties.getPolarionSsoMaxAge(), Clock.systemUTC());
    }
}
