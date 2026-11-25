# How to Update Nested Data with Derived Optics

> Derive lenses, prisms, and traversals from schemas with ZioOpticsBuilder and compose them to update deeply nested immutable data.

In this guide, we update fields deep inside an immutable `Order`: the city of the customer's address, the details of a card payment, and the quantity of every line item. Instead of writing optics by hand, we derive them from the schemas we already have with `Schema#makeAccessors` and `ZioOpticsBuilder`, and then compose them into one optic per path.

We start with one lens per field, compose lenses to reach into nested case classes, use prisms to work with one case of a sealed trait, and finish with a traversal that updates every element of a list. The [Optics Derivation](../derivations/optics-derivation.md) page explains how `Schema#makeAccessors` and `AccessorBuilder` work; this guide focuses on putting the derived optics to work.

## The Problem

Case classes are immutable, so changing one nested field means copying every level above it. With three levels, a one-field change already takes a nested `copy` chain:

```scala
final case class Address(street: String, city: String)
final case class Customer(name: String, address: Address)
final case class LineItem(sku: String, quantity: Int)
final case class Order(id: String, customer: Customer, items: List[LineItem])

def moveCustomer(order: Order, city: String): Order =
  order.copy(
    customer = order.customer.copy(
      address = order.customer.address.copy(city = city)
    )
  )

def doubleQuantities(order: Order): Order =
  order.copy(items = order.items.map(item => item.copy(quantity = item.quantity * 2)))
```

Every function that touches the address repeats the same path, and none of these paths is a value we can name, pass around, or combine with another one. Optics turn each path into a value, but writing a lens by hand for every field is the same boilerplate in a different place.

A schema already lists the fields of a case class and the cases of a sealed trait, so ZIO Schema can derive one optic for each of them.

## Prerequisites

The `zio-schema-optics` module provides `ZioOpticsBuilder` and brings in [ZIO Optics](https://zio.dev/zio-optics), whose `Optic` type the derived optics use. We also need the derivation module for `DeriveSchema.gen`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema-derivation" % "1.9.2",
  "dev.zio" %% "zio-schema-optics"     % "1.9.2"
)
```

## Deriving the Accessors

For each type, we derive its schema in the companion object and call `Schema#makeAccessors` with `ZioOpticsBuilder`. A case class schema returns one lens per field, in declaration order, and a sealed trait schema returns one prism per case. Destructuring the tuple gives each optic a name:

```scala
import zio.Chunk
import zio.optics._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.optics.ZioOpticsBuilder

final case class Address(street: String, city: String)

object Address {
  implicit val schema: Schema.CaseClass2[String, String, Address] =
    DeriveSchema.gen[Address]

  val (street, city) = schema.makeAccessors(ZioOpticsBuilder)
}

final case class Customer(name: String, address: Address)

object Customer {
  implicit val schema: Schema.CaseClass2[String, Address, Customer] =
    DeriveSchema.gen[Customer]

  val (name, address) = schema.makeAccessors(ZioOpticsBuilder)
}

final case class LineItem(sku: String, quantity: Int)

object LineItem {
  implicit val schema: Schema.CaseClass2[String, Int, LineItem] =
    DeriveSchema.gen[LineItem]

  val (sku, quantity) = schema.makeAccessors(ZioOpticsBuilder)
}

sealed trait Payment

object Payment {
  final case class Card(last4: String, expiry: String) extends Payment
  final case class Invoice(dueInDays: Int)            extends Payment

  implicit val schema: Schema.Enum2[Card, Invoice, Payment] =
    DeriveSchema.gen[Payment]

  val (card, invoice) = schema.makeAccessors(ZioOpticsBuilder)
}

final case class Order(id: String, customer: Customer, items: List[LineItem], payment: Payment)

object Order {
  implicit val schema: Schema.CaseClass4[String, Customer, List[LineItem], Payment, Order] =
    DeriveSchema.gen[Order]

  val (id, customer, items, payment) = schema.makeAccessors(ZioOpticsBuilder)
}
```

