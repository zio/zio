# Low Level API

> The low level API provides low level query creation and execution while still offering a large reduction in boilerplate. It's is based one to one on DynamoDB abstractions and the surface area consists of:
- `AttrMap` which is a convenience container with automatic conversion between Scala values and `AttributeValue`'s. It also has type aliases of `PrimaryKey` and `Item` (see reference section for more details)
- `$` function for creating untyped `ProjectionExpression`s eg `$("person.address.houseNumber")`
- query methods in the `DynamoDBQuery` companion object that all contain the word `Item` such as `getItem`, `putItem`, `updateItem`, `deleteItem`, `queryAllItem`. 
- Expressions - most of these query methods take expressions as arguments. Once we have a `ProjectionExpression` via the dollar function we can use it as a springboard to create further expressions such as `ConditionExpression`, `UpdateExpression` and `PrimaryKeyExpression`:
  - `ConditionExpression`'s - `$("name") === "John"`
  - `UpdateExpression`'s - `$("name").set("Smith")`

The low level API provides low level query creation and execution while still offering a large reduction in boilerplate. It's is based one to one on DynamoDB abstractions and the surface area consists of:
- `AttrMap` which is a convenience container with automatic conversion between Scala values and `AttributeValue`'s. It also has type aliases of `PrimaryKey` and `Item` (see reference section for more details)
- `$` function for creating untyped `ProjectionExpression`s eg `$("person.address.houseNumber")`
- query methods in the `DynamoDBQuery` companion object that all contain the word `Item` such as `getItem`, `putItem`, `updateItem`, `deleteItem`, `queryAllItem`. 
- Expressions - most of these query methods take expressions as arguments. Once we have a `ProjectionExpression` via the dollar function we can use it as a springboard to create further expressions such as `ConditionExpression`, `UpdateExpression` and `PrimaryKeyExpression`:
  - `ConditionExpression`'s - `$("name") === "John"`
  - `UpdateExpression`'s - `$("name").set("Smith")`

Examples of low level queries are shown below:
```scala
for {
  _ <- DynamoDBQuery.putItem("person-table", Item("id" -> "1", "name" -> "John", "age" -> 42)).execute
  maybeFound <- DynamoDBQuery.getItem("person-table")(PrimaryKey("id" -> "1")).execute
  _ <- DynamoDBQuery.updateItem("person-table")(PrimaryKey("id" -> "1"))($("name").set("Smith") + $("age").set(21)).execute
  _ <- DynamoDBQuery.deleteItem("person-table")(PrimaryKey("id" -> "1")).execute
} yield ()
```

However there are some caveats to using the Low Level API:
- It is not type safe - subtle runtime errors can occur - for example if there are typos in the field names, or if incompatible types are used in expressions.
- Serialization and deserialization of case classes is the responsibility of the user - this is usually a major burden.
