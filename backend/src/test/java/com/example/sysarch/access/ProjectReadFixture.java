package com.example.sysarch.access;

import static org.mockito.Mockito.*;
import com.example.sysarch.audit.AuditLog;
import com.example.sysarch.branch.BranchWorkspaces;
import com.example.sysarch.identity.AuthProperties;
import com.example.sysarch.identity.Principal;
import com.example.sysarch.sync.SyncProperties;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real ownership lookups and membership rules against a small relational fixture, without a running server. */
public class ProjectReadFixture {
    public final Principal alice = Principal.dev("alice", List.of("alpha/project_user"));
    public final Principal bob = Principal.dev("bob", List.of("beta/project_observer"));
    public final Principal admin = Principal.dev("root", List.of("global:admin"));
    public final AuthProperties auth = new AuthProperties();
    public final RoleMappings roles = mock(RoleMappings.class);
    public final JdbcClient jdbc;
    public final ProjectReadAccess reads;

    public ProjectReadFixture() {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = JdbcClient.create(db);
        jdbc.sql("CREATE TABLE project (id VARCHAR PRIMARY KEY, name VARCHAR)").update();
        jdbc.sql("INSERT INTO project VALUES ('p-a','Alpha model'),('p-b','Beta secret'),('workspace-b','Beta branch'),('unbound','Shared staging')").update();
        jdbc.sql("CREATE TABLE project_semantic_data(project_id VARCHAR, semantic_data_id VARCHAR)").update();
        jdbc.sql("INSERT INTO project_semantic_data VALUES ('p-a','ec-a'),('p-b','ec-b')").update();
        jdbc.sql("CREATE TABLE branch(id VARCHAR, project_id VARCHAR, name VARCHAR, base_baseline VARCHAR, created_by VARCHAR, created_at TIMESTAMP WITH TIME ZONE, status VARCHAR, workspace_project_id VARCHAR)").update();
        jdbc.sql("INSERT INTO branch VALUES ('branch-b','p-b','secret variant','base-b','bob',CURRENT_TIMESTAMP,'OPEN','workspace-b')").update();
        jdbc.sql("CREATE TABLE baseline(id VARCHAR, project_id VARCHAR)").update();
        jdbc.sql("INSERT INTO baseline VALUES ('base-a','p-a'),('base-b','p-b')").update();
        jdbc.sql("CREATE TABLE report_job(id VARCHAR, project_id VARCHAR)").update();
        jdbc.sql("INSERT INTO report_job VALUES ('report-a','p-a'),('report-b','p-b')").update();
        jdbc.sql("CREATE TABLE import_job(id VARCHAR, requested_by VARCHAR)").update();
        jdbc.sql("INSERT INTO import_job VALUES ('import-a','alice'),('import-b','bob')").update();
        var sync = new SyncProperties();
        sync.getProjects().put("alpha", "Alpha model");
        sync.getProjects().put("beta", "p-b");
        var bindings = new ProjectBindings(sync, jdbc, new BranchWorkspaces(jdbc));
        when(roles.list()).thenReturn(List.of(
                new RoleMappings.Row(1,"*","global:admin",ToolRole.ADMIN,"system",null),
                new RoleMappings.Row(2,"*","project_user",ToolRole.ARCHITECT,"system",null),
                new RoleMappings.Row(3,"*","*",ToolRole.OBSERVER,"system",null),
                new RoleMappings.Row(4,"(unbound)","*",ToolRole.ARCHITECT,"system",null)));
        reads = new ProjectReadAccess(bindings, roles, sync, jdbc, auth, mock(AuditLog.class));
    }
}
