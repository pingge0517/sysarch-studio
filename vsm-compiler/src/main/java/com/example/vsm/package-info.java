/**
 * VSM compiler: views/*.vsm.yaml to Sirius Web DiagramDescription Java sources.
 *
 * <p>TODO(T3): parse YAML, cross-check against the metamodel, generate from FreeMarker templates,
 * write the registry. Output must be byte-for-byte deterministic; any validation error prints
 * {@code views/<file>:<line> <message>} and exits with code 1.
 */
package com.example.vsm;
