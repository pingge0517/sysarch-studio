package com.example.vsm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Structural check of a VSM file against {@code views/schema/vsm.schema.json} (closed field set, types, enums,
 * required fields). Each schema error is mapped back to the YAML line it refers to.
 */
final class SchemaValidator {

    private final JsonSchema schema;

    SchemaValidator(Path schemaFile) throws IOException {
        var schemaJson = new ObjectMapper().readTree(schemaFile.toFile());
        // Messages are localized from the JVM default locale unless pinned; output must not depend on the machine.
        var config = SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build();
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaJson, config);
    }

    List<Diagnostic> validate(String file, YamlNode document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ValidationMessage message : this.schema.validate(document.toJson())) {
            YamlNode located = document.at(elements(message.getInstanceLocation()));
            String property = message.getProperty();
            int line = property != null && located.isMapping() ? located.keyLine(property) : located.line();
            diagnostics.add(new Diagnostic(file, line, "schema: " + message.getMessage()));
        }
        return diagnostics;
    }

    private static List<Object> elements(JsonNodePath path) {
        List<Object> elements = new ArrayList<>();
        for (int i = 0; i < path.getNameCount(); i++) {
            elements.add(path.getElement(i));
        }
        return elements;
    }
}
