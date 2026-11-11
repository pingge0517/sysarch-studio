package com.example.sysarch.spike;

import com.example.sysarch.model.State;
import com.example.sysarch.model.StateMachine;
import com.example.sysarch.model.Transition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.components.collaborative.api.IRepresentationDescriptionsProvider;
import org.eclipse.sirius.components.collaborative.api.RepresentationDescriptionMetadata;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextRepresentationDescriptionProvider;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.diagrams.ArrowStyle;
import org.eclipse.sirius.components.diagrams.EdgeStyle;
import org.eclipse.sirius.components.diagrams.EdgeType;
import org.eclipse.sirius.components.diagrams.FreeFormLayoutStrategy;
import org.eclipse.sirius.components.diagrams.HeaderSeparatorDisplayMode;
import org.eclipse.sirius.components.diagrams.ImageNodeStyle;
import org.eclipse.sirius.components.diagrams.InsideLabelLocation;
import org.eclipse.sirius.components.diagrams.LabelOverflowStrategy;
import org.eclipse.sirius.components.diagrams.LabelTextAlign;
import org.eclipse.sirius.components.diagrams.LabelVisibility;
import org.eclipse.sirius.components.diagrams.LineStyle;
import org.eclipse.sirius.components.diagrams.ListLayoutStrategy;
import org.eclipse.sirius.components.diagrams.NodeType;
import org.eclipse.sirius.components.diagrams.RectangularNodeStyle;
import org.eclipse.sirius.components.diagrams.description.DiagramDescription;
import org.eclipse.sirius.components.diagrams.description.EdgeDescription;
import org.eclipse.sirius.components.diagrams.description.InsideLabelDescription;
import org.eclipse.sirius.components.diagrams.description.LabelDescription;
import org.eclipse.sirius.components.diagrams.description.LabelStyleDescription;
import org.eclipse.sirius.components.diagrams.description.NodeDescription;
import org.eclipse.sirius.components.diagrams.description.SynchronizationPolicy;
import org.eclipse.sirius.components.diagrams.renderer.DiagramRenderingCache;
import org.eclipse.sirius.components.diagrams.tools.Palette;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IRepresentationDescription;
import org.eclipse.sirius.components.representations.VariableManager;
import org.springframework.stereotype.Service;

/**
 * SPIKE (P3 T9, probe A, spec §6.1) — on branch spike/p3-sequence-probe only, never merged. Question: can Sirius Web
 * 2025.8 draw a usable sequence diagram (lifelines, messages, activation bars) with what it already has, no renderer of
 * our own? No metamodel change either: a state machine stands in for an interaction (non-initial, non-final states =
 * lifelines, transitions in list order = messages, trigger = message name).
 *
 * <p>Trick: each lifeline is a node with Sirius' vertical {@link ListLayoutStrategy}, holding one "slot" per message
 * (all lifelines the same number, in message order), so slot i sits at the same height on every lifeline; message i is
 * an edge from slot i of its sender to slot i of its receiver. A slot of a lifeline that takes part in the message shows
 * an activation bar, the others a dashed lifeline segment (two SVGs).
 */
@Service
public class SequenceProbe implements IEditingContextRepresentationDescriptionProvider, IRepresentationDescriptionsProvider {

    static final String ID = "sysarch-spike://sequence-probe";

    /** One cell of the grid: a lifeline at the height of a message; equal by value, so edges find their slot nodes. */
    public record Slot(State lifeline, Transition message, int row) {
    }

    private final IIdentityService identity;

    private final DiagramDescription description;

    public SequenceProbe(IIdentityService identity) {
        this.identity = identity;
        this.description = this.build();
    }

    @Override
    public List<IRepresentationDescription> getRepresentationDescriptions(IEditingContext editingContext) {
        return List.of(this.description);
    }

    @Override
    public boolean canHandle(IRepresentationDescription representationDescription) {
        return ID.equals(representationDescription.getId());
    }

    @Override
    public List<RepresentationDescriptionMetadata> handle(IEditingContext editingContext, Object object, IRepresentationDescription representationDescription) {
        return List.of(new RepresentationDescriptionMetadata(ID, "时序图探针（Spike）", "时序图探针（Spike）"));
    }

