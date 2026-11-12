package com.example.sysarch.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Edit sessions and the per-project limit on concurrent writers (P2 §2.1, T2), in {@code edit_session}.
 *
 * <ul>
 * <li>One open session per user and project. The user's first edit in a project opens it (user's decision
 * "编辑即开会话"), every edit keeps it active, and it closes after {@code idle-timeout} (30 min) without an edit. Clients
 * may also open and close one explicitly ({@code /api/v1/sessions}).</li>
 * <li>At most {@code max-per-project} (30) open sessions per project: a further user is refused
 * ({@link FullException}, 429 at the API; in Sirius the edit is rolled back with "当前项目编辑人数已满").</li>
 * <li>Anonymous edits open no session and take no place, and are refused while the project is full (user's
 * decision).</li>
 * <li>Sessions are only about people; element locks stay in {@code lock} (spec T2 step 3).</li>
 * </ul>
 *
 * <p>Opening is serialized per project in this JVM, so two users can never both take the last place. The P2
 * deployment runs one backend instance (spec §7.3); several instances would need a database lock here.
 */
@Service
public class SessionService {

    /** An edit session. {@code closedAt} is null while open. */
    public record Session(String id, String projectId, String userId, OffsetDateTime openedAt, OffsetDateTime lastActiveAt, OffsetDateTime closedAt,
            String clientInfo) {
    }

    /** The project already has its maximum of open sessions (429 at the API). */
    public static class FullException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int open;

        private final int limit;

        public FullException(int open, int limit) {
            this("当前项目编辑人数已满（" + open + "/" + limit + "），请稍后再试", open, limit);
        }

        FullException(String message, int open, int limit) {
            super(message);
            this.open = open;
            this.limit = limit;
        }

        public int open() {
            return this.open;
        }

