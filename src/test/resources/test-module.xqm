xquery version "3.1";

(:~ A test module exercising a wide range of xqdoc comment features.
 :
 :  This module demonstrates xqdoc comments on the module declaration,
 :  namespace declarations, variable declarations, and function declarations
 :  with @param, @return, @author, @version, @since, @see, @deprecated,
 :  and @error tags.
 :
 :  @author Joe Wicentowski
 :  @version 1.0.0
 :  @see https://exist-db.org
 :  @since eXist-db 7.0
 :)
module namespace test = "http://exist-db.org/xquery/test/xqdoc";

declare namespace map = "http://www.w3.org/2005/xpath-functions/map";
declare namespace array = "http://www.w3.org/2005/xpath-functions/array";

import module namespace semver = "http://exist-db.org/xquery/semver"
    at "semver.xqm";

(:~ The default greeting message. :)
declare variable $test:default-greeting as xs:string := "Hello";

(:~ Maximum retry count.
 :
 :  @since 1.0.0
 :)
declare variable $test:max-retries as xs:integer external;

(:~ Greet a user by name.
 :
 :  Constructs a personalized greeting message using the configured
 :  default greeting and the user's name.
 :
 :  @param $name The user's display name
 :  @return A greeting string
 :  @see $test:default-greeting
 :)
declare function test:greet($name as xs:string) as xs:string {
    $test:default-greeting || ", " || $name || "!"
};

(:~ Compare two SemVer strings and return the higher one.
 :
 :  @param $v1 First version string (must be valid SemVer)
 :  @param $v2 Second version string (must be valid SemVer)
 :  @return The higher version string, or the first if equal
 :  @error semver:regex-error If either version string is invalid
 :  @deprecated As of 2.0.0, use semver:sort() instead
 :)
declare function test:max-version(
    $v1 as xs:string,
    $v2 as xs:string
) as xs:string {
    if (semver:ge($v1, $v2)) then $v1 else $v2
};

(:~ Process items with a callback function.
 :
 :  Applies the given function to each item and collects the results.
 :  Supports higher-order function parameters.
 :
 :  @param $items The items to process
 :  @param $callback A function to apply to each item
 :  @return The collected results
 :)
declare
    %public
    %rest:GET
    %rest:path("/api/process")
function test:process(
    $items as item()*,
    $callback as function(item()) as item()*
) as item()* {
    $items ! $callback(.)
};

(:~ A private helper for internal use only. :)
declare
    %private
function test:internal-helper($x as xs:integer) as xs:integer {
    $x * 2
};

(:~ A function with no parameters and no return type. :)
declare function test:no-params() {
    "constant"
};
