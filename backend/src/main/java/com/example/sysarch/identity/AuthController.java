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

    private final PolarionSso sso;

    public AuthController(Identities identities, SessionCookies cookies) {
        this(identities, cookies, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(Identities identities, SessionCookies cookies, PolarionSso sso) {
        this.sso = sso;
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
        ResponseCookie cookie = sessionCookie(properties, this.cookies.issue(principal, expires), properties.getSessionTtl());
        Map<String, Object> body = me(properties, principal);
        body.put("expiresAt", expires.toString());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        AuthProperties properties = this.identities.properties();
        ResponseCookie session = sessionCookie(properties, "", java.time.Duration.ZERO);
        ResponseCookie typed = ResponseCookie.from(CurrentUser.COOKIE, "").sameSite(sameSite(properties)).path("/").maxAge(0).build(); // dev mode's typed name
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, session.toString()).header(HttpHeaders.SET_COOKIE, typed.toString()).build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return me(this.identities.properties(), CurrentUser.principal(request).orElse(null));
    }

    private static String domain(AuthProperties properties) {
        return properties.getCookieDomain() == null || properties.getCookieDomain().isBlank() ? null : properties.getCookieDomain().strip();
    }

    /**
     * The session cookie, the same attributes for login, single sign-on and logout: HttpOnly, path /, SameSite, Secure,
     * Domain, and {@code Partitioned} when configured (CHIPS: a cookie a browser keeps in a cross-site frame, keyed by
     * the top-level site, where it blocks unpartitioned third-party cookies).
     */
    static ResponseCookie sessionCookie(AuthProperties properties, String value, java.time.Duration maxAge) {
        return ResponseCookie.from(SessionCookies.COOKIE, value).httpOnly(true).sameSite(sameSite(properties)).path("/").secure(properties.isSecureCookie())
                .partitioned(properties.isCookiePartitioned()).domain(domain(properties)).maxAge(maxAge).build();
    }

    private static String sameSite(AuthProperties properties) {
        return "none".equalsIgnoreCase(properties.getCookieSameSite()) ? "None" : "strict".equalsIgnoreCase(properties.getCookieSameSite()) ? "Strict" : "Lax";
    }

    /**
     * {@code POST /api/v1/auth/polarion-sso} (form: {@code assertion}, {@code return}): the SysArch extension in
     * Polarion posts here the signed statement of who is logged in to Polarion ({@link PolarionSso}). Their name and
     * roles are read with the service account, the usual session cookie is set, and the browser goes on to
     * {@code return} (a path on this host only; anything else goes to {@code /}). 401 for an assertion that is not
     * accepted, 503 when single sign-on or the service account is not configured.
     */
    // Polarion's page posts this form from its own origin: only this endpoint accepts it (POST, no credentials needed),
    // so a script on a Polarion page gets no general access to the API with the user's cookie.
    @org.springframework.web.bind.annotation.CrossOrigin(originPatterns = "${sysarch.auth.sso-origins}", methods = org.springframework.web.bind.annotation.RequestMethod.POST,
            allowCredentials = "false")
    @PostMapping(path = "/polarion-sso", consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> polarionSso(@org.springframework.web.bind.annotation.RequestParam(required = false) String assertion,
            @org.springframework.web.bind.annotation.RequestParam(name = "return", required = false) String target) {
        if (this.sso == null || !this.sso.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("未配置 Polarion 单点登录（SYSARCH_POLARION_SSO_SECRET）");
        }
        String user;
        try {
            user = this.sso.verify(assertion);
        } catch (PolarionSso.InvalidAssertionException e) {
            this.logger.warn("Polarion single sign-on refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("登录凭证无效或已过期，请从 Polarion 重新打开架构页面");
        }
        Principal principal;
        try {
            principal = this.identities.fromPolarionUser(user).orElse(null);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
        } catch (PolarionException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("无法从 Polarion 读取用户角色：" + e.getMessage());
        }
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Polarion 中没有这个用户");
        }
        AuthProperties properties = this.identities.properties();
        Instant expires = this.clock.instant().plus(properties.getSessionTtl());
        ResponseCookie cookie = sessionCookie(properties, this.cookies.issue(principal, expires), properties.getSessionTtl());
        this.logger.info("Polarion single sign-on: {}", principal.user());
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.SET_COOKIE, cookie.toString()).header(HttpHeaders.LOCATION, localPath(target))
                .build();
    }

    /** Only a path on this host: no scheme, no {@code //host}, no backslash tricks. */
    static String localPath(String target) {
        if (target == null || !target.startsWith("/") || target.startsWith("//") || target.contains("\\") || target.chars().anyMatch(c -> c < 0x20)) {
            return "/";
        }
        return target;
    }

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private static Map<String, Object> me(AuthProperties properties, Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", properties.getMode().name().toLowerCase(java.util.Locale.ROOT));
        body.put("user", principal == null ? null : principal.user());
        body.put("name", principal == null ? null : principal.name());
        body.put("source", principal == null ? null : principal.source());
        body.put("globalRoles", principal == null ? List.of() : principal.globalRoles().stream().sorted().toList());
        body.put("projectRoles", principal == null ? Map.of() : principal.projectRoles());
        // P4 T8 (F4): may this deployment be read without signing in? The application shows the sign-in page otherwise.
        body.put("anonymousRead", properties.getAnonymousRead() != null ? properties.getAnonymousRead() : properties.dev());
        return body;
    }
}
