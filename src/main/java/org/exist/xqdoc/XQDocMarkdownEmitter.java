/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 */
package org.exist.xqdoc;

import org.exist.xqdoc.XQDocGenerator.*;

/**
 * Emits Markdown documentation from a parsed Module.
 */
public class XQDocMarkdownEmitter {

    public String emit(final XQDocModule module) {
        final StringBuilder sb = new StringBuilder();

        // Module header
        if (module.namespaceUri != null) {
            sb.append("# Module: `").append(module.namespacePrefix).append("`\n\n");
            sb.append("**Namespace:** `").append(module.namespaceUri).append("`\n\n");
        } else {
            sb.append("# Main Module\n\n");
        }
        if (module.version != null) {
            sb.append("**XQuery Version:** ").append(module.version).append("\n\n");
        }
        if (module.xqdocComment != null) {
            sb.append(formatXQDocComment(module.xqdocComment)).append("\n\n");
        }

        // Imports
        if (!module.imports.isEmpty()) {
            sb.append("## Imports\n\n");
            for (final Import imp : module.imports) {
                sb.append("- `").append(imp.prefix != null ? imp.prefix : "").append("` = `")
                        .append(imp.uri).append("`");
                if (imp.at != null) sb.append(" at `").append(imp.at).append("`");
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Variables
        if (!module.variables.isEmpty()) {
            sb.append("## Variables\n\n");
            for (final Variable var : module.variables) {
                sb.append("### `").append(var.name).append("`");
                if (var.type != null) sb.append(" as `").append(var.type).append("`");
                if (var.isExternal) sb.append(" (external)");
                sb.append("\n\n");
                if (var.xqdocComment != null) {
                    sb.append(formatXQDocComment(var.xqdocComment)).append("\n\n");
                }
            }
        }

        // Functions
        if (!module.functions.isEmpty()) {
            sb.append("## Functions\n\n");
            for (final Function func : module.functions) {
                // Function header
                sb.append("### `").append(func.name).append("#").append(func.params.size()).append("`\n\n");

                // Signature
                sb.append("```xquery\n");
                if (func.isUpdating) sb.append("updating ");
                sb.append("declare function ").append(func.name).append("(\n");
                for (int i = 0; i < func.params.size(); i++) {
                    final Parameter p = func.params.get(i);
                    sb.append("    ").append(p.name);
                    if (p.type != null) sb.append(" as ").append(p.type);
                    if (p.defaultValue != null) sb.append(" := ").append(p.defaultValue);
                    if (i < func.params.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append(")");
                if (func.returnType != null) sb.append(" as ").append(func.returnType);
                sb.append("\n```\n\n");

                // Annotations
                if (!func.annotations.isEmpty()) {
                    sb.append("**Annotations:** ");
                    for (int i = 0; i < func.annotations.size(); i++) {
                        if (i > 0) sb.append(", ");
                        final Annotation ann = func.annotations.get(i);
                        sb.append("`%").append(ann.name);
                        if (!ann.values.isEmpty()) {
                            sb.append("(").append(String.join(", ", ann.values)).append(")");
                        }
                        sb.append("`");
                    }
                    sb.append("\n\n");
                }

                // XQDoc comment
                if (func.xqdocComment != null) {
                    sb.append(formatXQDocComment(func.xqdocComment)).append("\n\n");
                }

                sb.append("---\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Format an xqdoc comment, handling @param, @return, @author, etc.
     */
    private String formatXQDocComment(final String comment) {
        final StringBuilder sb = new StringBuilder();
        final String[] lines = comment.split("\n");
        boolean inParams = false;

        for (String line : lines) {
            line = line.trim();
            // Strip leading * or : from xqdoc comment lines
            if (line.startsWith("*")) line = line.substring(1).trim();
            if (line.startsWith(":")) line = line.substring(1).trim();

            if (line.startsWith("@param")) {
                if (!inParams) {
                    sb.append("\n**Parameters:**\n\n");
                    inParams = true;
                }
                final String rest = line.substring(6).trim();
                final int space = rest.indexOf(' ');
                if (space > 0) {
                    sb.append("- `").append(rest.substring(0, space)).append("` — ")
                            .append(rest.substring(space + 1).trim()).append("\n");
                } else {
                    sb.append("- `").append(rest).append("`\n");
                }
            } else if (line.startsWith("@return")) {
                inParams = false;
                sb.append("\n**Returns:** ").append(line.substring(7).trim()).append("\n");
            } else if (line.startsWith("@author")) {
                inParams = false;
                sb.append("\n**Author:** ").append(line.substring(7).trim()).append("\n");
            } else if (line.startsWith("@version")) {
                inParams = false;
                sb.append("\n**Version:** ").append(line.substring(8).trim()).append("\n");
            } else if (line.startsWith("@since")) {
                inParams = false;
                sb.append("\n**Since:** ").append(line.substring(6).trim()).append("\n");
            } else if (line.startsWith("@see")) {
                inParams = false;
                sb.append("\n**See:** ").append(line.substring(4).trim()).append("\n");
            } else if (line.startsWith("@deprecated")) {
                inParams = false;
                sb.append("\n> **Deprecated:** ").append(line.substring(11).trim()).append("\n");
            } else if (!line.isEmpty()) {
                inParams = false;
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
