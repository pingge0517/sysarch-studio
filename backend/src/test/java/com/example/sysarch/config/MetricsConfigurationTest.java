package com.example.sysarch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** P2 T9: Sirius' per-input timers must not leave one meter per request behind (the load test's heap growth). */
class MetricsConfigurationTest {

    private static void sirius(MeterRegistry registry, int inputs) {
        for (int i = 0; i < inputs; i++) {
            // what Sirius' EditingContextEventProcessor and RepresentationEventProcessorRefresher register
            registry.timer("timer_processing_input", "input", i % 2 == 0 ? "EditLabelInput" : "EditTextfieldInput", "inputId", UUID.randomUUID().toString())
                    .record(Duration.ofMillis(3));
            registry.timer("timer_refresh_representation", "changeDescription", UUID.randomUUID().toString()).record(Duration.ofMillis(2));
        }
    }

    @Test
    void perRequestTagsAreDroppedSoTheMeterCountStaysBounded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricsConfiguration().boundedSiriusMetrics());
        sirius(registry, 1000);
        assertEquals(2, registry.find("timer_processing_input").timers().size(), "one timer per input type, not per input");
        assertEquals(1, registry.find("timer_refresh_representation").timers().size());
        assertEquals(1000, registry.find("timer_processing_input").timers().stream().mapToLong(t -> t.count()).sum(), "nothing measured is lost");
    }

    @Test
    void theCompositeInFrontIsBoundedToo() {
        // Spring Boot filters only the registries behind its composite; the composite kept a meter per input (load test 2)
        SimpleMeterRegistry behind = new SimpleMeterRegistry();
        behind.config().meterFilter(MetricsConfiguration.filter());
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(behind);
        MetricsConfiguration.boundedCompositeMetrics().postProcessAfterInitialization(composite, "compositeMeterRegistry");
        sirius(composite, 1000);
        assertEquals(3, composite.getMeters().size(), "2 input types + 1 refresh timer in the composite itself");
        assertEquals(3, behind.getMeters().size());
    }
}
