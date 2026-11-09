package com.example.sysarch.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Which user sent an input to an editing context (P2 T1). Sirius runs an input on the editing context's own thread,
 * away from the HTTP request, and an input carries no user, only its client-chosen id. So the user is recorded under
 * that id when the request arrives ({@link GraphQLEditorFilter} for Sirius' GraphQL mutations, {@code ProjectModels}
 * for our own model tasks) and looked up when the change is checked against the locks.
 *
 * <p>An input that was never recorded is a system input (sync pull, repair, migration): locks do not apply to it. A
 * recorded input without a user is an anonymous edit: every lock applies to it, and it takes no lock itself.
 */
@Component
public class Editors {

    /** The user of an input; {@code user} is null for an anonymous edit. */
    public record Editor(String user) {

        public static final Editor ANONYMOUS = new Editor(null);

        public boolean holds(String owner) {
            return this.user != null && this.user.equals(owner);
        }

        public String label() {
            return this.user == null ? "匿名用户" : this.user;
        }
    }

    static final Duration KEEP = Duration.ofMinutes(5);

    private record Entry(Editor editor, long at) {
    }

    private final Map<String, Entry> byInput = new ConcurrentHashMap<>();

    private final Clock clock;

    public Editors() {
        this(Clock.systemUTC());
    }

    Editors(Clock clock) {
        this.clock = clock;
    }

    public void record(String inputId, Editor editor) {
        if (inputId == null) {
            return;
        }
        long now = this.clock.millis();
        if (this.byInput.size() > 1000) {
            this.byInput.values().removeIf(e -> now - e.at() > KEEP.toMillis());
        }
        this.byInput.put(inputId, new Entry(editor, now));
    }

    /** The editor of an input, or empty for a system input. */
    public Optional<Editor> of(Object inputId) {
        Entry entry = inputId == null ? null : this.byInput.get(inputId.toString());
        return entry == null ? Optional.empty() : Optional.of(entry.editor());
    }

    public void forget(Object inputId) {
        if (inputId != null) {
            this.byInput.remove(inputId.toString());
        }
    }
}