The precise schema types matter. `DeriveSchema.gen` returns a `Schema.CaseClass2[String, String, Address]`, and that type tells `Schema#makeAccessors` to return a pair of lenses. Declared as a plain `Schema[Address]`, the same value only promises an abstract `Accessors` type, and the tuple cannot be destructured.

The rest of the guide works on one sample order:

```scala
val order: Order =
  Order(
    id = "A-1001",
    customer = Customer("Ada Lovelace", Address("12 St James's Square", "London")),
    items = List(LineItem("SKU-1", 1), LineItem("SKU-2", 3)),
    payment = Payment.Card("4242", "08/27")
  )
```

## Reading and Writing One Field

Each lens reads its field with `Optic#get` and replaces it with `Optic#set`, which takes the new value first and the whole value second. Both return an `Either`, because the optics that `ZioOpticsBuilder` derives report failures as an `OpticFailure` instead of throwing:

```scala
Order.id.get(order)
// res1: package.OpticResult[OpticFailure, String] = Right(value = "A-1001")

Order.id.set("A-1002")(order).map(_.id)
// res2: Either[OpticFailure, String] = Right(value = "A-1002")
```

`Optic#update` combines the two: it reads the field, applies a function, and writes the result back. It takes the whole value first and the function second:

```scala
Customer.name.update(order.customer)(_.toUpperCase)
// res3: package.OpticResult[OpticFailure, Customer] = Right(
//   value = Customer(
//     name = "ADA LOVELACE",
//     address = Address(street = "12 St James's Square", city = "London")
//   )
// )
```

## Composing Lenses Across Nested Case Classes

The `Optic#>>>` operator composes two optics: the left one focuses on a part, and the right one focuses on a part of that part. Composing the `Order.customer`, `Customer.address`, and `Address.city` lenses gives one lens from an `Order` straight to the city:

```scala
val orderCity = Order.customer >>> Customer.address >>> Address.city
```

The composed lens has the same `Optic#get`, `Optic#set`, and `Optic#update` operations as the lenses it is built from:

```scala
orderCity.get(order)
// res4: package.OpticResult[OpticFailure, String] = Right(value = "London")

orderCity.set("Paris")(order).map(_.customer)
// res5: Either[OpticFailure, Customer] = Right(
//   value = Customer(
//     name = "Ada Lovelace",
//     address = Address(street = "12 St James's Square", city = "Paris")
//   )
// )

orderCity.update(order)(_.toUpperCase).map(_.customer.address)
// res6: Either[OpticFailure, Address] = Right(
//   value = Address(street = "12 St James's Square", city = "LONDON")
// )
```

`orderCity` replaces the nested `copy` chain of `moveCustomer`. Because it is a value, we define it once and reuse it wherever the code needs the customer's city.

## Working with One Case of a Sealed Trait

A prism focuses on one case of a sealed trait. `Optic#get` returns the case when the value matches it, and an `OpticFailure` otherwise:

```scala
Payment.card.get(order.payment)
// res7: package.OpticResult[OpticFailure, Payment.Card] = Right(
//   value = Card(last4 = "4242", expiry = "08/27")
// )

Payment.card.get(Payment.Invoice(dueInDays = 30))
// res8: package.OpticResult[OpticFailure, Payment.Card] = Left(
//   value = OpticFailure(message = "Cannot deconstruct to term Card")
// )
```

A lens and a prism compose through the `Either` that `Optic#get` returns. Reading the card of an order takes the `Order.payment` lens and then the `Payment.card` prism:

```scala
Order.payment.get(order).flatMap(payment => Payment.card.get(payment))
// res9: Either[OpticFailure, Payment.Card] = Right(
//   value = Card(last4 = "4242", expiry = "08/27")
// )
```

To change a card only when the order is paid by card, we update the field through the `Order.payment` lens and use the prism inside the update function. A payment of any other case passes through unchanged:

