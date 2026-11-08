package com.example.sysarch.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sysarch.demo.DomainControllerDemo;
import com.example.sysarch.migration.InterfaceEntityConversion.Row;
import com.example.sysarch.migration.InterfaceEntityConversion.Status;
import com.example.sysarch.migration.InterfaceMigrationReport.StoredDocument;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchPackage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.sirius.components.emf.services.JSONResourceFactory;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;

import liquibase.integration.spring.SpringLiquibase;

/**
 * P1 T1: the {@code Port.interfaceType} string to {@code InterfaceDef} migration (spec P1 §2.4). Acceptance: the
 * dry-run report and the real run agree, on the demo data too.
 */
class InterfaceMigrationTest {

    /** A document as P0 stored it: interface names in {@code interfaceType}, nested ports, a blank and a missing value. */
    private static final String P0_DOCUMENT_TEMPLATE = """
            {"json":{"version":"1.0","encoding":"utf-8"},"ns":{"sysarch":"http://example.com/sysarch/1.0"},"content":[
              {"id":"pd-1","eClass":"sysarch:PartDefinition","data":{"name":"ECU",
                "ownedPorts":[
                  {"id":"po-1","eClass":"sysarch:Port","data":{"name":"can0","direction":"OUT","interfaceType":"CAN"}},
                  {"id":"po-2","eClass":"sysarch:Port","data":{"name":"can1","direction":"IN","interfaceType":"CAN"}},
                  {"id":"po-3","eClass":"sysarch:Port","data":{"name":"eth0","interfaceType":"ETH"}},
                  {"id":"po-4","eClass":"sysarch:Port","data":{"name":"blank","interfaceType":"  "}},
                  {"id":"po-5","eClass":"sysarch:Port","data":{"name":"none"}}],
                "ownedParts":[
                  {"id":"pt-1","eClass":"sysarch:Part","data":{"name":"gw","ports":[
                    {"id":"po-6","eClass":"sysarch:Port","data":{"name":"eth1","interfaceType":"ETH"}}]}}]}},
              {"id":"co-1","eClass":"sysarch:Connection","data":{"name":"bus","source":"po-1","target":"po-2","interfaceType":"CAN"}}]}""";

    /** The same with real object ids: Sirius JSON resources only accept UUIDs. */
    private static final String P0_DOCUMENT = withUuids(P0_DOCUMENT_TEMPLATE);

    private static final String KEY = InterfaceMigrationReport.documentKey("doc-1");

    private JdbcClient jdbc;

    private InterfaceMigrationBackup backup;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/sysarch/changelog.xml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        this.jdbc = JdbcClient.create(dataSource);
        this.backup = new InterfaceMigrationBackup(this.jdbc, new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    }

    // =========================================================== the conversion (spec §2.4 steps 1-4)

    @Test
    void stringsBecomeOneEntityPerValueAndPortsReferToThem() {
        JsonObject document = parse(P0_DOCUMENT);

        List<Row> rows = InterfaceEntityConversion.convert(KEY, document);

        String can = InterfaceEntityConversion.entityId(KEY, "CAN");
        String eth = InterfaceEntityConversion.entityId(KEY, "ETH");
        assertEquals(List.of(
                new Row(KEY, id("po-1"), "can0", "CAN", can, Status.CONVERTED),
                new Row(KEY, id("po-2"), "can1", "CAN", can, Status.CONVERTED),
                new Row(KEY, id("po-3"), "eth0", "ETH", eth, Status.CONVERTED),
                new Row(KEY, id("po-4"), "blank", "  ", null, Status.EMPTY),
                new Row(KEY, id("po-5"), "none", null, null, Status.EMPTY),
                new Row(KEY, id("po-6"), "eth1", "ETH", eth, Status.CONVERTED)), rows);
        JsonArray content = document.getAsJsonArray("content");
        assertEquals(4, content.size(), "two new roots: one InterfaceDef per distinct value");
        assertEquals("{\"id\":\"" + can + "\",\"eClass\":\"sysarch:InterfaceDef\",\"data\":{\"name\":\"CAN\",\"layer\":\"LOGICAL\"}}", content.get(2).toString());
        assertEquals("CAN", content.get(1).getAsJsonObject().getAsJsonObject("data").get("interfaceType").getAsString(),
                "a Connection's interfaceType is still text in v1.1: untouched");
    }

