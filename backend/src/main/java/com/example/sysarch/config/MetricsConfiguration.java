package com.example.sysarch.config;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P2 T9: bounded metrics. Sirius Web 2025.8.11 times every input of an editing context as
 * {@code timer_processing_input{input=<class>, inputId=<the input's UUID>}} and every representation refresh as
 * {@code timer_refresh_representation{changeDescription=<source id>}}. Micrometer keeps one meter per distinct tag set
 * for the life of the JVM, so each edit left a timer behind: the 30-user load test grew the live heap by about 160 MB
 * in 30 minutes (109,303 timers after 109,886 requests). Dropping the per-request tags folds them into one timer per
 * input type; the measurements are kept. Additive, Sirius' code is untouched.
 *
 * <p>The filter has to sit on <em>every</em> registry: Spring Boot applies {@code MeterFilter} beans to the registries
 * behind its auto-configured composite, not to the composite itself, and the composite keeps its own meter per tag set
 * before the filters behind it run (the second load test still held 104,079 {@code CompositeTimer}s). So the filter is
 * also added to each composite registry as it is created, and to Micrometer's global registry.
 */
@Configuration
public class MetricsConfiguration {

    /** Tags whose value is new on every request. */
    static final String[] UNBOUNDED_TAGS = { "inputId", "changeDescription" };

    static MeterFilter filter() {
        return MeterFilter.ignoreTags(UNBOUNDED_TAGS);
    }

    @Bean
    public MeterFilter boundedSiriusMetrics() {
        return filter();
    }

    /** The composite registries, which Spring Boot leaves unfiltered. Static: a post-processor is created early. */
    @Bean
    public static BeanPostProcessor boundedCompositeMetrics() {
        Metrics.globalRegistry.config().meterFilter(filter());
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof CompositeMeterRegistry composite) {
                    composite.config().meterFilter(filter());
                }
                return bean;
            }
        };
    }
}
