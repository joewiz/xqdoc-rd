/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package org.exist.xqdoc;

import org.exist.xquery.parser.next.Token;
import org.exist.xquery.parser.next.XQueryLexer;
import org.exist.xquery.parser.next.ParseError;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates XQDoc documentation from XQuery source using the rd parser's lexer.
 *
 * <p>Walks the token stream to extract module declarations, namespace declarations,
 * imports, variable declarations, and function declarations with their xqdoc comments,
 * annotations, parameter types, and return types.</p>
 *
 * <p>Supports XQuery 3.1, 4.0, XQUF 3.0, and XQFT 3.0 syntax.</p>
 */
public class XQDocGenerator {

    /** Parsed module metadata. */
    public static class XQDocModule {
        public String version;           // "3.1", "4.0", etc.
        public String encoding;          // "UTF-8", etc.
        public String namespacePrefix;   // module namespace prefix
        public String namespaceUri;      // module namespace URI
        public String xqdocComment;      // module-level xqdoc comment
        public final List<Namespace> namespaces = new ArrayList<>();
        public final List<Import> imports = new ArrayList<>();
        public final List<Variable> variables = new ArrayList<>();
        public final List<Function> functions = new ArrayList<>();
    }

    public static class Namespace {
        public String prefix;
        public String uri;
    }

    public static class Import {
        public String prefix;
        public String uri;
        public String at;  // location hint
    }

    public static class Variable {
        public String name;        // $prefix:local or $local
        public String type;        // sequence type as string
        public String xqdocComment;
        public boolean isExternal;
        public final List<Annotation> annotations = new ArrayList<>();
    }

    public static class Function {
        public String name;        // prefix:local or local
        public String xqdocComment;
        public boolean isUpdating;
        public boolean isExternal;
        public final List<Annotation> annotations = new ArrayList<>();
        public final List<Parameter> params = new ArrayList<>();
        public String returnType;  // "as type" declaration
        public int line;
    }

    public static class Parameter {
        public String name;  // $param
        public String type;  // sequence type
        public String defaultValue; // default value if any
    }

    public static class Annotation {
        public String name;  // %name
        public final List<String> values = new ArrayList<>();
    }

    /**
     * Parsed xqdoc comment with structured tags.
     */
    public static class ParsedComment {
        public String description = "";
        public final List<ParamTag> params = new ArrayList<>();
        public String returnDesc;
        public String author;
        public String version;
        public String since;
        public final List<String> see = new ArrayList<>();
        public String deprecated;
        public final List<String> errors = new ArrayList<>();
    }

    /** A parsed @param tag. */
    public static class ParamTag {
        public String name;
        public String description;

        public ParamTag(final String name, final String description) {
            this.name = name;
            this.description = description;
        }
    }

    /**
     * Parse an xqdoc comment string into structured tags.
     *
     * @param comment the raw xqdoc comment text (without delimiters)
     * @return parsed comment, or null if comment is null
     */
    public static ParsedComment parseComment(final String comment) {
        if (comment == null) return null;

        final ParsedComment result = new ParsedComment();
        final StringBuilder description = new StringBuilder();
        final String[] lines = comment.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("*")) line = line.substring(1).trim();
            if (line.startsWith(":")) line = line.substring(1).trim();

