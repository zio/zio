# update

> ```scala
def update[From: Schema](tableName: String)(primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From])(
    action: Action[From]
): DynamoDBQuery[From, Option[From]]  = ???
```

```scala
def update[From: Schema](tableName: String)(primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From])(
    action: Action[From]
): DynamoDBQuery[From, Option[From]]  = ???
```

The `update` operation is used to modify an existing item in a table. Both `KeyConditionExpr.PrimaryKeyExpr` and the `Action` params can be created using the `ProjectionExpression`'s in the companion object for model class: 
    
```scala
for {
  _ <- DynamoDBQuery.update("person")(Person.id.primaryKey === "1")(
    Person.name.set(42) + Person.age.set(42)
  ).execute
} yield maybePerson
```

## `update` expressions

action | description
---|---
`+` | combines update actions eg `Person.name.set(42) + Person.age.set(42)`  
`set` | Set an attribute `Person.name.set("John")`
`setIfNotExists` | Set attribute if it does not exists `Person.name.setIfNotExists("John")`
`appendList` | Add supplied list to the end of this list attribute
`prepend` | Prepend an element to a list attribute
`prependList` | Prepend a list to list attribute
`deleteFromSet` | delete all elements that match the supplied set
`add(a: To)` | adds this value as a number attribute if it does not exists, else adds the numeric value to the existing attribute
`addSet` | Adds this set as an attribute if it does not exists, else if it exists it adds the elements of the set
`remove(index: Int)` | remove an element at the specified index
`remove` | Removes this path expression from an item

See [AWS API Reference](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.UpdateExpressions.html) to learn more about update expressions.

### `update` query operations

```scala
<UPDATE_QUERY>
  .returns(<ReturnValues>) // ReturnValues.AllNew | ReturnValues.AllOld | ReturnValues.None <default>
  .where(<ConditionExpression>)
```
