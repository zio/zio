# DynamoDB Transactions

> [Transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html) are as simple as calling the `.transaction` method on a `DynamoDBQuery`. As long as every component of the query is a valid transaction item and the `DynamoDBQuery` does not have a mix of get and write transaction items. A list of valid items for both types of queries is listed below.

[Transactions](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/transaction-apis.html) are as simple as calling the `.transaction` method on a `DynamoDBQuery`. As long as every component of the query is a valid transaction item and the `DynamoDBQuery` does not have a mix of get and write transaction items. A list of valid items for both types of queries is listed below.

## Examples

Assuming the below model:
```scala
final case class Student(email: String, subject: String)
object Person {
  implicit lazy val schema: Schema.CaseClass2[String, String, Student] = DeriveSchema.gen[Student]

  val (email, subject) = ProjectionExpression.accessors[Student]
}
final case class Bill(email: String, amount: Int)
object Bill {
  implicit lazy val schema: Schema.CaseClass2[String, Int, Bill] = DeriveSchema.gen[Bill]

  val (email, amount) = ProjectionExpression.accessors[Bill]
}
final case class WaitList(email: String)
object Bill {
  implicit lazy val schema: Schema.CaseClass1[String, WaitList] = DeriveSchema.gen[WaitList]

  val email = ProjectionExpression.accessors[WaitList]
}
```

### [Write Transactions](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html)
```scala
val student = Student("avi@gmail.com", "maths")
val bill = Bill("avi@gmail.com", 1)

val putStudent = put("student", student)
val billedStudent = put("billing", bill)
val deleteFromWaitlist = deleteFrom("waitlist")(WaitList.email.partitionKey === student.email)

val studentEnrollmentTransaction = (putStudent zip billedStudent zip deleteFromWaitlist).transaction

for {
  _ <- studentEnrollmentTransaction.execute
} yield ()
```

### [ReadTransactions](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactGetItems.html)

```scala
val getStudent = get("student")(Student.id.partitionKey === "1")
val getBill = get("billing")(Bill.id.partitionKey === "1")

val getStudentAndBillTransaction = (getStudent zip getBill).transaction

for {
  studentAndBill <- getStudentAndBillTransaction.execute
} yield studentAndBill
```

## Transaction Failures

DynamoDBQueries using the `.transaction` method will fail at runtime if there are invalid transaction actions such as creating a table, scanning for items, or querying. Note a limited number of actions that can be performed for either a read or a write transaction. There is a `.safeTransaction` method that is also available that will return `Either[DynamoDBError.TransactionError, DynamoDBQuery[A]]`.

There are more examples in our integration tests [here](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/it/scala/zio/dynamodb/TypeSafeApiCrudSpec.scala) and [here](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/it/scala/zio/dynamodb/LiveSpec.scala).

### Valid Transact Write Items

* PutItem
* DeleteItem
* BatchWriteItem
* UpdateItem
* ConditionCheck

### Valid Transact Get Item

* GetItem
* BatchGetItem
