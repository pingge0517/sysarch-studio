package com.example.sysarch.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.sync.api.model.Link;
import com.example.sysarch.sync.api.model.LinkRequest;

import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import liquibase.integration.spring.SpringLiquibase;

/** T7 step 3: {@code /api/v1/links} create, idempotent re-create, 409, list, unbind. */
class LinksControllerTest {

    private LinksController controller;

    private LinkRepository links;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.links = new LinkRepository(JdbcClient.create(dataSource));
        this.controller = new LinksController(this.links);
    }

    @Test
    void bindsAnElement() {
        assertEquals(201, this.controller.linksPost(request("PD-001", "PROJ-123")).getStatusCode().value());

        List<Link> listed = this.controller.linksGet("sysarch", null).getBody();
        assertEquals(1, listed.size());
        assertEquals("PROJ-123", listed.get(0).getWorkItemId());
        assertEquals(Link.StateEnum.ACTIVE, listed.get(0).getState());
        assertEquals(Link.ElementTypeEnum.PART_DEFINITION, listed.get(0).getElementType());
    }

    @Test
    void bindingTheSameWorkItemAgainIsIdempotent() {
        this.controller.linksPost(request("PD-001", "PROJ-123"));

        assertEquals(201, this.controller.linksPost(request("PD-001", "PROJ-123")).getStatusCode().value());
        assertEquals(1, this.controller.linksGet("sysarch", null).getBody().size());
    }

    /** Spec T7 step 3: an element already bound to another Work Item is a 409. */
    @Test
    void bindingToAnotherWorkItemIsAConflict() {
        this.controller.linksPost(request("PD-001", "PROJ-123"));

        var error = assertThrows(ResponseStatusException.class, () -> this.controller.linksPost(request("PD-001", "PROJ-999")));

        assertEquals(409, error.getStatusCode().value());
        assertEquals("PROJ-123", this.links.find("PD-001").orElseThrow().workItemId());
    }

    @Test
    void valuesOutsideTheOpenApiEnumsAreA400() {
        LinkRequest bad = request("PD-001", "PROJ-123").elementType("Allocate");

        assertEquals(400, assertThrows(ResponseStatusException.class, () -> this.controller.linksPost(bad)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> this.controller.linksGet("sysarch", "BROKEN")).getStatusCode().value());
    }

    @Test
    void listsByProjectAndState() {
        this.controller.linksPost(request("PD-001", "PROJ-1"));
        this.controller.linksPost(request("PD-002", "PROJ-2"));
        this.links.update(this.links.find("PD-002").orElseThrow().withState("ORPHAN"));
        this.controller.linksPost(request("PD-003", "PROJ-3").projectId("other"));

        assertEquals(2, this.controller.linksGet("sysarch", null).getBody().size());
        assertEquals(List.of("PD-002"), this.controller.linksGet("sysarch", "ORPHAN").getBody().stream().map(Link::getElementId).toList());
    }

    @Test
    void unbindingRemovesTheBindingOnly() {
        this.controller.linksPost(request("PD-001", "PROJ-123"));

        assertEquals(204, this.controller.linksElementIdDelete("PD-001").getStatusCode().value());
        assertTrue(this.links.find("PD-001").isEmpty());
        assertEquals(204, this.controller.linksElementIdDelete("PD-001").getStatusCode().value(), "unbinding twice is fine");
    }

    private static LinkRequest request(String elementId, String workItemId) {
        return new LinkRequest(elementId, "PartDefinition", workItemId, "architecture_element", "sysarch");
    }
}
