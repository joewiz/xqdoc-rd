/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 */
package org.exist.xqdoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI tool for generating XQDoc documentation from XQuery source files.
 *
 * <p>Usage: {@code java -jar xqdoc-rd.jar [--format xml|md] <file.xqm>}</p>
 *
 * <p>Uses eXist-db's hand-written XQuery lexer for tokenization. Supports
 * XQuery 3.1, 4.0, XQUF 3.0, and XQFT 3.0 syntax. Zero external
 * dependencies.</p>
 */
public class XQDocCli {

    public static void main(final String[] args) throws IOException {
        String format = "xml";
        String filePath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--format", "-f" -> {
                    if (i + 1 < args.length) format = args[++i];
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> filePath = args[i];
            }
        }

        if (filePath == null) {
            printUsage();
            System.exit(1);
        }

        final Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            System.err.println("File not found: " + filePath);
            System.exit(1);
        }

        final String source = Files.readString(path);
        final XQDocGenerator generator = new XQDocGenerator();
        final XQDocGenerator.XQDocModule module = generator.parse(source);

        final String output = switch (format.toLowerCase()) {
            case "md", "markdown" -> new XQDocMarkdownEmitter().emit(module);
            case "xml" -> new XQDocXmlEmitter().emit(module);
            default -> {
                System.err.println("Unknown format: " + format + " (use xml or md)");
                System.exit(1);
                yield "";
            }
        };

        System.out.print(output);
    }

    private static void printUsage() {
        System.out.println("xqdoc-rd — XQDoc generator using eXist-db's rd parser lexer");
        System.out.println();
        System.out.println("Usage: java -jar xqdoc-rd.jar [--format xml|md] <file.xqm>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --format, -f  Output format: xml (default) or md (Markdown)");
        System.out.println("  --help, -h    Show this help");
        System.out.println();
        System.out.println("Supports: XQuery 3.1, 4.0, XQUF 3.0, XQFT 3.0");
        System.out.println("Dependencies: none (standalone JAR)");
    }
}
