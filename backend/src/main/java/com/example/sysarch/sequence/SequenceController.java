package com.example.sysarch.sequence;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.Interaction;
import com.example.sysarch.project.ProjectModels;
import com.example.sysarch.report.ReportConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only sequence pictures (probe A's fallback, spec P3 §6.1): {@code GET /api/v1/projects/{project}/interactions}
 * lists the interactions of a project (Sirius project id or name, or a mapped Polarion project id), and
 * {@code GET …/interactions/{id}/sequence.svg} / {@code sequence.png} draws one ({@link SequenceRenderer}). The model is
 * read on the project's editing context; nothing is written. Interactions are edited in Sirius' explorer (no canvas).
 */
@RestController
@RequestMapping("/api/v1/projects/{project}/interactions")
public class SequenceController {

    private final ProjectModels models;

    private final SequenceRenderer renderer;

    public SequenceController(ProjectModels models, @Value("${sysarch.report.fonts:#{null}}") List<String> fonts) {
        this.models = Objects.requireNonNull(models);
        List<Path> candidates = fonts == null ? ReportConfiguration.defaultFonts()
                : fonts.stream().map(String::strip).filter(s -> !s.isEmpty()).map(Path::of).toList();
        this.renderer = new SequenceRenderer(candidates);
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable String project) {
        return this.read(project, resources -> {
            List<Map<String, Object>> out = new ArrayList<>();
            EcoreUtil.<Object>getAllContents(resources, true) /* yields the resources too */.forEachRemaining(o -> {
                if (o instanceof Interaction interaction) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", ElementIds.of(interaction));
                    row.put("name", interaction.getName());
                    row.put("lifelines", interaction.getLifelines().size());
                    row.put("messages", interaction.getMessages().size());
                    out.add(row);
                }
            });
            return out;
        });
    }

    @GetMapping(value = "/{id}/sequence.svg", produces = "image/svg+xml")
    public ResponseEntity<String> svg(@PathVariable String project, @PathVariable String id) {
        String svg = this.read(project, resources -> this.renderer.svg(this.interaction(resources, id)));
        return ResponseEntity.ok().contentType(new MediaType("image", "svg+xml", java.nio.charset.StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore()).body(svg);
    }

    @GetMapping(value = "/{id}/sequence.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> png(@PathVariable String project, @PathVariable String id) {
        byte[] png = this.read(project, resources -> this.renderer.png(this.interaction(resources, id)));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

    private Interaction interaction(List<Resource> resources, String id) {
        var all = EcoreUtil.<Object>getAllContents(resources, true) /* yields the resources too */;
        while (all.hasNext()) {
            if (all.next() instanceof Interaction interaction && id.equals(ElementIds.of(interaction))) {
                return interaction;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目里没有交互 " + id);
    }

    private <T> T read(String project, java.util.function.Function<List<Resource>, T> task) {
        try {
            return this.models.read(project, task);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
