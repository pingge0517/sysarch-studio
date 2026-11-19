package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.sync.SyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class PolarionReadFeatureTest {
    @Configuration(proxyBeanMethods=false)
    @Import(PolarionReadController.class)
    static class ConfigurationUnderTest {
        @Bean ModelCodec codec() { return new ModelCodec(); }
        @Bean SyncProperties sync() { return new SyncProperties(); }
        @Bean com.example.sysarch.storage.PolarionStoreProperties store() { return new com.example.sysarch.storage.PolarionStoreProperties(); }
    }
    @Test void defaultConfigurationDoesNotPublishAReadEndpoint() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationUnderTest.class).run(context->
            assertTrue(context.getBeansOfType(PolarionReadController.class).isEmpty()));
    }
    @Test void explicitOptInRegistersTheReadControllerOnly() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationUnderTest.class)
            .withPropertyValues("sysarch.polarion-store.enabled=true").run(context->
                assertEquals(1,context.getBeansOfType(PolarionReadController.class).size()));
    }
}
