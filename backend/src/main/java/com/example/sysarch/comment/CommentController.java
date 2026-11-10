package com.example.sysarch.comment;

import com.example.sysarch.access.Access;
import com.example.sysarch.access.Permission;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.project.ProjectModels;

import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Element comments (P2 T8, spec §7.1 评审人 "只读 + 评论"; user's decision "做最小评论功能"), table
 * {@code element_comment} (change set 017), outside the frozen model.
 * <ul>
 * <li>{@code GET /api/v1/comments?projectId=[&elementId=]}: newest first (every role may read).</li>
 * <li>{@code POST /api/v1/comments {projectId, elementId, body}}: 201; needs {@link Permission#COMMENT} (reviewers,
 * architects, administrators), the element must exist (404), the body 1–4000 characters.</li>
 * </ul>
 * Comments are never edited or deleted (a review record).
 */
@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    public record Comment(String id, String projectId, String elementId, String author, String body, OffsetDateTime createdAt) {
    }

    public record PostRequest(String projectId, String elementId, String body) {
    }

    static final int MAX_LENGTH = 4000;

    private final JdbcClient jdbc;

    private final ProjectModels models;

    private final Access access;

    public CommentController(JdbcClient jdbc, ProjectModels models, Access access) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.models = Objects.requireNonNull(models);
        this.access = Objects.requireNonNull(access);
    }

    @GetMapping
    public List<Comment> list(@RequestParam("projectId") String projectId, @RequestParam(value = "elementId", required = false) String elementId) {
        String project = this.project(projectId);
        String sql = "SELECT id, project_id, element_id, author, body, created_at FROM element_comment WHERE project_id = :p"
                + (elementId == null || elementId.isBlank() ? "" : " AND element_id = :e") + " ORDER BY created_at DESC, id";
        var statement = this.jdbc.sql(sql).param("p", project);
        if (elementId != null && !elementId.isBlank()) {
            statement = statement.param("e", elementId.strip());
        }
        return statement.query((rs, n) -> new Comment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getObject(6, OffsetDateTime.class))).list();
    }

    @PostMapping
    public ResponseEntity<Comment> post(@RequestBody PostRequest request, HttpServletRequest http) {
        if (request == null || blank(request.projectId()) || blank(request.elementId()) || blank(request.body())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId, elementId and body are required");
        }
        if (request.body().length() > MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论最多 " + MAX_LENGTH + " 个字符");
        }
        String project = this.project(request.projectId());
        String author = this.access.require(http, project, Permission.COMMENT).user();
        String elementId = request.elementId().strip();
        boolean exists = this.models.read(project, resources -> resources.stream().anyMatch(r -> {
            var all = r.getAllContents();
            while (all.hasNext()) {
                if (all.next() instanceof ArchElement element && elementId.equals(EcoreUtil.getID(element))) {
                    return true;
                }
            }
            return false;
        }));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型里没有元素 " + elementId);
        }
        Comment comment = new Comment(UUID.randomUUID().toString(), project, elementId, author, request.body().strip(), OffsetDateTime.now());
        this.jdbc.sql("INSERT INTO element_comment (id, project_id, element_id, author, body, created_at) VALUES (:id, :p, :e, :a, :b, :at)")
                .param("id", comment.id()).param("p", project).param("e", elementId).param("a", author).param("b", comment.body())
                .param("at", comment.createdAt()).update();
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    private String project(String project) {
        try {
            return this.models.siriusProjectId(project);
        } catch (ProjectModels.UnknownProjectException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