```scala
def renewCard(order: Order, expiry: String): Either[OpticFailure, Order] =
  Order.payment.update(order) { payment =>
    Payment.card.get(payment).map(_.copy(expiry = expiry)).getOrElse(payment)
  }
```

The same call renews the card of a card order and leaves an invoice order as it is:

```scala
renewCard(order, "08/30").map(_.payment)
// res10: Either[OpticFailure, Payment] = Right(
//   value = Card(last4 = "4242", expiry = "08/30")
// )

renewCard(order.copy(payment = Payment.Invoice(30)), "08/30").map(_.payment)
// res11: Either[OpticFailure, Payment] = Right(
//   value = Invoice(dueInDays = 30)
// )
```

## Updating Every Element of a Collection

A traversal focuses on all elements of a collection at once. `Schema#makeAccessors` derives one from a sequence schema, but `Schema.list` is typed as a plain `Schema[List[A]]`, which hides the sequence type that `Schema#makeAccessors` needs. Building the `Schema.Sequence` for `List[LineItem]` explicitly keeps that type, and `Schema#makeAccessors` then returns a traversal:

```scala
val lineItems: ZioOpticsBuilder.Traversal[List[LineItem], LineItem] =
  Schema
    .Sequence[List[LineItem], LineItem, String](
      elementSchema = LineItem.schema,
      fromChunk = _.toList,
      toChunk = items => Chunk.fromIterable(items),
      annotations = Chunk.empty,
      identity = "List"
    )
    .makeAccessors(ZioOpticsBuilder)
```

A traversal reads and writes the elements as a `Chunk`. Composing the `Order.items` lens with it gives an optic from an `Order` to all of its line items:

```scala
val orderItems = Order.items >>> lineItems
```

With `Optic#update`, the function receives every line item and returns the new ones. The following call doubles every quantity:

```scala
orderItems.update(order)(_.map(item => item.copy(quantity = item.quantity * 2))).map(_.items)
// res12: Either[OpticFailure, List[LineItem]] = Right(
//   value = List(
//     LineItem(sku = "SKU-1", quantity = 2),
//     LineItem(sku = "SKU-2", quantity = 6)
//   )
// )
```

:::warning
A traversal replaces existing elements; it never adds or removes them. The traversals that `ZioOpticsBuilder` derives for sequences write the new chunk position by position: extra elements in the new chunk are dropped, and when the new chunk is shorter, the remaining original elements stay in place. The traversals for maps and sets merge the new elements into the existing ones, so they can add entries but never remove any.
:::

Appending through the traversal leaves the order unchanged, because the new chunk has more elements than the list:

```scala
orderItems.update(order)(_ :+ LineItem("SKU-3", 1)).map(_.items)
// res13: Either[OpticFailure, List[LineItem]] = Right(
//   value = List(
//     LineItem(sku = "SKU-1", quantity = 1),
//     LineItem(sku = "SKU-2", quantity = 3)
//   )
// )
```

To change the length of the list, we update the whole list through the `Order.items` lens instead:

```scala
Order.items.update(order)(_ :+ LineItem("SKU-3", 1)).map(_.items)
// res14: Either[OpticFailure, List[LineItem]] = Right(
//   value = List(
//     LineItem(sku = "SKU-1", quantity = 1),
//     LineItem(sku = "SKU-2", quantity = 3),
//     LineItem(sku = "SKU-3", quantity = 1)
//   )
// )
```

:::note
`ZioOpticsBuilder` implements every lens get and set by converting the whole value to a `DynamicValue` and back, so a derived lens, and every optic composed from one, is much slower than the equivalent `copy` call. Use derived optics where the convenience of a reusable path matters more than speed, not in tight loops.
:::

## Going Further

The [Optics Derivation](../derivations/optics-derivation.md) page shows how `AccessorBuilder` defines the three kinds of optics and how `ZioOpticsBuilder` implements them. The [ZIO Optics](https://zio.dev/zio-optics) documentation covers the `Optic` type itself, including `Optic#zip` for setting two fields at once and the other operators that the derived optics support.
