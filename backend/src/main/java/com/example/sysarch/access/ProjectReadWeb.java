package com.example.sysarch.access;

import com.example.sysarch.branch.BranchService;
import com.example.sysarch.identity.CurrentUser;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.sysmlimport.ImportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Guards resource ids before controller execution and filters global lists before serialization. */
@RestControllerAdvice
public class ProjectReadWeb extends RequestBodyAdviceAdapter implements HandlerInterceptor, WebMvcConfigurer, ResponseBodyAdvice<Object> {
    private final ProjectReadAccess reads;
    private final ObjectMapper json;

    public ProjectReadWeb(ProjectReadAccess reads, ObjectMapper json) { this.reads = reads; this.json = json; }

    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(this).addPathPatterns("/api/**"); }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/v1/webhooks/polarion")) return true;
        Principal principal = CurrentUser.principal(request).orElse(null);
        if (path.startsWith("/api/v1/migrations/") && !this.reads.globalAdministrator(principal)) throw new ProjectReadAccess.Denied();
        @SuppressWarnings("unchecked")
        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars != null) {
            for (String name : List.of("project", "projectId")) {
                if (vars.containsKey(name)) this.reads.reference(principal, vars.get(name));
            }
            for (String name : List.of("editingContextId", "semanticDataId")) {
                if (vars.containsKey(name)) this.reads.require(principal, this.reads.editingContext(vars.get(name)));
            }
            if (vars.containsKey("baselineId") && path.startsWith("/api/v1/baselines/")) this.reads.resource(principal, "baseline", vars.get("baselineId"));
            if (vars.containsKey("branchId") && path.startsWith("/api/v1/branches/")) this.reads.resource(principal, "branch", vars.get("branchId"));
            if (vars.containsKey("jobId")) {
                if (path.startsWith("/api/v1/imports/")) {
                    if (!this.reads.canReadImport(principal, vars.get("jobId"))) throw new ProjectReadAccess.Denied();
                } else if (path.startsWith("/api/v1/reports/")) this.reads.resource(principal, "report", vars.get("jobId"));
            }
        }
        for (String name : List.of("projectId", "polarionProject")) {
            for (String value : request.getParameterValues(name) == null ? new String[0] : request.getParameterValues(name)) {
                if (!value.isBlank()) this.reads.reference(principal, value);
            }
        }
        for (String other : request.getParameterValues("otherBaselineId") == null ? new String[0] : request.getParameterValues("otherBaselineId")) {
            if (!other.isBlank()) this.reads.resource(principal, "baseline", other);
        }
        return true;
    }

    @Override public boolean supports(MethodParameter p, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return p.getContainingClass().getName().startsWith("com.example.sysarch.");
    }

    @Override public Object afterBodyRead(Object body, HttpInputMessage message, MethodParameter p, Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Principal principal = CurrentUser.principal(attributes.getRequest()).orElse(null);
            JsonNode node = this.json.valueToTree(body);
            // Top-level business scope only: never interpret arbitrary model fields or source text as authorization input.
            if (node != null && node.isObject()) {
                if (node.path("projectId").isTextual() && !node.path("projectId").asText().isBlank()) this.reads.reference(principal, node.path("projectId").asText());
                if (node.path("baselineId").isTextual() && !node.path("baselineId").asText().isBlank()) this.reads.resource(principal, "baseline", node.path("baselineId").asText());
            }
        }
        return body;
    }

    @Override public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override public Object beforeBodyWrite(Object body, MethodParameter p, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof List<?> list) || !(request instanceof ServletServerHttpRequest servlet)) return body;
        Principal principal = CurrentUser.principal(servlet.getServletRequest()).orElse(null);
        return list.stream().filter(item -> {
            if (item instanceof BranchService.BranchView b) return this.reads.visibleReference(principal, b.projectId());
            if (item instanceof ProjectBindings.Binding b) return this.reads.visibleReference(principal, b.siriusProject());
            if (item instanceof ImportService.Job job) return this.reads.canReadImport(principal, job.id());
            if (item instanceof RoleMappings.Row row) return this.reads.globalAdministrator(principal)
                    || row.polarionProject().equals(RoleMappings.ANY) || row.polarionProject().equals(RoleMappings.UNBOUND)
                    || this.reads.visibleReference(principal, row.polarionProject());
            if (item instanceof org.eclipse.sirius.web.application.project.dto.ProjectDTO project) return this.reads.canRead(principal, project.id());
            if (item instanceof org.eclipse.sirius.web.application.project.dto.RestProject project) return this.reads.canRead(principal, project.id());
            return true;
        }).toList();
    }

    @ExceptionHandler(ProjectReadAccess.Denied.class)
    public org.springframework.http.ResponseEntity<ProblemDetail> denied() {
        return org.springframework.http.ResponseEntity.status(403).body(ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.FORBIDDEN, "无权读取该项目，请联系项目管理员授权"));
    }
}