            if (line.startsWith("@param ")) {
                final String rest = line.substring(7).trim();
                final int space = rest.indexOf(' ');
                if (space > 0) {
                    result.params.add(new ParamTag(
                            rest.substring(0, space), rest.substring(space + 1).trim()));
                } else {
                    result.params.add(new ParamTag(rest, ""));
                }
            } else if (line.startsWith("@return ")) {
                result.returnDesc = line.substring(8).trim();
            } else if (line.startsWith("@author ")) {
                result.author = line.substring(8).trim();
            } else if (line.startsWith("@version ")) {
                result.version = line.substring(9).trim();
            } else if (line.startsWith("@since ")) {
                result.since = line.substring(7).trim();
            } else if (line.startsWith("@see ")) {
                result.see.add(line.substring(5).trim());
            } else if (line.startsWith("@deprecated ") || line.equals("@deprecated")) {
                result.deprecated = line.length() > 11 ? line.substring(12).trim() : "";
            } else if (line.startsWith("@error ")) {
                result.errors.add(line.substring(7).trim());
            } else if (!line.isEmpty()) {
                if (!description.isEmpty()) description.append("\n");
                description.append(line);
            }
        }
        result.description = description.toString().trim();
        return result;
    }

    /**
     * Parse XQuery source and extract documentation.
     *
     * @param source the XQuery source code
     * @return the parsed module documentation
     */
    public XQDocModule parse(final String source) {
        final XQDocModule module = new XQDocModule();
        final XQueryLexer lexer = new XQueryLexer(source);

        String pendingXQDoc = null;
        Token tok = lexer.nextToken();

        while (tok.type != Token.EOF) {
            // Capture xqdoc comments
            if (tok.type == Token.XQDOC_COMMENT) {
                pendingXQDoc = tok.value;
                tok = lexer.nextToken();
                continue;
            }

            if (tok.type == Token.NCNAME) {
                switch (tok.value) {
                    case "xquery" -> {
                        tok = parseVersionDecl(lexer, tok, module);
                        continue;
                    }
                    case "module" -> {
                        tok = parseModuleDecl(lexer, tok, module);
                        if (pendingXQDoc != null) {
                            module.xqdocComment = pendingXQDoc;
                            pendingXQDoc = null;
                        }
                        continue;
                    }
                    case "declare" -> {
                        tok = parseDeclaration(lexer, tok, module, pendingXQDoc);
                        pendingXQDoc = null;
                        continue;
                    }
                    case "import" -> {
                        tok = parseImport(lexer, tok, module);
                        pendingXQDoc = null;
                        continue;
                    }
                }
            }

            // If we hit a non-declaration token at the top level, we're past the prolog
            if (tok.type == Token.PERCENT) {
                // Annotation before a declaration — parse annotations then declaration
                final List<Annotation> annotations = new ArrayList<>();
                tok = parseAnnotations(lexer, tok, annotations);
                if (tok.type == Token.NCNAME && tok.value.equals("declare")) {
                    tok = parseDeclaration(lexer, tok, module, pendingXQDoc, annotations);
                    pendingXQDoc = null;
                    continue;
                }
            }

            pendingXQDoc = null;
            tok = lexer.nextToken();
        }

        return module;
    }

    private Token parseVersionDecl(final XQueryLexer lexer, Token tok, final XQDocModule module) {
        tok = lexer.nextToken(); // skip 'xquery'
        if (tok.type == Token.NCNAME && tok.value.equals("version")) {
            tok = lexer.nextToken();
            if (tok.type == Token.STRING_LITERAL) {
                module.version = tok.value;
                tok = lexer.nextToken();
                if (tok.type == Token.NCNAME && tok.value.equals("encoding")) {
                    tok = lexer.nextToken();
                    if (tok.type == Token.STRING_LITERAL) {
                        module.encoding = tok.value;
                        tok = lexer.nextToken();
                    }
                }
            }
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseModuleDecl(final XQueryLexer lexer, Token tok, final XQDocModule module) {
        tok = lexer.nextToken(); // skip 'module'
        if (tok.type == Token.NCNAME && tok.value.equals("namespace")) {
            tok = lexer.nextToken();
            if (tok.type == Token.NCNAME || tok.type == Token.QNAME) {
                module.namespacePrefix = tok.value;
                tok = lexer.nextToken();
                if (tok.type == Token.EQ) {
                    tok = lexer.nextToken();
                    if (tok.type == Token.STRING_LITERAL) {
                        module.namespaceUri = tok.value;
                        tok = lexer.nextToken();
                    }
                }
            }
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseDeclaration(final XQueryLexer lexer, Token tok, final XQDocModule module, final String xqdoc) {
        return parseDeclaration(lexer, tok, module, xqdoc, new ArrayList<>());
    }

    private Token parseDeclaration(final XQueryLexer lexer, Token tok, final XQDocModule module,
                                    final String xqdoc, final List<Annotation> annotations) {
        tok = lexer.nextToken(); // skip 'declare'

        // Check for annotations before the keyword
        while (tok.type == Token.PERCENT) {
            tok = parseAnnotations(lexer, tok, annotations);
        }

        if (tok.type == Token.NCNAME) {
            switch (tok.value) {
                case "namespace" -> {
                    return parseNamespaceDecl(lexer, tok, module);
                }
                case "default" -> {
                    return parseDefaultDecl(lexer, tok, module);
                }
                case "variable" -> {
                    return parseVariableDecl(lexer, tok, module, xqdoc, annotations);
                }
                case "function" -> {
                    return parseFunctionDecl(lexer, tok, module, xqdoc, annotations, false);
                }
                case "updating" -> {
                    tok = lexer.nextToken();
                    if (tok.type == Token.NCNAME && tok.value.equals("function")) {
                        return parseFunctionDecl(lexer, tok, module, xqdoc, annotations, true);
                    }
                }
                case "option" -> {
                    return skipToSemicolon(lexer, tok);
                }
            }
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseNamespaceDecl(final XQueryLexer lexer, Token tok, final XQDocModule module) {
        tok = lexer.nextToken(); // skip 'namespace'
        final Namespace ns = new Namespace();
        if (tok.type == Token.NCNAME) {
            ns.prefix = tok.value;
            tok = lexer.nextToken();
            if (tok.type == Token.EQ) {
                tok = lexer.nextToken();
                if (tok.type == Token.STRING_LITERAL) {
                    ns.uri = tok.value;
                    module.namespaces.add(ns);
                    tok = lexer.nextToken();
                }
            }
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseDefaultDecl(final XQueryLexer lexer, Token tok, final XQDocModule module) {
        // Skip: declare default element/function namespace "uri"
        return skipToSemicolon(lexer, tok);
    }

    private Token parseVariableDecl(final XQueryLexer lexer, Token tok, final XQDocModule module,
                                     final String xqdoc, final List<Annotation> annotations) {
        tok = lexer.nextToken(); // skip 'variable'
        final Variable var = new Variable();
        var.xqdocComment = xqdoc;
        var.annotations.addAll(annotations);

        if (tok.type == Token.DOLLAR) {
            tok = lexer.nextToken();
            if (tok.type == Token.NCNAME || tok.type == Token.QNAME) {
                var.name = "$" + tok.value;
                tok = lexer.nextToken();

                // Optional type declaration
                if (tok.type == Token.NCNAME && tok.value.equals("as")) {
                    final StringBuilder type = new StringBuilder();
                    tok = lexer.nextToken();
                    tok = readSequenceType(lexer, tok, type);
                    var.type = type.toString().trim();
                }

                if (tok.type == Token.NCNAME && tok.value.equals("external")) {
                    var.isExternal = true;
                    tok = lexer.nextToken();
                }

                module.variables.add(var);
            }
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseFunctionDecl(final XQueryLexer lexer, Token tok, final XQDocModule module,
                                     final String xqdoc, final List<Annotation> annotations,
                                     final boolean isUpdating) {
        tok = lexer.nextToken(); // skip 'function'
        final Function func = new Function();
        func.xqdocComment = xqdoc;
        func.annotations.addAll(annotations);
        func.isUpdating = isUpdating;
        func.line = tok.line;

        if (tok.type == Token.NCNAME || tok.type == Token.QNAME) {
            func.name = tok.value;
            tok = lexer.nextToken();

            // Parameter list
            if (tok.type == Token.LPAREN) {
                tok = lexer.nextToken();
                while (tok.type != Token.RPAREN && tok.type != Token.EOF) {
                    if (tok.type == Token.DOLLAR) {
                        final Parameter param = new Parameter();
                        tok = lexer.nextToken();
                        if (tok.type == Token.NCNAME || tok.type == Token.QNAME) {
                            param.name = "$" + tok.value;
                            tok = lexer.nextToken();

                            // Optional type
                            if (tok.type == Token.NCNAME && tok.value.equals("as")) {
                                final StringBuilder type = new StringBuilder();
                                tok = lexer.nextToken();
                                tok = readSequenceType(lexer, tok, type);
                                param.type = type.toString().trim();
                            }

                            // Optional default value (:= expr)
                            if (tok.type == Token.COLON_EQ) {
                                final StringBuilder defaultVal = new StringBuilder();
                                tok = lexer.nextToken();
                                tok = readExpression(lexer, tok, defaultVal);
                                param.defaultValue = defaultVal.toString().trim();
                            }

                            func.params.add(param);
                        }
                    }
                    if (tok.type == Token.COMMA) {
                        tok = lexer.nextToken();
                    } else if (tok.type != Token.RPAREN && tok.type != Token.DOLLAR) {
                        tok = lexer.nextToken();
                    }
                }
                if (tok.type == Token.RPAREN) {
                    tok = lexer.nextToken();
                }
            }

            // Return type
            if (tok.type == Token.NCNAME && tok.value.equals("as")) {
                final StringBuilder type = new StringBuilder();
                tok = lexer.nextToken();
                tok = readSequenceType(lexer, tok, type);
                func.returnType = type.toString().trim();
            }

            if (tok.type == Token.NCNAME && tok.value.equals("external")) {
                func.isExternal = true;
                tok = lexer.nextToken();
            }

            module.functions.add(func);
        }

        // Skip function body (balanced braces)
        if (tok.type == Token.LBRACE) {
            tok = skipBalancedBraces(lexer, tok);
        }

        return skipToSemicolon(lexer, tok);
    }

    private Token parseImport(final XQueryLexer lexer, Token tok, final XQDocModule module) {
        tok = lexer.nextToken(); // skip 'import'
        if (tok.type == Token.NCNAME && tok.value.equals("module")) {
            tok = lexer.nextToken();
            final Import imp = new Import();
            if (tok.type == Token.NCNAME && tok.value.equals("namespace")) {
                tok = lexer.nextToken();
                if (tok.type == Token.NCNAME) {
                    imp.prefix = tok.value;
                    tok = lexer.nextToken();
                    if (tok.type == Token.EQ) {
                        tok = lexer.nextToken();
                        if (tok.type == Token.STRING_LITERAL) {
                            imp.uri = tok.value;
                            tok = lexer.nextToken();
                        }
                    }
                }
            }
            if (tok.type == Token.NCNAME && tok.value.equals("at")) {
                tok = lexer.nextToken();
                if (tok.type == Token.STRING_LITERAL) {
                    imp.at = tok.value;
                    tok = lexer.nextToken();
                }
            }
            module.imports.add(imp);
        }
        return skipToSemicolon(lexer, tok);
    }

    private Token parseAnnotations(final XQueryLexer lexer, Token tok, final List<Annotation> annotations) {
        while (tok.type == Token.PERCENT) {
            tok = lexer.nextToken();
            final Annotation ann = new Annotation();
            if (tok.type == Token.NCNAME || tok.type == Token.QNAME) {
                ann.name = tok.value;
                tok = lexer.nextToken();
                // Optional annotation values
                if (tok.type == Token.LPAREN) {
                    tok = lexer.nextToken();
                    while (tok.type != Token.RPAREN && tok.type != Token.EOF) {
                        if (tok.type == Token.STRING_LITERAL || tok.type == Token.INTEGER_LITERAL
                                || tok.type == Token.DECIMAL_LITERAL || tok.type == Token.DOUBLE_LITERAL) {
                            ann.values.add(tok.value);
                        }
                        tok = lexer.nextToken();
                        if (tok.type == Token.COMMA) {
                            tok = lexer.nextToken();
                        }
                    }
                    if (tok.type == Token.RPAREN) {
                        tok = lexer.nextToken();
                    }
                }
                annotations.add(ann);
            }
        }
        return tok;
    }

    /**
     * Read a sequence type, accumulating tokens into the StringBuilder.
     * Stops at comma, rparen, lbrace, semicolon, colon_eq, or 'external'.
     */
    private Token readSequenceType(final XQueryLexer lexer, Token tok, final StringBuilder sb) {
        int parenDepth = 0;
        while (tok.type != Token.EOF) {
            if (parenDepth == 0 && (tok.type == Token.COMMA || tok.type == Token.RPAREN
                    || tok.type == Token.LBRACE || tok.type == Token.SEMICOLON
                    || tok.type == Token.COLON_EQ
                    || (tok.type == Token.NCNAME && tok.value.equals("external")))) {
                break;
            }
            if (tok.type == Token.LPAREN) parenDepth++;
            if (tok.type == Token.RPAREN) parenDepth--;
            sb.append(tok.value);
            final Token next = lexer.nextToken();
            // Add space after names unless followed by punctuation
            if ((tok.type == Token.NCNAME || tok.type == Token.QNAME)
                    && next.type != Token.LPAREN && next.type != Token.RPAREN
                    && next.type != Token.STAR && next.type != Token.PLUS
                    && next.type != Token.QUESTION && next.type != Token.COMMA
                    && next.type != Token.SEMICOLON && next.type != Token.LBRACE) {
                sb.append(" ");
            }
            tok = next;
        }
        return tok;
    }

    /**
     * Read an expression until comma or rparen at depth 0.
     */
    private Token readExpression(final XQueryLexer lexer, Token tok, final StringBuilder sb) {
        int depth = 0;
        while (tok.type != Token.EOF) {
            if (depth == 0 && (tok.type == Token.COMMA || tok.type == Token.RPAREN)) {
                break;
            }
            if (tok.type == Token.LPAREN) depth++;
            if (tok.type == Token.RPAREN) depth--;
            sb.append(tok.value);
            tok = lexer.nextToken();
        }
        return tok;
    }

    private Token skipBalancedBraces(final XQueryLexer lexer, Token tok) {
        int depth = 1;
        tok = lexer.nextToken(); // skip opening brace
        while (tok.type != Token.EOF && depth > 0) {
            if (tok.type == Token.LBRACE) depth++;
            else if (tok.type == Token.RBRACE) depth--;
            if (depth > 0) tok = lexer.nextToken();
        }
        if (tok.type == Token.RBRACE) {
            tok = lexer.nextToken();
        }
        return tok;
    }

    private Token skipToSemicolon(final XQueryLexer lexer, Token tok) {
        while (tok.type != Token.SEMICOLON && tok.type != Token.EOF) {
            // If we hit a brace, skip the body
            if (tok.type == Token.LBRACE) {
                tok = skipBalancedBraces(lexer, tok);
                continue;
            }
            tok = lexer.nextToken();
        }
        if (tok.type == Token.SEMICOLON) {
            tok = lexer.nextToken();
        }
        return tok;
    }
}
