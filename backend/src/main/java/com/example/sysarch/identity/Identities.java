package com.example.sysarch.identity;

import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.polarion.PolarionClient;
import com.example.sysarch.sync.polarion.PolarionToken;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves who a request is (P2 T8), in this order:
 * <ol>
 * <li>the {@link SessionCookies#COOKIE} cookie (issued at login after Polarion verified the token);</li>
 * <li>{@code Authorization: Bearer <Polarion token>} (API clients; sync calls send it anyway): Polarion is asked who
 * owns the token and what roles they have, cached for {@code token-cache-ttl} under the token's SHA-256, never the
 * token itself;</li>
 * <li>only in {@code sysarch.auth.mode=dev}: {@code X-Sysarch-User} (+ {@code X-Sysarch-Roles}) or the
 * {@code sysarch_user} cookie.</li>
 * </ol>
 * Anything else is anonymous. In polarion mode a typed name is never believed.
 */
@Component
public class Identities {

    public static final String ROLES_HEADER = "X-Sysarch-Roles";

    public static final class InvalidTokenException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private record Cached(Principal principal, long at) {
    }

    private final Logger logger = LoggerFactory.getLogger(Identities.class);

    private final AuthProperties properties;

    private final PolarionClient polarion;

    private final SyncProperties sync;

    private final SessionCookies cookies;

    private final Clock clock;

    private final Map<String, Cached> byTokenHash = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public Identities(AuthProperties properties, PolarionClient polarion, SyncProperties sync, SessionCookies cookies) {
        this(properties, polarion, sync, cookies, Clock.systemUTC());
    }

    Identities(AuthProperties properties, PolarionClient polarion, SyncProperties sync, SessionCookies cookies, Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.polarion = Objects.requireNonNull(polarion);
        this.sync = Objects.requireNonNull(sync);
        this.cookies = Objects.requireNonNull(cookies);
        this.clock = Objects.requireNonNull(clock);
    }

    public AuthProperties properties() {
        return this.properties;
    }

    /** The request's principal, empty for an anonymous request. */
    public Optional<Principal> resolve(HttpServletRequest request) {
        Optional<Principal> session = this.cookies.read(cookie(request, SessionCookies.COOKIE));
        if (session.isPresent()) {
            return session;
        }
        PolarionToken token = PolarionToken.fromAuthorizationHeader(request.getHeader("Authorization"));
        if (token != null) {
            try {
                Optional<Principal> owner = this.fromToken(token);
                if (owner.isPresent()) {
                    return owner;
                }
            } catch (RuntimeException e) {
                this.logger.warn("Could not ask Polarion who owns a token: {}", e.getMessage());
            }
        }
        if (this.properties.dev()) {
            String header = CurrentUser.clean(request.getHeader(CurrentUser.HEADER));
            if (header != null) {
                String roles = request.getHeader(ROLES_HEADER);
                return Optional.of(Principal.dev(header, roles == null ? List.of() : Arrays.asList(roles.split(","))));
            }
            String typed = cookie(request, CurrentUser.COOKIE);
            String name = typed == null ? null : CurrentUser.clean(URLDecoder.decode(typed, StandardCharsets.UTF_8));
            if (name != null) {
                return Optional.of(Principal.dev(name, List.of()));
            }
        }
        return Optional.empty();
    }

    /** Verifies a token against Polarion (login). */
    public Principal login(PolarionToken token) {
        Optional<Principal> owner = this.ask(token);
        if (owner.isEmpty()) {
            throw new InvalidTokenException("Polarion 不认这个令牌（已过期、已吊销或输错）");
        }
        this.byTokenHash.put(hash(token), new Cached(owner.get(), this.clock.millis()));
        return owner.get();
    }

    /** The owner of a token: cached, else asked from Polarion; empty when Polarion refuses the token. */
    public Optional<Principal> fromToken(PolarionToken token) {
        String key = hash(token);
        long now = this.clock.millis();
        Cached cached = this.byTokenHash.get(key);
        if (cached != null && now - cached.at() < this.properties.getTokenCacheTtl().toMillis()) {
            return Optional.of(cached.principal());
        }
        if (this.byTokenHash.size() > 10_000) {
            this.byTokenHash.values().removeIf(c -> now - c.at() >= this.properties.getTokenCacheTtl().toMillis());
        }
        Optional<Principal> owner = this.ask(token);
        owner.ifPresentOrElse(p -> this.byTokenHash.put(key, new Cached(p, now)), () -> this.byTokenHash.remove(key));
        return owner;
    }

    /**
     * The principal of a user Polarion single sign-on vouched for: their name and roles read with the service account
     * ({@code POLARION_SYNC_TOKEN}), as a token login reads them with the user's own token.
     *
     * @throws IllegalStateException when no service account is configured
     */
    public Optional<Principal> fromPolarionUser(String userId) {
        String service = this.sync.getSyncToken();
        if (service == null || service.isBlank()) {
            throw new IllegalStateException("Polarion 单点登录需要服务账号令牌 POLARION_SYNC_TOKEN 来读取用户角色");
        }
        return this.polarion.user(userId, new PolarionToken(service)).map(this::principal);
    }

    private Optional<Principal> ask(PolarionToken token) {
        return this.polarion.currentUser(token).map(this::principal);
    }

    /**
     * A Polarion user as a principal. Only the roles in Polarion projects bound to a model are kept (an administrator's
     * roles in every project would not fit in a cookie, and only bound projects use them).
     */
    Principal principal(PolarionClient.PolarionUser user) {
        Set<String> bound = this.sync.getProjects().keySet();
        Map<String, Set<String>> projects = new TreeMap<>(Principal.projects(user.projectRoles()));
        projects.keySet().retainAll(bound);
        return new Principal(user.id(), user.name(), Set.copyOf(user.globalRoles()), projects, "polarion");
    }

    static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private static String hash(PolarionToken token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
