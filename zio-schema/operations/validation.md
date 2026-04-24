# Validation

> When we create a schema for a type, we can also specify validation rules for the type. Validations are a way to ensure that the data conforms to certain rules.

When we create a schema for a type, we can also specify validation rules for the type. Validations are a way to ensure that the data conforms to certain rules.

Using `Schema#validate` we can validate a value against the validation rules of its schema:

```scala
trait Schema[A] {
  def validate(value: A)(implicit schema: Schema[A]): Chunk[ValidationError]
}
```

Let's write a schema for the `Person` case class and add validation rules to it. For example, we can specify that the `age` field must be greater than 0 and less than 120 and the `name` field must be non-empty:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = CaseClass2(
    id0 = TypeId.fromTypeName("Person"),
    field01 = Schema.Field(
      name0 = "name",
      schema0 = Schema[String],
      validation0 = Validation.minLength(1),
      get0 = (p: Person) => p.name,
      set0 = { (p: Person, s: String) => p.copy(name = s) }
    ),
    field02 = Schema.Field(
      name0 = "age",
      schema0 = Schema[Int],
      validation0 = Validation.between(0, 120),
      get0 = (p: Person) => p.age,
      set0 = { (p: Person, age: Int) => p.copy(age = age) }
    ),
    construct0 = (name, age) => Person(name, age),
    annotations0 = Chunk.empty
  )
}
```

Both fields of the `Person` case class have validation rules. Let's see what happens when we try to validate a `Person` value that does not conform to the validation rules:

```scala

val result: Chunk[ValidationError] = Person.schema.validate(Person("John Doe", 130))
println(result)
// Output: 
// Chunk(EqualTo(130,120),LessThan(130,120))
```

Due to the failed validation rules, a list of the specific rules that were not met is generated. In this case, it indicates that the age is not equal, or less than 120.
