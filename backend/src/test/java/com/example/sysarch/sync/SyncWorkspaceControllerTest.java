package com.example.sysarch.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.access.ProjectBindings;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.sync.model.ModelGateway;
import com.example.sysarch.sync.model.ModelGateway.ElementSnapshot;
import com.example.sysarch.validation.ValidationError;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class SyncWorkspaceControllerTest {

    private final ProjectModels projects = mock(ProjectModels.class);
    private final ProjectBindings bindings = mock(ProjectBindings.class);
    private final BranchWorkspaces branches = mock(BranchWorkspaces.class);
    private final Access access = mock(Access.class);
    private final ModelGateway model = mock(ModelGateway.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final SyncWorkspaceController controller = new SyncWorkspaceController(projects, bindings, branches, access, model, new SyncProperties());

    @BeforeEach
    void setup() {
        Principal principal = Principal.dev("alice", List.of("sysarch/project_user"));
        request.setAttribute(Principal.class.getName(), (Supplier<Optional<Principal>>) () -> Optional.of(principal));
        when(projects.siriusProjectId("model")).thenReturn("sirius-id");
        when(branches.branchOf("sirius-id")).thenReturn(Optional.empty());
        when(bindings.polarionProjectOf("sirius-id")).thenReturn(Optional.of("sysarch"));
        when(model.readAll("sysarch")).thenReturn(Map.of());
    }

    @Test
    void anonymousUsersCannotEnumerateSyncElements() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> controller.workspace("model", new MockHttpServletRequest())).getStatusCode().value());
        verifyNoInteractions(projects, model);
    }

    @Test
    void branchesNeverSilentlySelectTheTrunksPolarionProject() {
        var branch = new BranchWorkspaces.Branch("b", "trunk", "variant", "base", "alice", null, "OPEN", "sirius-id");
        when(branches.branchOf("sirius-id")).thenReturn(Optional.of(branch));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.workspace("model", request)).getStatusCode().value());
        verifyNoInteractions(bindings, model);
    }

    @Test
    void anUnboundModelCannotOfferASyncTarget() {
        when(bindings.polarionProjectOf("sirius-id")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.workspace("model", request)).getStatusCode().value());
        verifyNoInteractions(model);
    }

    @Test
    void syncPermissionIsCheckedBeforeReadingElements() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(access).requirePolarion(request, "sysarch", Permission.SYNC);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.workspace("model", request)).getStatusCode().value());
        verifyNoInteractions(model);
    }

    @Test
    void onlyConfiguredSyncTypesAreListedWithValidationFailuresAndStableIds() {
        var violation = new ValidationError("NameNotEmpty", "PD-2", "名称不能为空", List.of("PD-2"));
        when(model.readAll("sysarch")).thenReturn(Map.of(
                "PD-2", element("PD-2", "PartDefinition", List.of(violation)),
                "PD-1", element("PD-1", "PartDefinition", List.of()),
                "SM-1", element("SM-1", "StateMachine", List.of())));
        var result = controller.workspace("model", request);
        assertEquals("sysarch", result.polarionProject());
        assertEquals("sirius-id", result.siriusProjectId());
        assertEquals(List.of("PD-1", "PD-2"), result.elements().stream().map(SyncWorkspaceController.Element::id).toList());
        assertEquals("architecture_element", result.elements().getFirst().workItemType());
        assertEquals(List.of(violation), result.elements().get(1).errors());
        verify(access).requirePolarion(request, "sysarch", Permission.SYNC);
    }

    private static ElementSnapshot element(String id, String type, List<ValidationError> errors) {
        return new ElementSnapshot(id, type, id, "LOGICAL", List.of(), null, null, null, 0, errors, List.of(), List.of(), null);
    }
}
