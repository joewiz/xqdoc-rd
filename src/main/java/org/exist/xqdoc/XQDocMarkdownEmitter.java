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
            emitParsedComment(sb, XQDocGenerator.parseComment(module.xqdocComment));
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
                    emitParsedComment(sb, XQDocGenerator.parseComment(var.xqdocComment));
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
                    emitParsedComment(sb, XQDocGenerator.parseComment(func.xqdocComment));
                }

                sb.append("---\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * Emit a parsed xqdoc comment as Markdown.
     */
    private void emitParsedComment(final StringBuilder sb, final XQDocGenerator.ParsedComment parsed) {
        if (parsed == null) return;

        if (!parsed.description.isEmpty()) {
            sb.append(parsed.description).append("\n\n");
        }

        if (parsed.author != null) {
            sb.append("**Author:** ").append(parsed.author).append("\n\n");
        }
        if (parsed.version != null) {
            sb.append("**Version:** ").append(parsed.version).append("\n\n");
        }
        if (parsed.since != null) {
            sb.append("**Since:** ").append(parsed.since).append("\n\n");
        }
        for (final String see : parsed.see) {
            sb.append("**See:** ").append(see).append("\n\n");
        }
        if (parsed.deprecated != null) {
            sb.append("> **Deprecated:** ").append(parsed.deprecated).append("\n\n");
        }

        if (!parsed.params.isEmpty()) {
            sb.append("**Parameters:**\n\n");
            for (final XQDocGenerator.ParamTag param : parsed.params) {
                sb.append("- `").append(param.name).append("`");
                if (param.description != null && !param.description.isEmpty()) {
                    sb.append(" — ").append(param.description);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (parsed.returnDesc != null && !parsed.returnDesc.isEmpty()) {
            sb.append("**Returns:** ").append(parsed.returnDesc).append("\n\n");
        }

        for (final String error : parsed.errors) {
            sb.append("**Error:** ").append(error).append("\n\n");
        }
    }
}
