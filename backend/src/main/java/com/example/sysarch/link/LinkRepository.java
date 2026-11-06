package com.example.sysarch.link;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code polarion_link} table: one row per bound model element (spec T7 step 3). {@code element_id} is the key,
 * so an element is bound to at most one Work Item.
 */
@Repository
public class LinkRepository {

    private static final String COLUMNS = "element_id, element_type, work_item_id, work_item_type, project_id, state, model_rev, wi_rev, etag, "
            + "last_push_at, last_pull_at, pushed_fields, work_item_title";

    private final JdbcClient jdbc;

    public LinkRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public Optional<StoredLink> find(String elementId) {
        return this.jdbc.sql("SELECT " + COLUMNS + " FROM polarion_link WHERE element_id = :id")
                .param("id", elementId)
                .query(StoredLink.class)
                .optional();
    }

    /** All links of a project, optionally only those in {@code state}, ordered by element id. */
    public List<StoredLink> findByProject(String projectId, String state) {
        String sql = "SELECT " + COLUMNS + " FROM polarion_link WHERE project_id = :project" + (state == null ? "" : " AND state = :state")
                + " ORDER BY element_id";
        var statement = this.jdbc.sql(sql).param("project", projectId);
        if (state != null) {
            statement = statement.param("state", state);
        }
        return statement.query(StoredLink.class).list();
    }

    public void insert(StoredLink link) {
        // created_at is filled by the database default (JdbcClient cannot mix a parameter source with named parameters).
        this.jdbc.sql("INSERT INTO polarion_link (" + COLUMNS + ") VALUES (:elementId, :elementType, :workItemId, :workItemType, :projectId, "
                + ":state, :modelRev, :wiRev, :etag, :lastPushAt, :lastPullAt, :pushedFields, :workItemTitle)")
                .paramSource(link)
                .update();
        // A bound element is no longer waiting to be claimed (R1 repair, spec §7).
        this.jdbc.sql("DELETE FROM element_pending_claim WHERE element_id = :id").param("id", link.elementId()).update();
    }

    /** Writes every mutable column of an existing link. */
    public void update(StoredLink link) {
        this.jdbc.sql("UPDATE polarion_link SET element_type = :elementType, work_item_id = :workItemId, work_item_type = :workItemType, "
                + "project_id = :projectId, state = :state, model_rev = :modelRev, wi_rev = :wiRev, etag = :etag, last_push_at = :lastPushAt, "
                + "last_pull_at = :lastPullAt, pushed_fields = :pushedFields, work_item_title = :workItemTitle WHERE element_id = :elementId")
                .paramSource(link)
                .update();
    }

    /** @return true if a link was removed */
    public boolean delete(String elementId) {
        return this.jdbc.sql("DELETE FROM polarion_link WHERE element_id = :id").param("id", elementId).update() > 0;
    }

    /**
     * A row of {@code polarion_link}.
     *
     * @param pushedFields JSON object of the model-owned field values last written to Polarion, for conflict detection
     * @param workItemTitle the Polarion-owned title, shown read-only in the model
     */
    public record StoredLink(String elementId, String elementType, String workItemId, String workItemType, String projectId, String state,
            Integer modelRev, String wiRev, String etag, OffsetDateTime lastPushAt, OffsetDateTime lastPullAt, String pushedFields,
            String workItemTitle) {

        public StoredLink withState(String newState) {
            return new StoredLink(this.elementId, this.elementType, this.workItemId, this.workItemType, this.projectId, newState, this.modelRev,
                    this.wiRev, this.etag, this.lastPushAt, this.lastPullAt, this.pushedFields, this.workItemTitle);
        }
    }
}
