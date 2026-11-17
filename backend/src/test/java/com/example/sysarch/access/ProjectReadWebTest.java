package com.example.sysarch.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.sysarch.identity.Principal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ProjectReadWebTest {
    private ProjectReadFixture f;
    private MockMvc mvc;
    private final FixtureController controller=new FixtureController();
    @BeforeEach void setup() {
        f=new ProjectReadFixture();
        var web=new ProjectReadWeb(f.reads,new ObjectMapper());
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(web).addInterceptors(web).build();
    }
    private MockHttpServletRequestBuilder alice(MockHttpServletRequestBuilder request) {
        return request.requestAttr(Principal.class.getName(),(Supplier<Optional<Principal>>)()->Optional.of(f.alice));
    }

    @Test void tableReadsAreDeniedBeforeTheControllerCanReadTheModel() throws Exception {
        mvc.perform(alice(get("/api/v1/projects/p-a/tables/t"))).andExpect(status().isOk());
        mvc.perform(alice(get("/api/v1/projects/p-b/tables/t"))).andExpect(status().isForbidden());
        assertEquals(1,controller.executed.get());
    }

    @Test void nativeDocumentDownloadsCannotBypassProjectAuthorization() throws Exception {
        mvc.perform(alice(get("/api/editingcontexts/ec-b/documents/doc"))).andExpect(status().isForbidden());
        assertEquals(0,controller.executed.get());
    }

    @Test void jsonExportsAreGuardedBeforeTheirBodyIsUsed() throws Exception {
        mvc.perform(alice(post("/api/v1/export/sysmlv2").contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":\"beta\"}")))
                .andExpect(status().isForbidden());
        assertEquals(0,controller.executed.get());
    }

    @Test void reportDownloadsCheckTheStoredJobOwnerProject() throws Exception {
        mvc.perform(alice(get("/api/v1/reports/report-b/files/pdf"))).andExpect(status().isForbidden());
        mvc.perform(alice(get("/api/v1/reports/report-a/files/pdf"))).andExpect(status().isOk());
        assertEquals(1,controller.executed.get());
    }

    @Test void deploymentUnderAContextPathDoesNotBypassResourceOwnership() throws Exception {
        mvc.perform(alice(get("/sysarch/api/v1/reports/report-b/files/pdf").contextPath("/sysarch")))
                .andExpect(status().isForbidden());
        assertEquals(0,controller.executed.get());
    }

    @Test void comparingTwoBaselinesRequiresReadAccessToBoth() throws Exception {
        mvc.perform(alice(get("/api/v1/baselines/base-a/diff").param("otherBaselineId","base-b"))).andExpect(status().isForbidden());
        assertEquals(0,controller.executed.get());
    }

    @Test void listResponsesDoNotEnumerateOtherProjectsBindings() throws Exception {
        mvc.perform(alice(get("/api/v1/project-bindings"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].polarionProject").value("alpha"));
    }

    @Test void nativeRestProjectListsAreFilteredBeforeSerialization() throws Exception {
        mvc.perform(alice(get("/api/rest/projects"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value("p-a"));
    }

    @RestController static class FixtureController {
        final AtomicInteger executed=new AtomicInteger();
        private Map<String,String> model() {executed.incrementAndGet();return Map.of("data","model contents");}
        @GetMapping("/api/v1/projects/{project}/tables/{table}") Object table(){return model();}
        @GetMapping("/api/editingcontexts/{editingContextId}/documents/{documentId}") Object document(){return model();}
        @PostMapping("/api/v1/export/sysmlv2") Object export(@RequestBody Map<String,String> body){return model();}
        @GetMapping("/api/v1/reports/{jobId}/files/{format}") Object report(){return model();}
        @GetMapping("/api/v1/baselines/{baselineId}/diff") Object diff(){return model();}
        @GetMapping("/api/v1/project-bindings") Object bindings(){return List.of(
                new ProjectBindings.Binding("alpha","p-a","table",null,null),new ProjectBindings.Binding("beta","p-b","table",null,null));}
        @GetMapping("/api/rest/projects") Object projects(){return List.of(
                new org.eclipse.sirius.web.application.project.dto.ProjectDTO("p-a","Alpha model",List.of()),
                new org.eclipse.sirius.web.application.project.dto.ProjectDTO("p-b","Beta secret",List.of()));}
    }
}
