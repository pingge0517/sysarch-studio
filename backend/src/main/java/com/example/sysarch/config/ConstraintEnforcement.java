package com.example.sysarch.config;

import com.example.sysarch.identity.Editors;
import com.example.sysarch.lock.EditAdmission;
import com.example.sysarch.lock.LockEnforcement;
import com.example.sysarch.lock.LockService;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.session.SessionService;
import com.example.sysarch.validation.ChangeGuard;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.SysarchEValidator;
import com.example.sysarch.validation.VetoNotices;
import com.example.sysarch.validation.ValidationError;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.api.IInputPostProcessor;
import org.eclipse.sirius.components.collaborative.api.IInputPreProcessor;
import org.eclipse.sirius.components.collaborative.editingcontext.api.IChangeDescriptionConsumer;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Sinks;

/**
 * Wires the constraint checks into Sirius Web's edit pipeline (T6 step 2), per the decision "按约束分开处理".
 *
 * <ul>
 * <li>{@link Registration}: {@link SysarchEValidator} in Sirius' {@code EValidator.Registry}. The properties view then
 * marks violated fields red and the Validation view lists violations (NameNotEmpty, InterfaceTypeRequired, and
 * everything else).</li>
 * <li>{@link Guard}: rolls back edits that introduce a {@link ChangeGuard#VETOED} violation, <i>before</i> Sirius
 * refreshes representations or saves. Sirius has no veto hook, so this uses its pipeline order: input
 * pre-processors run before the handler, and every consumer's {@code preAccept} runs before any {@code accept}
 * (refresh) or {@code postAccept} (save, {@code EditingContextSaver}). The handler's change is recorded with an EMF
 * {@link ChangeRecorder} from pre-processing to {@code preAccept}.</li>
 * <li>{@link Guard} also applies the element locks (P2 T1): an edit sent by a user (Sirius GraphQL, recorded by
 * {@code GraphQLEditorFilter}) that changes an element someone else holds is rolled back the same way, with the holder
 * in the notice; an edit that goes through gives its user a lease on what it changed ({@link LockEnforcement}). A
 * user beyond the project's limit of concurrent editors is refused the same way (P2 T2, {@link EditAdmission}).</li>
 * </ul>
 *
 * <p>Limitation: by {@code preAccept} the handler has already answered the client, so a vetoed edit cannot show a
 * message there. The user sees the value revert, and the reason is logged at WARN.
 */
public final class ConstraintEnforcement {

    private ConstraintEnforcement() {
    }

    /** Registers the sysarch EMF validator with Sirius Web. */
    @Configuration
    public static class Registration {

        public Registration(EValidator.Registry eValidatorRegistry, ConstraintValidator constraints, VetoNotices notices) {
            eValidatorRegistry.put(SysarchPackage.eINSTANCE, new SysarchEValidator(constraints, notices));
        }
    }

    /** Records each input's model changes and vetoes those that introduce a vetoed violation. */
    @Service
    public static class Guard implements IInputPreProcessor, IInputPostProcessor, IChangeDescriptionConsumer {

        private final Logger logger = LoggerFactory.getLogger(Guard.class);

        private final ChangeGuard guard;

        private final VetoNotices notices;

        /** One recorder per editing context; Sirius processes an editing context's inputs one at a time. */
        private final Map<String, ChangeRecorder> recorders = new ConcurrentHashMap<>();

        private final Editors editors;

        private final LockEnforcement locks;

        private final EditAdmission admission;

        public Guard(ConstraintValidator constraints, VetoNotices notices, Editors editors, LockEnforcement locks, EditAdmission admission) {
            this.guard = new ChangeGuard(Objects.requireNonNull(constraints));
            this.notices = Objects.requireNonNull(notices);
            this.editors = Objects.requireNonNull(editors);
            this.locks = Objects.requireNonNull(locks);
            this.admission = Objects.requireNonNull(admission);
        }

        @Override
        public IInput preProcess(IEditingContext editingContext, IInput input, Sinks.Many<ChangeDescription> changeDescriptionSink) {
            resourceSet(editingContext).ifPresent(resourceSet -> this.startRecording(editingContext.getId(), resourceSet));
            return input;
        }

        @Override
        public void preAccept(Sinks.Many<IPayload> payloadSink, Sinks.Many<Boolean> canBeDisposedSink, IEditingContext editingContext,
                ChangeDescription changeDescription) {
            if (!ChangeKind.SEMANTIC_CHANGE.equals(changeDescription.getKind())) {
                return;
            }
            ChangeRecorder recorder = this.recorders.remove(editingContext.getId());
            var resourceSet = resourceSet(editingContext);
            if (recorder == null || resourceSet.isEmpty()) {
                return;
            }
            var change = recorder.endRecording();
            recorder.dispose();
            var edited = ChangeGuard.edited(change); // before check: a rollback reverses the change description
            var editor = this.editors.of(changeDescription.getInput().id());
            var project = this.locks.projectOf(editingContext.getId());
            if (editor.isPresent() && project.isPresent()) {
                try {
                    this.admission.check(project.get(), editor.get(), change, "Sirius Web");
                } catch (LockService.LockedException | SessionService.FullException refusal) {
                    change.applyAndReverse();
                    List<ValidationError> reasons = EditAdmission.reasons(refusal);
                    this.notices.record(resourceSet.get(), edited, reasons);
                    this.logger.warn("Rejected edit {} by {} in editing context {}: {}", changeDescription.getInput().getClass().getSimpleName(),
                            editor.get().label(), editingContext.getId(), reasons.stream().map(ValidationError::message).collect(Collectors.joining("; ")));
                    this.startRecording(editingContext.getId(), resourceSet.get());
                    return;
                }
            }
            List<ValidationError> introduced = this.guard.check(resourceSet.get(), change);
            if (introduced.isEmpty() && project.isPresent()) {
                if (editor.isPresent()) {
                    this.admission.committed(project.get(), editor.get(), change);
                } else {
                    this.locks.deletedBySystem(project.get(), change);
                }
            }
            // The client already has its answer, so the reason goes onto the edited fields until the next edit.
            this.notices.record(resourceSet.get(), edited, introduced);
            if (!introduced.isEmpty()) {
                this.logger.warn("Rejected edit {} in editing context {}: {}", changeDescription.getInput().getClass().getSimpleName(), editingContext.getId(),
                        introduced.stream().map(e -> e.code() + " " + e.message()).collect(Collectors.joining("; ")));
            }
            // An input can emit several semantic changes; keep recording the rest of it.
            this.startRecording(editingContext.getId(), resourceSet.get());
        }

        @Override
        public void postProcess(IEditingContext editingContext, IInput input, Sinks.Many<ChangeDescription> changeDescriptionSink) {
            ChangeRecorder recorder = this.recorders.remove(editingContext.getId());
            if (recorder != null) {
                recorder.endRecording();
                recorder.dispose();
            }
        }

        private void startRecording(String editingContextId, ResourceSet resourceSet) {
            ChangeRecorder previous = this.recorders.put(editingContextId, new ChangeRecorder(resourceSet));
            if (previous != null) {
                previous.dispose();
            }
        }

        private static Optional<ResourceSet> resourceSet(IEditingContext editingContext) {
            return editingContext instanceof IEMFEditingContext emf ? Optional.of(emf.getDomain().getResourceSet()) : Optional.empty();
        }
    }
}