        public int limit() {
            return this.limit;
        }
    }

    /** No such open session (404 at the API). */
    public static class NoSuchSessionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoSuchSessionException(String message) {
            super(message);
        }
    }

    /** Activity is written at most this often per session: an edit every second must not mean a database write. */
    static final Duration ACTIVITY_RESOLUTION = Duration.ofSeconds(30);

    private final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private final JdbcClient jdbc;

    private final int limit;

    private final List<ProjectLimits> limits;

    private final Duration idleTimeout;

    private final Clock clock;

    private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();

    /** (project, user) → the open session and when its activity was last written. */
    private final Map<String, Recent> recent = new ConcurrentHashMap<>();

    private record Recent(Session session, Instant written) {
    }

    @Autowired
    public SessionService(JdbcClient jdbc, @Value("${sysarch.sessions.max-per-project:30}") int limit,
            @Value("${sysarch.sessions.idle-timeout:PT30M}") Duration idleTimeout, List<ProjectLimits> limits) {
        this(jdbc, limit, idleTimeout, Clock.systemUTC(), limits);
    }

    SessionService(JdbcClient jdbc, int limit, Duration idleTimeout, Clock clock) {
        this(jdbc, limit, idleTimeout, clock, List.of());
    }

    /** With a given clock and per-project limits (tests). */
    public SessionService(JdbcClient jdbc, int limit, Duration idleTimeout, Clock clock, List<ProjectLimits> limits) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.limit = limit;
        this.limits = List.copyOf(limits);
        this.idleTimeout = Objects.requireNonNull(idleTimeout);
        this.clock = Objects.requireNonNull(clock);
    }

    /** The default limit. */
    public int limit() {
        return this.limit;
    }

    /** The limit of one project: the default, or lower for a branch workspace (one writer, P2 T3). */
    public int limit(String projectId) {
        return this.limits.stream().map(l -> l.limit(projectId)).filter(java.util.OptionalInt::isPresent).mapToInt(java.util.OptionalInt::getAsInt)
                .min().orElse(this.limit);
    }

    /** The user's open session in the project is closed (leaving a branch workspace, P2 T3). */
    public void closeUser(String projectId, String user) {
        this.jdbc.sql("UPDATE edit_session SET closed_at = :now WHERE project_id = :p AND user_id = :u AND closed_at IS NULL")
                .param("now", at(this.clock.instant())).param("p", projectId).param("u", user).update();
        this.recent.remove(projectId + '\n' + user);
    }

    /** Every session of a project goes (the Sirius project was deleted); returns how many rows. */
    public int dropProject(String projectId) {
        this.recent.keySet().removeIf(key -> key.startsWith(projectId + '\n'));
        this.projectLocks.remove(projectId);
        return this.jdbc.sql("DELETE FROM edit_session WHERE project_id = :p").param("p", projectId).update();
    }

    private FullException full(String projectId, int open, int limit) {
        if (limit == 1) {
            String holders = this.jdbc.sql("SELECT user_id FROM edit_session WHERE project_id = :p AND closed_at IS NULL").param("p", projectId)
                    .query(String.class).list().stream().collect(java.util.stream.Collectors.joining("、"));
            return new FullException("该分支正由 " + holders + " 编辑，同一分支同时只允许一人写，请稍后再试", open, limit);
        }
        return new FullException(open, limit);
    }

    /**
     * The user edits the project: their open session, kept active, or a new one.
     *
     * @throws FullException when the user has no session and the project is full
     */
    public Session touch(String projectId, String user, String clientInfo) {
        Objects.requireNonNull(user);
        Instant now = this.clock.instant();
        String key = projectId + '\n' + user;
        Recent cached = this.recent.get(key);
        if (cached != null && Duration.between(cached.written(), now).compareTo(ACTIVITY_RESOLUTION) < 0) {
            return cached.session(); // active less than 30 s ago: cannot have idled out; closing it clears the cache
        }
        synchronized (this.projectLocks.computeIfAbsent(projectId, p -> new Object())) {
            this.closeIdle(projectId, now);
            Optional<Session> existing = this.jdbc
                    .sql("SELECT * FROM edit_session WHERE project_id = :p AND user_id = :u AND closed_at IS NULL ORDER BY opened_at DESC")
                    .param("p", projectId).param("u", user).query(SessionService::session).list().stream().findFirst();
            if (existing.isPresent()) {
                this.jdbc.sql("UPDATE edit_session SET last_active_at = :now WHERE id = :id").param("now", at(now)).param("id", existing.get().id()).update();
                Session session = this.open(existing.get().id()).orElseThrow();
                this.recent.put(key, new Recent(session, now));
                return session;
            }
            int open = this.countOpen(projectId);
            int limit = this.limit(projectId);
            if (open >= limit) {
                throw this.full(projectId, open, limit);
            }
            String id = UUID.randomUUID().toString();
            this.jdbc.sql("INSERT INTO edit_session (id, project_id, user_id, opened_at, last_active_at, client_info) VALUES (:id, :p, :u, :now, :now, :c)")
                    .param("id", id).param("p", projectId).param("u", user).param("now", at(now)).param("c", truncate(clientInfo)).update();
            Session session = this.open(id).orElseThrow();
            this.recent.put(key, new Recent(session, now));
            this.logger.debug("Opened edit session {} for {} in project {} ({}/{})", id, user, projectId, open + 1, limit);
            return session;
        }
    }

    /**
     * An edit nobody can be counted for (anonymous): allowed while the project has room.
     *
     * @throws FullException when the project is full
     */
    public void checkAnonymous(String projectId) {
        Instant now = this.clock.instant();
        synchronized (this.projectLocks.computeIfAbsent(projectId, p -> new Object())) {
            this.closeIdle(projectId, now);
            int open = this.countOpen(projectId);
            int limit = this.limit(projectId);
            if (open >= limit) {
                throw this.full(projectId, open, limit);
            }
        }
    }

    /** Closes a session (the user left). Only its user may. */
    public void close(String sessionId, String user) {
        Session session = this.open(sessionId).orElseThrow(() -> new NoSuchSessionException("no open session " + sessionId));
        if (!session.userId().equals(user)) {
            throw new NoSuchSessionException("session " + sessionId + " is not " + user + "'s");
        }
        this.jdbc.sql("UPDATE edit_session SET closed_at = :now WHERE id = :id AND closed_at IS NULL").param("now", at(this.clock.instant()))
                .param("id", sessionId).update();
        this.recent.remove(session.projectId() + '\n' + session.userId());
    }

    /** The open sessions of a project, oldest first. */
    public List<Session> list(String projectId) {
        this.closeIdle(projectId, this.clock.instant());
        return this.jdbc.sql("SELECT * FROM edit_session WHERE project_id = :p AND closed_at IS NULL ORDER BY opened_at").param("p", projectId)
                .query(SessionService::session).list();
    }

    /** Every minute: closes sessions idle for longer than the timeout (spec T2 step 1). */
    @Scheduled(fixedDelayString = "${sysarch.sessions.sweep-ms:60000}", initialDelayString = "${sysarch.sessions.sweep-ms:60000}")
    public int sweep() {
        Instant now = this.clock.instant();
        int closed = this.jdbc.sql("UPDATE edit_session SET closed_at = :now WHERE closed_at IS NULL AND last_active_at < :idle")
                .param("now", at(now)).param("idle", at(now.minus(this.idleTimeout))).update();
        if (closed > 0) {
            this.recent.clear();
        }
        return closed;
    }

    private void closeIdle(String projectId, Instant now) {
        int closed = this.jdbc.sql("UPDATE edit_session SET closed_at = :now WHERE project_id = :p AND closed_at IS NULL AND last_active_at < :idle")
                .param("now", at(now)).param("p", projectId).param("idle", at(now.minus(this.idleTimeout))).update();
        if (closed > 0) {
            this.recent.keySet().removeIf(k -> k.startsWith(projectId + '\n'));
        }
    }

    private int countOpen(String projectId) {
        return this.jdbc.sql("SELECT COUNT(*) FROM edit_session WHERE project_id = :p AND closed_at IS NULL").param("p", projectId)
                .query(Integer.class).single();
    }

    private Optional<Session> open(String sessionId) {
        return this.jdbc.sql("SELECT * FROM edit_session WHERE id = :id AND closed_at IS NULL").param("id", sessionId).query(SessionService::session)
                .optional().filter(s -> !s.lastActiveAt().toInstant().isBefore(this.clock.instant().minus(this.idleTimeout)));
    }

    private static Session session(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Session(rs.getString("id"), rs.getString("project_id"), rs.getString("user_id"), rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("last_active_at", OffsetDateTime.class), rs.getObject("closed_at", OffsetDateTime.class), rs.getString("client_info"));
    }

    private static String truncate(String clientInfo) {
        return clientInfo == null ? null : clientInfo.length() > 200 ? clientInfo.substring(0, 200) : clientInfo;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
