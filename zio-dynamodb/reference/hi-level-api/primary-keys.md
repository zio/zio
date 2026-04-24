# Primary Keys

> In the native AWS DynamoDB API primary keys are represented in two different ways depending on context:

In the native AWS DynamoDB API primary keys are represented in two different ways depending on context:

 
| AWS|Example|Context
| ---|---|---
| Primary Keys | `{"id": "1", "year": 2023}` | `GetItem`, `PutItem`, `DeleteItem` [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_GetItem.html#DDB-GetItem-request-Key)
| Key Condition Expressions | `#id=:val1 and #year > :val2` | `Query` [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.KeyConditionExpressions.html)

Here we see that for CRUD operations the primary key is represented as a JSON object where each value is checked for _equality_, while for Query operations the primary key is represented as a key condition _expression_ .

The High Level API unifies the two different ways to represent a primary key into a single _type safe_ API.

## Unified Type Safe High Level API for Primary Key Expressions
Assuming the below model
```scala
final case class Person(id: String, year: Int, address: String)
object Person {
  implicit val schema: Schema.CaseClass3[String, Int, String, Person] = DeriveSchema.gen[Person]
  val (id, year, address) = ProjectExpression.accessors[Person]
}
final case class Employee(id: String, group: String, address: String)
object Person {
  implicit val schema: Schema.CaseClass3[String, Int, String, Person] = DeriveSchema.gen[Person]
  val (id, year, address) = ProjectExpression.accessors[Person]
}
```

The High Level API unifies the two different ways into a single Type Safe API that is accessed by using the `ProjectExpression` returned by the `ProjectExpression.accessors` function as a springboard via the `partitionKey` and `sortKey` methods.

| AWS| Example                                                                          |Context
| ---|----------------------------------------------------------------------------------|---
| Primary Keys | `Person.id.partitionKey === "1" && Person.year.sortKey === "2020`"               | `GetItem`, `PutItem`, `DeleteItem` 
| Key Condition Expressions | [Query]`.whereKey(Person.id.partitionKey === "1" && Person.year.sortKey > 2020)` | [Query]`.whereKey`

Valid operations on primary keys are:

context | operation | applies to
---|---|---
`Person.id.partitionKey``Person.year.sortKey` |  `===` | `GetItem`, `PutItem`, `DeleteItem`, [Query]`.whereKey` 
`Person.id.partitionKey === "1"` |  `&&` | `GetItem`, `PutItem`, `DeleteItem`, [Query]`.whereKey`.  Provides conjunction from a partition key to a sort key
`Person.year.sortKey` |  `>` | [Query]`.whereKey` only
 |  `>=` | 
 |  `<` | 
 |  `<=` | 
 |  `<>` | 
 |  `.between(2021, 2023)` |
`Employee.group.sortKey` | `.beginsWith("Group1")` | Applies to String sort keys only
