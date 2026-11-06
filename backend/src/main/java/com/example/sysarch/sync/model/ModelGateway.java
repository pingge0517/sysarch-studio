package com.example.sysarch.sync.model;

import com.example.sysarch.validation.ValidationError;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * How sync reads and writes the model. Production goes through Sirius Web's editing context
 * ({@link SiriusModelGateway}), because spec §2.3 forbids writing around it; PolarionSyncIT uses an in-memory model.
 */
public interface ModelGateway {

    /**
     * The elements with these ids in the model of a Polarion project. Ids that do not exist are absent from the
     * result.
     *
     * @throws UnmappedProjectException if no Sirius project is configured for {@code polarionProjectId}
     */
    Map<String, ElementSnapshot> read(String polarionProjectId, Collection<String> elementIds);

    /**
     * Writes Polarion-owned values into the model (spec §6.2: a Requirement's text). It runs as one edit, so it is
     * saved, refreshed and checked like any other.
     *
     * @param changes element id to field name to new value; only {@link ElementSnapshot#TEXT} is supported
     */
    void applyPolarionOwned(String polarionProjectId, Map<String, Map<String, String>> changes);

    /**
     * What sync needs of one element, read inside the editing context.
     *
     * @param type the EClass name, e.g. {@code PartDefinition}
     * @param sourceId for a Connection, the element id of its source port; null otherwise
     * @param targetId for a Connection, the element id of its target port; null otherwise
     * @param text for a Requirement, its text (Polarion-owned); null otherwise
     * @param rev the element's revision counter
     * @param errors constraint violations, for the push precheck
     */
    record ElementSnapshot(String id, String type, String name, String layer, List<String> stereotypes, String sourceId, String targetId, String text,
            Integer rev, List<ValidationError> errors) {

        public static final String TEXT = "text";

        public ElementSnapshot {
            stereotypes = List.copyOf(stereotypes);
            errors = List.copyOf(errors);
        }
    }

    /** No Sirius project is configured for a Polarion project id ({@code sysarch.polarion.projects}). */
    final class UnmappedProjectException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnmappedProjectException(String polarionProjectId) {
            super("no Sirius Web project is configured for Polarion project '" + polarionProjectId + "' (sysarch.polarion.projects."
                    + polarionProjectId + ")");
        }
    }
}
