package com.example.sysarch.view.table;

import com.example.sysarch.ElementIds;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.view.api.Axis;
import com.example.sysarch.view.api.CellCondition;
import com.example.sysarch.view.api.TableAction;
import com.example.sysarch.view.api.TableActionKind;
import com.example.sysarch.view.api.TableDescription;
import com.example.sysarch.view.projection.FilterExpression;
import com.example.sysarch.view.projection.LabelTemplates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Projects a {@link TableDescription} onto the model (P1 T4), the table counterpart of {@code DiagramProjector}.
 *
 * <ol>
 * <li><b>Scope</b>: every sysarch element of the given resources (a Sirius project's documents), in document order.</li>
 * <li><b>Rows and columns</b>: the elements of the axis type that pass its filter. An axis of the abstract
 * {@code ArchElement} holds only {@code PartDefinition} and {@code Part} (user's decision, see {@link Axis}).
 * With {@code groupBy: layer}, headers are ordered by layer (OPERATIONAL → EPBS), then document order.</li>
 * <li><b>Cells</b>: see {@link TableDescription} for which element the condition is evaluated on.</li>
 * </ol>
 */
public final class MatrixProjector {

    /** What an {@code ArchElement} axis holds. */
    static final Set<EClass> STRUCTURAL = Set.of(SysarchPackage.Literals.PART_DEFINITION, SysarchPackage.Literals.PART);

    private MatrixProjector() {
    }

    public static Matrix project(TableDescription table, Collection<Resource> resources) {
        Model model = Model.of(table, resources);
        List<Matrix.Header> rows = model.rows.stream().map(e -> header(table.rows(), e)).toList();
        List<Matrix.Header> columns = model.columns.stream().map(e -> header(table.columns(), e)).toList();
        List<Matrix.Cell> cells = new ArrayList<>();
        Set<String> marked = new LinkedHashSet<>();
        for (EObject row : model.rows) {
            for (EObject column : model.columns) {
                if (model.marked(row, column) && marked.add(ElementIds.of(row) + "\u0000" + ElementIds.of(column))) {
                    cells.add(new Matrix.Cell(ElementIds.of(row), ElementIds.of(column), model.links(row, column)));
                }
            }
        }
        Set<String> groupKeys = new LinkedHashSet<>();
        rows.forEach(h -> groupKeys.add(h.group()));
        columns.forEach(h -> groupKeys.add(h.group()));
        List<Matrix.Group> groups = groupKeys.stream().filter(k -> k != null).map(k -> new Matrix.Group(k, groupLabel(k))).toList();
        List<Matrix.Action> actions = table.actions().stream()
                .map(a -> new Matrix.Action(a.id(), a.label(), a.kind().name(), a.kind() == TableActionKind.DELETE_LINK,
                        a.kind() == TableActionKind.LINK ? undoLabel(a.label()) : null))
                .toList();
        return new Matrix(table.id(), table.label(), table.markSymbol(), table.markColor(), groups, rows, columns, cells, actions);
    }

    /** 建立满足关系 → 取消满足关系; any other label gets 取消 in front. */
    static String undoLabel(String label) {
        if (label == null) {
            return "取消";
        }
        return label.startsWith("建立") ? "取消" + label.substring(2) : "取消" + label;
    }

    /** {@code LOGICAL} → {@code LA 逻辑架构}; other values unchanged. */
    static String groupLabel(String key) {
        return switch (key) {
            case "OPERATIONAL" -> "OA 运行分析";
            case "SYSTEM" -> "SA 系统分析";
            case "LOGICAL" -> "LA 逻辑架构";
            case "PHYSICAL" -> "PA 物理架构";
            case "EPBS" -> "EPBS 构型项";
            default -> key;
        };
    }

    private static Matrix.Header header(Axis axis, EObject element) {
        String label = LabelTemplates.render(axis.labelTemplate(), element);
        return new Matrix.Header(ElementIds.of(element), label == null ? "" : label, group(axis, element));
    }

    static String group(Axis axis, EObject element) {
        if (axis.groupBy() == null) {
            return null;
        }
        EStructuralFeature feature = element.eClass().getEStructuralFeature(axis.groupBy());
        Object value = feature == null ? null : element.eGet(feature);
        return value instanceof Enumerator enumerator ? enumerator.getName() : value == null ? null : value.toString();
    }

    /** The model as the table sees it: rows, columns, the domain elements, and the cell test. Also used by the actions. */
    static final class Model {

        final TableDescription table;

        final EClass domainClass;

        final boolean selfMode;

        final List<EObject> rows;

        final List<EObject> columns;

        final List<EObject> domainElements;

        /** Row and column elements by id. */
        final Map<String, EObject> rowsById = new LinkedHashMap<>();

        final Map<String, EObject> columnsById = new LinkedHashMap<>();

        private Model(TableDescription table, List<EObject> all) {
            this.table = table;
            this.domainClass = eClass(table.domainType());
            EClass rowClass = eClass(table.rows().type());
            this.selfMode = this.domainClass.isSuperTypeOf(rowClass);
            this.rows = axis(table.rows(), all);
            this.columns = axis(table.columns(), all);
            this.domainElements = all.stream().filter(this.domainClass::isInstance).toList();
            this.rows.forEach(e -> this.rowsById.putIfAbsent(ElementIds.of(e), e));
            this.columns.forEach(e -> this.columnsById.putIfAbsent(ElementIds.of(e), e));
        }

        static Model of(TableDescription table, Collection<Resource> resources) {
            List<EObject> all = new ArrayList<>();
            for (Resource resource : resources) {
                for (TreeIterator<EObject> it = resource.getAllContents(); it.hasNext();) {
                    EObject element = it.next();
                    if (element.eClass().getEPackage() == SysarchPackage.eINSTANCE) {
                        all.add(element);
                    }
                }
            }
            return new Model(table, all);
        }

        boolean marked(EObject row, EObject column) {
            if (this.selfMode) {
                return this.domainClass.isInstance(row) && holds(this.table.cellCondition(), row, row, column);
            }
            return !this.cellElements(row, column).isEmpty();
        }

        /** The domain elements that mark the cell (not in self mode). */
        List<EObject> cellElements(EObject row, EObject column) {
            return this.domainElements.stream().filter(e -> holds(this.table.cellCondition(), e, row, column)).toList();
        }

        /** The link actions whose relation the row holds to the column (self mode only). */
        List<String> links(EObject row, EObject column) {
            if (!this.selfMode) {
                return List.of();
            }
            return this.table.actions().stream().filter(a -> a.kind() == TableActionKind.LINK && holdsLink(row, a.feature(), column))
                    .map(TableAction::id).toList();
        }

        TableAction action(String id) {
            return this.table.actions().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
        }
    }

    private static List<EObject> axis(Axis axis, List<EObject> all) {
        EClass type = eClass(axis.type());
        Predicate<EObject> structural = type == SysarchPackage.Literals.ARCH_ELEMENT ? e -> STRUCTURAL.contains(e.eClass()) : e -> true;
        Predicate<EObject> filter = axis.filter() == null ? e -> true : FilterExpression.parse(axis.filter());
        List<EObject> elements = all.stream().filter(type::isInstance).filter(structural).filter(filter).toList();
        if (axis.groupBy() == null) {
            return elements;
        }
        List<EObject> sorted = new ArrayList<>(elements); // stable: document order within a group
        sorted.sort(Comparator.comparingInt(e -> groupOrder(group(axis, e))));
        return sorted;
    }

    private static int groupOrder(String group) {
        if (group == null) {
            return Integer.MAX_VALUE;
        }
        Layer layer = Layer.get(group);
        return layer == null ? Integer.MAX_VALUE - 1 : layer.getValue();
    }

    static boolean holds(CellCondition condition, EObject self, EObject row, EObject column) {
        return switch (condition) {
            case CellCondition.And and -> holds(and.left(), self, row, column) && holds(and.right(), self, row, column);
            case CellCondition.Or or -> holds(or.left(), self, row, column) || holds(or.right(), self, row, column);
            case CellCondition.Not not -> !holds(not.inner(), self, row, column);
            case CellCondition.EqualTo equalTo -> value(self, equalTo.feature()) == side(equalTo.side(), row, column);
            case CellCondition.Includes includes -> value(self, includes.feature()) instanceof Collection<?> values
                    && values.contains(side(includes.side(), row, column));
        };
    }

    static boolean holdsLink(EObject row, String feature, EObject column) {
        return value(row, feature) instanceof Collection<?> values && values.contains(column);
    }

    private static Object value(EObject self, String featureName) {
        EStructuralFeature feature = self.eClass().getEStructuralFeature(featureName);
        return feature == null ? null : self.eGet(feature);
    }

    private static EObject side(CellCondition.Side side, EObject row, EObject column) {
        return side == CellCondition.Side.ROW ? row : column;
    }

    /** {@code sysarch::Allocate} → its EClass. */
    static EClass eClass(String domainType) {
        String name = domainType.substring(domainType.indexOf("::") + 2);
        if (SysarchPackage.eINSTANCE.getEClassifier(name) instanceof EClass eClass) {
            return eClass;
        }
        throw new IllegalArgumentException("unknown type " + domainType);
    }
}
