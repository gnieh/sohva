---
layout: default
title: Mango Queries
---

An interesting feature of CouchDB introduced in version 2.0 is the mango query server. This feature makes it much easier to query document without having to write views and mastering the map/reduce concepts.

Sohva supports sending such queries and also offers a DSL to make them easy to write.

Overview
--------

The DSL lives in the `mango` package, thus start by importing it.

```scala
import gnieh.sohva.mango._
```

Then you can start using it to write your query, starting with the `find` word.

```scala
val query = find
```

You can filter the fields you are interested in using the `fields` clause

```scala
val query = find fields ("title", "author", "date")
```

The selector part of the query comes after the `where` word where each field is represented by `field("name")`.

```scala
val query = find fields ("title", "author", "date") where field("tags").containsAll("post")
```

Selector expressions may be combined with `&&` and `||`.

Result of a query may be sorted using the `sortBy` word.

```scala
val query = find fields ("title", "author", "date") where field("tags").containsAll("post") sortBy Asc("date")
```

Once you have your query built to your liking, you can run it against the database

```scala
val result = db.find(query)
```

Constructors
------------

To start a query using the DSL, use the [find](/latest/api/index.html#gnieh.sohva.mango.package@find:gnieh.sohva.mango.Query) constructor which returns an empty request.

To start selecting on a field with the DSL, use the [field](/latest/api/index.html#gnieh.sohva.mango.package@field(fld:String):gnieh.sohva.mango.SelectorBase) constructors, which returns a [SelectorBase](/latest/api/#gnieh.sohva.mango.SelectorBase) with operators to build a complete selector.

To apply a selector on the subfields of a field, use the [within](/latest/api/index.html#gnieh.sohva.mango.package@within(fld:String)(sel:gnieh.sohva.mango.Selector):gnieh.sohva.mango.Selector) constructor.

Combinators
-----------

### `$and` combinator

```scala
sel1 && sel2
```

is the json selector

```json
{
  "$and": [ sel1, sel2 ]
}
```

### `$or` combinator

```scala
sel1 || sel2
```

is the json selector

```json
{
  "$or": [ sel1, sel2 ]
}
```

### `$not` combinator

```scala
!sel
```

is the json selector

```json
{
  "$not": sel
}
```

### `$nor` combinator

```scala
!(sel1 || sel2)
```

is the json selector

```json
{
  "$nor": [ sel1, sel2 ]
}
```

Conditions
----------

### `$eq` condition

```scala
field("a") === 43
```

is the json selector

```json
{
  "a": {
    "$eq": 43
  }
}
```

### `$ne` condition

```scala
field("a") !== 4.3
```

is the json selector

```json
{
  "a": {
    "$ne": 4.3
  }
}
```

### `$lt` condition

```scala
field("a") < 122
```

is the json selector

```json
{
  "a": {
    "$lt": 122
  }
}
```

### `$lte` condition

```scala
field("a") <= 122
```

is the json selector

```json
{
  "a": {
    "$lte": 122
  }
}
```

### `$gt` condition

```scala
field("a") > 233
```

is the json selector

```json
{
  "a": {
    "$gt": 233
  }
}
```

### `$gte` condition

```scala
field("a") >= 233
```

is the json selector

```json
{
  "a": {
    "$gte": 233
  }
}
```

### `$exists` condition

```scala
field("a").exists
```

is the json selector

```json
{
  "a": {
    "$exists": true
  }
}

```

```scala
field("a").doesNotExist
```

is the json selector

```json
{
  "a": {
    "$exists": false
  }
}
```

### `$type` condition

```scala
field("a").hasType("string")
```

is the json selector

```json
{
  "a": {
    "$type": "string"
  }
}
```

### `$in` condition

```scala
field("a") in Vector("a", "b", "c")
```

is the json selector

```json
{
  "a": {
    "$in": [ "a", "b", "c" ]
  }
}
```

### `$nin` condition

```scala
field("a") notIn Vector("a", "b", "c")
```

is the json selector

```json
{
  "a": {
    "$nin": [ "a", "b", "c" ]
  }
}
```

### `$size` condition

```scala
field("a").hasSize(34)
```

is the json selector

```json
{
  "a": {
    "$size": 34
  }
}
```

### `$mod` condition

```scala
field("a") % 4 === 3
```

is the json selector

```json
{
  "a": {
    "$mod": [ 4, 3 ]
  }
}
```

### `$regex` condition

```scala
field("a").matches("[a-zA-Z_][a-zA-Z0-9_]*")
```

is the json selector

```json
{
  "a": {
    "$regex": "[a-zA-Z_][a-zA-Z0-9_]*"
  }
}
```

### `$all` condition

```scala
field("a").containsAll("a", "b", "c")
```

is the json selector

```json
{
  "a": {
    "$all": [ "a", "b", "c" ]
  }
}
```

### `$elemMatch` condition

```scala
field("a").contains(sel)
```

is the json selector

```json
{
  "a": {
    "$elemMatch": sel
  }
```

Query
-----

### Define the selector

```scala
val query = find where sel
```

defines a query with the given selector.

### Select fields

```scala
val query = find fields ("a", "b", "c")
```

returns only the given fields.

### Sort result

```scala
val query = q sortBy (Asc("a"), Desc("b"))
```

sorts the result of query `q` ascending by field `a` and then descending by field `b`.

### Limit the result

```scala
val query = q limit 54
```

returns only the 54 first result of query `q`.

### Skip some results

```scala
val query = q skip 43
```

skips the 43 first results of query `q`.

### Using a specific index

```scala
val query = q use ("design", "idx")
// or
val query = q use "design"
```

transforms query `q` to use the specified index

### Resetting some query elements

```scala
val query = q without sort
```

is the query `q` without its `sort` elements.
