# $ and parse functions

> The `$` and `parse` functions are the primary way to create a `ProjectionExpression` in the Low Level API.

The `$` and `parse` functions are the primary way to create a `ProjectionExpression` in the Low Level API.

They take a string representation of the attribute path that uses the 
[standard AWS DynamoDB path expression syntax](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ProjectionExpressions.html)
and return a `ProjectionExpression`.

eg

```scala
final case class Person(email: String, hobbies: Map[String, List[String]])

val errorOrProjectionExpression: Either[String, ProjectionExpression] = ProjectionExpression.parse("hobbies.sport[0]")
val projectionExpression: ProjectionExpression = ProjectionExpression.$("hobbies.sport[0]")
```

The `parse` function takes a string representation of the path expression (that uses standard AWS DynamoDB path expression syntax).
It returns an `Either` which is a `Left[String]` when the string projection expression is invalid, or a `Right[ProjectionExpression]`
when the string projection expression valid and successfully parsed.

The `$` function is the unsafe version of the `parse` function - if the string projection expression is invalid it throws an `IllegalStateException`. 

The below table shows the supported path expression syntax:

Expression Syntax | Description
---|---
`"foo"` | top level element  
`"foos[0]"` | simple top level list element - indexes are zero based  
`"foo.bar"` | addressing a map element inside a top level element  
`"foo_bar"` | underscores are valid
`"foo-bar"` | hyphens are valid
&#96`foo.bar`&#96 | special characters such as "dot" need to be escaped with backticks. Here we have an attribute name that contains a special character. 

After the expression is parsed into a `ProjectionExpression`, at execution time the AWS API [Expression Attributes Names](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ExpressionAttributeNames.html) and 
[Expression Attributes Values](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ExpressionAttributeValues.html)
maps are used to escape all path elements safely when required. 

## Low level representation of `ProjectionExpression`

For those who are interested the below code snippet shows the slightly simplified version of the `ProjectionExpression` implementation:

```scala
sealed trait ProjectionExpression
object ProjectionExpression {
  final case object Root extends ProjectionExpression
  final case class ListElement(parent: ProjectionExpression, index: Int) extends ProjectionExpression
  final case class MapElement(parent: ProjectionExpression, key: String) extends ProjectionExpression
}

val projectionExpression: ProjectionExpression = ProjectionExpression.$("hobbies.sport[0]")
println(projectionExpression) // ListElement(MapElement(MapElement(Root, "hobbies"), "sport"), 0)
```

Note that top level elements are represented as `MapElement(Root, <ELEMENT_NAME>)`.
