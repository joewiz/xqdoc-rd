xquery version "3.1";

(:~
 : XQuery Update Facility 3.0 test module for xqdoc generation.
 :
 : Exercises updating functions, copy-modify-return expressions,
 : insert/delete/replace/rename operations, and transform with
 : annotations.
 :
 : @author eXist-db project
 : @version 1.0
 : @since XQUF 3.0
 : @see https://www.w3.org/TR/xquery-update-30/
 :)
module namespace xquf = "http://exist-db.org/test/xquf";

(:~
 : A sample document for testing update operations.
 :)
declare variable $xquf:sample as document-node() := document {
    <catalog>
        <item id="1" status="active">
            <name>Widget</name>
            <price>9.99</price>
        </item>
    </catalog>
};

(:~
 : Insert a new item into a catalog.
 :
 : Creates a new item element and inserts it as the last child
 : of the catalog root element.
 :
 : @param $catalog The catalog document to update
 : @param $id The new item's identifier
 : @param $name The item name
 : @param $price The item price
 : @error xquf:DUP-ID If an item with the same id already exists
 :)
declare updating function xquf:add-item(
    $catalog as document-node(),
    $id as xs:string,
    $name as xs:string,
    $price as xs:decimal
) {
    let $new-item :=
        <item id="{$id}" status="active">
            <name>{$name}</name>
            <price>{$price}</price>
        </item>
    return
        insert node $new-item as last into $catalog/catalog
};

(:~
 : Remove an item from the catalog by its id.
 :
 : @param $catalog The catalog document
 : @param $id The id of the item to remove
 : @return The updated catalog (via copy-modify-return)
 : @error xquf:NOT-FOUND If no item with the given id exists
 :)
declare function xquf:remove-item(
    $catalog as document-node(),
    $id as xs:string
) as document-node() {
    copy $result := $catalog
    modify delete node $result//item[@id = $id]
    return $result
};

(:~
 : Update the price of an item.
 :
 : Uses copy-modify-return to produce a new document with the
 : price replaced, leaving the original unchanged.
 :
 : @param $catalog The catalog document
 : @param $id The item id
 : @param $new-price The new price value
 : @return A copy of the catalog with the updated price
 :)
declare function xquf:update-price(
    $catalog as document-node(),
    $id as xs:string,
    $new-price as xs:decimal
) as document-node() {
    copy $result := $catalog
    modify (
        replace value of node $result//item[@id = $id]/price
            with $new-price
    )
    return $result
};

(:~
 : Rename an element in the catalog.
 :
 : Demonstrates the rename expression from XQUF 3.0.
 :
 : @param $catalog The catalog document
 : @param $old-name The current element name
 : @param $new-name The new element name
 : @return The catalog with the renamed element
 :)
declare function xquf:rename-element(
    $catalog as document-node(),
    $old-name as xs:string,
    $new-name as xs:string
) as document-node() {
    copy $result := $catalog
    modify (
        for $elem in $result//*[local-name() = $old-name]
        return rename node $elem as $new-name
    )
    return $result
};

(:~
 : Deactivate all items matching a predicate.
 :
 : An updating function that modifies the status attribute
 : of matching items in place.
 :
 : @param $catalog The catalog to update
 : @param $predicate A function that tests each item
 : @see xquf:add-item#4
 :)
declare updating function xquf:deactivate-matching(
    $catalog as document-node(),
    $predicate as function(element()) as xs:boolean
) {
    for $item in $catalog//item
    where $predicate($item)
    return
        replace value of node $item/@status with "inactive"
};

(:~
 : Apply a batch of updates to a catalog.
 :
 : Demonstrates combining multiple update operations in a single
 : updating function using comma-separated pending update lists.
 :
 : @param $catalog The catalog document
 : @param $updates A sequence of update instruction maps
 : @deprecated Use the new batch API in xquf:apply-batch#2 instead
 :)
declare updating function xquf:apply-updates(
    $catalog as document-node(),
    $updates as map(*)*
) {
    for $update in $updates
    return
        switch ($update?action)
            case "insert" return
                insert node $update?node as last into $catalog/catalog
            case "delete" return
                delete node $catalog//item[@id = $update?id]
            case "replace" return
                replace value of node $catalog//item[@id = $update?id]/price
                    with $update?value
            default return ()
};