    private static List<State> lifelines(StateMachine machine) {
        return machine.getStates().stream().filter(s -> !s.isIsInitial() && !s.isIsFinal()).toList();
    }

    private static List<Transition> messages(StateMachine machine) {
        List<State> lifelines = lifelines(machine);
        return machine.getTransitions().stream().filter(t -> lifelines.contains(t.getSource()) && lifelines.contains(t.getTarget())).toList();
    }

    private DiagramDescription build() {
        SynchronizationPolicy sync = SynchronizationPolicy.SYNCHRONIZED;
        LabelStyleDescription text = LabelStyleDescription.newLabelStyleDescription()
                .colorProvider(vm -> "#1A2B4A").fontSizeProvider(vm -> 14).boldProvider(vm -> true).italicProvider(vm -> false)
                .underlineProvider(vm -> false).strikeThroughProvider(vm -> false).iconURLProvider(vm -> List.of())
                .backgroundProvider(vm -> "transparent").borderColorProvider(vm -> "transparent").borderSizeProvider(vm -> 0)
                .borderRadiusProvider(vm -> 0).borderStyleProvider(vm -> LineStyle.Solid).maxWidthProvider(vm -> null)
                .visibilityProvider(vm -> LabelVisibility.visible).build();

        NodeDescription slot = NodeDescription.newNodeDescription(ID + "/slot")
                .synchronizationPolicy(sync)
                .typeProvider(vm -> NodeType.NODE_IMAGE)
                .targetObjectIdProvider(vm -> slot(vm).map(s -> this.identity.getId(s.lifeline()) + "@" + s.row()).orElse(null))
                .targetObjectKindProvider(vm -> "sysarch::Slot")
                .targetObjectLabelProvider(vm -> "")
                .semanticElementsProvider(vm -> vm.get(VariableManager.SELF, State.class).map(SequenceProbe::slots).orElse(List.of()))
                .styleProvider(vm -> ImageNodeStyle.newImageNodeStyle()
                        .imageURL(slot(vm).map(s -> s.message().getSource() == s.lifeline() || s.message().getTarget() == s.lifeline())
                                .orElse(false) ? "/sysarch-shapes/spike-activation.svg" : "/sysarch-shapes/spike-lifeline.svg")
                        .scalingFactor(1).borderColor("transparent").borderSize(0).borderRadius(0).borderStyle(LineStyle.Solid)
                        .positionDependentRotation(false).childrenLayoutStrategy(new FreeFormLayoutStrategy()).build())
                .defaultWidthProvider(vm -> 24)
                .defaultHeightProvider(vm -> 40)
                .deleteHandler(vm -> new Failure("探针：只读"))
                .initialChildBorderNodePositions(Map.of())
                .build();

        NodeDescription lifeline = NodeDescription.newNodeDescription(ID + "/lifeline")
                .synchronizationPolicy(sync)
                .typeProvider(vm -> NodeType.NODE_RECTANGLE)
                .targetObjectIdProvider(vm -> vm.get(VariableManager.SELF, EObject.class).map(this.identity::getId).orElse(null))
                .targetObjectKindProvider(vm -> "sysarch::State")
                .targetObjectLabelProvider(vm -> vm.get(VariableManager.SELF, State.class).map(State::getName).orElse(""))
                .semanticElementsProvider(vm -> root(vm).map(m -> new ArrayList<Object>(lifelines(m))).orElse(new ArrayList<>()))
                .styleProvider(vm -> RectangularNodeStyle.newRectangularNodeStyle().background("transparent").borderColor("transparent").borderSize(0)
                        .borderRadius(0).borderStyle(LineStyle.Solid)
                        .childrenLayoutStrategy(ListLayoutStrategy.newListLayoutStrategy().areChildNodesDraggable(false).topGap(4).bottomGap(4).growableNodeIds(List.of()).build())
                        .build())
                // iteration 3: no header inside (a node is never narrower than its header), the name as an outside label
                .outsideLabelDescriptions(List.of(org.eclipse.sirius.components.diagrams.description.OutsideLabelDescription.newOutsideLabelDescription(ID + "/lifeline/label")
                        .textProvider(vm -> vm.get(VariableManager.SELF, State.class).map(State::getName).orElse(""))
                        .styleDescriptionProvider(vm -> text)
                        .outsideLabelLocation(org.eclipse.sirius.components.diagrams.OutsideLabelLocation.BOTTOM_MIDDLE)
                        .overflowStrategy(LabelOverflowStrategy.NONE).textAlign(LabelTextAlign.CENTER).build()))
                .childNodeDescriptions(List.of(slot))
                .defaultWidthProvider(vm -> 40) // iteration 2: narrow, so the box does not cover the message lines
                .deleteHandler(vm -> new Failure("探针：只读"))
                .initialChildBorderNodePositions(Map.of())
                .build();

        EdgeDescription message = EdgeDescription.newEdgeDescription(ID + "/message")
                .synchronizationPolicy(sync)
                .targetObjectIdProvider(vm -> vm.get(VariableManager.SELF, Transition.class).map(this.identity::getId).orElse(null))
                .targetObjectKindProvider(vm -> "sysarch::Transition")
                .targetObjectLabelProvider(vm -> vm.get(VariableManager.SELF, Transition.class).map(Transition::getTrigger).orElse(""))
                .semanticElementsProvider(vm -> root(vm).map(m -> new ArrayList<Object>(messages(m))).orElse(new ArrayList<>()))
                .sourceDescriptions(List.of(slot))
                .targetDescriptions(List.of(slot))
                .sourceProvider(vm -> ends(vm, true))
                .targetProvider(vm -> ends(vm, false))
                .styleProvider(vm -> EdgeStyle.newEdgeStyle().size(1).lineStyle(LineStyle.Solid).sourceArrow(ArrowStyle.None)
                        .targetArrow(ArrowStyle.InputFillClosedArrow).color("#1A2B4A").edgeType(EdgeType.Oblique).build())
                .centerLabelDescription(LabelDescription.newLabelDescription(ID + "/message/label")
                        .textProvider(vm -> vm.get(VariableManager.SELF, Transition.class).map(t -> (messages(machineOf(t)).indexOf(t) + 1) + ": " + t.getTrigger()).orElse(""))
                        .styleDescriptionProvider(vm -> text).build())
                .deleteHandler(vm -> new Failure("探针：只读"))
                .build();

        return DiagramDescription.newDiagramDescription(ID)
                .label("时序图探针（Spike）")
                .targetObjectIdProvider(vm -> vm.get(VariableManager.SELF, EObject.class).map(this.identity::getId).orElse(null))
                .canCreatePredicate(vm -> vm.get(VariableManager.SELF, StateMachine.class).isPresent())
                .labelProvider(vm -> vm.get(DiagramDescription.LABEL, String.class).orElse("时序图探针"))
                .palettes(List.of(Palette.newPalette(ID + "/palette").tools(List.of()).toolSections(List.of()).build()))
                .nodeDescriptions(List.of(lifeline))
                .edgeDescriptions(List.of(message))
                .dropHandler(vm -> new Failure("探针：只读"))
                .iconURLsProvider(vm -> List.of())
                .build();
    }

