# Manual Schema Construction

> Assume we have a domain containing following models:

Assume we have a domain containing following models:

```scala
object Domain {
  final case class Person(name: String, age: Int)

  sealed trait PaymentMethod

  object PaymentMethod {
    final case class CreditCard(number: String, expirationMonth: Int, expirationYear: Int) extends PaymentMethod
    final case class WireTransfer(accountNumber: String, bankCode: String) extends PaymentMethod
  }

  final case class Customer(person: Person, paymentMethod: PaymentMethod)
  
}
```

Let's begin by creating a schema for the `Person` data type:

```scala

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    Schema.CaseClass2[String, Int, Person](
      id0 = TypeId.fromTypeName("Person"),
      field01 = Schema.Field(name0 = "name", schema0 = Schema[String], get0 = _.name, set0 = (p, x) => p.copy(name = x)),
      field02 = Schema.Field(name0 = "age", schema0 = Schema[Int], get0 = _.age, set0 = (person, age) => person.copy(age = age)),
      construct0 = (name, age) => Person(name, age),
    )
}
```

The next step is writing schema for `PaymentMethod`:

```scala

sealed trait PaymentMethod

object PaymentMethod {
  implicit val schema: Schema[PaymentMethod] =
    Schema.Enum2[CreditCard, WireTransfer, PaymentMethod](
      id = TypeId.fromTypeName("PaymentMethod"),
      case1 = Schema.Case[PaymentMethod, CreditCard](
        id = "CreditCard",
        schema = CreditCard.schema,
        unsafeDeconstruct = pm => pm.asInstanceOf[PaymentMethod.CreditCard],
        construct = cc => cc.asInstanceOf[PaymentMethod],
        isCase = _.isInstanceOf[PaymentMethod.CreditCard],
        annotations = Chunk.empty
      ),
      case2 = Schema.Case[PaymentMethod, WireTransfer](
        id = "WireTransfer",
        schema = WireTransfer.schema,
        unsafeDeconstruct = pm => pm.asInstanceOf[PaymentMethod.WireTransfer],
        construct = wt => wt.asInstanceOf[PaymentMethod],
        isCase = _.isInstanceOf[PaymentMethod.WireTransfer],
        annotations = Chunk.empty
      )
    )

  final case class CreditCard(
      number: String,
      expirationMonth: Int,
      expirationYear: Int
  ) extends PaymentMethod

  object CreditCard {
    implicit val schema: Schema[CreditCard] =
      Schema.CaseClass3[String, Int, Int, CreditCard](
        id0 = TypeId.fromTypeName("CreditCard"),
        field01 = Schema.Field[CreditCard, String](
          name0 = "number",
          schema0 = Schema.primitive[String],
          get0 = _.number,
          set0 = (cc, n) => cc.copy(number = n)
        ),
        field02 = Schema.Field[CreditCard, Int](
          name0 = "expirationMonth",
          schema0 = Schema.primitive[Int],
          get0 = _.expirationMonth,
          set0 = (cc, em) => cc.copy(expirationMonth = em)
        ),
        field03 = Schema.Field[CreditCard, Int](
          name0 = "expirationYear",
          schema0 = Schema.primitive[Int],
          get0 = _.expirationYear,
          set0 = (cc, ey) => cc.copy(expirationYear = ey)
        ),
        construct0 = (n, em, ey) => CreditCard(n, em, ey)
      )
  }

  final case class WireTransfer(accountNumber: String, bankCode: String)
      extends PaymentMethod

  object WireTransfer {
    implicit val schema: Schema[WireTransfer] =
      Schema.CaseClass2[String, String, WireTransfer](
        id0 = TypeId.fromTypeName("WireTransfer"),
        field01 = Schema.Field[WireTransfer, String](
          name0 = "accountNumber",
          schema0 = Schema.primitive[String],
          get0 = _.accountNumber,
          set0 = (wt, an) => wt.copy(accountNumber = an)
        ),
        field02 = Schema.Field[WireTransfer, String](
          name0 = "bankCode",
          schema0 = Schema.primitive[String],
          get0 = _.bankCode,
          set0 = (wt, bc) => wt.copy(bankCode = bc)
        ),
        construct0 = (ac, bc) => WireTransfer(ac, bc)
      )
  }
}
```

And finally, we need to define the schema for the `Customer` data type:

```scala

final case class Customer(person: Person, paymentMethod: PaymentMethod)

object Customer {
  implicit val schema: Schema[Customer] =
    Schema.CaseClass2[Person, PaymentMethod, Customer](
      id0 = TypeId.fromTypeName("Customer"),
      field01 = Schema.Field[Customer, Person](
        name0 = "person",
        schema0 = Person.schema,
        get0 = _.person,
        set0 = (c, p) => c.copy(person = p)
      ),
      field02 = Schema.Field[Customer, PaymentMethod](
        name0 = "paymentMethod",
        schema0 = Schema[PaymentMethod],
        get0 = _.paymentMethod,
        set0 = (c, pm) => c.copy(paymentMethod = pm)
      ),
      construct0 = (p, pm) => Customer(p, pm)
    )
}
```

Now that we have written all the required schemas, we can proceed to create encoders and decoders (codecs) for each of our domain models. 

Let's start with writing protobuf codecs. We need to add the following line to our `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-protobuf" % 1.7.5
```

Here's an example that demonstrates a roundtrip test for protobuf codecs:

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
