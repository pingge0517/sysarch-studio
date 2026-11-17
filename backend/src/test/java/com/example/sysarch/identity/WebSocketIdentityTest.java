package com.example.sysarch.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.polarion.PolarionClient;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WebSocketIdentityTest {
    @Test void aValidSignedCookieIsRecheckedAndTypedNamesAreIgnoredInProduction() {
        var clock=Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),java.time.ZoneOffset.UTC);
        var cookies=new SessionCookies("test-session-secret",clock);
        var properties=new AuthProperties();
        var polarion=mock(PolarionClient.class);
        var identities=new Identities(properties,polarion,new SyncProperties(),cookies,clock);
        var alice=Principal.dev("alice",List.of("alpha/project_user"));
        var headers=new HttpHeaders();
        headers.add("Cookie","unrelated=x; sysarch_session="+cookies.issue(alice,clock.instant().plusSeconds(60)));
        headers.add("X-Sysarch-User","root");
        headers.add("X-Sysarch-Roles","global:admin");
        assertEquals("alice",identities.resolveHeaders(headers).orElseThrow().user());
        headers.set("Cookie","sysarch_session="+cookies.issue(alice,clock.instant().minusSeconds(1)));
        assertTrue(identities.resolveHeaders(headers).isEmpty());
        headers.set("Cookie","sysarch_session=forged.signature");
        assertTrue(identities.resolveHeaders(headers).isEmpty());
        verifyNoInteractions(polarion);
    }
}
