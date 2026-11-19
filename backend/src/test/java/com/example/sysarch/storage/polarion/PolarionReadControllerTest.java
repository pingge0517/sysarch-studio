package com.example.sysarch.storage.polarion;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.sync.SyncProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolarionReadControllerTest {
    private WireMockServer server;
    private MockMvc mvc;
    private static final String API="/api/v1/model-store/projects/alpha/models";
    @BeforeEach void start() {
        server=new WireMockServer(wireMockConfig().dynamicPort()); server.start();
        var properties=new SyncProperties(); properties.setBaseUrl(server.baseUrl()+"/polarion");
        mvc=MockMvcBuilders.standaloneSetup(new PolarionReadController(properties,new ModelCodec())).build();
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/polarion/sysarch-model/identity"))
            .willReturn(okJson("{\"protocol\":\"sysarch-model-read/1\",\"authentication\":\"sdk-access-token\",\"actor\":\"alice\"}")));
    }
    @AfterEach void stop() { server.stop(); }
    @Test void cookieAndTypedNameCannotReplacePat() throws Exception {
        mvc.perform(get(API).header("X-Sysarch-User","alice").cookie(new jakarta.servlet.http.Cookie("sysarch_user","alice"))).andExpect(status().isUnauthorized());
        assertTrue(server.getAllServeEvents().isEmpty());
    }
    @Test void authenticatedEmptyProjectReturnsExplicitPageAndCannotBeCached() throws Exception {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems")).willReturn(okJson("{\"data\":[]}")));
        mvc.perform(get(API).header("Authorization","Bearer fixture-token")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Vary","Authorization"))
            .andExpect(jsonPath("$.models").isEmpty()).andExpect(jsonPath("$.mayHaveMore").value(false));
        server.verify(2,com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(anyUrl()).withHeader("Authorization",equalTo("Bearer fixture-token")));
    }
    @Test void permissionDenialDoesNotReturnAnEmptySuccessfulPage() throws Exception {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems")).willReturn(aResponse().withStatus(403).withBody("private remote details")));
        var result=mvc.perform(get(API).header("Authorization","Bearer fixture-token")).andExpect(status().isForbidden()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private remote details"));
    }
    @Test void invalidPaginationCannotTriggerAProjectQuery() throws Exception {
        mvc.perform(get(API).param("pageSize","101").header("Authorization","Bearer fixture-token")).andExpect(status().isBadRequest());
        server.verify(0,com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems")));
    }
    @Test void missingOrInvalidModelUuidIsRejectedAndWritesAreNotMapped() throws Exception {
        mvc.perform(get(API+"/ROOT-1/snapshot").header("Authorization","Bearer fixture-token")).andExpect(status().isBadRequest());
        mvc.perform(get(API+"/ROOT-1/snapshot").param("modelUuid","invalid").header("Authorization","Bearer fixture-token")).andExpect(status().isBadRequest());
        mvc.perform(post(API).header("Authorization","Bearer fixture-token")).andExpect(status().isMethodNotAllowed());
        assertTrue(server.getAllServeEvents().isEmpty());
    }
    @Test void upstreamUnavailabilityIsNotReturnedAsEmptyModel() throws Exception {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/polarion/sysarch-model/identity")).willReturn(aResponse().withStatus(503)));
        mvc.perform(get(API).header("Authorization","Bearer fixture-token")).andExpect(status().isServiceUnavailable());
    }
    @Test void readViewsRequireTheSameIdentityAndObjectPermissionAndRejectWrites() throws Exception {
        String path=API+"/ROOT-1/views/allocation-matrix";
        mvc.perform(get(path).param("modelUuid",java.util.UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems/ROOT-1")).willReturn(aResponse().withStatus(403)));
        mvc.perform(get(path).param("modelUuid",java.util.UUID.randomUUID().toString()).header("Authorization","Bearer fixture-token"))
            .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post(path).header("Authorization","Bearer fixture-token")).andExpect(status().isMethodNotAllowed());
    }
    @Test void polarionProjectScopeIsNotInterpretedAsALocalSiriusProject() throws Exception {
        var reads=org.mockito.Mockito.mock(com.example.sysarch.access.ProjectReadAccess.class);
        org.mockito.Mockito.doThrow(new com.example.sysarch.access.ProjectReadAccess.Denied()).when(reads)
            .reference(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
        var web=new com.example.sysarch.access.ProjectReadWeb(reads,new com.fasterxml.jackson.databind.ObjectMapper());
        var properties=new SyncProperties(); properties.setBaseUrl(server.baseUrl()+"/polarion");
        var guarded=MockMvcBuilders.standaloneSetup(new PolarionReadController(properties,new ModelCodec())).setControllerAdvice(web).addInterceptors(web)
            .addFilters(new com.example.sysarch.identity.ReadAccessFilter(new com.example.sysarch.identity.AuthProperties())).build();
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/polarion/rest/v1/projects/alpha/workitems")).willReturn(okJson("{\"data\":[]}")));
        guarded.perform(get(API).header("Authorization","Bearer fixture-token")).andExpect(status().isOk());
        org.mockito.Mockito.verifyNoInteractions(reads);
    }
}
