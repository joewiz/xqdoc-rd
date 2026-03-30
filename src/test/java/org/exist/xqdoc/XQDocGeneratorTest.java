/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 */
package org.exist.xqdoc;

import org.exist.xqdoc.XQDocGenerator.*;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Tests for XQDocGenerator covering module parsing, xqdoc comments,
 * annotations, parameters, types, and edge cases.
 */
public class XQDocGeneratorTest {

    private final XQDocGenerator generator = new XQDocGenerator();

    // ---- Module declaration ----

    @Test
    public void parseLibraryModule() {
        final XQDocModule module = generator.parse(
                "xquery version '3.1';\n" +
                "module namespace foo = 'http://example.com/foo';\n");
        assertEquals("3.1", module.version);
        assertEquals("foo", module.namespacePrefix);
        assertEquals("http://example.com/foo", module.namespaceUri);
    }

    @Test
    public void parseMainModule() {
        final XQDocModule module = generator.parse(
                "xquery version '4.0';\n" +
                "1 + 1\n");
        assertEquals("4.0", module.version);
        assertNull(module.namespaceUri);
    }

    @Test
    public void parseVersionWithEncoding() {
        final XQDocModule module = generator.parse(
                "xquery version '3.1' encoding 'UTF-8';\n" +
                "module namespace m = 'urn:test';\n");
        assertEquals("3.1", module.version);
        assertEquals("UTF-8", module.encoding);
    }

    // ---- XQDoc comments ----

    @Test
    public void moduleXQDocComment() {
        final XQDocModule module = generator.parse(
                "xquery version '3.1';\n" +
                "(:~ Module description.\n" +
                " :  @author Test Author\n" +
                " :)\n" +
                "module namespace m = 'urn:test';\n");
        assertNotNull(module.xqdocComment);
        assertTrue(module.xqdocComment.contains("Module description"));
        assertTrue(module.xqdocComment.contains("@author Test Author"));
    }