    /** Why the conversion runs before EMF reads the document: unconverted, the name is taken for an id and lost. */
    @Test
    void theConvertedDocumentLoadsWithResolvedInterfacesAndTheRawOneWouldNot() throws Exception {
        JsonObject converted = parse(P0_DOCUMENT);
        InterfaceEntityConversion.convert(KEY, converted);

        Resource resource = load(converted.toString());
        Port can0 = port(resource, "can0");
        InterfaceDef can = can0.getInterfaceType();
        assertNotNull(can);
        assertEquals("CAN", can.getName());
        assertSame(can, port(resource, "can1").getInterfaceType(), "same value, same entity");
        assertEquals("ETH", port(resource, "eth1").getInterfaceType().getName());
        assertNull(port(resource, "blank").getInterfaceType());
        assertEquals("CAN", ((Connection) resource.getContents().get(1)).getInterfaceType());

        Port raw = port(load(P0_DOCUMENT), "can0");
        assertTrue(raw.getInterfaceType() == null || raw.getInterfaceType().eIsProxy(), "unconverted, \"CAN\" does not resolve");
    }

    // =========================================================== dry-run = real run (acceptance)

    @Test
    void theDryRunReportEqualsWhatTheRealLoadDid() {
        var report = new InterfaceMigrationReport(() -> List.of(new StoredDocument("p", "doc-1", "m", P0_DOCUMENT)), this.backup);

        List<Row> dryRun = report.dryRun();
        assertEquals(List.of(), this.backup.rows(null), "a dry run writes nothing");
        new InterfaceEntityMigrationParticipant(this.backup).preDeserialization(jsonResource("doc-1"), parse(P0_DOCUMENT));

        assertEquals(sorted(dryRun), sorted(report.applied()));
        assertEquals(6, dryRun.size());
    }

    /** "在演示数据上 dry-run 报告与实跑结果一致": the domain-controller demo, as P0 would have stored it. */
    @Test
    void onTheDemoDataTheDryRunAndTheRealRunAgree() throws Exception {
        String p0Demo = p0Shape(save(DomainControllerDemo.build()));
        var report = new InterfaceMigrationReport(() -> List.of(new StoredDocument("demo", "doc-demo", "域控制器演示", p0Demo)), this.backup);

        List<Row> dryRun = report.dryRun();
        JsonObject loaded = parse(p0Demo);
        new InterfaceEntityMigrationParticipant(this.backup).preDeserialization(jsonResource("doc-demo"), loaded);

        assertEquals(sorted(dryRun), sorted(report.applied()));
        assertEquals(6, dryRun.stream().filter(row -> row.status() == Status.CONVERTED).count(), "the demo's 6 ports");
        assertEquals(List.of("CAN-FD"), dryRun.stream().map(Row::oldValue).distinct().toList());
        Resource resource = load(loaded.toString());
        assertEquals("CAN-FD", port(resource, "objects").getInterfaceType().getName());
    }

    /** A document converted again before it was saved (loaded twice) gets the same ids, and the backup keeps one row. */
    @Test
    void convertingAgainIsHarmless() {
        var participant = new InterfaceEntityMigrationParticipant(this.backup);
        JsonObject first = parse(P0_DOCUMENT);
        JsonObject second = parse(P0_DOCUMENT);

        participant.preDeserialization(jsonResource("doc-1"), first);
        participant.preDeserialization(jsonResource("doc-1"), second);

        assertEquals(first, second);
        assertEquals(6, this.backup.rows(KEY).size());
    }

    /**
     * Sirius loads a project's documents with migration participants only if some
     * {@code IEditingContextMigrationParticipantPredicate} accepts the editing context, and registers none for our
     * domain: without this predicate the migration never ran (found in the live check against a model written by P0).
     */
    @Test
    void migrationsRunForEditingContextsHoldingSysarchModelsOnly() {
        this.jdbc.sql("CREATE TABLE document (id UUID PRIMARY KEY, semantic_data_id UUID NOT NULL, name TEXT, content TEXT)").update();
        UUID sysarch = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        this.jdbc.sql("INSERT INTO document VALUES (:id, :sd, 'm', :c)").param("id", UUID.randomUUID()).param("sd", sysarch).param("c", P0_DOCUMENT).update();
        this.jdbc.sql("INSERT INTO document VALUES (:id, :sd, 'v', :c)").param("id", UUID.randomUUID()).param("sd", other)
                .param("c", "{\"ns\":{\"view\":\"http://www.eclipse.org/sirius-web/view\"},\"content\":[]}").update();
        var predicate = new SysarchMigrationPredicate(this.jdbc);

        assertTrue(predicate.test(sysarch.toString()));
        assertFalse(predicate.test(other.toString()), "other projects keep Sirius' default: no migration on load");
        assertFalse(predicate.test("not-a-uuid"));
    }

    @Test
    void documentsSavedAfterTheMigrationAreNotInTheDryRun() {
        JsonObject migrated = parse(P0_DOCUMENT);
        JsonObject marker = new JsonObject();
        marker.addProperty("lastMigrationPerformed", "InterfaceEntityMigrationParticipant");
        marker.addProperty("migrationVersion", InterfaceEntityMigrationParticipant.VERSION);
        migrated.add("migration", marker);
        var report = new InterfaceMigrationReport(() -> List.of(new StoredDocument("p", "doc-1", "m", migrated.toString())), this.backup);

        assertEquals(List.of(), report.dryRun());
    }

