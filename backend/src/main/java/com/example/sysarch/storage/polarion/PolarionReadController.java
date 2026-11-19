package com.example.sysarch.storage.polarion;

import com.example.sysarch.storage.ModelCodec;
import com.example.sysarch.storage.StoredModel;
import com.example.sysarch.sync.SyncProperties;
import com.example.sysarch.sync.polarion.PolarionToken;
import com.example.sysarch.storage.polarion.PolarionSnapshotManifest.*;
import com.example.sysarch.storage.polarion.PolarionSnapshotLoader.*;
import java.net.URI;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Separate, detached read browser. Never registers a model or publishes it into a writable Sirius context. */
@RestController
@RequestMapping("/api/v1/model-store")
@ConditionalOnProperty(prefix="sysarch.polarion-store",name="enabled",havingValue="true")
public final class PolarionReadController {
    private final URI server;
    private final ModelCodec codec;
    private final PolarionSnapshotLoader loader;
    private final PolarionReadViews views=new PolarionReadViews();
    public PolarionReadController(SyncProperties properties,ModelCodec codec) {
        this(properties,codec,new com.example.sysarch.storage.PolarionStoreProperties());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PolarionReadController(SyncProperties properties,ModelCodec codec,com.example.sysarch.storage.PolarionStoreProperties store) {
        this.server=properties.getBaseUrl().isBlank()?null:URI.create(properties.getBaseUrl());
        this.codec=codec; this.loader=new PolarionSnapshotLoader(codec,store::canReferenceRequirement);
    }
    public record Snapshot(String subject,long version,UUID commitId,StoredModel model,java.util.List<RequirementReference> requirements,java.util.List<PolarionReadViews.Description> views) { }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> rejected(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore()).header("Vary","Authorization")
            .body(Map.of("status",failure.getStatusCode().value(),"detail",failure.getReason()==null?"Model read failed":failure.getReason()));
    }
    @GetMapping("/projects/{polarionProject}/models")
    public ResponseEntity<PolarionRestReadSession.ModelPage> models(@PathVariable("polarionProject") String project,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="25") int pageSize,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        return read(authorization,session->session.models(project,page,pageSize));
    }
    @GetMapping("/projects/{polarionProject}/models/{root}/snapshot")
    public ResponseEntity<Snapshot> snapshot(@PathVariable("polarionProject") String project,@PathVariable String root,
            @RequestParam UUID modelUuid,@RequestHeader(value="Authorization",required=false) String authorization) {
        return read(authorization,session->{
            var loaded=this.loader.load(new Location(this.server,new Item(project,root,null),modelUuid),session);
            var model=this.codec.capture(modelUuid,loaded.model().documents(),loaded.model().identities());
            return new Snapshot(loaded.subject(),loaded.head().version(),loaded.head().commitId(),model,loaded.manifest().requirements(),views.descriptions());
        });
    }
    public record View(String subject,long version,UUID commitId,PolarionReadViews.Rendered representation) { }
    @GetMapping("/projects/{polarionProject}/models/{root}/views/{view}")
    public ResponseEntity<View> view(@PathVariable("polarionProject") String project,@PathVariable String root,@PathVariable String view,
            @RequestParam UUID modelUuid,@RequestParam(required=false) UUID elementUuid,
            @RequestHeader(value="Authorization",required=false) String authorization) {
        return read(authorization,session->{
            var loaded=this.loader.load(new Location(this.server,new Item(project,root,null),modelUuid),session);
            return new View(loaded.subject(),loaded.head().version(),loaded.head().commitId(),views.render(loaded.model(),view,elementUuid));
        });
    }
    private <T> ResponseEntity<T> read(String authorization,Function<PolarionRestReadSession,T> action) {
        PolarionToken token=PolarionToken.fromAuthorizationHeader(authorization);
        if(token==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Polarion PAT required");
        if(this.server==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Polarion server not configured");
        try(var session=PolarionRestReadSession.open(this.server,token)) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Authorization").body(action.apply(session));
        } catch(ReadFailure failure) {
            HttpStatus status=switch(failure.failure()) {
                case DENIED -> HttpStatus.FORBIDDEN;
                case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(status,"Polarion model read rejected: "+failure.failure().name());
        } catch(IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid model read parameters");
        }
    }
}
