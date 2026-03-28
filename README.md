# xqdoc-rd

A standalone XQDoc generator using eXist-db's hand-written XQuery lexer. Parses XQuery source files and emits documentation in XML ([xqdoc 1.0](http://www.xqdoc.org/)) or Markdown format.

## Features

- **Zero dependencies** -- standalone 39 KB JAR, no Saxon, no ANTLR, no eXist runtime
- **Fast** -- 76ms per module (including JVM startup) on a 37-function library
- **XQuery 3.1 + 4.0** -- pipeline operator, focus functions, keyword arguments, string templates, default parameters
- **XQUF 3.0** -- updating function declarations, copy/modify/return
- **XQFT 3.0** -- contains text, score clauses
- **Full xqdoc tag support** -- `@param`, `@return`, `@author`, `@version`, `@since`, `@see`, `@deprecated`, `@error`

## Quick Start

```bash
# Build
mvn package

# Generate Markdown documentation
java -jar target/xqdoc-rd-0.1.0-SNAPSHOT.jar --format md path/to/module.xqm

# Generate xqdoc XML
java -jar target/xqdoc-rd-0.1.0-SNAPSHOT.jar --format xml path/to/module.xqm
```

## Usage

```
xqdoc-rd -- XQDoc generator using eXist-db's rd parser lexer

Usage: java -jar xqdoc-rd.jar [--format xml|md] <file.xqm>

Options:
  --format, -f  Output format: xml (default) or md (Markdown)
  --help, -h    Show help
```

## Example

Given a module like `semver.xqm`:

```xquery
xquery version "3.1";

(:~ Validate, compare, sort, parse, and serialize Semantic Versioning
 :  (SemVer) 2.0.0 version strings, using XQuery.
 :
 :  @author Joe Wicentowski
 :  @see https://semver.org/spec/v2.0.0.html
 :)
module namespace semver = "http://exist-db.org/xquery/semver";

(:~ Compare two versions (strictly)
 :
 :  @param $v1 A version string
 :  @param $v2 A second version string
 :  @return -1 if v1 < v2, 0 if v1 = v2, or 1 if v1 > v2.
 :)
declare function semver:compare(
    $v1 as xs:string,
    $v2 as xs:string
) as xs:integer {
    semver:compare-parsed(semver:parse($v1), semver:parse($v2))
};
```

**Markdown output** (`--format md`):

```markdown
# Module: `semver`

**Namespace:** `http://exist-db.org/xquery/semver`

**XQuery Version:** 3.1

Validate, compare, sort, parse, and serialize Semantic Versioning
(SemVer) 2.0.0 version strings, using XQuery.

**Author:** Joe Wicentowski

**See:** https://semver.org/spec/v2.0.0.html

## Functions

### `semver:compare#2`

\```xquery
declare function semver:compare(
    $v1 as xs:string,
    $v2 as xs:string
) as xs:integer
\```

Compare two versions (strictly)

**Parameters:**

- `$v1` -- A version string
- `$v2` -- A second version string

**Returns:** -1 if v1 < v2, 0 if v1 = v2, or 1 if v1 > v2.
```

**XML output** (`--format xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xqdoc xmlns="http://www.xqdoc.org/1.0">
  <module type="library">
    <uri>http://exist-db.org/xquery/semver</uri>
    <name>semver</name>
    <comment>
      <description>Validate, compare, sort, parse, ...</description>
    </comment>
  </module>
  <functions>
    <function arity="2">
      <comment>
        <description>Compare two versions (strictly) ...</description>
      </comment>
      <name>semver:compare</name>
      <signature>semver:compare($v1 as xs:string, $v2 as xs:string) as xs:integer</signature>
      <parameters>
        <parameter>
          <name>$v1</name>
          <type>xs:string</type>
        </parameter>
        <parameter>
          <name>$v2</name>
          <type>xs:string</type>
        </parameter>
      </parameters>
      <return>
        <type>xs:integer</type>
      </return>
    </function>
  </functions>
</xqdoc>
```

## How It Works

The tool extracts 4 files from eXist-db's hand-written recursive descent parser -- specifically the **lexer layer**, which has zero eXist runtime dependencies:

| File | Lines | Purpose |
|------|-------|---------|
| `XQueryLexer.java` | 860 | Tokenizes XQuery source (all versions) |
| `Token.java` | 425 | Token types and position tracking |
| `Keywords.java` | 384 | XQuery keyword constants |
| `ParseError.java` | 177 | Error reporting |

On top of the lexer, a lightweight **token-walking generator** (~400 lines) identifies module declarations, namespace declarations, imports, variable declarations, and function declarations by matching token patterns. This is sufficient for xqdoc extraction since documentation is attached to declarations, not expressions.

The lexer was modified to emit `XQDOC_COMMENT` tokens for `(:~ ... :)` comments (the original skips them).

## Architecture

```
XQuery Source
     |
     v
 XQueryLexer  (tokenize)
     |
     v
 Token Stream  (NCNAME, STRING_LITERAL, XQDOC_COMMENT, ...)
     |
     v
 XQDocGenerator  (walk tokens, extract declarations)
     |
     v
 XQDocModule  (module, functions, variables, namespaces, imports)
     |
     +---> XQDocXmlEmitter   --> xqdoc XML
     |
     +---> XQDocMarkdownEmitter --> Markdown
```

## Running Tests

```bash
mvn test
```

The test suite covers:
- Module declarations (library and main modules)
- Version declarations with encoding
- XQDoc comment extraction and attachment
- Regular comments not captured
- Namespace and import declarations
- Variable declarations (typed, external)
- Function declarations (params, return types, no params, no return type)
- Annotations (single, multiple, with values)
- Complex types (function types, map types, sequences)
- XML output well-formedness and escaping
- Markdown output formatting of xqdoc tags
- Edge cases (empty module, nested braces, prolog-only)
- Full test module with all features combined

## License

LGPL 2.1 (same as eXist-db)
