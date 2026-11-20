package com.example.sysarch.probe;

import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.types.Text;
import com.polarion.platform.ITransactionService;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.security.login.AccessToken;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.security.auth.Subject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Dedicated M0 test extension, not the production model-store endpoint. No system-user impersonation. */
public final class ProbeServlet extends HttpServlet {
    private static final class Trace {
        final long started = System.currentTimeMillis();
        final Thread thread = Thread.currentThread();
        volatile String actor;
        volatile String stage = "AUTHENTICATING";
    }
    private static final java.util.concurrent.ConcurrentHashMap<String, Trace> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private static void stage(String name) {
        Trace trace = CURRENT.get(); if (trace != null) trace.stage = name;
    }
    private ISecurityService security;
    private ITrackerService tracker;
    private ITransactionService transactions;
    private boolean enabled;
    private boolean managedCommitEnabled;
    private Set<String> projects = Set.of();
    private JdbcCommandGate commandGate;

    @Override
    public void init() throws javax.servlet.ServletException {
        try (var input = getClass().getResourceAsStream("/probe.properties")) {
            Properties config = new Properties();
            if (input != null) config.load(input);
            this.enabled = "true".equals(config.getProperty("enabled"));
            this.managedCommitEnabled = "true".equals(config.getProperty("managedCommit"));
            this.projects = Set.of(config.getProperty("projects", "").split(","));
            if ("true".equals(config.getProperty("gateRequired"))) {
                this.commandGate = new JdbcCommandGate(config.getProperty("gateFile"));
            }
            var platform = PlatformContext.getPlatform();
            this.security = platform.lookupService(ISecurityService.class);
            this.tracker = platform.lookupService(ITrackerService.class);
            this.transactions = platform.lookupService(ITransactionService.class);
        } catch (Exception e) { throw new javax.servlet.ServletException("M0 probe initialization failed"); }
    }

