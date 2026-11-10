package com.example.sysarch.identity;

import com.example.sysarch.sync.polarion.PolarionException;
import com.example.sysarch.sync.polarion.PolarionToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/v1/auth} (P2 T8; not in the spec's OpenAPI).
 * <ul>
 * <li>{@code POST /login {token}}: Polarion is asked who owns the token and what roles they have; the answer becomes
 * the signed {@value SessionCookies#COOKIE} cookie (HttpOnly, per host, so Sirius on :8080 receives it too). The token
 * is forgotten when the request ends. 401 when Polarion refuses it, 502 when Polarion cannot be reached. In dev mode
 * {@code {user, roles}} logs in without Polarion.</li>
 * <li>{@code POST /logout}: clears the cookies.</li>
 * <li>{@code GET /me}: the mode and the current user (or {@code user: null}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(String token, String user, List<String> roles) {
    }

    private final Identities identities;

    private final SessionCookies cookies;

    private final Clock clock;

    public AuthController(Identities identities, SessionCookies cookies) {
        this.identities = Objects.requireNonNull(identities);
        this.cookies = Objects.requireNonNull(cookies);
        this.clock = Clock.systemUTC();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest request) {
        AuthProperties properties = this.identities.properties();
        Principal principal;
        if (request != null && request.token() != null && !request.token().isBlank()) {
            try {
                principal = this.identities.login(new PolarionToken(request.token().strip()));
            } catch (Identities.InvalidTokenException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
            } catch (PolarionException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法向 Polarion 验证令牌：" + e.getMessage());
            }
        } else if (properties.dev() && request != null && CurrentUser.clean(request.user()) != null) {
            principal = Principal.dev(CurrentUser.clean(request.user()), request.roles());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, properties.dev() ? "token or user is required" : "请填写你的 Polarion 个人访问令牌");
        }
        Instant expires = this.clock.instant().plus(properties.getSessionTtl());
        ResponseCookie cookie = ResponseCookie.from(SessionCookies.COOKIE, this.cookies.issue(principal, expires)).httpOnly(true).sameSite("Lax").path("/")
                .secure(properties.isSecureCookie()).domain(domain(properties)).maxAge(properties.getSessionTtl()).build();
        Map<String, Object> body = me(properties, principal);
        body.put("expiresAt", expires.toString());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        AuthProperties properties = this.identities.properties();
        ResponseCookie session = ResponseCookie.from(SessionCookies.COOKIE, "").httpOnly(true).sameSite("Lax").path("/").secure(properties.isSecureCookie()).domain(domain(properties))
                .maxAge(0).build();
        ResponseCookie typed = ResponseCookie.from(CurrentUser.COOKIE, "").sameSite("Lax").path("/").maxAge(0).build(); // dev mode's typed name
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, session.toString()).header(HttpHeaders.SET_COOKIE, typed.toString()).build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return me(this.identities.properties(), CurrentUser.principal(request).orElse(null));
    }

    private static String domain(AuthProperties properties) {
        return properties.getCookieDomain() == null || properties.getCookieDomain().isBlank() ? null : properties.getCookieDomain().strip();
    }

    private static Map<String, Object> me(AuthProperties properties, Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", properties.getMode().name().toLowerCase(java.util.Locale.ROOT));
        body.put("user", principal == null ? null : principal.user());
        body.put("name", principal == null ? null : principal.name());
        body.put("source", principal == null ? null : principal.source());
        body.put("globalRoles", principal == null ? List.of() : principal.globalRoles().stream().sorted().toList());
        body.put("projectRoles", principal == null ? Map.of() : principal.projectRoles());
        return body;
    }
}
