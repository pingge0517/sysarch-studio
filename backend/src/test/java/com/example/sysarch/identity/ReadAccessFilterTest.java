package com.example.sysarch.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** P4 T8, F4: in polarion mode reading needs a signed-in user; sign-in itself, the webhook and images stay open. */
class ReadAccessFilterTest {

    private static int status(ReadAccessFilter filter, String method, String path, boolean signedIn) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (signedIn) {
            Principal principal = Principal.dev("alice", List.of());
            request.setAttribute(Principal.class.getName(), (Supplier<Optional<Principal>>) () -> Optional.of(principal));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null ? 200 : response.getStatus();
    }

    @Test
    void inPolarionModeReadingNeedsSignIn() throws Exception {
        ReadAccessFilter filter = new ReadAccessFilter(new AuthProperties());
        assertFalse(filter.anonymousRead());

        for (String path : new String[] { "/api/graphql", "/api/v1/export/sysmlv2", "/api/v1/projects/p/tables/t", "/api/rest/projects", "/subscriptions" }) {
            assertEquals(401, status(filter, "POST", path, false), path);
            assertEquals(200, status(filter, "POST", path, true), path + " signed in");
        }
        for (String path : new String[] { "/api/v1/auth/me", "/api/v1/auth/login", "/api/v1/auth/polarion-sso", "/api/v1/webhooks/polarion",
                "/api/images/sysarch-shapes/diamond.svg", "/actuator/health", "/", "/projects/x/edit", "/sysarch", "/assets/index.js" }) {
            assertEquals(200, status(filter, "GET", path, false), path + " stays open");
        }
        assertEquals(200, status(filter, "OPTIONS", "/api/graphql", false), "CORS preflights carry no cookies");
    }

    @Test
    void devModeAndTheSwitchKeepAnonymousReading() throws Exception {
        AuthProperties dev = new AuthProperties();
        dev.setMode(AuthProperties.Mode.DEV);
        assertTrue(new ReadAccessFilter(dev).anonymousRead());
        assertEquals(200, status(new ReadAccessFilter(dev), "POST", "/api/graphql", false));

        AuthProperties open = new AuthProperties();
        open.setAnonymousRead(true);
        assertEquals(200, status(new ReadAccessFilter(open), "POST", "/api/graphql", false), "SYSARCH_ANONYMOUS_READ=true");

        AuthProperties strictDev = new AuthProperties();
        strictDev.setMode(AuthProperties.Mode.DEV);
        strictDev.setAnonymousRead(false);
        assertEquals(401, status(new ReadAccessFilter(strictDev), "POST", "/api/graphql", false), "can be required in dev mode too");
    }
}
