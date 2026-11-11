package com.example.sysarch.sequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sysarch.project.ProjectModels;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * The endpoints over the list of resources the editing context hands over (found live: iterating that list also yields
 * the Resource objects themselves).
 */
class SequenceControllerTest {

    @SuppressWarnings("unchecked")
    private SequenceController controller() {
        Resource document = new ResourceImpl(URI.createURI("memory:/door.json"));
        document.getContents().add(SequenceRendererTest.door());
        ProjectModels models = mock(ProjectModels.class);
        when(models.read(anyString(), any(Function.class))).thenAnswer(i -> ((Function<List<Resource>, Object>) i.getArgument(1)).apply(List.of(document)));
        return new SequenceController(models, null);
    }

    @Test
    void listsAndDrawsTheInteractions() {
        SequenceController controller = this.controller();
        assertEquals(List.of(Map.of("id", "SQ-1", "name", "开门", "lifelines", 3, "messages", 7)), controller.list("p"));
        assertTrue(controller.svg("p", "SQ-1").getBody().contains("sd 开门"));
        byte[] png = controller.png("p", "SQ-1").getBody();
        assertEquals("PNG", new String(png, 1, 3, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.svg("p", "NOPE")).getStatusCode().value());
    }
}
