/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 */
package org.exist.xqdoc;

import org.exist.xqdoc.XQDocGenerator.*;

/**
 * Emits xqdoc XML from a parsed Module.
 */
public class XQDocXmlEmitter {

    public static final String XQDOC_NS = "http://www.xqdoc.org/1.0";

    public String emit(final XQDocModule module) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<xqdoc xmlns=\"").append(XQDOC_NS).append("\">\n");

        // Module section
        sb.append("  <module type=\"").append(module.namespaceUri != null ? "library" : "main").append("\">\n");
        if (module.namespaceUri != null) {
            sb.append("    <uri>").append(esc(module.namespaceUri)).append("</uri>\n");
            if (module.namespacePrefix != null) {
                sb.append("    <name>").append(esc(module.namespacePrefix)).append("</name>\n");
            }
        }
        if (module.xqdocComment != null) {
            emitComment(sb, module.xqdocComment, "    ");
        }
        sb.append("  </module>\n");

        // Imports
        if (!module.imports.isEmpty()) {
            sb.append("  <imports>\n");
            for (final Import imp : module.imports) {
                sb.append("    <import type=\"library\">\n");
                if (imp.uri != null) sb.append("      <uri>").append(esc(imp.uri)).append("</uri>\n");
                sb.append("    </import>\n");
            }
            sb.append("  </imports>\n");
        }

        // Namespaces
        if (!module.namespaces.isEmpty()) {
            sb.append("  <namespaces>\n");
            for (final Namespace ns : module.namespaces) {
                sb.append("    <namespace prefix=\"").append(esc(ns.prefix))
                        .append("\" uri=\"").append(esc(ns.uri)).append("\"/>\n");
            }
            sb.append("  </namespaces>\n");
        }

        // Variables
        if (!module.variables.isEmpty()) {
            sb.append("  <variables>\n");
            for (final Variable var : module.variables) {
                sb.append("    <variable>\n");
                if (var.xqdocComment != null) {
                    emitComment(sb, var.xqdocComment, "      ");
                }
                sb.append("      <name>").append(esc(var.name)).append("</name>\n");
                if (var.type != null) {
                    sb.append("      <type>").append(esc(var.type)).append("</type>\n");
                }
                emitAnnotations(sb, var.annotations, "      ");
                sb.append("    </variable>\n");
            }
            sb.append("  </variables>\n");
        }

        // Functions
        if (!module.functions.isEmpty()) {
            sb.append("  <functions>\n");
            for (final Function func : module.functions) {
                sb.append("    <function arity=\"").append(func.params.size()).append("\">\n");
                if (func.xqdocComment != null) {
                    emitComment(sb, func.xqdocComment, "      ");
                }
                sb.append("      <name>").append(esc(func.name)).append("</name>\n");
                emitAnnotations(sb, func.annotations, "      ");

                // Signature
                final StringBuilder sig = new StringBuilder();
                sig.append(func.name).append("(");
                for (int i = 0; i < func.params.size(); i++) {
                    if (i > 0) sig.append(", ");
                    final Parameter p = func.params.get(i);
                    sig.append(p.name);
                    if (p.type != null) sig.append(" as ").append(p.type);
                }
                sig.append(")");
                if (func.returnType != null) sig.append(" as ").append(func.returnType);
                sb.append("      <signature>").append(esc(sig.toString())).append("</signature>\n");

                // Parameters
                if (!func.params.isEmpty()) {
                    sb.append("      <parameters>\n");
                    for (final Parameter p : func.params) {
                        sb.append("        <parameter>\n");
                        sb.append("          <name>").append(esc(p.name)).append("</name>\n");
                        if (p.type != null) {
                            sb.append("          <type>").append(esc(p.type)).append("</type>\n");
                        }
                        sb.append("        </parameter>\n");
                    }
                    sb.append("      </parameters>\n");
                }

                // Return type
                if (func.returnType != null) {
                    sb.append("      <return>\n");
                    sb.append("        <type>").append(esc(func.returnType)).append("</type>\n");
                    sb.append("      </return>\n");
                }

                sb.append("    </function>\n");
            }
            sb.append("  </functions>\n");
        }

        sb.append("</xqdoc>\n");
        return sb.toString();
    }

    private void emitComment(final StringBuilder sb, final String comment, final String indent) {
        sb.append(indent).append("<comment>\n");
        sb.append(indent).append("  <description>").append(esc(comment.trim())).append("</description>\n");
        sb.append(indent).append("</comment>\n");
    }

    private void emitAnnotations(final StringBuilder sb, final java.util.List<Annotation> annotations, final String indent) {
        if (annotations.isEmpty()) return;
        sb.append(indent).append("<annotations>\n");
        for (final Annotation ann : annotations) {
            sb.append(indent).append("  <annotation name=\"").append(esc(ann.name)).append("\"");
            if (ann.values.isEmpty()) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                for (final String val : ann.values) {
                    sb.append(indent).append("    <literal>").append(esc(val)).append("</literal>\n");
                }
                sb.append(indent).append("  </annotation>\n");
            }
        }
        sb.append(indent).append("</annotations>\n");
    }

    private static String esc(final String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
