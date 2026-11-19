package com.example.sysarch.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class ModelStorageMigrationTest {
    @Test
    void actualLiquibaseChangeSetIsRepeatableAndKeepsRegisteredIdentity() throws Exception {
        var data = new JdbcDataSource(); data.setURL("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var accessor = new ClassLoaderResourceAccessor();
                var migration = new Liquibase("db/sysarch/019-model-storage.xml", accessor, new JdbcConnection(data.getConnection()))) {
            migration.update(new Contexts(), new LabelExpression());
        }
        JdbcClient jdbc = JdbcClient.create(data);
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO model_storage_registration (sirius_project, model_uuid, created_by) VALUES ('fixture',:id,'fixture')")
                .param("id", id).update();
        try (var accessor = new ClassLoaderResourceAccessor();
                var migration = new Liquibase("db/sysarch/019-model-storage.xml", accessor, new JdbcConnection(data.getConnection()))) {
            migration.update(new Contexts(), new LabelExpression());
        }
        assertEquals(id, jdbc.sql("SELECT model_uuid FROM model_storage_registration").query(String.class).single());
        assertEquals("LOCAL", jdbc.sql("SELECT storage_mode FROM model_storage_registration").query(String.class).single());
    }
}
