xquery version "3.1";

(:~
 : XQuery 3.1 feature test module for xqdoc generation.
 :
 : Exercises maps, arrays, arrow operator, string concatenation,
 : inline functions, try/catch, switch, typeswitch, and complex
 : type signatures that xqdoc generators must handle.
 :
 : @author eXist-db project
 : @version 1.0
 : @since XQuery 3.1
 : @see https://www.w3.org/TR/xquery-31/
 :)
module namespace xq31 = "http://exist-db.org/test/xq31";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";
declare namespace array = "http://www.w3.org/2005/xpath-functions/array";

(:~
 : A configuration map with default settings.
 :
 : @since 1.0
 :)
declare variable $xq31:defaults as map(xs:string, item()*) := map {
    "timeout": 30,
    "retries": 3,
    "headers": map {
        "Content-Type": "application/json",
        "Accept": "application/xml"
    }
};

(:~ Supported output formats as an array. :)
declare variable $xq31:formats as array(xs:string) := ["xml", "json", "csv"];

(:~
 : Merge two configuration maps, with overrides taking precedence.
 :
 : Uses map:merge with the duplicates=use-last option to handle
 : key conflicts. Demonstrates map type parameters.
 :
 : @param $base The base configuration map
 : @param $overrides The overriding values
 : @return A merged configuration map
 : @see $xq31:defaults
 :)
declare function xq31:merge-config(
    $base as map(xs:string, item()*),
    $overrides as map(xs:string, item()*)
) as map(xs:string, item()*) {
    map:merge(($base, $overrides), map { "duplicates": "use-last" })
};

(:~
 : Transform a sequence using the arrow operator.
 :
 : Demonstrates chained arrow expressions with string operations.
 :
 : @param $input The input string
 : @return The normalized string
 :)
declare function xq31:normalize($input as xs:string) as xs:string {
    $input
        => normalize-space()
        => lower-case()
        => replace("\s+", "-")
};

(:~
 : Flatten a nested array into a sequence.
 :
 : Recursively processes array members, flattening nested arrays
 : into a single sequence of atomic values.
 :
 : @param $arr The array to flatten
 : @return A flat sequence of all values
 :)
declare function xq31:flatten($arr as array(*)) as item()* {
    array:for-each($arr, function($member) {
        if ($member instance of array(*)) then
            xq31:flatten($member)
        else
            $member
    })
};

(:~
 : Apply a transformation function to each entry in a map.
 :
 : Demonstrates higher-order function parameters with map and
 : function types in the signature.
 :
 : @param $m The source map
 : @param $fn A function that transforms each value
 : @return A new map with transformed values
 :)
declare function xq31:map-transform(
    $m as map(*),
    $fn as function(xs:string, item()*) as item()*
) as map(*) {
    map:merge(
        map:keys($m) ! map:entry(., $fn(., $m(.)))
    )
};

(:~
 : Safely parse an integer with error handling.
 :
 : Uses try/catch to handle invalid input gracefully.
 :
 : @param $value The string to parse
 : @return The parsed integer, or -1 on failure
 : @error err:FORG0001 If the value cannot be cast to xs:integer
 :)
declare function xq31:safe-parse-int($value as xs:string) as xs:integer {
    try {
        xs:integer($value)
    } catch * {
        -1
    }
};

(:~
 : Classify a value by type using typeswitch.
 :
 : @param $value The value to classify
 : @return A string describing the type
 :)
declare function xq31:classify($value as item()) as xs:string {
    typeswitch ($value)
        case xs:integer return "integer"
        case xs:string return "string"
        case xs:boolean return "boolean"
        case map(*) return "map"
        case array(*) return "array"
        case element() return "element"
        default return "other"
};

(:~
 : Format a value based on a mode string using switch.
 :
 : @param $value The value to format
 : @param $mode The output mode: "json", "xml", or "text"
 : @return The formatted string
 :)
declare function xq31:format($value as item(), $mode as xs:string) as xs:string {
    switch ($mode)
        case "json" return serialize($value, map { "method": "json" })
        case "xml" return serialize($value, map { "method": "xml" })
        case "text" return string($value)
        default return string($value)
};

(:~
 : Build a lookup table from key-value pairs.
 :
 : Demonstrates string concatenation operator (||) and
 : FLWOR with map construction.
 :
 : @param $pairs A sequence of "key=value" strings
 : @return A map of the parsed pairs
 :)
declare function xq31:build-lookup($pairs as xs:string*) as map(xs:string, xs:string) {
    map:merge(
        for $pair in $pairs
        let $parts := tokenize($pair, "=")
        where count($parts) eq 2
        return map:entry($parts[1], $parts[2])
    )
};
