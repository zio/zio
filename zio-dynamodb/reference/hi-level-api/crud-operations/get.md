# get

> ```scala
  def get[From: Schema](tableName: String)(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[From, Either[ItemError, From]] = ???
```

```scala
  def get[From: Schema](tableName: String)(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[From, Either[ItemError, From]] = ???
```

The `get` operation is used to retrieve an item from a table. The `KeyConditionExpr.PrimaryKeyExpr` can be created using the `ProjectionExpression`'s in the companion object for model class. It returns an `Either[ItemError, From]` where `ItemError` is a sealed trait that that has `ValueNotFound` and `DecodingError` instances. 

```scala
for {
  errorOrPerson <- DynamoDBQuery.get("person")(Person.id.primaryKey === "1").execute
} yield errorOrPerson
```

### Working with `get` return values

Sometimes working with a `Either[ItemError, From]` can be a little unwieldy so there are two approaches we can take.

The first approach is to use the ZIO `absolve` method to push all ItemErrors into the ZIO error channel

```scala

for {
  person <- DynamoDBQuery.get("person")(Person.id.primaryKey === "1").execute.absolve
} yield person
```

However sometimes we wish to treat `NotFound` as a success case and for this the `maybeFound` extension method can be imported to push the `DecodingError` into the ZIO error channel and handle `NotFound` as a successful operation by using an `Option` type. 

```scala

for {
  maybePerson <- DynamoDBQuery.get("person")(Person.id.primaryKey === "1").execute.maybeFound
} yield maybePerson
```

### `get` query operations

```scala
<GET_QUERY>
  .consistency(<ConsistencyMode>)
  .where(<ConditionExpression>)
```
