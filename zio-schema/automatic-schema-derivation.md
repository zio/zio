# Automatic Schema Derivation

> Automatic schema derivation is the process of generating schema definitions for data types automatically, without the need to manually write them. It allows us to generate the schema for a data type based on its structure and annotations.

Automatic schema derivation is the process of generating schema definitions for data types automatically, without the need to manually write them. It allows us to generate the schema for a data type based on its structure and annotations.

Instead of manually specifying the schema for each data type, we can rely on automatic schema derivation to generate the schema for us. This approach can save time and reduce the potential for errors, especially when dealing with complex data models.

By leveraging reflection and type introspection using macros, automatic schema derivation analyzes the structure of the data type and its fields, including their names, types, and annotations. It then generates the corresponding schema definition based on this analysis.

ZIO streamlines schema derivation through its `zio-schema-derivation` package, which utilizes the capabilities of Scala macros to automatically derive schemas. In order to use automatic schema derivation, we neeed to add the following line to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-derivation" % 1.7.5
```

Once again, let's revisit our domain models:

```scala
final case class Person(name: String, age: Int)

sealed trait PaymentMethod

object PaymentMethod {
  final case class CreditCard(number: String, expirationMonth: Int, expirationYear: Int) extends PaymentMethod
  final case class WireTransfer(accountNumber: String, bankCode: String) extends PaymentMethod
}

final case class Customer(person: Person, paymentMethod: PaymentMethod)
```

We can easily use auto derivation to create schemas:

```scala

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

sealed trait PaymentMethod

object PaymentMethod {

  implicit val schema: Schema[PaymentMethod] =
    DeriveSchema.gen[PaymentMethod]

  final case class CreditCard(
      number: String,
      expirationMonth: Int,
      expirationYear: Int
  ) extends PaymentMethod

  final case class WireTransfer(accountNumber: String, bankCode: String)
      extends PaymentMethod
}

final case class Customer(person: Person, paymentMethod: PaymentMethod)

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer]
}
```

Now we can write an example that demonstrates a roundtrip test for protobuf codecs:

```scala
// Create a customer instance
val customer =
  Customer(
    person = Person("John Doe", 42),
    paymentMethod = PaymentMethod.CreditCard("1000100010001000", 6, 2024)
  )

// Create binary codec from customer 
val customerCodec: BinaryCodec[Customer] =
  ProtobufCodec.protobufCodec[Customer]

// Encode the customer object
val encodedCustomer: Chunk[Byte] = customerCodec.encode(customer)

// Decode the byte array back to the person instance
val decodedCustomer: Either[DecodeError, Customer] =
  customerCodec.decode(encodedCustomer)

assert(Right(customer) == decodedCustomer)
```
