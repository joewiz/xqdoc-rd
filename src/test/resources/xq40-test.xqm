xquery version "4.0";

(:~
 : XQuery 4.0 feature test module for xqdoc generation.
 :
 : Exercises pipeline operator, focus functions, keyword arguments,
 : string templates, otherwise, for member, thin arrow, default
 : parameter values, and other XQ4 additions.
 :
 : @author eXist-db project
 : @version 1.0
 : @since XQuery 4.0
 : @see https://qt4cg.org/specifications/xquery-40/Overview.html
 :)
module namespace xq4 = "http://exist-db.org/test/xq4";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";
declare namespace array = "http://www.w3.org/2005/xpath-functions/array";

(:~
 : A version constant using a string template.
 :)
declare variable $xq4:version as xs:string := ``[XQuery 4.0 test module v`{ 1.0 }`]``;

(:~
 : Transform and filter a sequence using the pipeline operator.
 :
 : Demonstrates the |> pipeline for chaining operations without
 : nested function calls or the arrow operator.
 :
 : @param $items A sequence of strings to process
 : @return The processed, filtered, sorted sequence
 :)
declare function xq4:pipeline-transform($items as xs:string*) as xs:string* {
    $items
        |> filter(fn($s) { string-length($s) > 2 })
        |> for-each(fn($s) { upper-case($s) })
        |> sort()
};

(:~
 : A focus function that operates on the context item.
 :
 : Focus functions use fn { ... } syntax and implicitly receive
 : the context item as their argument.
 :
 : @param $items The items to transform
 : @return The transformed items
 :)
declare function xq4:with-focus($items as xs:string*) as xs:string* {
    $items ! (fn { upper-case(.) || "!" })()
};

(:~
 : Demonstrate keyword arguments in function calls.
 :
 : @param $name The user's name
 : @param $greeting The greeting prefix
 : @param $punctuation The ending punctuation
 : @return A formatted greeting string
 :)
declare function xq4:greet(
    $name as xs:string,
    $greeting as xs:string := "Hello",
    $punctuation as xs:string := "!"
) as xs:string {
    $greeting || ", " || $name || $punctuation
};

(:~
 : Process array members using for member clause.
 :
 : The for member clause iterates over array members without
 : flattening, preserving the array structure awareness.
 :
 : @param $data An array of maps with name and score keys
 : @return A sequence of formatted result strings
 :)
declare function xq4:process-members($data as array(map(*))) as xs:string* {
    for member $entry in $data
    let $name := $entry?name
    let $score := $entry?score
    where $score > 50
    order by $score descending
    return $name || ": " || $score
};

(:~
 : Demonstrate the otherwise operator for fallback values.
 :
 : The otherwise operator returns the left operand if it is
 : non-empty, or the right operand as a fallback.
 :
 : @param $primary The primary value to try
 : @param $fallback The fallback value
 : @return The primary value if non-empty, otherwise the fallback
 :)
declare function xq4:coalesce($primary as item()*, $fallback as item()*) as item()* {
    $primary otherwise $fallback
};

(:~
 : Use the thin arrow operator for method-style calls.
 :
 : The thin arrow (=>) passes the left operand as the first
 : argument to the function on the right.
 :
 : @param $values A sequence of strings
 : @return A single joined string
 : @since 4.0
 :)
declare function xq4:join-with-arrow($values as xs:string*) as xs:string {
    $values
        => string-join(", ")
        => concat("Items: ", .)
};

(:~
 : Build a formatted report using string templates.
 :
 : String templates allow embedded expressions within backtick-
 : delimited strings for readable string construction.
 :
 : @param $title The report title
 : @param $count The item count
 : @param $total The total value
 : @return A formatted report string
 :)
declare function xq4:build-report(
    $title as xs:string,
    $count as xs:integer,
    $total as xs:decimal
) as xs:string {
    ``[
Report: `{ $title }`
Items:  `{ $count }`
Total:  `{ format-number($total, "#,##0.00") }`
Status: `{ if ($count > 0) then "OK" else "Empty" }`
    ]``
};

(:~
 : Demonstrate default parameter values.
 :
 : Functions can declare default values for parameters using :=,
 : allowing callers to omit trailing arguments.
 :
 : @param $items The items to paginate
 : @param $page The page number (1-based)
 : @param $size The page size
 : @return A subsequence for the requested page
 : @error xq4:PAGINATION If page is less than 1
 :)
declare function xq4:paginate(
    $items as item()*,
    $page as xs:integer := 1,
    $size as xs:integer := 10
) as item()* {
    let $start := ($page - 1) * $size + 1
    return subsequence($items, $start, $size)
};

(:~
 : A deprecated function demonstrating the @deprecated tag.
 :
 : @param $x The input value
 : @return The doubled value
 : @deprecated Use xq4:multiply($x, 2) instead. Will be removed in 2.0.
 :)
declare function xq4:double($x as xs:numeric) as xs:numeric {
    $x * 2
};
