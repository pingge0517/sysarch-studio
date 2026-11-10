package com.example.sysarch.lock;

import com.example.sysarch.audit.AuditLog;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Element lease locks (P2 §2.2, T1), in {@code edit_lock}. All state is in the database, never in memory, so any
 * backend instance sees the same locks and a restart loses none.
 *
 * <ul>
 * <li>A lock is held while {@code expires_at} is in the future. An expired row is free whatever the sweep did: every
 * acquisition and check compares with the clock, and {@link #sweep()} only removes the rows (every
 * minute).</li>
 * <li>Acquisition is all or nothing for up to {@value #MAX_BATCH} elements, in one transaction: one element held by
 * someone else fails the whole batch with the list of holders. Taking a lock you already hold renews it.</li>
 * <li>No pre-emption. Only a forced release (audited) takes a lock away from its owner.</li>
 * </ul>
 */
@Service
public class LockService {

    public static final int MAX_BATCH = 50;

    public static final int DEFAULT_LEASE = 90;

    public static final int MIN_LEASE = 30;

    public static final int MAX_LEASE = 300;

    /** A held lock. */
    public record Lock(String id, String projectId, String elementId, String ownerUser, int leaseSeconds, OffsetDateTime acquiredAt,
            OffsetDateTime expiresAt) {
    }

    /** An element someone else holds. */
    public record Conflict(String elementId, String ownerUser, OffsetDateTime expiresAt) {
    }

    /** Thrown when some of the requested elements are held by someone else (423 at the API). */
    public static class LockedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient List<Conflict> conflicts;

        public LockedException(List<Conflict> conflicts) {
            super(conflicts.stream().map(LockService::describe).collect(Collectors.joining("；")));
            this.conflicts = List.copyOf(conflicts);
        }

        public List<Conflict> conflicts() {
            return this.conflicts;
        }
    }

    /** A bad request (400 at the API). */
    public static class InvalidLockRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InvalidLockRequestException(String message) {
            super(message);
        }
    }

    /** The lock does not exist or has expired (404 at the API). */
    public static class NoSuchLockException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoSuchLockException(String message) {
            super(message);
        }
    }

    /** The lock belongs to someone else (403 at the API). */
    public static class NotOwnerException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NotOwnerException(String message) {
            super(message);
        }
    }

    /** Portable (PostgreSQL and the tests' H2): a concurrent insert of the same element fails on the unique key. */
    private static final String INSERT_IF_ABSENT = "INSERT INTO edit_lock (id, project_id, element_id, owner_user, lease_seconds, acquired_at, expires_at) "
            + "SELECT :id, :p, :e, :o, :l, :now, :x WHERE NOT EXISTS (SELECT 1 FROM edit_lock WHERE project_id = :p AND element_id = :e)";

    /** Attempts of one acquisition that lost a race on the unique key (each is a fresh transaction). */
    private static final int ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(LockService.class);

    private final JdbcClient jdbc;

    private final TransactionTemplate transaction;

    private final AuditLog audit;

    private final Clock clock;

    @Autowired
    public LockService(JdbcClient jdbc, TransactionTemplate transaction, AuditLog audit) {
        this(jdbc, transaction, audit, Clock.systemUTC());
    }

    /** With a given clock (tests). */
    public LockService(JdbcClient jdbc, TransactionTemplate transaction, AuditLog audit, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transaction = Objects.requireNonNull(transaction);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Takes or renews the locks of all these elements for {@code owner}, or none of them.
     *
     * @throws LockedException with every element someone else holds
     */
    public List<Lock> acquire(String projectId, Collection<String> elementIds, String owner, Integer leaseSeconds) {
        int lease = leaseSeconds == null ? DEFAULT_LEASE : leaseSeconds;
        if (lease < MIN_LEASE || lease > MAX_LEASE) {
            throw new InvalidLockRequestException("leaseSeconds must be between " + MIN_LEASE + " and " + MAX_LEASE);
        }
        if (owner == null || owner.isBlank()) {
            throw new InvalidLockRequestException("ownerUser is required");
        }
        // Sorted: two batches over the same elements always take the rows in the same order, so they cannot deadlock.
        TreeSet<String> ids = new TreeSet<>();
        elementIds.stream().filter(Objects::nonNull).map(String::strip).filter(id -> !id.isEmpty()).forEach(ids::add);
        if (ids.isEmpty()) {
            throw new InvalidLockRequestException("elementIds must not be empty");
        }
        if (ids.size() > MAX_BATCH) {
            throw new InvalidLockRequestException("at most " + MAX_BATCH + " elements per request (" + ids.size() + " given); larger edits go through an edit session");
        }
        for (int attempt = 1;; attempt++) {
            try {
                return this.tryAcquire(projectId, ids, owner, lease);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // Someone inserted one of these elements between our check and our insert; the transaction rolled back.
                // A new attempt sees their row and answers with the holder.
                if (attempt == ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private List<Lock> tryAcquire(String projectId, TreeSet<String> ids, String owner, int lease) {
        return this.transaction.execute(status -> {
            Instant now = this.clock.instant();
            OffsetDateTime expires = at(now.plusSeconds(lease));
            this.jdbc.sql("DELETE FROM edit_lock WHERE project_id = :p AND element_id IN (:ids) AND expires_at <= :now")
                    .param("p", projectId).param("ids", ids).param("now", at(now)).update();
            for (String id : ids) {
                this.jdbc.sql(INSERT_IF_ABSENT)
                        .param("id", UUID.randomUUID().toString()).param("p", projectId).param("e", id).param("o", owner).param("l", lease)
                        .param("now", at(now)).param("x", expires).update();
            }
            List<Lock> rows = this.rows(projectId, ids);
            List<Conflict> conflicts = rows.stream().filter(l -> !l.ownerUser().equals(owner))
                    .map(l -> new Conflict(l.elementId(), l.ownerUser(), l.expiresAt())).toList();
            if (!conflicts.isEmpty()) {
                status.setRollbackOnly();
                throw new LockedException(conflicts);
            }
            // Locks already held by the owner: renewed with the new lease.
            this.jdbc.sql("UPDATE edit_lock SET lease_seconds = :l, expires_at = :x WHERE project_id = :p AND element_id IN (:ids) AND owner_user = :o")
                    .param("l", lease).param("x", expires).param("p", projectId).param("ids", ids).param("o", owner).update();
            return this.rows(projectId, ids);
        });
    }

    /** Extends a lock by its lease (the 45 s heartbeat, {@code PUT /locks/{id}}). */
    public Lock renew(String lockId, String owner) {
        Lock lock = this.held(lockId).orElseThrow(() -> new NoSuchLockException("lock " + lockId + " does not exist or has expired"));
        if (!lock.ownerUser().equals(owner)) {
            throw new NotOwnerException("lock " + lockId + " is held by " + lock.ownerUser());
        }
        OffsetDateTime expires = at(this.clock.instant().plusSeconds(lock.leaseSeconds()));
        int updated = this.jdbc.sql("UPDATE edit_lock SET expires_at = :x WHERE id = :id AND owner_user = :o AND expires_at > :now")
                .param("x", expires).param("id", lockId).param("o", owner).param("now", at(this.clock.instant())).update();
        if (updated == 0) {
            throw new NoSuchLockException("lock " + lockId + " has expired");
        }
        return this.held(lockId).orElseThrow();
    }

    /**
     * Releases the lock of one element. Only its owner can, unless {@code force} (administrators, P2 §2.3; audited).
     *
     * @return whether a held lock was released (false: there was none, which is not an error)
     */
    public boolean release(String projectId, String elementId, String actor, boolean force) {
        Optional<Lock> lock = this.heldOn(projectId, elementId);
        if (lock.isEmpty()) {
            return false;
        }
        return this.release(lock.get(), actor, force);
    }

    /** All of a user's locks in a project end (leaving a branch workspace, P2 T3 step 3). */
    public int releaseAll(String projectId, String owner) {
        return this.jdbc.sql("DELETE FROM edit_lock WHERE project_id = :p AND owner_user = :o").param("p", projectId).param("o", owner).update();
    }

    /** Every lock of a project ends (its branch workspace is deleted, P2 T3). */
    public int dropProject(String projectId) {
        return this.jdbc.sql("DELETE FROM edit_lock WHERE project_id = :p").param("p", projectId).update();
    }

    /** Same, by lock id. */
    /** A held lock by id. */
    public java.util.Optional<Lock> find(String lockId) {
        return this.held(lockId);
    }

    public boolean releaseById(String lockId, String actor, boolean force) {
        Optional<Lock> lock = this.held(lockId);
        return lock.isPresent() && this.release(lock.get(), actor, force);
    }

    private boolean release(Lock lock, String actor, boolean force) {
        if (!force && !lock.ownerUser().equals(actor)) {
            throw new NotOwnerException(describe(new Conflict(lock.elementId(), lock.ownerUser(), lock.expiresAt())) + "；只有持锁人或管理员（force=true）可以释放");
        }
        this.transaction.executeWithoutResult(status -> {
            this.jdbc.sql("DELETE FROM edit_lock WHERE id = :id").param("id", lock.id()).update();
            if (force && !lock.ownerUser().equals(actor)) {
                this.audit.append(lock.projectId(), actor, AuditLog.LOCK_FORCE_RELEASE, lock.elementId(),
                        "lock " + lock.id() + " of " + lock.ownerUser() + ", expiring " + lock.expiresAt());
            }
        });
        if (force && !lock.ownerUser().equals(actor)) {
            this.logger.warn("Lock on {} in project {} held by {} force-released by {}", lock.elementId(), lock.projectId(), lock.ownerUser(), actor);
        }
        return true;
    }

    /** Every held lock of a project, by element id. */
    public List<Lock> list(String projectId) {
        return this.jdbc.sql("SELECT * FROM edit_lock WHERE project_id = :p AND expires_at > :now ORDER BY element_id")
                .param("p", projectId).param("now", at(this.clock.instant())).query(LockService::lock).list();
    }

    /** The held locks on these elements, by element id. */
    public Map<String, Lock> held(String projectId, Collection<String> elementIds) {
        if (elementIds.isEmpty()) {
            return Map.of();
        }
        return this.jdbc.sql("SELECT * FROM edit_lock WHERE project_id = :p AND element_id IN (:ids) AND expires_at > :now")
                .param("p", projectId).param("ids", elementIds).param("now", at(this.clock.instant())).query(LockService::lock).list()
                .stream().collect(Collectors.toMap(Lock::elementId, l -> l));
    }

    /**
     * Server-managed leases (user's decision "编辑即自动加锁"): after an edit in Sirius, its user holds the edited
     * elements for at least {@value #DEFAULT_LEASE} s more. Elements someone else holds are left alone (the edit was
     * checked against them before); a longer lease the user took explicitly is not shortened.
     */
    public void touch(String projectId, Collection<String> elementIds, String owner) {
        if (elementIds.isEmpty() || owner == null) {
            return;
        }
        try {
            this.tryTouch(projectId, elementIds, owner);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            this.tryTouch(projectId, elementIds, owner); // lost a race for an element: once more, now it is someone's
        }
    }

    private void tryTouch(String projectId, Collection<String> elementIds, String owner) {
        Instant now = this.clock.instant();
        OffsetDateTime expires = at(now.plusSeconds(DEFAULT_LEASE));
        this.transaction.executeWithoutResult(status -> {
            this.jdbc.sql("DELETE FROM edit_lock WHERE project_id = :p AND element_id IN (:ids) AND expires_at <= :now")
                    .param("p", projectId).param("ids", elementIds).param("now", at(now)).update();
            for (String id : new TreeSet<>(elementIds)) {
                this.jdbc.sql(INSERT_IF_ABSENT)
                        .param("id", UUID.randomUUID().toString()).param("p", projectId).param("e", id).param("o", owner).param("l", DEFAULT_LEASE)
                        .param("now", at(now)).param("x", expires).update();
            }
            this.jdbc.sql("UPDATE edit_lock SET expires_at = :x WHERE project_id = :p AND element_id IN (:ids) AND owner_user = :o AND expires_at < :x")
                    .param("x", expires).param("p", projectId).param("ids", elementIds).param("o", owner).update();
        });
    }

    /** Locks on deleted elements end with them (P2 §2.3). */
    public void dropElements(String projectId, Collection<String> elementIds) {
        if (!elementIds.isEmpty()) {
            this.jdbc.sql("DELETE FROM edit_lock WHERE project_id = :p AND element_id IN (:ids)").param("p", projectId).param("ids", elementIds).update();
        }
    }

    /** The recovery job (P2 §2.4, T1 step 2): every minute, delete the expired rows. */
    @Scheduled(fixedDelayString = "${sysarch.locks.sweep-ms:60000}", initialDelayString = "${sysarch.locks.sweep-ms:60000}")
    public int sweep() {
        int removed = this.jdbc.sql("DELETE FROM edit_lock WHERE expires_at <= :now").param("now", at(this.clock.instant())).update();
        if (removed > 0) {
            this.logger.debug("Removed {} expired edit locks", removed);
        }
        return removed;
    }

    public static String describe(Conflict conflict) {
        return conflict.elementId() + " 已被 " + conflict.ownerUser() + " 锁定（至 "
                + conflict.expiresAt().atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "）";
    }

    private Optional<Lock> held(String lockId) {
        return this.jdbc.sql("SELECT * FROM edit_lock WHERE id = :id AND expires_at > :now").param("id", lockId).param("now", at(this.clock.instant()))
                .query(LockService::lock).optional();
    }

    private Optional<Lock> heldOn(String projectId, String elementId) {
        return Optional.ofNullable(this.held(projectId, List.of(elementId)).get(elementId));
    }

    private List<Lock> rows(String projectId, Collection<String> ids) {
        return new ArrayList<>(this.jdbc.sql("SELECT * FROM edit_lock WHERE project_id = :p AND element_id IN (:ids) ORDER BY element_id")
                .param("p", projectId).param("ids", ids).query(LockService::lock).list());
    }

    private static Lock lock(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Lock(rs.getString("id"), rs.getString("project_id"), rs.getString("element_id"), rs.getString("owner_user"), rs.getInt("lease_seconds"),
                rs.getObject("acquired_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class));
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
