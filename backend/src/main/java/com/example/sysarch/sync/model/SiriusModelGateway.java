package com.example.sysarch.sync.model;

import com.example.sysarch.model.ArchElement;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.validation.ConstraintValidator;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Sinks;

/**
 * {@link ModelGateway} on Sirius Web: every read and write is an input dispatched to the project's editing context,
 * so it runs on that context's own thread (EMF models are not thread-safe) and writes are saved, refreshed and
 * checked like any user edit (spec §2.3: never write around the EditingContext). The operations themselves are
 * {@link ModelOperations}.
 *
 * <p>{@code sysarch.polarion.projects.<polarion project>} names the Sirius project: a Sirius project id, or else a
 * project name, looked up read-only in Sirius' {@code project} table.
 */
@Service
public class SiriusModelGateway implements ModelGateway {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final SyncProperties properties;

    private final IEditingContextEventProcessorRegistry editingContexts;

    private final IProjectEditingContextService projectEditingContexts;

    private final IProjectSearchService projects;

    private final JdbcClient jdbc;

    public SiriusModelGateway(SyncProperties properties, IEditingContextEventProcessorRegistry editingContexts,
            IProjectEditingContextService projectEditingContexts, IProjectSearchService projects, JdbcClient jdbc) {
        this.properties = Objects.requireNonNull(properties);
        this.editingContexts = Objects.requireNonNull(editingContexts);
        this.projectEditingContexts = Objects.requireNonNull(projectEditingContexts);
        this.projects = Objects.requireNonNull(projects);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public Map<String, ElementSnapshot> read(String polarionProjectId, Collection<String> elementIds) {
        IPayload payload = this.dispatch(polarionProjectId, new ReadElementsInput(UUID.randomUUID(), Set.copyOf(elementIds)));
        return ((ElementsPayload) payload).elements();
    }

    @Override
    public Map<String, ElementSnapshot> readAll(String polarionProjectId) {
        IPayload payload = this.dispatch(polarionProjectId, new ReadElementsInput(UUID.randomUUID(), null));
        return ((ElementsPayload) payload).elements();
    }

    @Override
    public void applyPolarionOwned(String polarionProjectId, Map<String, Map<String, String>> changes) {
        if (!changes.isEmpty()) {
            this.dispatch(polarionProjectId, new ApplyPolarionValuesInput(UUID.randomUUID(), Map.copyOf(changes)));
        }
    }

    @Override
    public String repairConnection(String polarionProjectId, ConnectionRepair repair) {
        IPayload payload = this.dispatch(polarionProjectId, new RepairConnectionInput(UUID.randomUUID(), repair));
        return ((RepairedPayload) payload).failure();
    }

    /** The Polarion project an editing context belongs to, if one is mapped to it. */
    public Optional<String> polarionProjectOf(String editingContextId) {
        for (String polarionProjectId : this.properties.getProjects().keySet()) {
            try {
                if (this.editingContextId(polarionProjectId).filter(editingContextId::equals).isPresent()) {
                    return Optional.of(polarionProjectId);
                }
            } catch (UnmappedProjectException e) {
                // a misconfigured entry maps to nothing
            }
        }
        return Optional.empty();
    }

    private Optional<String> editingContextId(String polarionProjectId) {
        return this.projectEditingContexts.getEditingContextId(this.siriusProjectId(polarionProjectId));
    }

    private IPayload dispatch(String polarionProjectId, IInput input) {
        String editingContextId = this.editingContextId(polarionProjectId).orElseThrow(() -> new UnmappedProjectException(polarionProjectId));
        IPayload payload = this.editingContexts.dispatchEvent(editingContextId, input).block(TIMEOUT);
        if (payload instanceof ErrorPayload error) {
            throw new IllegalStateException("model access failed: " + error.messages());
        }
        if (payload == null) {
            throw new IllegalStateException("model access timed out after " + TIMEOUT);
        }
        return payload;
    }

    private String siriusProjectId(String polarionProjectId) {
        String configured = this.properties.getProjects().get(polarionProjectId);
        if (configured == null || configured.isBlank()) {
            throw new UnmappedProjectException(polarionProjectId);
        }
        if (this.projects.existsById(configured)) {
            return configured;
        }
        List<String> byName = this.jdbc.sql("SELECT id FROM project WHERE name = :name").param("name", configured).query(String.class).list();
        if (byName.size() != 1) {
            throw new UnmappedProjectException(polarionProjectId + "' (configured as '" + configured + "', which matches " + byName.size()
                    + " Sirius projects");
        }
        return byName.get(0);
    }

    // ---- inputs, payloads and their handler (run inside the editing context) ----

    /** {@code elementIds} null means every element. */
    record ReadElementsInput(UUID id, Set<String> elementIds) implements IInput {
    }

    record ElementsPayload(UUID id, Map<String, ElementSnapshot> elements) implements IPayload {
    }

    record ApplyPolarionValuesInput(UUID id, Map<String, Map<String, String>> changes) implements IInput {
    }

    record AppliedPayload(UUID id, int applied) implements IPayload {
    }

    record RepairConnectionInput(UUID id, ConnectionRepair repair) implements IInput {
    }

    /** {@code failure} is null when the repair was applied. */
    record RepairedPayload(UUID id, String failure) implements IPayload {
    }

    /** Executes the gateway's inputs on the editing context thread. */
    @Service
    public static class Handler implements IEditingContextEventHandler {

        private final ConstraintValidator constraints;

        public Handler(ConstraintValidator constraints) {
            this.constraints = Objects.requireNonNull(constraints);
        }

        @Override
        public boolean canHandle(IEditingContext editingContext, IInput input) {
            return editingContext instanceof IEMFEditingContext
                    && (input instanceof ReadElementsInput || input instanceof ApplyPolarionValuesInput || input instanceof RepairConnectionInput);
        }

        @Override
        public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext,
                IInput input) {
            Map<String, ArchElement> index = ModelOperations.index(((IEMFEditingContext) editingContext).getDomain().getResourceSet().getAllContents());
            boolean changed = false;
            if (input instanceof ReadElementsInput read) {
                payloadSink.tryEmitValue(new ElementsPayload(input.id(), ModelOperations.snapshots(index, read.elementIds(), this.constraints)));
            } else if (input instanceof ApplyPolarionValuesInput apply) {
                int applied = ModelOperations.applyPolarionOwned(index, apply.changes());
                changed = applied > 0;
                payloadSink.tryEmitValue(new AppliedPayload(input.id(), applied));
            } else if (input instanceof RepairConnectionInput repair) {
                String failure = ModelOperations.repairConnection(index, repair.repair(), this.constraints);
                changed = failure == null;
                payloadSink.tryEmitValue(new RepairedPayload(input.id(), failure));
            }
            changeDescriptionSink.tryEmitNext(new ChangeDescription(changed ? ChangeKind.SEMANTIC_CHANGE : ChangeKind.NOTHING, editingContext.getId(), input));
        }
    }
}