    private record Result(int status, Map<String, Object> body) { }
    private static final class Rejected extends RuntimeException {
        private final int status;
        Rejected(int status, String code) { super(code); this.status = status; }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("application/json;charset=UTF-8");
        if (!this.enabled) { reply(response, new Result(404, Map.of("error", "probe-disabled"))); return; }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() > 8192) {
            reply(response, new Result(401, Map.of("error", "bearer-required"))); return;
        }
        Subject subject;
        String traceId = UUID.randomUUID().toString();
        Trace trace = new Trace(); ACTIVE.put(traceId, trace); CURRENT.set(trace);
        try {
            subject = this.security.login().from("sysarch-m0-probe")
                    .authenticator(AccessToken.id()).with(AccessToken.token(header.substring(7))).perform();
        } catch (Exception e) {
            ACTIVE.remove(traceId); CURRENT.remove();
            reply(response, new Result(401, Map.of("error", "invalid-credential"))); return;
        }
        try {
            Result result = this.security.doAsUser(subject, (PrivilegedExceptionAction<Result>) () -> {
                String actor = this.security.getCurrentUser();
                trace.actor = actor; stage("AUTHENTICATED");
                if (actor == null || actor.isBlank()) throw new Rejected(401, "missing-subject");
                if ("GET".equals(request.getMethod()) && "/whoami".equals(request.getPathInfo())) {
                    return new Result(200, Map.of("actor", actor, "authentication", "sdk-access-token", "writesEnabled", true,
                            "coordination", this.commandGate == null ? "native-sdk-only" : "shared-postgresql",
                            "capabilities", List.of("title-drift", "capacity", "model-uuid-registration")));
                }
                if ("GET".equals(request.getMethod()) && "/active".equals(request.getPathInfo())) {
                    List<Object> active = new ArrayList<>();
                    ACTIVE.forEach((id, entry) -> {
                        if (actor.equals(entry.actor) && entry != trace) active.add(Map.of("requestId", id, "stage", entry.stage,
                                "elapsedMs", System.currentTimeMillis() - entry.started, "threadState", entry.thread.getState().name(),
                                "stack", java.util.Arrays.stream(entry.thread.getStackTrace()).limit(14)
                                    .map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList()));
                    });
                    return new Result(200, Map.of("actor", actor, "active", active));
                }
                if (!"POST".equals(request.getMethod()) || !"1".equals(request.getHeader("X-Sysarch-Probe"))) {
                    throw new Rejected(405, "explicit-post-required");
                }
                if (request.getContentLengthLong() > 8192) throw new Rejected(413, "request-too-large");
                String project = parameter(request, "project");
                if (!this.projects.contains(project)) throw new Rejected(403, "project-not-allowlisted");
                String run = UUID.fromString(parameter(request, "run")).toString();
                String action = request.getPathInfo();
                if ("/managed-commit".equals(action)) return runManagedCommit(request, project, run, actor, traceId);
                JdbcCommandGate.Ticket ticket = null;
                if (Set.of("/commit", "/capacity").contains(action) && this.commandGate != null) {
                    // Check object rights in a completed read-only transaction BEFORE touching shared coordination.
                    this.transactions.execute().callable(() -> {
                        requireModify(owned(project, parameter(request, "root"), run)); return null;
                    });
                    stage("ACQUIRING_DURABLE_GATE");
                    ticket = this.commandGate.acquire(project + "/" + run + "/" + parameter(request, "root"), traceId,
                            UUID.fromString(parameter(request, "command")).toString(), actor);
                    if (ticket == null) throw new Rejected(409, "model-command-in-progress");
                }
                JdbcCommandGate.ModelClaim modelClaim = null;
                String modelUuid = null;
                if ("/register-model".equals(action)) {
                    if (this.commandGate == null) throw new Rejected(503, "registration-coordinator-required");
                    this.transactions.execute().callable(() -> {
                        var context = this.tracker.getTrackerProject(project);
                        if (context.isUnresolvable() || !context.can().read() || !context.can().createWorkItemOfType("task")) {
                            throw new Rejected(403, "cannot-create-probe-task");
                        }
                        return null;
                    });
                    modelUuid = UUID.fromString(parameter(request, "modelUuid")).toString();
                    modelClaim = this.commandGate.claimModel(modelUuid, project, actor, traceId);
                    if (modelClaim == null) throw new Rejected(409, "model-uuid-already-registered");
                }
                final JdbcCommandGate.Ticket acquired = ticket;
                final JdbcCommandGate.ModelClaim claimed = modelClaim;
                final String requestedModelUuid = modelUuid;
                try {
                stage("BEFORE_TRANSACTION");
                Result transactionResult = this.transactions.execute().write().callable(() -> {
                    stage("IN_TRANSACTION");
                    if ("/setup".equals(action) || "/register-model".equals(action)) return setup(project, run, actor, requestedModelUuid);
                    IWorkItem root = owned(project, parameter(request, "root"), run);
                    Properties state = state(root);
                    stage("ROOT_LOADED");
                    return switch (action) {
                        case "/inspect" -> inspect(root, state, project, run, actor);
                        case "/command-status" -> commandStatus(request, state, actor);
                        case "/recover" -> recoveryProof(request, root, state, project, run, actor);
                        case "/commit" -> commit(request, root, state, project, run, actor);
                        case "/capacity" -> capacity(request, root, state, project, run, actor);
                        case "/capacity-check" -> capacityCheck(request, state, project, run, actor);
                        case "/cleanup" -> cleanup(root, state, project, run, actor);
                        default -> throw new Rejected(404, "unknown-operation");
                    };
                });
                stage("TRANSACTION_RETURNED");
                if (acquired != null && "/commit".equals(action) && "after-commit".equals(request.getParameter("fault"))) {
                    throw new IOException("Injected acknowledgement failure after confirmed remote commit");
                }
                if (acquired != null) this.commandGate.release(acquired);
                if (claimed != null) this.commandGate.finishModel(claimed, transactionResult.body().get("root").toString(), false);
                if ("/cleanup".equals(action) && this.commandGate != null && transactionResult.body().containsKey("modelUuid")) {
                    this.commandGate.retireTestModel(transactionResult.body().get("modelUuid").toString(), project, parameter(request, "root"), actor);
                }
                if ("/recover".equals(action) && transactionResult.status() == 200 && this.commandGate != null) {
                    boolean recovered = this.commandGate.recoverCommitted(project + "/" + run + "/" + parameter(request, "root"),
                            UUID.fromString(parameter(request, "command")).toString(), actor);
                    Map<String, Object> body = new LinkedHashMap<>(transactionResult.body()); body.put("recovered", recovered);
                    return new Result(200, body);
                }
                return transactionResult;
                } catch (Exception failure) {
                    if (acquired != null) {
                        if (failure instanceof Rejected) this.commandGate.release(acquired);
                        else this.commandGate.unknown(acquired);
                    }
                    if (claimed != null) {
                        if (failure instanceof Rejected) this.commandGate.finishModel(claimed, null, true);
                        else this.commandGate.unknownModel(claimed);
                    }
                    throw failure;
                }
            });
            reply(response, result);
            stage("RESPONSE_WRITTEN");
        } catch (Exception e) {
            Throwable cause = e;
            for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
                if (cause instanceof Rejected rejection) {
                    reply(response, new Result(rejection.status, Map.of("error", rejection.getMessage()))); return;
                }
                if (cause.getClass().getSimpleName().contains("PermissionDenied")) {
                    reply(response, new Result(403, Map.of("error", "permission-denied"))); return;
                }
            }
            // No raw exception message: authentication providers or transports may include sensitive details.
            List<String> types = new ArrayList<>();
            for (Throwable t = e; t != null && types.size() < 8; t = t.getCause()) types.add(t.getClass().getSimpleName());
            reply(response, new Result(503, Map.of("error", "transaction-outcome-requires-inspection", "types", types, "stage", trace.stage)));
        } finally {
            stage("LOGOUT");
            try { this.security.logout(subject); }
            finally { ACTIVE.remove(traceId); CURRENT.remove(); }
        }
    }

    private Result runManagedCommit(HttpServletRequest request, String project, String run, String actor, String traceId) throws Exception {
        stage("MANAGED_VALIDATE_REQUEST");
        if (!this.managedCommitEnabled || this.commandGate == null) throw new Rejected(404, "managed-commit-test-disabled");
        if (!"application/x-www-form-urlencoded".equals(request.getContentType()) || request.getContentLengthLong() < 1) {
            throw new Rejected(422, "bounded-form-required");
        }
        String model = UUID.fromString(parameter(request, "modelUuid")).toString();
        String command = UUID.fromString(parameter(request, "command")).toString();
        String root = parameter(request, "root");
        this.transactions.execute().callable(() -> {
            requireModify(managedItem(project, root, run, actor, model, "sysarchModel"));
            return null;
        });
        stage("MANAGED_ACQUIRE_GATE");
        var ticket = this.commandGate.acquire(project + "/" + run + "/" + root, traceId, command, actor);
        if (ticket == null) throw new Rejected(409, "model-command-in-progress");
        Result result;
        try {
            result = this.transactions.execute().write().callable(() -> managedCommit(request, project, run, actor, model, command, root));
        } catch (Exception failure) {
            if (failure instanceof Rejected) this.commandGate.release(ticket);
            else this.commandGate.unknown(ticket);
            throw failure;
        }
        this.commandGate.release(ticket);
        return result;
    }

    /** One-element canary using the managed model field contract. Only disposable owned fixtures are accepted. */
    private Result managedCommit(HttpServletRequest request, String project, String run, String actor,
            String model, String command, String rootId) throws Exception {
        stage("MANAGED_VALIDATE_PRECONDITIONS");
        IWorkItem root = managedItem(project, rootId, run, actor, model, "sysarchModel");
        IWorkItem element = managedItem(project, parameter(request, "element"), run, actor, model, "sysarchElement");
        requireModify(root);
        requireModify(element);
        var context = this.tracker.getTrackerProject(project);
        if (!context.can().createWorkItemOfType("sysarchCommit")) throw new Rejected(403, "cannot-create-managed-receipt");
        String elementUuid = UUID.fromString(parameter(request, "elementUuid")).toString();
        if (!elementUuid.equals(element.getCustomField("sysarchElementUuid"))) throw new Rejected(409, "managed-element-identity-changed");
        Object version = root.getCustomField("sysarchHeadVersion");
        if (!(version instanceof Number number) || number.longValue() != 1
                || !parameter(request, "rootRevision").equals(root.getLastRevision())
                || !parameter(request, "elementRevision").equals(element.getLastRevision())
                || !parameter(request, "expectedCommit").equals(root.getCustomField("sysarchHeadCommitId"))
                || !parameter(request, "expectedManifestHash").equals(root.getCustomField("sysarchHeadManifestHash"))) {
            throw new Rejected(409, "managed-precondition-changed");
        }
        String requestHash = parameter(request, "requestHash");
        if (!requestHash.matches("[a-f0-9]{64}")) throw new Rejected(422, "invalid-request-hash");
        byte[] snapshot = java.util.Base64.getUrlDecoder().decode(parameter(request, "snapshot"));
        String payload = parameter(request, "payload");
        if (snapshot.length > 4096 || payload.getBytes(StandardCharsets.UTF_8).length > 2048) throw new Rejected(413, "fixture-payload-too-large");

        String commitId = UUID.randomUUID().toString();
        stage("MANAGED_SAVE_PAYLOAD");
        element.setCustomField("sysarchPayload", Text.plain(payload));
        element.save();
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("project", project);
        owner.put("workItem", rootId);
        owner.put("revision", null);
        stage("MANAGED_SAVE_SNAPSHOT");
        var snapshotRef = saveManagedAttachment(root, "snapshot-" + commitId + ".json", snapshot, owner);
        Map<String, Object> binding = new LinkedHashMap<>(owner);
        binding.put("workItem", element.getId());
        byte[] manifest = json(Map.of("schemaVersion", "1.0", "modelUuid", model, "version", 2, "commitId", commitId,
            "snapshot", snapshotRef, "elements", List.of(Map.of("uuid", elementUuid, "item", binding)), "requirements", List.of())).getBytes(StandardCharsets.UTF_8);
        stage("MANAGED_SAVE_MANIFEST");
        var manifestRef = saveManagedAttachment(root, "manifest-" + commitId + ".json", manifest, owner);
        String manifestHash = (String) manifestRef.get("sha256");
        stage("MANAGED_SAVE_RECEIPT_RECORD");
        IWorkItem record = context.createWorkItem("sysarchCommit");
        record.setTitle("sysarch-m2-" + run + "-sysarchCommit");
        record.setCustomField("sysarchManaged", true);
        record.setCustomField("sysarchSchemaVersion", "1.0");
        record.setCustomField("sysarchModelUuid", model);
        record.setCustomField("sysarchCommandId", command);
        record.setCustomField("sysarchCommitId", commitId);
        record.setCustomField("sysarchRequestHash", requestHash);
        record.setCustomField("sysarchManifestHash", manifestHash);
        record.save();
        byte[] receipt = json(Map.of("schemaVersion", "1.0", "modelUuid", model, "commandId", command, "actor", actor,
            "requestHash", requestHash, "expectedVersion", 1, "version", 2, "commitId", commitId, "manifestHash", manifestHash)).getBytes(StandardCharsets.UTF_8);
        stage("MANAGED_SAVE_RECEIPT_ATTACHMENT");
        saveManagedAttachment(record, "sysarch-command-receipt.json", receipt, Map.of());

        stage("MANAGED_SAVE_HEAD");
        setManagedInteger(root, "sysarchHeadVersion", 2);
        root.setCustomField("sysarchHeadCommitId", commitId);
        root.setCustomField("sysarchHeadManifestAttachment", manifestRef.get("attachmentId"));
        root.setCustomField("sysarchHeadManifestHash", manifestHash);
        setManagedInteger(root, "sysarchHeadManifestBytes", manifest.length);
        root.save();
        if ("rollback".equals(request.getParameter("fault"))) throw new Rejected(409, "managed-test-rollback");
        stage("MANAGED_FINISH_TRANSACTION");
        return new Result(201, Map.of("actor", actor, "workItem", record.getId(), "commitId", commitId));
    }

    private static void setManagedInteger(IWorkItem item, String field, int value) {
        Object current = item.getCustomField(field);
        if (current instanceof Integer) item.setCustomField(field, value);
        else if (current instanceof Long) item.setCustomField(field, (long) value);
        else throw new Rejected(409, "managed-integer-field-type-changed");
    }

    private IWorkItem managedItem(String project, String id, String run, String actor, String model, String type) {
        if (!id.matches("[A-Za-z0-9_-]{1,80}")) throw new Rejected(422, "invalid-item-id");
        var context = this.tracker.getTrackerProject(project);
        if (context.isUnresolvable() || !context.can().read()) throw new Rejected(403, "project-not-readable");
        IWorkItem item = this.tracker.getWorkItem(project, id);
        if (item.isUnresolvable() || !item.can().read()) throw new Rejected(403, "item-not-readable");
        if (!("sysarch-m2-" + run + "-" + type).equals(item.getTitle()) || item.getAuthor() == null
                || !actor.equals(item.getAuthor().getId()) || !type.equals(item.getType().getId())
                || !Boolean.TRUE.equals(item.getCustomField("sysarchManaged"))
                || !"1.0".equals(item.getCustomField("sysarchSchemaVersion")) || !model.equals(item.getCustomField("sysarchModelUuid"))) {
            throw new Rejected(409, "not-an-owned-managed-fixture");
        }
        return item;
    }

    private static Map<String, Object> saveManagedAttachment(IWorkItem item, String name, byte[] bytes, Map<String, Object> owner) throws Exception {
        var attachment = item.createAttachment(name, name, new java.io.ByteArrayInputStream(bytes));
        attachment.save();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return Map.of("owner", owner, "attachmentId", attachment.getId(), "sha256", hash, "bytes", bytes.length);
    }

    private Result setup(String project, String run, String actor, String modelUuid) throws Exception {
        var context = this.tracker.getTrackerProject(project);
        if (context.isUnresolvable() || !context.can().read() || !context.can().createWorkItemOfType("task")) {
            throw new Rejected(403, "cannot-create-probe-task");
        }
        IWorkItem root = create(project, run, "root");
        IWorkItem left = create(project, run, "left");
        IWorkItem right = create(project, run, "right");
        Properties state = new Properties();
        state.setProperty("run", run); state.setProperty("owner", actor); state.setProperty("version", "0");
        state.setProperty("left", left.getId()); state.setProperty("right", right.getId());
        if (modelUuid != null) state.setProperty("modelUuid", modelUuid);
        root.addLinkedItem(left, context.getWorkItemLinkRoleEnum().wrapOption("relates_to"), null, false);
        rememberExpected(root, left, right, state);
        saveState(root, state);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("actor", actor, "root", root.getId(), "left", left.getId(), "right", right.getId(), "version", 0));
        if (modelUuid != null) body.put("modelUuid", modelUuid);
        return new Result(201, body);
    }

    private IWorkItem create(String project, String run, String suffix) {
        IWorkItem item = this.tracker.getTrackerProject(project).createWorkItem("task");
        item.setTitle(prefix(run) + suffix);
        item.save();
        return item;
    }

    private Result inspect(IWorkItem root, Properties state, String project, String run, String actor) {
        IWorkItem left = owned(project, state.getProperty("left"), run);
        IWorkItem right = owned(project, state.getProperty("right"), run);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("actor", actor, "version", Integer.parseInt(state.getProperty("version")),
                "leftTitle", left.getTitle(), "rightTitle", right.getTitle(), "links", root.getLinkedWorkItemsStructsDirect().size(),
                "revision", String.valueOf(root.getLastRevision())));
        List<String> differences = drift(root, left, right, state);
        body.put("drift", !differences.isEmpty()); body.put("driftFields", differences);
        if (state.containsKey("modelUuid")) body.put("modelUuid", state.getProperty("modelUuid"));
        return new Result(200, body);
    }

    private Result commit(HttpServletRequest request, IWorkItem root, Properties state, String project, String run, String actor) throws Exception {
        requireModify(root);
        if (!actor.equals(state.getProperty("owner"))) throw new Rejected(403, "probe-owner-required");
        String command = UUID.fromString(parameter(request, "command")).toString();
        String value = parameter(request, "value");
        if (!value.matches("[A-Za-z0-9-]{1,80}")) throw new Rejected(422, "invalid-probe-value");
        int expected = Integer.parseInt(parameter(request, "expectedVersion"));
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((run + "\n" + expected + "\n" + value).getBytes(StandardCharsets.UTF_8)));
        String commandKey = "command." + command;
        if (state.containsKey(commandKey + ".hash")) {
            if (!hash.equals(state.getProperty(commandKey + ".hash"))) throw new Rejected(409, "idempotency-payload-mismatch");
            return new Result(200, Map.of("actor", actor, "replayed", true, "version", Integer.parseInt(state.getProperty(commandKey + ".version")),
                    "commandItem", state.getProperty(commandKey + ".item")));
        }
        int version = Integer.parseInt(state.getProperty("version"));
        if (expected != version) throw new Rejected(409, "version-conflict");
        stage("VERSION_CHECKED");
        if (version >= 16) throw new Rejected(413, "probe-command-limit");
        IWorkItem left = owned(project, state.getProperty("left"), run);
        IWorkItem right = owned(project, state.getProperty("right"), run);
        requireModify(left); requireModify(right);
        if (!drift(root, left, right, state).isEmpty()) throw new Rejected(409, "external-change-detected");
        left.setTitle(prefix(run) + "left-" + value); left.save();
        stage("LEFT_SAVED");
        IWorkItem record = create(project, run, "command-" + command);
        stage("RECORD_CREATED");
        root.addLinkedItem(record, this.tracker.getTrackerProject(project).getWorkItemLinkRoleEnum().wrapOption("relates_to"), null, false);
        right.setTitle(prefix(run) + "right-" + value); right.save();
        stage("RIGHT_SAVED");
        state.setProperty("version", Integer.toString(version + 1));
        state.setProperty(commandKey + ".hash", hash);
        state.setProperty(commandKey + ".version", Integer.toString(version + 1));
        state.setProperty(commandKey + ".item", record.getId());
        rememberExpected(root, left, right, state);
        saveState(root, state);
        stage("ROOT_SAVED");
        if ("true".equals(request.getParameter("rollback"))) throw new Rejected(422, "injected-rollback");
        return new Result(200, Map.of("actor", actor, "replayed", false, "version", version + 1, "commandItem", record.getId()));
    }

    private Result commandStatus(HttpServletRequest request, Properties state, String actor) {
        String key = "command." + UUID.fromString(parameter(request, "command"));
        if (!state.containsKey(key + ".hash")) return new Result(404, Map.of("status", "NOT_FOUND", "actor", actor));
        return new Result(200, Map.of("status", "COMMITTED", "actor", actor,
                "version", Integer.parseInt(state.getProperty(key + ".version")), "commandItem", state.getProperty(key + ".item")));
    }

    private Result recoveryProof(HttpServletRequest request, IWorkItem root, Properties state, String project, String run, String actor) {
        requireModify(root);
        if (!actor.equals(state.getProperty("owner"))) throw new Rejected(403, "probe-owner-required");
        String command = UUID.fromString(parameter(request, "command")).toString();
        Result result = commandStatus(request, state, actor);
        if (result.status() != 200) return result;
        IWorkItem record = owned(project, state.getProperty("command." + command + ".item"), run);
        if (!record.getTitle().equals(prefix(run) + "command-" + command)) throw new Rejected(409, "command-record-mismatch");
        return result;
    }

    private Result cleanup(IWorkItem root, Properties state, String project, String run, String actor) {
        if (!actor.equals(state.getProperty("owner"))) throw new Rejected(403, "probe-owner-required");
        List<IWorkItem> items = new ArrayList<>();
        items.add(owned(project, state.getProperty("left"), run));
        items.add(owned(project, state.getProperty("right"), run));
        for (String name : state.stringPropertyNames()) if ((name.startsWith("command.") || name.startsWith("artifact.")) && name.endsWith(".item")) {
            items.add(owned(project, state.getProperty(name), run));
        }
        items.add(root);
        for (IWorkItem item : items) if (!item.can().delete()) throw new Rejected(403, "probe-cleanup-permission-required");
        List<String> ids = items.stream().map(IWorkItem::getId).toList();
        items.forEach(IWorkItem::delete);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("actor", actor, "deleted", ids));
        if (state.containsKey("modelUuid")) body.put("modelUuid", state.getProperty("modelUuid"));
        return new Result(200, body);
    }

    private static String linkState(IWorkItem root) {
        return root.getLinkedWorkItemsStructsDirect().stream().map(link -> link.getLinkRole().getId() + ":" + link.getLinkedItem().getId())
                .sorted().collect(java.util.stream.Collectors.joining("\n"));
    }

    private static void rememberExpected(IWorkItem root, IWorkItem left, IWorkItem right, Properties state) {
        state.setProperty("expected.root.title", root.getTitle());
        state.setProperty("expected.left.title", left.getTitle()); state.setProperty("expected.right.title", right.getTitle());
        state.setProperty("expected.links", linkState(root));
    }

    private static List<String> drift(IWorkItem root, IWorkItem left, IWorkItem right, Properties state) {
        List<String> differences = new ArrayList<>();
        if (!java.util.Objects.equals(root.getTitle(), state.getProperty("expected.root.title"))) differences.add("root.title");
        if (!java.util.Objects.equals(left.getTitle(), state.getProperty("expected.left.title"))) differences.add("left.title");
        if (!java.util.Objects.equals(right.getTitle(), state.getProperty("expected.right.title"))) differences.add("right.title");
        if (!java.util.Objects.equals(linkState(root), state.getProperty("expected.links"))) differences.add("root.links");
        return differences;
    }

    private Result capacity(HttpServletRequest request, IWorkItem root, Properties state, String project, String run, String actor) throws Exception {
        requireModify(root);
        if (!actor.equals(state.getProperty("owner"))) throw new Rejected(403, "probe-owner-required");
        String command = UUID.fromString(parameter(request, "command")).toString();
        int size = Integer.parseInt(parameter(request, "bytes"));
        if (size != 1048576 && size != 20971520) throw new Rejected(422, "capacity-size-not-allowlisted");
        String key = "artifact." + command;
        if (state.containsKey(key + ".item")) throw new Rejected(409, "capacity-already-created");
        IWorkItem item = create(project, run, "capacity-" + command);
        item.setDescription(Text.plain("A".repeat(65536))); item.save();
        byte[] payload = new byte[size]; for (int i = 0; i < size; i++) payload[i] = (byte) (i % 251);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        var attachment = item.createAttachment("probe-" + command + ".bin", "M0 bounded capacity fixture", new java.io.ByteArrayInputStream(payload));
        attachment.save();
        state.setProperty(key + ".item", item.getId()); state.setProperty(key + ".bytes", Integer.toString(size)); state.setProperty(key + ".sha256", hash);
        saveState(root, state);
        return new Result(201, Map.of("actor", actor, "item", item.getId(), "bytes", size, "sha256", hash, "textBytes", 65536));
    }

    private Result capacityCheck(HttpServletRequest request, Properties state, String project, String run, String actor) throws Exception {
        String command = UUID.fromString(parameter(request, "command")).toString();
        String key = "artifact." + command;
        IWorkItem item = owned(project, state.getProperty(key + ".item"), run);
        var attachment = item.getAttachmentByFileName("probe-" + command + ".bin");
        if (attachment == null || attachment.isUnresolvable() || !attachment.can().read()) throw new Rejected(403, "attachment-not-readable");
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long size = 0;
        try (var stream = attachment.getDataStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) {
                size += count; if (size > 20971520) throw new Rejected(413, "capacity-read-limit");
                digest.update(buffer, 0, count);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        return new Result(200, Map.of("actor", actor, "bytes", size, "sha256", actual,
                "hashMatches", actual.equals(state.getProperty(key + ".sha256")),
                "textBytes", item.getDescription().getContent().getBytes(StandardCharsets.UTF_8).length));
    }

    private IWorkItem owned(String project, String id, String run) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) throw new Rejected(422, "invalid-item-id");
        IWorkItem item = this.tracker.getWorkItem(project, id);
        if (item.isUnresolvable() || !item.can().read()) throw new Rejected(403, "item-not-readable");
        if (item.getTitle() == null || !item.getTitle().startsWith(prefix(run))) throw new Rejected(409, "not-an-owned-probe-item");
        return item;
    }

    private static void requireModify(IWorkItem item) {
        if (!item.can().modify()) throw new Rejected(403, "item-not-writable");
    }

    private static Properties state(IWorkItem root) throws IOException {
        Properties state = new Properties();
        state.load(new StringReader(root.getDescription().getContent()));
        return state;
    }

    private static void saveState(IWorkItem root, Properties state) throws IOException {
        StringWriter output = new StringWriter(); state.store(output, "SysArch M0 test state");
        root.setDescription(Text.plain(output.toString())); root.save();
    }

    private static String prefix(String run) { return "SYSARCH-M0 " + run + " "; }
    private static String parameter(HttpServletRequest request, String key) {
        String[] values = request.getParameterValues(key);
        if (values == null || values.length != 1 || values[0].isBlank()) throw new Rejected(422, "missing-or-duplicate-parameter");
        return values[0];
    }

    private static void reply(HttpServletResponse response, Result result) throws IOException {
        response.setStatus(result.status()); response.getWriter().write(json(result.body()));
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return "{" + map.entrySet().stream()
                .map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}";
        if (value instanceof List<?> list) return "[" + list.stream().map(ProbeServlet::json).collect(java.util.stream.Collectors.joining(",")) + "]";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        StringBuilder out = new StringBuilder("\"");
        for (char c : String.valueOf(value).toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
