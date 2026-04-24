# put

> ```scala
def put[A: Schema](tableName: String, a: A): DynamoDBQuery[A, Option[A]] = ???
```

```scala
def put[A: Schema](tableName: String, a: A): DynamoDBQuery[A, Option[A]] = ???
```

The `put` operation is used to insert or replace an item in a table.

```scala
for {
  _ <- DynamoDBQuery.put("person", Person("1", "John", 21))
        .where(Person.id.notExists) // a ConditionExpression
        .execute
} yield ()
```

### `put` query operations

```scala
<PUT_QUERY>
  .returns(<ReturnValues>) // ReturnValues.AllOld | ReturnValues.None <default>
  .where(<ConditionExpression>) // eg Person.id.notExists
```

### Using `put` with Top level traits using `discriminatorName` annotation
When using a top level sealed trait with `@discriminatorName` annotation, it must be provided explicitly to the `put`
to ensure that discriminator field is encoded.

```scala
@discriminatorName("invoiceType")
sealed trait Invoice
final case class PreBilledInvoice(/* ... */) extends Invoice
final case class BilledInvoice(/* ... */) extends Invoice

for {
  _ <- DynamoDBQuery.put[Invoice]("invoice", BilledInvoice(/* ... */)).execute // OK - discriminator encoded
  _ <- DynamoDBQuery.put[BilledInvoice]("invoice", BilledInvoice(/* ... */)).execute // WRONG - discriminator not encoded
} yield ()
```
