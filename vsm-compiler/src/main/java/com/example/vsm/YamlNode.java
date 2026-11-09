package com.example.vsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Read-only view of a composed YAML node that keeps source line numbers, so every diagnostic can point at
 * {@code file:line}. Values are read as raw scalars; typing is enforced by the JSON Schema first.
 */
final class YamlNode {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Node node;

    private YamlNode(Node node) {
        this.node = node;
    }

    /** Parses one YAML document. Throws SnakeYAML's {@code MarkedYAMLException} on syntax errors. */
    static YamlNode parse(String text) {
        Node root = new Yaml(new LoaderOptions()).compose(new StringReader(text));
        return root == null ? null : new YamlNode(root);
    }

    /** 1-based line where this node starts. */
    int line() {
        return this.node.getStartMark().getLine() + 1;
    }

    boolean isMapping() {
        return this.node instanceof MappingNode;
    }

    /** Value of {@code key} in this mapping, or null. */
    YamlNode get(String key) {
        NodeTuple tuple = this.tuple(key);
        return tuple == null ? null : new YamlNode(tuple.getValueNode());
    }

    /** Line of {@code key} itself, or this node's line if the key is absent. */
    int keyLine(String key) {
        NodeTuple tuple = this.tuple(key);
        return tuple == null ? this.line() : tuple.getKeyNode().getStartMark().getLine() + 1;
    }

    boolean has(String key) {
        return this.tuple(key) != null;
    }

    /** Keys of this mapping in document order; empty for anything else. */
    List<String> keys() {
        if (!(this.node instanceof MappingNode mapping)) {
            return List.of();
        }
        return mapping.getValue().stream().map(t -> t.getKeyNode() instanceof ScalarNode key ? key.getValue() : "").toList();
    }

    String text(String key) {
        YamlNode value = this.get(key);
        return value == null ? null : value.scalar();
    }

    Integer integer(String key) {
        String text = this.text(key);
        return text == null ? null : Integer.valueOf(text);
    }

    boolean bool(String key) {
        String text = this.text(key);
        return text != null && isTrue(text);
    }

    /** Elements of the sequence under {@code key}; empty if the key is absent. */
    List<YamlNode> items(String key) {
        YamlNode value = this.get(key);
        if (value == null || !(value.node instanceof SequenceNode sequence)) {
            return List.of();
        }
        return sequence.getValue().stream().map(YamlNode::new).toList();
    }

    String scalar() {
        return this.node instanceof ScalarNode scalarNode ? scalarNode.getValue() : null;
    }

    /** Navigates a JSON Schema instance location (property names and array indexes). */
    YamlNode at(List<Object> path) {
        YamlNode current = this;
        for (Object element : path) {
            YamlNode next = null;
            if (element instanceof Integer index && current.node instanceof SequenceNode sequence
                    && index < sequence.getValue().size()) {
                next = new YamlNode(sequence.getValue().get(index));
            } else if (element instanceof String key) {
                next = current.get(key);
            }
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    /** Keys that occur twice in the same mapping, anywhere in the tree, as {@code key -> line}. */
    List<Map.Entry<String, Integer>> duplicateKeys() {
        List<Map.Entry<String, Integer>> duplicates = new ArrayList<>();
        collectDuplicates(this.node, duplicates);
        return duplicates;
    }

    /** Converts to a Jackson tree for JSON Schema validation, using YAML's resolved scalar types. */
    JsonNode toJson() {
        return toJson(this.node);
    }

    private NodeTuple tuple(String key) {
        if (this.node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode keyNode && key.equals(keyNode.getValue())) {
                    return tuple;
                }
            }
        }
        return null;
    }

    private static void collectDuplicates(Node node, List<Map.Entry<String, Integer>> duplicates) {
        if (node instanceof MappingNode mapping) {
            Map<String, Integer> seen = new HashMap<>();
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode keyNode) {
                    int line = keyNode.getStartMark().getLine() + 1;
                    if (seen.putIfAbsent(keyNode.getValue(), line) != null) {
                        duplicates.add(Map.entry(keyNode.getValue(), line));
                    }
                }
                collectDuplicates(tuple.getValueNode(), duplicates);
            }
        } else if (node instanceof SequenceNode sequence) {
            sequence.getValue().forEach(child -> collectDuplicates(child, duplicates));
        }
    }

    private static JsonNode toJson(Node node) {
        if (node instanceof MappingNode mapping) {
            ObjectNode object = JSON.objectNode();
            for (NodeTuple tuple : mapping.getValue()) {
                String key = tuple.getKeyNode() instanceof ScalarNode keyNode ? keyNode.getValue() : String.valueOf(tuple.getKeyNode());
                object.set(key, toJson(tuple.getValueNode()));
            }
            return object;
        }
        if (node instanceof SequenceNode sequence) {
            ArrayNode array = JSON.arrayNode();
            sequence.getValue().forEach(child -> array.add(toJson(child)));
            return array;
        }
        ScalarNode scalar = (ScalarNode) node;
        String value = scalar.getValue();
        Tag tag = scalar.getTag();
        try {
            if (Tag.INT.equals(tag)) {
                return JSON.numberNode(new BigInteger(value.replace("_", "")));
            }
            if (Tag.FLOAT.equals(tag)) {
                return JSON.numberNode(new BigDecimal(value.replace("_", "")));
            }
        } catch (NumberFormatException e) {
            return JSON.textNode(value);
        }
        if (Tag.BOOL.equals(tag)) {
            return JSON.booleanNode(isTrue(value));
        }
        if (Tag.NULL.equals(tag)) {
            return JSON.nullNode();
        }
        return JSON.textNode(value);
    }

    private static boolean isTrue(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("true") || lower.equals("yes") || lower.equals("on");
    }
}
