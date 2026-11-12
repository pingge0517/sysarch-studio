package com.example.sysarch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** P4 T5: /actuator/prometheus only with the metrics token; nothing else is affected. */
class MetricsTokenFilterTest {

    private static int status(MetricsTokenFilter filter, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null ? 200 : response.getStatus();
    }

    @Test
    void theMetricsNeedTheToken() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("s3cret");

        assertEquals(200, status(filter, "/actuator/prometheus", "Bearer s3cret"));
        assertEquals(401, status(filter, "/actuator/prometheus", null));
        assertEquals(401, status(filter, "/actuator/prometheus", "Bearer wrong"));
        assertEquals(401, status(filter, "/actuator/prometheus", "s3cret"), "the Bearer scheme is required");
        assertEquals(200, status(filter, "/actuator/health", null), "health stays open for probes");
        assertEquals(200, status(filter, "/api/v1/tables", null));
    }

    @Test
    void withoutATokenTheEndpointDoesNotExist() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("");

        assertEquals(404, status(filter, "/actuator/prometheus", "Bearer anything"));
        assertTrue(status(filter, "/actuator/health", null) == 200);
    }
}
