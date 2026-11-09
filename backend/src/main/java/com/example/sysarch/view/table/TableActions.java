package com.example.sysarch.view.table;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.validation.ConstraintValidator;
import com.example.sysarch.validation.ValidationError;
import com.example.sysarch.view.api.CellCondition;
import com.example.sysarch.view.api.TableAction;
import com.example.sysarch.view.api.TableDescription;

import java.util.Collection;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Runs a table action on one cell (P1 T4), on the model as it is. Callers run it inside the editing context
 * ({@link TableService}), so Sirius saves the change, refreshes every view and applies the save-time veto as for any
 * edit.
 *
 * <ul>
 * <li><b>create-link</b>: a new element of the target class whose {@code ==} features are the row and the column
 * (an Allocate {@code from} the row {@code to} the column), named {@code row → column}. With {@code validateOnCreate}
 * every constraint of the element is evaluated <i>before</i> it is added, as the canvas does for an edge drop
 * (spec P1 §3.3): a rejected link never reaches the model, and the errors go back to the caller.</li>
 * <li><b>delete-link</b>: deletes the elements that mark the cell.</li>
 * <li><b>link</b>: adds the column to the row's many-valued {@code feature}.</li>
 * </ul>
 */
public final class TableActions {

    /** The outcome: {@code changed} if the model was modified; {@code errors} if the action was refused. */
    public record Outcome(boolean changed, List<ValidationError> errors) {

        public static Outcome refused(String code, String message, List<String> anchors) {
            return new Outcome(false, List.of(new ValidationError(code, null, message, anchors)));
        }
    }

    private TableActions() {
    }

    public static Outcome apply(TableDescription table, Collection<Resource> resources, String actionId, String rowId, String columnId,
            ConstraintValidator constraints) {
        MatrixProjector.Model model = MatrixProjector.Model.of(table, resources);
        TableAction action = model.action(actionId);
        if (action == null) {
            throw new IllegalArgumentException("unknown action '" + actionId + "' of table " + table.id());
        }
        EObject row = model.rowsById.get(rowId);
        EObject column = model.columnsById.get(columnId);
        if (row == null || column == null) {
            throw new IllegalArgumentException("no " + (row == null ? "row '" + rowId + "'" : "column '" + columnId + "'") + " in table " + table.id());
        }
        boolean marked = model.marked(row, column);
        return switch (action.kind()) {
            case CREATE_LINK -> marked ? Outcome.refused("ALREADY_LINKED", "这里已经有" + action.label().replaceFirst("^建立", "") + "了", List.of(rowId, columnId))
                    : createLink(table, action, row, column, constraints);
            case DELETE_LINK -> {
                if (!marked) {
                    yield Outcome.refused("NOT_LINKED", "这个格子里没有可取消的内容", List.of(rowId, columnId));
                }
                for (EObject element : model.cellElements(row, column)) {
                    EcoreUtil.delete(element, true);
                }
                yield new Outcome(true, List.of());
            }
            case LINK -> link(action, row, column);
        };
    }

    private static Outcome createLink(TableDescription table, TableAction action, EObject row, EObject column, ConstraintValidator constraints) {
        EClass type = MatrixProjector.eClass(action.targetClass());
        EObject element = EcoreUtil.create(type);
        set(table.cellCondition(), element, row, column);
        EStructuralFeature name = type.getEStructuralFeature("name");
        if (name != null) {
            element.eSet(name, label(row) + " → " + label(column));
        }
        if (action.validateOnCreate() && element instanceof ArchElement archElement) {
            List<ValidationError> errors = constraints.validate(archElement);
            if (!errors.isEmpty()) {
                return new Outcome(false, errors); // nothing was added: the element was never in the model
            }
        }
        Resource resource = row.eResource() != null ? row.eResource() : column.eResource();
        resource.getContents().add(element);
        return new Outcome(true, List.of());
    }

    /** Sets every {@code feature == row|column} of the (conjunctive) condition; vsm-compiler guarantees that shape. */
    private static void set(CellCondition condition, EObject element, EObject row, EObject column) {
        switch (condition) {
            case CellCondition.And and -> {
                set(and.left(), element, row, column);
                set(and.right(), element, row, column);
            }
            case CellCondition.EqualTo equalTo -> element.eSet(element.eClass().getEStructuralFeature(equalTo.feature()),
                    equalTo.side() == CellCondition.Side.ROW ? row : column);
            default -> throw new IllegalStateException("create-link needs a conjunction of == conditions");
        }
    }

    private static Outcome link(TableAction action, EObject row, EObject column) {
        EStructuralFeature feature = row.eClass().getEStructuralFeature(action.feature());
        if (feature == null || !feature.isMany()) {
            throw new IllegalStateException("link feature " + action.feature() + " is not a many-valued feature of " + row.eClass().getName());
        }
        @SuppressWarnings("unchecked")
        EList<EObject> values = (EList<EObject>) row.eGet(feature);
        if (values.contains(column)) {
            return Outcome.refused("ALREADY_LINKED", "这里已经有这条关系了", List.of(ElementIds.of(row), ElementIds.of(column)));
        }
        values.add(column);
        return new Outcome(true, List.of());
    }

    private static String label(EObject element) {
        EStructuralFeature name = element.eClass().getEStructuralFeature("name");
        Object value = name == null ? null : element.eGet(name);
        return value == null ? ElementIds.of(element) : value.toString();
    }
}
