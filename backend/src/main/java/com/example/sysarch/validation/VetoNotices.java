package com.example.sysarch.validation;

import com.example.sysarch.ModelStamp;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.springframework.stereotype.Component;

/**
 * Why the last edit was vetoed, shown on the fields the user edited.
 *
 * <p>A vetoed edit is rolled back in {@code preAccept}, after Sirius has already answered the client, so no message
 * can go back with the response. Instead {@link SysarchEValidator} reports a notice ("修改未保存：…") on each field
 * the edit changed. Sirius' properties view shows it under the reverted value, and its Validation view lists it.
 *
 * <p>The notice lasts until the next edit: it remembers the model stamp after the rollback, and any change moves the
 * stamp on, so there is nothing to clear. It is view state per resource set (weak), never part of the model.
 */
@Component
public class VetoNotices {

    public static final String PREFIX = "修改未保存：";

    /** @param fields the features the vetoed edit changed, per edited element */
    private record Notice(long stamp, Map<EObject, Set<EStructuralFeature>> fields, List<String> reasons) {
    }

    private final Map<Notifier, Notice> byScope = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Called after {@link ChangeGuard#check}: with violations the edit was rolled back and gets a notice; without,
     * nothing is recorded (an earlier notice expires by itself, as the edit moved the stamp on).
     *
     * @param edited what the edit changed, from {@link ChangeGuard#edited}, taken before the check
     */
    public void record(Notifier scope, Map<EObject, Set<EStructuralFeature>> edited, List<ValidationError> introduced) {
        if (introduced.isEmpty() || edited.isEmpty()) {
            return;
        }
        Map<EObject, Set<EStructuralFeature>> fields = new IdentityHashMap<>(edited);
        this.byScope.put(scope, new Notice(ModelStamp.of(scope), fields, introduced.stream().map(ValidationError::message).distinct().toList()));
    }

    /** The notices for one element: one message per reason, about these features. Empty once anything changed. */
    public Map<String, Set<EStructuralFeature>> noticesFor(EObject element) {
        Notice notice = this.byScope.get(ModelStamp.scope(element));
        if (notice == null || notice.stamp() != ModelStamp.of(element) || !notice.fields().containsKey(element)) {
            return Map.of();
        }
        Map<String, Set<EStructuralFeature>> result = new java.util.LinkedHashMap<>();
        notice.reasons().forEach(reason -> result.put(PREFIX + reason, notice.fields().get(element)));
        return result;
    }
}