    @Test
    public void functionXQDocComment() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "(:~ Greet a user.\n" +
                " :  @param $name The user name\n" +
                " :  @return A greeting\n" +
                " :)\n" +
                "declare function m:greet($name as xs:string) as xs:string {\n" +
                "    'Hello, ' || $name\n" +
                "};\n");
        assertEquals(1, module.functions.size());
        final Function func = module.functions.get(0);
        assertNotNull(func.xqdocComment);
        assertTrue(func.xqdocComment.contains("Greet a user"));
        assertTrue(func.xqdocComment.contains("@param $name"));
        assertTrue(func.xqdocComment.contains("@return A greeting"));
    }

    @Test
    public void variableXQDocComment() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "(:~ The default value. :)\n" +
                "declare variable $m:default := 42;\n");
        assertEquals(1, module.variables.size());
        assertNotNull(module.variables.get(0).xqdocComment);
        assertTrue(module.variables.get(0).xqdocComment.contains("default value"));
    }

    @Test
    public void regularCommentNotCaptured() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "(: This is NOT an xqdoc comment :)\n" +
                "declare function m:foo() { () };\n");
        assertEquals(1, module.functions.size());
        assertNull(module.functions.get(0).xqdocComment);
    }

    // ---- Namespace declarations ----

    @Test
    public void parseNamespaceDeclarations() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare namespace map = 'http://www.w3.org/2005/xpath-functions/map';\n" +
                "declare namespace array = 'http://www.w3.org/2005/xpath-functions/array';\n");
        assertEquals(2, module.namespaces.size());
        assertEquals("map", module.namespaces.get(0).prefix);
        assertEquals("http://www.w3.org/2005/xpath-functions/map", module.namespaces.get(0).uri);
        assertEquals("array", module.namespaces.get(1).prefix);
    }

    // ---- Imports ----

    @Test
    public void parseModuleImport() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "import module namespace other = 'urn:other' at 'other.xqm';\n");
        assertEquals(1, module.imports.size());
        assertEquals("other", module.imports.get(0).prefix);
        assertEquals("urn:other", module.imports.get(0).uri);
        assertEquals("other.xqm", module.imports.get(0).at);
    }

    // ---- Variable declarations ----

    @Test
    public void parseVariableWithType() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare variable $m:count as xs:integer := 0;\n");
        assertEquals(1, module.variables.size());
        assertEquals("$m:count", module.variables.get(0).name);
        assertEquals("xs:integer", module.variables.get(0).type);
        assertFalse(module.variables.get(0).isExternal);
    }

    @Test
    public void parseExternalVariable() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare variable $m:config as xs:string external;\n");
        assertEquals(1, module.variables.size());
        assertTrue(module.variables.get(0).isExternal);
    }

    // ---- Function declarations ----

    @Test
    public void parseFunctionWithParams() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:add($a as xs:integer, $b as xs:integer) as xs:integer {\n" +
                "    $a + $b\n" +
                "};\n");
        assertEquals(1, module.functions.size());
        final Function func = module.functions.get(0);
        assertEquals("m:add", func.name);
        assertEquals(2, func.params.size());
        assertEquals("$a", func.params.get(0).name);
        assertEquals("xs:integer", func.params.get(0).type);
        assertEquals("$b", func.params.get(1).name);
        assertEquals("xs:integer", func.returnType);
    }

    @Test
    public void parseFunctionNoParams() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:now() as xs:dateTime { current-dateTime() };\n");
        assertEquals(1, module.functions.size());
        assertEquals("m:now", module.functions.get(0).name);
        assertEquals(0, module.functions.get(0).params.size());
        assertEquals("xs:dateTime", module.functions.get(0).returnType);
    }

    @Test
    public void parseFunctionNoReturnType() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:side-effect() { () };\n");
        assertEquals(1, module.functions.size());
        assertNull(module.functions.get(0).returnType);
    }

    @Test
    public void parseMultipleFunctions() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:a() { 1 };\n" +
                "declare function m:b($x as xs:string) as xs:string { $x };\n" +
                "declare function m:c($x as xs:integer, $y as xs:integer) as xs:integer { $x + $y };\n");
        assertEquals(3, module.functions.size());
        assertEquals("m:a", module.functions.get(0).name);
        assertEquals(0, module.functions.get(0).params.size());
        assertEquals("m:b", module.functions.get(1).name);
        assertEquals(1, module.functions.get(1).params.size());
        assertEquals("m:c", module.functions.get(2).name);
        assertEquals(2, module.functions.get(2).params.size());
    }

    // ---- Annotations ----

    @Test
    public void parseFunctionAnnotations() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare\n" +
                "    %public\n" +
                "    %rest:GET\n" +
                "    %rest:path('/api/items')\n" +
                "function m:get-items() { () };\n");
        assertEquals(1, module.functions.size());
        final Function func = module.functions.get(0);
        assertEquals(3, func.annotations.size());
        assertEquals("public", func.annotations.get(0).name);
        assertEquals("rest:GET", func.annotations.get(1).name);
        assertEquals("rest:path", func.annotations.get(2).name);
        assertEquals(1, func.annotations.get(2).values.size());
        assertEquals("/api/items", func.annotations.get(2).values.get(0));
    }

    @Test
    public void parsePrivateAnnotation() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare %private function m:helper() { () };\n");
        assertEquals(1, module.functions.size());
        assertEquals(1, module.functions.get(0).annotations.size());
        assertEquals("private", module.functions.get(0).annotations.get(0).name);
    }

    // ---- Complex types ----

    @Test
    public void parseComplexParamTypes() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:transform(\n" +
                "    $items as item()*,\n" +
                "    $fn as function(item()) as item()*\n" +
                ") as item()* { $items ! $fn(.) };\n");
        assertEquals(1, module.functions.size());
        final Function func = module.functions.get(0);
        assertEquals(2, func.params.size());
        assertEquals("item()*", func.params.get(0).type);
        assertTrue(func.params.get(1).type.contains("function"));
        assertEquals("item()*", func.returnType);
    }

    @Test
    public void parseMapType() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:parse($s as xs:string) as map(*) { map {} };\n");
        assertEquals(1, module.functions.size());
        assertTrue(module.functions.get(0).returnType.contains("map"));
    }

    // ---- Full test module from resources ----

    @Test
    public void parseTestModule() throws IOException {
        final String source = loadResource("test-module.xqm");
        final XQDocModule module = generator.parse(source);

        // Module metadata
        assertEquals("3.1", module.version);
        assertEquals("test", module.namespacePrefix);
        assertEquals("http://exist-db.org/xquery/test/xqdoc", module.namespaceUri);
        assertNotNull(module.xqdocComment);
        assertTrue(module.xqdocComment.contains("@author Joe Wicentowski"));
        assertTrue(module.xqdocComment.contains("@version 1.0.0"));
        assertTrue(module.xqdocComment.contains("@since eXist-db 7.0"));

        // Namespaces
        assertEquals(2, module.namespaces.size());

        // Imports
        assertEquals(1, module.imports.size());
        assertEquals("semver", module.imports.get(0).prefix);

        // Variables
        assertEquals(2, module.variables.size());
        assertEquals("$test:default-greeting", module.variables.get(0).name);
        assertEquals("xs:string", module.variables.get(0).type);
        assertNotNull(module.variables.get(0).xqdocComment);
        assertTrue(module.variables.get(1).isExternal);

        // Functions
        assertEquals(5, module.functions.size());

        // test:greet
        final Function greet = module.functions.get(0);
        assertEquals("test:greet", greet.name);
        assertEquals(1, greet.params.size());
        assertEquals("xs:string", greet.returnType);
        assertNotNull(greet.xqdocComment);
        assertTrue(greet.xqdocComment.contains("@param $name"));
        assertTrue(greet.xqdocComment.contains("@return A greeting string"));

        // test:max-version (deprecated)
        final Function maxVersion = module.functions.get(1);
        assertEquals("test:max-version", maxVersion.name);
        assertEquals(2, maxVersion.params.size());
        assertTrue(maxVersion.xqdocComment.contains("@deprecated"));

        // test:process (multiple annotations)
        final Function process = module.functions.get(2);
        assertEquals("test:process", process.name);
        assertTrue(process.annotations.size() >= 3);

        // test:internal-helper (private)
        final Function helper = module.functions.get(3);
        assertEquals("test:internal-helper", helper.name);
        assertTrue(helper.annotations.stream().anyMatch(a -> a.name.equals("private")));

        // test:no-params
        final Function noParams = module.functions.get(4);
        assertEquals("test:no-params", noParams.name);
        assertEquals(0, noParams.params.size());
    }

    // ---- XML output ----

    @Test
    public void xmlOutputWellFormed() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "(:~ A <special> & \"tricky\" module. :)\n" +
                "declare function m:foo($x as xs:string) as xs:string { $x };\n");
        final String xml = new XQDocXmlEmitter().emit(module);
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<xqdoc xmlns=\"http://www.xqdoc.org/1.0\">"));
        assertTrue(xml.contains("&lt;special&gt;"));
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&quot;tricky&quot;"));
        assertTrue(xml.contains("</xqdoc>"));
    }

    @Test
    public void xmlOutputContainsFunctionSignature() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:add($a as xs:integer, $b as xs:integer) as xs:integer { $a + $b };\n");
        final String xml = new XQDocXmlEmitter().emit(module);
        assertTrue(xml.contains("<signature>m:add($a as xs:integer, $b as xs:integer) as xs:integer</signature>"));
        assertTrue(xml.contains("<name>$a</name>"));
        assertTrue(xml.contains("<name>$b</name>"));
        assertTrue(xml.contains("arity=\"2\""));
    }

    // ---- Markdown output ----

    @Test
    public void markdownOutputContainsHeaders() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:hello() as xs:string { 'hi' };\n");
        final String md = new XQDocMarkdownEmitter().emit(module);
        assertTrue(md.contains("# Module: `m`"));
        assertTrue(md.contains("**Namespace:** `urn:test`"));
        assertTrue(md.contains("### `m:hello#0`"));
        assertTrue(md.contains("```xquery"));
    }

    @Test
    public void markdownOutputFormatsXQDocTags() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "(:~ Do something.\n" +
                " :  @param $x The input\n" +
                " :  @return The result\n" +
                " :  @author Test\n" +
                " :  @deprecated Use other()\n" +
                " :)\n" +
                "declare function m:something($x as xs:string) as xs:string { $x };\n");
        final String md = new XQDocMarkdownEmitter().emit(module);
        assertTrue(md.contains("**Parameters:**"));
        assertTrue(md.contains("- `$x` \u2014 The input"));
        assertTrue(md.contains("**Returns:** The result"));
        assertTrue(md.contains("**Author:** Test"));
        assertTrue(md.contains("> **Deprecated:** Use other()"));
    }

    // ---- Edge cases ----

    @Test
    public void emptyModule() {
        final XQDocModule module = generator.parse("");
        assertNotNull(module);
        assertTrue(module.functions.isEmpty());
        assertTrue(module.variables.isEmpty());
    }

    @Test
    public void moduleWithOnlyProlog() {
        final XQDocModule module = generator.parse(
                "xquery version '3.1';\n" +
                "module namespace m = 'urn:test';\n" +
                "declare namespace ns = 'urn:ns';\n");
        assertEquals("3.1", module.version);
        assertEquals(1, module.namespaces.size());
        assertTrue(module.functions.isEmpty());
    }

    @Test
    public void nestedBracesInFunctionBody() {
        final XQDocModule module = generator.parse(
                "module namespace m = 'urn:test';\n" +
                "declare function m:complex() {\n" +
                "    let $map := map { 'a': map { 'b': 1 } }\n" +
                "    return if ($map?a?b = 1) then { 'yes' } else { 'no' }\n" +
                "};\n" +
                "declare function m:next() { 2 };\n");
        assertEquals(2, module.functions.size());
        assertEquals("m:complex", module.functions.get(0).name);
        assertEquals("m:next", module.functions.get(1).name);
    }

    // ---- Real-world module: xqdoc-display.xqy (XQuery 1.0) ----

    @Test
    public void xqdocDisplayModule() throws IOException {
        final XQDocModule module = generator.parse(loadResource("xqdoc-display.xqy"));
        assertEquals("library", module.namespaceUri != null ? "library" : "main");
        assertEquals("display", module.namespacePrefix);
        assertEquals("xqdoc/xqdoc-display", module.namespaceUri);
        assertEquals(33, module.functions.size());
        // Module-level xqdoc comment
        assertNotNull(module.xqdocComment);
        assertTrue(module.xqdocComment.contains("xqDoc"));
        // Structured XML output
        final String xml = new XQDocXmlEmitter().emit(module);
        // All functions have descriptions
        assertTrue("Should have 33+ descriptions",
                xml.split("<description>").length - 1 >= 33);
        // Has @param and @return tags as separate elements
        assertTrue("Should have <param> elements", xml.contains("<param>"));
        assertTrue("Should have <return> elements", xml.contains("<return>"));
        assertTrue("Should have <author> element", xml.contains("<author>"));
    }

    // ---- XQuery 3.1 features ----

    @Test
    public void xq31Module() throws IOException {
        final XQDocModule module = generator.parse(loadResource("xq31-test.xqm"));
        assertEquals("xq31", module.namespacePrefix);
        assertEquals(8, module.functions.size());
        assertEquals(2, module.variables.size());

        // Map type parameters
        final Function mergeConfig = module.functions.stream()
                .filter(f -> f.name.equals("xq31:merge-config")).findFirst().orElse(null);
        assertNotNull("merge-config function", mergeConfig);
        assertEquals(2, mergeConfig.params.size());
        assertTrue(mergeConfig.params.get(0).type.contains("map"));

        // HOF parameter (function type)
        final Function mapTransform = module.functions.stream()
                .filter(f -> f.name.equals("xq31:map-transform")).findFirst().orElse(null);
        assertNotNull("map-transform function", mapTransform);
        assertTrue(mapTransform.params.get(1).type.contains("function"));

        // Array type
        final Function flatten = module.functions.stream()
                .filter(f -> f.name.equals("xq31:flatten")).findFirst().orElse(null);
        assertNotNull("flatten function", flatten);
        assertTrue(flatten.params.get(0).type.contains("array"));

        // Structured tags
        final ParsedComment mergeComment = XQDocGenerator.parseComment(mergeConfig.xqdocComment);
        assertNotNull(mergeComment);
        assertEquals(2, mergeComment.params.size());
        assertEquals("$base", mergeComment.params.get(0).name);
        assertNotNull(mergeComment.returnDesc);

        // @error tag
        final Function safeParse = module.functions.stream()
                .filter(f -> f.name.equals("xq31:safe-parse-int")).findFirst().orElse(null);
        assertNotNull(safeParse);
        final ParsedComment safeParseComment = XQDocGenerator.parseComment(safeParse.xqdocComment);
        assertFalse("Should have @error", safeParseComment.errors.isEmpty());
    }

    // ---- XQuery 4.0 features ----

    @Test
    public void xq40Module() throws IOException {
        final XQDocModule module = generator.parse(loadResource("xq40-test.xqm"));
        assertEquals("4.0", module.version);
        assertEquals("xq4", module.namespacePrefix);
        assertEquals(9, module.functions.size());
        assertEquals(1, module.variables.size());

        // Default parameter values
        final Function greet = module.functions.stream()
                .filter(f -> f.name.equals("xq4:greet")).findFirst().orElse(null);
        assertNotNull("greet function", greet);
        assertEquals(3, greet.params.size());

        // Default parameter values
        final Function paginate = module.functions.stream()
                .filter(f -> f.name.equals("xq4:paginate")).findFirst().orElse(null);
        assertNotNull("paginate function", paginate);
        assertEquals(3, paginate.params.size());

        // @deprecated tag
        final Function doubleFunc = module.functions.stream()
                .filter(f -> f.name.equals("xq4:double")).findFirst().orElse(null);
        assertNotNull(doubleFunc);
        final ParsedComment doubleComment = XQDocGenerator.parseComment(doubleFunc.xqdocComment);
        assertNotNull("Should have @deprecated", doubleComment.deprecated);
        assertTrue(doubleComment.deprecated.contains("multiply"));

        // Markdown output
        final String md = new XQDocMarkdownEmitter().emit(module);
        assertTrue(md.contains("xq4:pipeline-transform"));
        assertTrue(md.contains("xq4:greet"));
        assertTrue(md.contains("> **Deprecated:**"));
    }

    // ---- XQUF features ----

    @Test
    public void xqufModule() throws IOException {
        final XQDocModule module = generator.parse(loadResource("xquf-test.xqm"));
        assertEquals("xquf", module.namespacePrefix);
        assertEquals(6, module.functions.size());
        assertEquals(1, module.variables.size());

        // Updating function
        final Function addItem = module.functions.stream()
                .filter(f -> f.name.equals("xquf:add-item")).findFirst().orElse(null);
        assertNotNull("add-item updating function", addItem);
        assertTrue("Should be updating", addItem.isUpdating);
        assertEquals(4, addItem.params.size());

        // Non-updating function with copy-modify-return
        final Function removeItem = module.functions.stream()
                .filter(f -> f.name.equals("xquf:remove-item")).findFirst().orElse(null);
        assertNotNull(removeItem);
        assertFalse("Should not be updating", removeItem.isUpdating);
        assertEquals("document-node()", removeItem.returnType);

        // HOF parameter in updating function
        final Function deactivate = module.functions.stream()
                .filter(f -> f.name.equals("xquf:deactivate-matching")).findFirst().orElse(null);
        assertNotNull(deactivate);
        assertTrue(deactivate.isUpdating);
        assertTrue(deactivate.params.get(1).type.contains("function"));

        // @deprecated tag
        final Function applyUpdates = module.functions.stream()
                .filter(f -> f.name.equals("xquf:apply-updates")).findFirst().orElse(null);
        assertNotNull(applyUpdates);
        final ParsedComment applyComment = XQDocGenerator.parseComment(applyUpdates.xqdocComment);
        assertNotNull("Should have @deprecated", applyComment.deprecated);

        // @see tag
        final ParsedComment deactivateComment = XQDocGenerator.parseComment(deactivate.xqdocComment);
        assertFalse("Should have @see", deactivateComment.see.isEmpty());
        assertTrue(deactivateComment.see.get(0).contains("xquf:add-item"));

        // XML output structured tags
        final String xml = new XQDocXmlEmitter().emit(module);
        assertTrue(xml.contains("<deprecated>"));
        assertTrue(xml.contains("<see>"));
        assertTrue(xml.contains("<error>"));
    }

    // ---- ParsedComment unit tests ----

    @Test
    public void parseCommentAllTags() {
        final ParsedComment parsed = XQDocGenerator.parseComment(
                "A function description.\n" +
                " : @param $x The x value\n" +
                " : @param $y The y value\n" +
                " : @return The computed result\n" +
                " : @author Alice\n" +
                " : @version 2.0\n" +
                " : @since 1.5\n" +
                " : @see https://example.com\n" +
                " : @see other-function#2\n" +
                " : @deprecated Use new-function instead\n" +
                " : @error err:CUSTOM If validation fails\n");
        assertEquals("A function description.", parsed.description);
        assertEquals(2, parsed.params.size());
        assertEquals("$x", parsed.params.get(0).name);
        assertEquals("The x value", parsed.params.get(0).description);
        assertEquals("$y", parsed.params.get(1).name);
        assertEquals("The computed result", parsed.returnDesc);
        assertEquals("Alice", parsed.author);
        assertEquals("2.0", parsed.version);
        assertEquals("1.5", parsed.since);
        assertEquals(2, parsed.see.size());
        assertEquals("https://example.com", parsed.see.get(0));
        assertEquals("other-function#2", parsed.see.get(1));
        assertEquals("Use new-function instead", parsed.deprecated);
        assertEquals(1, parsed.errors.size());
        assertEquals("err:CUSTOM If validation fails", parsed.errors.get(0));
    }

    @Test
    public void parseCommentNullReturnsNull() {
        assertNull(XQDocGenerator.parseComment(null));
    }

    @Test
    public void parseCommentDescriptionOnly() {
        final ParsedComment parsed = XQDocGenerator.parseComment("Just a description.");
        assertEquals("Just a description.", parsed.description);
        assertTrue(parsed.params.isEmpty());
        assertNull(parsed.returnDesc);
        assertNull(parsed.author);
    }

    // ---- Helpers ----

    private String loadResource(final String name) throws IOException {
        try (final InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull("Test resource not found: " + name, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