    // =========================================================== rollback (step 5)

    @Test
    void revertRestoresTheStringsAndRemovesTheEntities() {
        JsonObject document = parse(P0_DOCUMENT);
        List<Row> rows = InterfaceEntityConversion.convert(KEY, document);

        InterfaceEntityConversion.revert(document, rows);

        JsonObject expected = parse(P0_DOCUMENT);
        expected.getAsJsonArray("content").get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("ownedPorts").get(3).getAsJsonObject()
                .getAsJsonObject("data").remove("interfaceType"); // the blank value is not restored: it meant "no interface" anyway
        assertEquals(expected, document);
    }

    /** After a rollback the document is older than the migration again, so a later upgrade converts it again. */
    @Test
    void aRolledBackDocumentIsMigratedAgainOnTheNextUpgrade() {
        JsonObject document = parse(P0_DOCUMENT);
        List<Row> rows = InterfaceEntityConversion.convert(KEY, document);

        JsonObject reverted = parse(InterfaceMigrationRollback.revert(document.toString(), rows));

        assertFalse(InterfaceMigrationReport.alreadyMigrated(reverted));
        assertTrue(InterfaceMigrationRollback.ROLLED_BACK_VERSION.compareTo(InterfaceEntityMigrationParticipant.VERSION) < 0);
        assertEquals("doc-1", InterfaceMigrationRollback.documentId(KEY));
    }

    @Test
    void theReportIsCsv() {
        String csv = InterfaceMigrationReport.toCsv(List.of(new Row(KEY, "po-1", "can, 0", "CAN", "x", Status.CONVERTED)));

        assertEquals(InterfaceMigrationReport.CSV_HEADER + "\n" + KEY + ",po-1,\"can, 0\",CAN,x,CONVERTED\n", csv);
    }

    // =========================================================== helpers

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** The UUID that stands for a readable fixture id. */
    private static String id(String readable) {
        return UUID.nameUUIDFromBytes(readable.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String withUuids(String template) {
        String json = template;
        for (String readable : List.of("pd-1", "pt-1", "co-1", "po-1", "po-2", "po-3", "po-4", "po-5", "po-6")) {
            json = json.replace("\"" + readable + "\"", "\"" + id(readable) + "\"");
        }
        return json;
    }

    /** The port with this name (Sirius JSON resources only look objects up by UUID). */
    private static Port port(Resource resource, String name) {
        var all = resource.getAllContents();
        while (all.hasNext()) {
            if (all.next() instanceof Port port && name.equals(port.getName())) {
                return port;
            }
        }
        throw new AssertionError("no port " + name);
    }

    private static List<Row> sorted(List<Row> rows) {
        List<Row> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(Row::documentKey).thenComparing(Row::portId));
        return copy;
    }

    private static JsonResource jsonResource(String documentId) {
        return new JSONResourceFactory().createResourceFromPath(documentId);
    }

    private static Resource load(String json) throws Exception {
        ResourceSet set = new ResourceSetImpl();
        set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource resource = new JSONResourceFactory().createResourceFromPath("loaded");
        set.getResources().add(resource);
        resource.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Map.of());
        return resource;
    }

    private static String save(List<org.eclipse.emf.ecore.EObject> roots) throws Exception {
        ResourceSet set = new ResourceSetImpl();
        set.getPackageRegistry().put(SysarchPackage.eNS_URI, SysarchPackage.eINSTANCE);
        Resource resource = new JSONResourceFactory().createResourceFromPath("demo");
        set.getResources().add(resource);
        resource.getContents().addAll(roots);
        var out = new ByteArrayOutputStream();
        resource.save(out, Map.of());
        return out.toString(StandardCharsets.UTF_8);
    }

    /** A v1.1 document in P0's shape: each port's interface reference replaced by the interface name, entities removed. */
    private static String p0Shape(String v11) {
        JsonObject document = parse(v11);
        JsonArray content = document.getAsJsonArray("content");
        Map<String, String> nameById = new java.util.HashMap<>();
        for (int i = content.size() - 1; i >= 0; i--) {
            JsonObject root = content.get(i).getAsJsonObject();
            if ("sysarch:InterfaceDef".equals(root.get("eClass").getAsString())) {
                nameById.put(root.get("id").getAsString(), root.getAsJsonObject("data").get("name").getAsString());
                content.remove(i);
            }
        }
        String json = document.toString();
        for (var entry : nameById.entrySet()) {
            json = json.replace("\"interfaceType\":\"" + entry.getKey() + "\"", "\"interfaceType\":\"" + entry.getValue() + "\"");
        }
        return json;
    }
}
