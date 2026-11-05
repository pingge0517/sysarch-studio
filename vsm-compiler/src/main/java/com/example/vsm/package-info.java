/**
 * VSM compiler: {@code views/*.vsm.yaml} to the backend's generated {@code DiagramDescription} providers.
 *
 * <p>Pipeline ({@link com.example.vsm.VsmCompiler}): parse YAML with line numbers, check it against
 * {@code views/schema/vsm.schema.json}, cross-check it against {@code metamodel/sysarch.ecore}, render the
 * FreeMarker templates, write the registry. Output is byte-for-byte deterministic. Any error prints
 * {@code views/<file>:<line> <message>} and exits with code 1.
 */
package com.example.vsm;
