package com.example.sysarch.storage.command;

import com.example.sysarch.storage.command.DurableCommandGate.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Explicit isolated-schema PostgreSQL test. No Polarion connection, model mutation or production gate activation. */
public final class CommandGateLiveAcceptance {
    private static final List<Map<String,Object>> CHECKS = new ArrayList<>();
    private static DriverManagerDataSource data(String schema) {
        if (!schema.matches("sysarch_gate_test_[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid test scope");
        String url = System.getenv("POSTGRES_URL");
        if (url != null && !url.equals("jdbc:postgresql://localhost:5432/sysarch")) throw new IllegalArgumentException("Unexpected test database");
        var data = new DriverManagerDataSource("jdbc:postgresql://localhost:5432/sysarch?currentSchema=" + schema,
            System.getenv("POSTGRES_USER"), System.getenv("POSTGRES_PASSWORD"));
        data.setDriverClassName("org.postgresql.Driver"); return data;
    }
    private static void check(String name, boolean passed) {
        CHECKS.add(Map.of("name", name, "passed", passed));
        if (!passed) throw new IllegalStateException("Gate acceptance failed");
        System.out.println("PASS " + name);
    }
    private static Ticket ticket(JdbcClient jdbc) {
        return jdbc.sql("SELECT * FROM model_command_gate").query((row,index) -> new Ticket(
            new Request(UUID.fromString(row.getString("model_uuid")),UUID.fromString(row.getString("command_id")),row.getString("request_hash"),row.getString("actor")),
            UUID.fromString(row.getString("attempt_id")),UUID.fromString(row.getString("coordinator_epoch")))).single();
    }
    private static Process worker(Path argsFile, String mode, String schema, Request request) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"@"+argsFile.toAbsolutePath(),mode,schema,
            request.modelUuid().toString(),request.commandId().toString(),request.requestHash()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    private static void kill(Process process) {
        if (process.isAlive()) {process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();}
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            var gate = new DurableCommandGate(data(args[1]));
            try {
                gate.acquire(new Request(UUID.fromString(args[2]),UUID.fromString(args[3]),args[4],"gate-fixture"));
                if (args[0].equals("hold")) {System.out.println("HOLDING");System.out.flush();Thread.sleep(Duration.ofMinutes(2));}
                else System.out.println("ACQUIRED");
            } catch (Busy busy) {System.out.println("BUSY");}
            return;
        }
        String schema = "sysarch_gate_test_" + UUID.randomUUID().toString().replace("-", "");
        var data = data(schema); var jdbc = JdbcClient.create(data);
        var children = new ArrayList<Process>(); boolean created = false;
        Path directory = Path.of("build/polarion-store"); Files.createDirectories(directory);
        Path argsFile = Files.createTempFile(directory,"gate-worker-",".args");
        Files.writeString(argsFile,"-Xmx64m\n-cp\n\""+System.getProperty("java.class.path").replace("\\","\\\\").replace("\"","\\\"")+"\"\n"+CommandGateLiveAcceptance.class.getName()+"\n",StandardCharsets.UTF_8);
        boolean success = false;
        try {
            jdbc.sql("CREATE SCHEMA " + schema).update(); created = true;
            new ResourceDatabasePopulator(new ClassPathResource("db/sysarch/020-model-command-gate.sql")).execute(data);
            var gate = new DurableCommandGate(data);
            var bound = new ModelCommandProtocol().bind(UUID.randomUUID(),"gate-fixture",("{\"commandId\":\""+UUID.randomUUID()+"\",\"expectedVersion\":3,\"schemaVersion\":\"1.0\",\"operations\":[{\"kind\":\"RenameElement\",\"elementUuid\":\""+UUID.randomUUID()+"\",\"name\":\"Gate fixture\"}]}").getBytes(StandardCharsets.UTF_8));
            var request = bound.gateRequest();
            try {gate.acquire(request);check("fresh PostgreSQL coordinator is locked",false);} catch (Locked expected) {check("fresh PostgreSQL coordinator is locked",true);}
            UUID epoch = UUID.randomUUID();
            jdbc.sql("UPDATE model_command_coordinator SET state='READY',epoch=:epoch").param("epoch",epoch.toString()).update();
            for(int index=0;index<4;index++) children.add(worker(argsFile,"acquire",schema,request));
            int winners=0, busy=0;
            for(var child:children) {
                if(!child.waitFor(20,TimeUnit.SECONDS)) throw new IllegalStateException("Worker deadline");
                String output=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
                if(child.exitValue()!=0) throw new IllegalStateException("Worker failed");
                if(output.equals("ACQUIRED")) winners++; else if(output.equals("BUSY")) busy++;
            }
            check("four Java processes have exactly one winner",winners==1 && busy==3);
            Ticket first=ticket(jdbc); new DurableCommandGate(data).markUnknown(first);
            check("unknown command survives a fresh connection",jdbc.sql("SELECT state FROM model_command_gate").query(String.class).single().equals("UNKNOWN"));
            check("absent remote record does not release unknown command",!gate.reconcile(first,value->Outcome.NOT_FOUND));
            check("confirmed rollback releases exact pending ticket",gate.reconcile(first,value->Outcome.ROLLED_BACK));
            Ticket replay=gate.acquire(request);
            var mismatch=new CommittedCommandVerifier.Receipt("1.0",request.modelUuid(),request.commandId(),request.actor(),"b".repeat(64),3,4,UUID.randomUUID(),"c".repeat(64));
            check("mismatched receipt leaves PostgreSQL command unknown",!gate.reconcile(replay,new CommittedCommandVerifier(bound,reader(bound,mismatch)))
                && jdbc.sql("SELECT state FROM model_command_gate").query(String.class).single().equals("UNKNOWN"));
            var match=new CommittedCommandVerifier.Receipt("1.0",request.modelUuid(),request.commandId(),request.actor(),request.requestHash(),3,4,UUID.randomUUID(),"c".repeat(64));
            check("matching fixture receipt releases exact PostgreSQL ticket",gate.reconcile(replay,new CommittedCommandVerifier(bound,reader(bound,match))));
            var held=worker(argsFile,"hold",schema,request); children.add(held);
            long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
            while(held.getInputStream().available()==0 && held.isAlive() && System.nanoTime()<deadline) Thread.sleep(50);
            if(held.getInputStream().available()==0 || !held.inputReader().readLine().equals("HOLDING")) throw new IllegalStateException("Holding worker failed");
            Ticket killed=ticket(jdbc); kill(held); if(!held.waitFor(10,TimeUnit.SECONDS)) throw new IllegalStateException("Worker termination failed");
            try {new DurableCommandGate(data).acquire(request);check("killed process leaves a blocking durable command",false);} catch(Busy expected) {check("killed process leaves a blocking durable command",true);}
            gate.markUnknown(killed); check("verified result recovers killed-process command",gate.reconcile(killed,value->Outcome.COMMITTED));
            Ticket next=gate.acquire(request);
            try {gate.markUnknown(first);check("old attempt cannot release a new attempt",false);} catch(LostOwnership expected) {check("old attempt cannot release a new attempt",true);}
            jdbc.sql("UPDATE model_command_coordinator SET epoch=:epoch").param("epoch",UUID.randomUUID().toString()).update();
            try {gate.reconcile(next,value->Outcome.COMMITTED);check("new coordinator epoch fences old tickets",false);} catch(LostOwnership expected) {check("new coordinator epoch fences old tickets",true);}
            success=true;
        } finally {
            children.forEach(CommandGateLiveAcceptance::kill);
            for(var child:children) child.waitFor(10,TimeUnit.SECONDS);
            if(created) {
                // Identifier is generated locally and validated by data(); never drop an existing shared schema.
                jdbc.sql("DROP SCHEMA " + schema + " CASCADE").update();
                check("isolated test schema removed",jdbc.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=:schema").param("schema",schema).query(Integer.class).single()==0);
            }
            Files.deleteIfExists(argsFile);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("m3-command-gate-live.json").toFile(),
                Map.of("checks",CHECKS,"success",success,"productionCoordinatorActivated",false,"polarionDatabaseTouched",false));
        }
    }
    private static CommittedCommandVerifier.ReceiptReader reader(ModelCommandProtocol.BoundCommand command,CommittedCommandVerifier.Receipt receipt) {
        return new CommittedCommandVerifier.ReceiptReader() {
            public String verifiedActor() {return command.actor();}
            public java.util.Optional<CommittedCommandVerifier.Receipt> read(UUID model,UUID id) {return java.util.Optional.of(receipt);}
        };
    }
}