    private static List<Object> slots(State lifeline) {
        StateMachine machine = (StateMachine) lifeline.eContainer();
        List<Transition> messages = messages(machine);
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            out.add(new Slot(lifeline, messages.get(i), i));
        }
        return out;
    }

    /** Slot i of the sender (or receiver): found in the rendering cache by value. */
    private static List<org.eclipse.sirius.components.representations.Element> ends(VariableManager vm, boolean source) {
        Optional<Transition> message = vm.get(VariableManager.SELF, Transition.class);
        Optional<DiagramRenderingCache> cache = vm.get(DiagramDescription.CACHE, DiagramRenderingCache.class);
        if (message.isEmpty() || cache.isEmpty()) {
            return List.of();
        }
        Transition t = message.get();
        int row = messages(machineOf(t)).indexOf(t);
        Slot slot = new Slot(source ? t.getSource() : t.getTarget(), t, row);
        return cache.get().getElementsRepresenting(slot).stream().limit(1).toList();
    }

    private static StateMachine machineOf(Transition t) {
        return (StateMachine) t.eContainer();
    }

    private static Optional<Slot> slot(VariableManager vm) {
        return vm.get(VariableManager.SELF, Slot.class);
    }

    private static Optional<StateMachine> root(VariableManager vm) {
        VariableManager top = vm;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        return top.get(VariableManager.SELF, StateMachine.class);
    }
}
