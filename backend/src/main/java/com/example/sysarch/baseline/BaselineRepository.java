package com.example.sysarch.baseline;

import com.example.sysarch.baseline.ModelSnapshot.ElementState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code baseline} and {@code baseline_element} (spec P1 §4.2): a baseline row and its element states. */
@Repository
public class BaselineRepository {

    /** A stored baseline (the {@code baseline} row). */
    public record StoredBaseline(String id, String projectId, String label, String description, int modelRev, int elementCount, String createdBy,
            OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    private final ObjectMapper json;

    /** {@code attributes} is jsonb on PostgreSQL, which takes text only with a cast; text on the tests' H2. */
    private final String attributesValue;

    public BaselineRepository(JdbcClient jdbc, ObjectMapper json, DataSource dataSource) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
        this.attributesValue = isPostgres(dataSource) ? "CAST(:attributes AS jsonb)" : ":attributes";
    }

    public void insert(StoredBaseline baseline, Collection<ElementState> elements) {
        this.jdbc.sql("INSERT INTO baseline (id, project_id, label, description, model_rev, element_count, created_by, created_at) "
                + "VALUES (:id, :project, :label, :description, :rev, :count, :by, :at)")
                .param("id", baseline.id()).param("project", baseline.projectId()).param("label", baseline.label())
                .param("description", baseline.description()).param("rev", baseline.modelRev()).param("count", baseline.elementCount())
                .param("by", baseline.createdBy()).param("at", baseline.createdAt())
                .update();
        for (ElementState element : elements) {
            this.jdbc.sql("INSERT INTO baseline_element (baseline_id, element_id, element_type, name, layer, attributes) "
                    + "VALUES (:baseline, :id, :type, :name, :layer, " + this.attributesValue + ")")
                    .param("baseline", baseline.id()).param("id", element.id()).param("type", element.type()).param("name", element.name())
                    .param("layer", element.layer()).param("attributes", this.write(element.attributes()))
                    .update();
        }
    }

    /** The project's baselines, newest first. */
    public List<StoredBaseline> list(String projectId) {
        return this.jdbc.sql("SELECT id, project_id, label, description, model_rev, element_count, created_by, created_at FROM baseline "
                + "WHERE project_id = :p ORDER BY created_at DESC, id").param("p", projectId).query(this::baseline).list();
    }

    public Optional<StoredBaseline> find(String id) {
        return this.jdbc.sql("SELECT id, project_id, label, description, model_rev, element_count, created_by, created_at FROM baseline WHERE id = :id")
                .param("id", id).query(this::baseline).optional();
    }

    public Map<String, ElementState> elements(String baselineId) {
        Map<String, ElementState> elements = new TreeMap<>();
        this.jdbc.sql("SELECT element_id, element_type, name, layer, attributes FROM baseline_element WHERE baseline_id = :b").param("b", baselineId)
                .query((rs, i) -> new ElementState(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), this.read(rs.getString(5))))
                .list().forEach(e -> elements.put(e.id(), e));
        return elements;
    }

    private StoredBaseline baseline(java.sql.ResultSet rs, int row) throws SQLException {
        return new StoredBaseline(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7),
                rs.getObject(8, OffsetDateTime.class));
    }

    private String write(Map<String, Object> attributes) {
        try {
            return this.json.writeValueAsString(new TreeMap<>(attributes));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> read(String text) {
        try {
            return text == null ? Map.of() : this.json.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable baseline attributes: " + text, e);
        }
    }

    private static boolean isPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read the database product", e);
        }
    }
}
