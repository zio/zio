# Optics

> Optics are a fundamental feature of ZIO Blocks that enable type-safe, composable access and modification of nested data structures. What sets ZIO Blocks apart is its implementation of **reflective optics** — a novel construct that combines the operational capabilities of traditional optics with embedded structural metadata, enabling both data manipulation AND introspection.

Optics are a fundamental feature of ZIO Blocks that enable type-safe, composable access and modification of nested data structures. What sets ZIO Blocks apart is its implementation of **reflective optics** — a novel construct that combines the operational capabilities of traditional optics with embedded structural metadata, enabling both data manipulation AND introspection.

:::tip
For a practical walkthrough of building query DSLs with optics, see the [Writing a Query DSL with Reified Optics](../guides/query-dsl-reified-optics.md) guide.
:::

## What Are Optics?

Optics are abstractions that allow you to focus on a specific part of a data structure. They provide a way to **view**, **update**, and **traverse** nested fields in immutable data types without boilerplate code:

Every optic has two type parameters:

```
Optic[S, A]
      │  │
      │  └── Focus: The "little thing" being accessed
      └───── Source: The "big thing" containing the focus
```

The `S` is the source type from which data is accessed or modified. The `A` is the focus type or the target type of the optic.

The terminology comes from physical optics — like using a magnifying glass to focus on a small part of something larger.

## Reflective Optics

[//]: # (Optics are **reified fields, cases, and for-loops**.)

Traditional optics are defined purely by functions — they give you **capabilities** (get/set) but no **knowledge** about the structure being accessed. ZIO Blocks introduces **reflective optics** which embed the structure of both the source and focus as first-class reified schemas:

```
sealed trait Optic[S, A] {
  def source: Reflect.Bound[S]
  def focus: Reflect.Bound[A]
  
  // Other common operations
}
```

The `source` and `focus` methods return `Reflect.Bound` instances that describe the schema of the source and focus types, respectively. This means that every optic carries with it detailed information about the data shapes it operates on.

This enables capabilities that ordinary optics have never had before:

- **Amazing Error Messages** — Know exactly where and why an optic operation failed, because you know which part of the structure was being accessed during the failure
- **DSL Integration** — Optics as first-class values enables us to write query DSLs that can introspect the data model being queried. So we can integrate that DSL with underlying storage systems (SQL, NoSQL, etc.) without losing type safety.

The `OpticCheck` data type is an error reporting mechanism for reflective optics. It captures detailed diagnostic information when optic operations fail, solving a long-standing pain point in traditional optics libraries where failures silently return `None` without explanation. It provides detailed context about where and why the replacement failed. It includes the expected and actual cases, the full optic path, and the actual value encountered.

## The Optic Type Hierarchy

ZIO Blocks provides four primary optic types, each designed for a specific data shape:

1. **`Lens[S, A]`** — Focuses on a single field within a record (case class)
2. **`Prism[S, A <: S]`** — Focuses on a specific case within a sum type (sealed trait/enum)
3. **`Optional[S, A]`** — It behaves like a combination of `Lens` and `Prism`; focuses on a value that may or may not exist. Like a `Lens`, it can focus on a part of a larger structure and (if present) get and set it. Like a `Prism`, the focus may or may not exist.
4. **`Traversal[S, A]`** — Focuses on zero or more elements within a collection (List, Vector, Set, Map, etc.)

```text
                              ┌─────────────────┐ 
                              │   Optic[S, A]   │
                              │   (abstract)    │
                              └────────┬────────┘
                                       │
         ┌───────────────────┬───────────────────┬───────────────────┐
         │                   │                   │                   │         
         ▼                   ▼                   ▼                   ▼         
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   Lens[S, A]    │ │  Prism[S, A]    │ │ Optional[S, A]  │ │Traversal[S, A]  │
│                 │ │   (A <: S)      │ │                 │ │                 │
│ Fields in       │ │ Cases in        │ │ Combinations of │ │ Elements in     │
│ Records         │ │ Enums/Variants  │ │ Lens + Prism    │ │ Collections     │
└─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘
```

## Lens

A **Lens** focuses on a single field within a record (case class). It always succeeds because records always have all their fields.

Lens has two primary operations: 

| Method    | Signature     | Description                             |
|-----------|---------------|-----------------------------------------|
| `get`     | `S => A`      | Extract the field value                 |
| `replace` | `(S, A) => S` | Create new structure with updated field |

```scala
sealed trait Lens[S, A] extends Optic[S, A] {
  def get(s: S): A
  def replace(s: S, a: A): S
}
```

Once you have a lens, you can use it to extract or replace the focused field immutably. There are two main approaches to creating lenses: manual construction and automatic macro-based derivation. Let's explore each.

### Manual Lens Construction

To create a lens manually for a field, you can use the following constructor:

```scala
object Lens {
  def apply[S, A](
    source   : Reflect.Record.Bound[S],
    focusTerm: Term.Bound[S, A]
  ): Lens[S, A] = ???
}
```

It takes a `Reflect.Record.Bound[S]` representing the schema of the source type `S`, and a `Term.Bound[S, A]` representing the specific field within `S` that the lens will focus on, and finally returns a lens of type from `S` to `A`.

Assume you have an `Address` case class like this with the schema derived:

```scala
case class Address(street: String, city: String, zipCode: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}
```

You can create a lens to focus on the `street` field like this:

```scala
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
  
  val street: Lens[Address, String] =
    Lens[Address, String](
      Schema[Address].reflect.asRecord.get,
      Schema[String].reflect.asTerm("street")
    )
}
```

Now you can use the lens to get or replace the `street` field:

```scala
val address = Address("123 Main St", "Springfield", "12345")
val streetName: String = Address.street.get(address) // "123 Main St"
val updatedAddress: Address = Address.street.replace(address, "456 Elm St")
// Address("456 Elm St", "Springfield", "12345")
```

### Automatic Macro-Based Lens Derivation

While manual lens construction gives you fine-grained control, ZIO Blocks provides macro-based derivation as the **preferred approach** for creating lenses.

The `optic` macro inside the `CompanionOptics` trait creates a lens using intuitive selector syntax that mirrors standard Scala field access:

```scala

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  
  val name: Lens[Person, String] = optic(_.name)
  val age : Lens[Person, Int   ] = optic(_.age)
}
```

The macro inspects the selector expression `_.name`, validates that it corresponds to a valid field path, and generates the appropriate lens. This approach is type-safe—the compiler verifies that the field exists and has the correct type.

For nested structures, the `optic` macro can create composed lenses by chaining field accesses:

```scala
case class Address(street: String, city: String)
case class Person(name: String, address: Address)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  
  // Lens directly to the nested street field
  val street: Lens[Person, String] = optic(_.address.street)
}
```

Now you can use the `street` lens to access the nested `street` field directly and update it:

```scala
val person = Person("John", Address("123 Main St", "Springfield"))
val street = Person.street.get(person)
val updatedPerson = Person.street.replace(person, "456 Elm St")
// => Person("John", Address("456 Elm St", "Springfield"))
```

## Prism

A **`Prism[S, A <: S]`** focuses on a specific case within a sum type (sealed trait/enum). It may fail if the value is a different case.

The key operations of a prism are summarized in the table below:

| Method          | Signature                         | Description                                                    |
|-----------------|-----------------------------------|----------------------------------------------------------------|
| `getOption`     | `(S)    => Option[A]`             | Extracts if value matches subtype `A`, otherwise `None`        |
| `getOrFail`     | `(S)    => Either[OpticCheck, A]` | Extracts with detailed error info on type mismatch             |
| `reverseGet`    | `(A)    => S`                     | Upcasts subtype `A` back to supertype `S`                      |
| `replace`       | `(S, A) => S`                     | Replaces if value matches subtype, otherwise returns unchanged |
| `replaceOption` | `(S, A) => Option[S]`             | Replaces if value matches subtype, otherwise `None`            |
| `replaceOrFail` | `(S, A) => Either[OpticCheck, S]` | Replaces with detailed error info on type mismatch             |

Before discussing these operations, let's explore how we can create prisms. Similar to lenses, we have two approaches: manual construction and automatic macro-based derivation.

### Manual Prism Construction

To create a prism for a case, you can use the following constructor:

```scala
object Prism {
  def apply[S, A <: S](
    source   : Reflect.Variant.Bound[S],
    focusTerm: Term.Bound[S, A]
  ): Prism[S, A] = ???
}
```

It takes a `Reflect.Variant.Bound[S]` representing the schema of the sum type `S`, and a `Term.Bound[S, A]` representing the specific case within `S` that the prism will focus on. The result is a prism from `S` to `A`.

Assume you have a `Notification` sealed trait representing different notification types:

```scala

sealed trait Notification

object Notification {
  case class Email(to: String, subject: String, body: String)                    extends Notification
  case class SMS(phoneNumber: String, message: String)                           extends Notification
  case class Push(deviceId: String, title: String, payload: Map[String, String]) extends Notification

  implicit val schema: Schema[Notification] = Schema.derived
}
```

First, we need to define schemas for each case class and then write a prism for each case (here we define a prism for the `Email` case):

```scala

sealed trait Notification

object Notification {
  case class Email(to: String, subject: String, body: String) extends Notification
  object Email {
    implicit val schema: Schema[Email] = Schema.derived
  }
  
  case class SMS(phoneNumber: String, message: String) extends Notification
  object SMS {
    implicit val schema: Schema[SMS] = Schema.derived
  }
  
  case class Push(deviceId: String, title: String, payload: Map[String, String]) extends Notification
  object Push {
    implicit val schema: Schema[Push] = Schema.derived
  }

  implicit val schema: Schema[Notification] = Schema.derived

  val email: Prism[Notification, Email] =
    Prism[Notification, Email](
      Schema[Notification].reflect.asVariant.get,
      Schema[Email].reflect.asTerm("Email")
    )
  
  val sms: Prism[Notification, SMS] =
    Prism[Notification, SMS](
      Schema[Notification].reflect.asVariant.get,
      Schema[SMS].reflect.asTerm("SMS")
    )
  
  val push: Prism[Notification, Push] =
    Prism[Notification, Push](
      Schema[Notification].reflect.asVariant.get,
      Schema[Push].reflect.asTerm("Push")
    )
}
```

### Automatic Macro-Based Prism Derivation

The `optic` macro inside the `CompanionOptics` trait creates a prism using intuitive selector syntax with the `when[CaseType]` method:

```scala

sealed trait Notification

object Notification extends CompanionOptics[Notification] {
  case class Email(to: String, subject: String, body: String)                    extends Notification
  case class SMS(phoneNumber: String, message: String)                           extends Notification
  case class Push(deviceId: String, title: String, payload: Map[String, String]) extends Notification

  implicit val schema: Schema[Notification] = Schema.derived

  // Macro-derived prisms using when[CaseType] syntax
  val email: Prism[Notification, Email] = optic(_.when[Email])
  val sms  : Prism[Notification, SMS]   = optic(_.when[SMS])
  val push : Prism[Notification, Push]  = optic(_.when[Push])
}
```

The macro inspects the selector expression `_.when[Email]`, validates that `Email` is a valid case of the sum type `Notification`, and generates the appropriate prism. This approach is type-safe—the compiler verifies that the case type exists and is a subtype of the sum type.

For nested structures, the `optic` macro can compose prisms with lenses by chaining case selection with field access:

```scala
sealed trait Response
object Response extends CompanionOptics[Response] {
  case class Success(data: Data)    extends Response
  case class Failure(error: String) extends Response
  
  case class Data(id: Int, value: String)
  
  implicit val schema: Schema[Response] = Schema.derived
  
  // Prism to the Success case
  val success: Prism[Response, Success] = optic(_.when[Success])
  
  // Composed optic: Prism + Lens = Optional
  // Focus on the 'value' field inside the Success case
  val successValue: Optional[Response, String] = optic(_.when[Success].data.value)
}
```

When you compose a prism with a lens using the `optic` macro, the result is an `Optional`—an optic that may fail to focus (because the sum type might be a different case) but if it succeeds, always finds the field:

```scala
val response = Response.Success(Response.Data(1, "hello"))

Response.successValue.getOption(response)
// => Some("hello")

Response.successValue.getOption(Response.Failure("error"))
// => None (response is Failure, not Success)
```

### Operations

Now let's explore prism operations. Assume we have some sample notifications:

```scala
// Sample notifications
val emailNotif: Notification = Notification.Email("user@example.com", "Hello", "Welcome!")
// emailNotif: Notification = Email(
//   to = "user@example.com",
//   subject = "Hello",
//   body = "Welcome!"
// )
val smsNotif: Notification   = Notification.SMS("+1234567890", "Your code is 1234")
// smsNotif: Notification = SMS(
//   phoneNumber = "+1234567890",
//   message = "Your code is 1234"
// )
val pushNotif: Notification  = Notification.Push("device-abc", "Alert", Map("action" -> "open"))
// pushNotif: Notification = Push(
//   deviceId = "device-abc",
//   title = "Alert",
//   payload = Map("action" -> "open")
// )
```

1. **`Prism#getOption`** — Extract the case if it matches, otherwise None

```scala
// getOption: Extract the case if it matches, otherwise None
Notification.email.getOption(emailNotif)
// => Some(Email(to = "user@example.com", subject = "Hello", body = "Welcome!"))

Notification.email.getOption(smsNotif)
// => None (smsNotif is an SMS, not an Email)

Notification.sms.getOption(smsNotif)
// Option[SMS] = Some(SMS(phoneNumber = "+1234567890", message = "Your code is 1234"))
```

2. **`Prism#getOrFail`** — Extract with detailed error information on failure:

```text
trait Prism[S, A <: S] {
  def getOrFail(s: S): Either[OpticCheck, A]
}
```

It returns `Right(a)` if the source `s` matches the expected case `A`, or `Left(opticCheck)` containing detailed error information if it does not:

```scala
Notification.email.getOrFail(emailNotif)
// => Right(Email(to = "user@example.com", subject = "Hello", body = "Welcome!"))

Notification.email.getOrFail(pushNotif)
// res5: Either[OpticCheck, Email] = Left(
//   OpticCheck(
//     List(
//       UnexpectedCase(
//         expectedCase = "Email",
//         actualCase = "Push",
//         full = DynamicOptic(ArraySeq(Case("Email"))),
//         prefix = DynamicOptic(ArraySeq(Case("Email"))),
//         actualValue = Push(
//           deviceId = "device-abc",
//           title = "Alert",
//           payload = Map("action" -> "open")
//         )
//       )
//     )
//   )
// )
```

3. **`Prism#reverseGet`** — Upcast a specific case back to the parent sum type:

```text
trait Prism[S, A <: S] {
  def reverseGet(a: A): S
}
```

An example of upcasting an `Email` back to `Notification`:

```scala
val email = Notification.Email("alice@example.com", "Meeting", "See you at 3pm")
val notification: Notification = Notification.email.reverseGet(email)
// => Email("alice@example.com", "Meeting", "See you at 3pm") as Notification
```

4. **`Prism#replace`** — Replace the value if it matches the case:

```scala
trait Prism[S, A <: S] {
  def replace(s: S, a: A): S
}
```

In the following example, we are replacing an existing `Email` notification with a new one:

```scala
val newEmail = Notification.Email("new@example.com", "Updated", "New content")

Notification.email.replace(emailNotif, newEmail)
// => Email("new@example.com", "Updated", "New content")
```

If the original value is NOT the expected case, replace returns unchanged:
```scala
Notification.email.replace(smsNotif, newEmail)
// res7: Notification = SMS(
//   phoneNumber = "+1234567890",
//   message = "Your code is 1234"
// )
// => SMS("+1234567890", "Your code is 1234") (unchanged, it was an SMS)
```

5. **`Prism#replaceOption`** — Replace returning Some on success, None on mismatch:

```scala
trait Prism[S, A <: S] {
  def replaceOption(s: S, a: A): Option[S]
}
```

The following example shows replacing an `Email` notification with a new one, returning `Some` on success and `None` if the original value is not an `Email`:

```scala
Notification.email.replaceOption(emailNotif, newEmail)
// => Some(Email("new@example.com", "Updated", "New content"))

Notification.email.replaceOption(smsNotif, newEmail)
// => None (cannot replace, smsNotif is not an Email)
```

6. **`Prism#replaceOrFail`** — Replace with detailed error on mismatch:

```scala
trait Prism[S, A <: S] {
  def replaceOrFail(s: S, a: A): Either[OpticCheck, S]
}
```

The following example shows replacing an `Email` notification with a new one, returning `Right(newValue)` on success and `Left(opticCheck)` with detailed error information if the original value is not an `Email`:

```scala
Notification.email.replaceOrFail(emailNotif, newEmail)
// => Right(Email("new@example.com", "Updated", "New content"))

Notification.email.replaceOrFail(pushNotif, newEmail)
// res11: Either[OpticCheck, Notification] = Left(
//   OpticCheck(
//     List(
//       UnexpectedCase(
//         expectedCase = "Email",
//         actualCase = "Push",
//         full = DynamicOptic(ArraySeq(Case("Email"))),
//         prefix = DynamicOptic(ArraySeq(Case("Email"))),
//         actualValue = Push(
//           deviceId = "device-abc",
//           title = "Alert",
//           payload = Map("action" -> "open")
//         )
//       )
//     )
//   )
// )
```

## Optional

You can think of **`Optional[S, A]`** as the composition of lenses and prisms — it focuses on a value that may or may not exist. Used for accessing fields through variant types, optional fields, or elements at specific indices.

```scala
sealed trait Optional[S, A] extends Optic[S, A] {
  def getOption(s: S): Option[A]
  def getOrFail(s: S): Either[OpticCheck, A]
  def replace(s: S, a: A): S
  def replaceOption(s: S, a: A): Option[S]
  def replaceOrFail(s: S, a: A): Either[OpticCheck, S]
}
```

| Method          | Signature                         | Description                                                  |
|-----------------|-----------------------------------|--------------------------------------------------------------|
| `getOption`     | `(S)    => Option[A]`             | Extracts the focused value if accessible, otherwise `None`   |
| `getOrFail`     | `(S)    => Either[OpticCheck, A]` | Extracts with detailed error info on failure                 |
| `replace`       | `(S, A) => S`                     | Replaces if accessible, otherwise returns original unchanged |
| `replaceOption` | `(S, A) => Option[S]`             | Replaces if accessible, otherwise `None`                     |
| `replaceOrFail` | `(S, A) => Either[OpticCheck, S]` | Replaces with detailed error info on failure                 |

### Manual Optional Construction

Unlike `Lens` and `Prism` which have direct constructors, `Optional` is primarily constructed through **composition** of other optics, mainly from `Lens` and `Prism`:

```scala
object Optional {
  // Compose Lens with Prism (yields Optional)
  def apply[S, T, A <: T](first: Lens[S, T], second: Prism[T, A]): Optional[S, A]

  // Compose Prism with Lens (yields Optional)
  def apply[S, T <: S, A](first: Prism[S, T], second: Lens[T, A]): Optional[S, A]
}
```

| First         | Second        | Output           |
|---------------|---------------|------------------|
| `Lens[S, T]`  | `Prism[T, A]` | `Optional[S, A]` |
| `Prism[S, T]` | `Lens[T, A]`  | `Optional[S, A]` |

These two basic compositions allow you to build `Optional` optics by combining lenses and prisms in either order. 

In addition, `Optional` can also be composed with other optics to create more complex optionals, all these composition methods are supported via the `Optional.apply` method overloads:

| First              | Second             | Output           |
|--------------------|--------------------|------------------|
| `Lens[S, T]`       | `Optional[T, A]`   | `Optional[S, A]` |
| `Optional[S, T]`   | `Lens[T, A]`       | `Optional[S, A]` |
| ------------------ | ------------------ | ---------------- |
| `Prism[S, T]`      | `Optional[T, A]`   | `Optional[S, A]` |
| `Optional[S, T]`   | `Prism[T, A]`      | `Optional[S, A]` |
| --------------     | ---------------    | ---------------- |
| `Optional[S, T]`   | `Optional[T, A]`   | `Optional[S, A]` |

Beside the above composition methods, `Optional` provides specialized constructors for common patterns such as index-based access in sequences, key-based access in maps, and accessing wrapped types:

| Constructor        | Signature                                                  | Description                             |
|--------------------|------------------------------------------------------------|-----------------------------------------|
| `Optional.at`      | `(Reflect.Sequence.Bound[A, C], Int) => Optional[C[A], A]` | Accesses element at index in a sequence |
| `Optional.atKey`   | `(Reflect.Map.Bound[K, V, M], K) => Optional[M[K, V], V]`  | Accesses value at key in a map          |
| `Optional.wrapped` | `(Reflect.Wrapper.Bound[A, B]) => Optional[A, B]`          | Accesses inner value of a wrapper type  |

#### Example: Composing Lens and Prism

The most common way to manually construct an `Optional` is by composing a `Lens` with a `Prism`. Assume you have a `PaymentMethod` sum type which has multiple cases, and we have written a prism for one of its cases, e.g., `CreditCard`:

```scala

sealed trait PaymentMethod
object PaymentMethod extends CompanionOptics[PaymentMethod] {
  case class CreditCard(number: String, expiry: String) extends PaymentMethod
  case class Cryptocurrency(walletAddress: String) extends PaymentMethod
  
  implicit val schema: Schema[PaymentMethod] = Schema.derived
  
  val creditCard     : Prism[PaymentMethod, CreditCard]     = optic(_.when[CreditCard])
  val cryptocurrency : Prism[PaymentMethod, Cryptocurrency] = optic(_.when[Cryptocurrency])
}
```

And also assume we have a `Customer` record that has a `payment` field of type `PaymentMethod` and we have written a lens for that field:

```scala
case class Customer(name: String, payment: PaymentMethod)
object Customer extends CompanionOptics[Customer] {
  implicit val schema: Schema[Customer] = Schema.derived
  
  val name   : Lens[Customer, String       ] = optic(_.name)
  val payment: Lens[Customer, PaymentMethod] = optic(_.payment)
}
```

We can now compose the `payment` lens with the `creditCard` prism to create an `Optional` that focuses on the `CreditCard` details within a `Customer`:

```scala
// Compose lens and prism manually to get Optional
val creditCard: Optional[Customer, PaymentMethod.CreditCard] =
  Optional(Customer.payment, PaymentMethod.creditCard)
```

#### Example: Index-Based Access

For accessing elements at specific indices in sequences:

```scala

case class Order(id: String, items: List[String])
object Order extends CompanionOptics[Order] {
  implicit val schema: Schema[Order] = Schema.derived[Order]
  
  val items: Lens[Order, List[String]] = optic(_.items)
  
  // Manual Optional for first item
  val firstItem: Optional[Order, String] = {
    val atFirst = Optional.at(
      Schema[List[String]].reflect.asSequence.get,
      index = 0
    )
    Optional(items, atFirst)
  }
}
```

### Automatic Macro-Based Optional Derivation

The `optic` macro inside the `CompanionOptics` trait creates optionals automatically through several syntactic patterns. The macro intelligently determines when an `Optional` is needed based on the path expression.

#### Accessing Inner Values of ADTs

By combining the `.when[Case]` (prism) and `.field-name` (lens) syntax of optic macro, you can create optionals that focus on the inner values of ADTs:

```scala

case class ApiResponse(
  requestId: String,
  timestamp: Option[Long],
  result: Either[String, Int] // Left = error message, Right = status code
)
object ApiResponse extends CompanionOptics[ApiResponse] {
  implicit val schema: Schema[ApiResponse] = Schema.derived[ApiResponse]

  // Optional to the inner Long value (may not exist if None)
  val timestamp: Optional[ApiResponse, Long] = optic(_.timestamp.when[Some[Long]].value)

  // Optional to the Left value (may not exist if Right)
  val errorMessage: Optional[ApiResponse, String] = optic(_.result.when[Left[String, Int]].value)

  // Optional to the Right value (may not exist if Left)
  val statusCode: Optional[ApiResponse, Int] = optic(_.result.when[Right[String, Int]].value)
}
```

Here is another example focusing on sum types:

```scala

sealed trait Response
object Response extends CompanionOptics[Response] {
  case class Success(data: String, code: Int) extends Response
  case class Failure(error: String)           extends Response
  
  implicit val schema: Schema[Response] = Schema.derived
  
  // Prism to the Success case
  val success: Prism[Response, Success] = optic(_.when[Success])
  
  // Optional: Prism + Lens = Optional
  // Focuses on the 'data' field inside Success case
  val successData : Optional[Response, String] = optic(_.when[Success].data)
  val successCode : Optional[Response, Int   ] = optic(_.when[Success].code)
  val failureError: Optional[Response, String] = optic(_.when[Failure].error)
}
```

#### Index-based Access with `.at(index)` Syntax

We can access elements at specific indices in sequences using `.at(index)` syntax in `optic` macro:

```scala

case class OrderItem(sku: String, quantity: Int)
object OrderItem {
  implicit val schema: Schema[OrderItem] = Schema.derived
}

case class Order(id: String, items: List[OrderItem])
object Order extends CompanionOptics[Order] {
  implicit val schema: Schema[Order] = Schema.derived
  
  // Optional for specific indices
  val firstItem : Optional[Order, OrderItem] = optic(_.items.at(0))
  val secondItem: Optional[Order, OrderItem] = optic(_.items.at(1))
  
  // Chain with field access
  val firstItemSku     : Optional[Order, String] = optic(_.items.at(0).sku)
  val firstItemQuantity: Optional[Order, Int   ] = optic(_.items.at(0).quantity)
}
```

Usage:

```scala
val order = Order("ord-1", List(
  OrderItem("SKU-A", 2),
  OrderItem("SKU-B", 1)
))
val emptyOrder = Order("ord-2", List.empty)

Order.firstItem.getOption(order)       // => Some(OrderItem("SKU-A", 2))
Order.secondItem.getOption(order)      // => Some(OrderItem("SKU-B", 1))
Order.firstItem.getOption(emptyOrder)  // => None (empty list)

Order.firstItemSku.getOption(order)    // => Some("SKU-A")
```

#### Key-based Access with `.atKey(key)`

To access values at a specific key, we can use `.atKey(key)` syntax in `optic` macro:

```scala

case class Config(settings: Map[String, String])
object Config extends CompanionOptics[Config] {
  implicit val schema: Schema[Config] = Schema.derived
  
  // Optional for specific keys
  def setting(key: String): Optional[Config, String] = optic(_.settings.atKey(key))
  
  // Pre-defined optionals for common keys
  val hostSetting: Optional[Config, String] = optic(_.settings.atKey("host"))
  val portSetting: Optional[Config, String] = optic(_.settings.atKey("port"))
}
```

Here is how you can use these optionals:

```scala
val config = Config(Map("host" -> "localhost", "port" -> "8080"))

Config.hostSetting.getOption(config)        // => Some("localhost")
Config.setting("timeout").getOption(config) // => None (key not present)
```

#### Wrapper Type Access with `.wrapped[T]` Syntax

To access the inner value of wrapper types (newtypes, opaque types) you can use the `.wrapped[T]` syntax in `optic` macro:

```scala

// Assume Email is a wrapper around String with validation
case class Email private (value: String)
object Email {
  implicit val schema: Schema[Email] = Schema.derived
  
  def apply(s: String): Either[String, Email] =
    if (s.contains("@")) Right(new Email(s)) else Left("Invalid email")
}

case class Contact(name: String, email: Email)
object Contact extends CompanionOptics[Contact] {
  implicit val schema: Schema[Contact] = Schema.derived
  
  // Optional to access the wrapped String inside Email
  val emailString: Optional[Contact, String] = optic(_.email.wrapped[String])
}
```

## Traversal

A **`Traversal[S, A]`** focuses on zero or more elements within a collection (List, Vector, Set, Map, etc.). Unlike `Lens` (exactly one element) or `Optional` (zero or one element), a `Traversal` can target any number of elements simultaneously.

The key methods of `Traversal` are:

| Method         | Signature                                   | Description                                      |
|----------------|---------------------------------------------|--------------------------------------------------|
| `fold`         | `(S)(Z, (Z, A) => Z) => Z`                  | Aggregates all focused values                    |
| `reduceOrFail` | `(S)((A, A) => A) => Either[OpticCheck, A]` | Reduces with error handling                      |
| `modify`       | `(S, A => A)      => S`                     | Applies function to all focused values           |
| `modifyOption` | `(S, A => A)      => Option[S]`             | Modifies if any elements exist, otherwise `None` |
| `modifyOrFail` | `(S, A => A)      => Either[OpticCheck, S]` | Modifies with detailed error info on failure     |

### Manual Traversal Construction

Traversals are primarily constructed through the `Traversal` companion object which provides specialized constructors for different collection types:

#### Sequence Traversals

The two basic constructors for creating traversals over sequences are `seqValues` and `atIndices`:

```scala
object Traversal {
  // Traverse all elements in a sequence (List, Vector, Set, ArraySeq, etc.)
  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A]

  // Traverse specific indices
  def atIndices[A, C[_]](seq: Reflect.Sequence.Bound[A, C], indices: Seq[Int]): Traversal[C[A], A]
}
```

The `seqValues` method creates a traversal that focuses on all elements in a sequence, while `atIndices` creates a traversal that focuses only on elements at the specified indices. 

There are also convenience methods for common collection types:

```scala
object Traversal {
  def listValues[A]    (reflect: Reflect.Bound[A]): Traversal[List[A],     A]
  def vectorValues[A]  (reflect: Reflect.Bound[A]): Traversal[Vector[A],   A]
  def setValues[A]     (reflect: Reflect.Bound[A]): Traversal[Set[A],      A]
  def arraySeqValues[A](reflect: Reflect.Bound[A]): Traversal[ArraySeq[A], A]
}
```

For example, to create a traversal over all items in a shopping cart represented as a list and quantities as a vector, you can do the following:

```scala

case class ShoppingCart(items: List[String], quantities: Vector[Int])
object ShoppingCart {
  implicit val schema: Schema[ShoppingCart] = Schema.derived

  // Manual traversal for all items in the cart
  val allItems: Traversal[List[String], String] =
    Traversal.listValues(Schema[String].reflect)

  // Manual traversal for all quantities
  val allQuantities: Traversal[Vector[Int], Int] =
    Traversal.vectorValues(Schema[Int].reflect)
}
```

#### Map Traversals

To create traversals over keys or values in a map you can use the following constructors:

```scala
object Traversal {
  // Traverse all keys in a map
  def mapKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], K]
  
  // Traverse all values in a map
  def mapValues[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], V]
  
  // Traverse values at specific keys
  def atKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M], keys: Seq[K]): Traversal[M[K, V], V]
}
```

Let's say you have an inventory represented as a map of product names to stock counts. You can create traversals for both keys and values as follows:

```scala

case class Inventory(stock: Map[String, Int])
object Inventory {
  implicit val schema: Schema[Inventory] = Schema.derived
  
  // Manual traversal for all product names (keys)
  val productNames: Traversal[Map[String, Int], String] =
    Traversal.mapKeys(Schema[Map[String, Int]].reflect.asMap.get)
  
  // Manual traversal for all stock counts (values)
  val stockCounts: Traversal[Map[String, Int], Int] =
    Traversal.mapValues(Schema[Map[String, Int]].reflect.asMap.get)
}
```

### Automatic Macro-Based Traversal Derivation

The `optic` macro inside the `CompanionOptics` trait creates traversals using intuitive selector syntax with the `.each`, `.eachKey`, and `.eachValue` methods.

1. Use `.each` to traverse all elements in a sequence (List, Vector, Set, ArraySeq):

```scala

case class Order(id: String, items: List[String], prices: Vector[Double])
object Order extends CompanionOptics[Order] {
  implicit val schema: Schema[Order] = Schema.derived
  
  // Traversal over all items
  val allItems: Traversal[Order, String] = optic(_.items.each)
  
  // Traversal over all prices
  val allPrices: Traversal[Order, Double] = optic(_.prices.each)
}
```

2. Use `.eachKey` to traverse all keys and `.eachValue` to traverse all values in a map:

```scala

case class UserScores(scores: Map[String, Int])
object UserScores extends CompanionOptics[UserScores] {
  implicit val schema: Schema[UserScores] = Schema.derived
  
  // Traversal over all user names (keys)
  val allUserNames: Traversal[UserScores, String] = optic(_.scores.eachKey)
  
  // Traversal over all scores (values)
  val allScores: Traversal[UserScores, Int] = optic(_.scores.eachValue)
}
```

3. Use `.atIndices(indices)` to traverse elements at specific indices:

```scala

case class Matrix(rows: Vector[Vector[Int]])
object Matrix extends CompanionOptics[Matrix] {
  implicit val schema: Schema[Matrix] = Schema.derived
  
  // Traverse elements at indices 0, 2, and 4
  val selectedRows: Traversal[Matrix, Vector[Int]] = optic(_.rows.atIndices(0, 2, 4))
}
```

4. Use `.atKeys(keys)` to traverse values at specific keys:

```scala

case class Environment(variables: Map[String, String])
object Environment extends CompanionOptics[Environment] {
  implicit val schema: Schema[Environment] = Schema.derived
  
  // Traverse values for specific environment variables
  val criticalVars: Traversal[Environment, String] = optic(_.variables.atKeys("PATH", "HOME", "USER"))
}
```

Please note that the `optic` macro supports chaining traversals with field access for deep navigation:

```scala

case class LineItem(sku: String, price: Double, quantity: Int)
object LineItem {
  implicit val schema: Schema[LineItem] = Schema.derived
}

case class Invoice(id: String, items: List[LineItem])
object Invoice extends CompanionOptics[Invoice] {
  implicit val schema: Schema[Invoice] = Schema.derived
  
  // Traverse to get all SKUs from all items
  val allSkus: Traversal[Invoice, String] = optic(_.items.each.sku)
  
  // Traverse to get all prices from all items
  val allPrices: Traversal[Invoice, Double] = optic(_.items.each.price)
  
  // Traverse to get all quantities from all items
  val allQuantities: Traversal[Invoice, Int] = optic(_.items.each.quantity)
}
```

### Operations

Let's explore traversal operations with a practical example. Assume we have a `Team` case class with a list of members and a map of scores:

```scala

case class Team(name: String, members: List[String], scores: Map[String, Int])
object Team extends CompanionOptics[Team] {
  implicit val schema: Schema[Team] = Schema.derived[Team]
  
  val allMembers    : Traversal[Team, String] = optic(_.members.each)
  val allScores     : Traversal[Team, Int   ] = optic(_.scores.eachValue)
  val allPlayerNames: Traversal[Team, String] = optic(_.scores.eachKey)
}

val team = Team(
  "Alpha",
  List("Alice", "Bob", "Charlie"),
  Map("Alice" -> 100, "Bob" -> 85, "Charlie" -> 92)
)
```

1. `Traversal#fold` aggregates all focused values:

```scala
// Count all members
Team.allMembers.fold(team)(0, (count, _) => count + 1)
// => 3

// Sum all scores
Team.allScores.fold(team)(0, _ + _)
// => 277

// Concatenate all member names
Team.allMembers.fold(team)("", (acc, name) => if (acc.isEmpty) name else s"$acc, $name")
// => "Alice, Bob, Charlie"
```

2. `Traversal#reduceOrFail` reduces with error handling:

```scala
// Find the maximum score
Team.allScores.reduceOrFail(team)(math.max)
// => Right(100)

// Find the minimum score
Team.allScores.reduceOrFail(team)(math.min)
// => Right(85)

// Attempt to reduce an empty collection
val emptyTeam = Team("Empty", Nil, Map.empty)
Team.allMembers.reduceOrFail(emptyTeam)(_ + _)
// => Left(OpticCheck(...)) with EmptySequence error
```

3. `Traversal#modify` — Apply function to all focused values:

```scala
// Convert all member names to uppercase
Team.allMembers.modify(team, _.toUpperCase)
// => Team("Alpha", List("ALICE", "BOB", "CHARLIE"), Map(...))

// Double all scores
Team.allScores.modify(team, _ * 2)
// => Team("Alpha", List(...), Map("Alice" -> 200, "Bob" -> 170, "Charlie" -> 184))

// Add prefix to all player names (keys)
Team.allPlayerNames.modify(team, name => s"Player: $name")
// => Team("Alpha", List(...), Map("Player: Alice" -> 100, ...))
```

4. `Traversal#modifyOption` — Modify returning Option

```scala
// Modify non-empty collection
Team.allMembers.modifyOption(team, _.toUpperCase)
// => Some(Team("Alpha", List("ALICE", "BOB", "CHARLIE"), Map(...)))

// Modify empty collection
val emptyTeam = Team("Empty", Nil, Map.empty)
Team.allMembers.modifyOption(emptyTeam, _.toUpperCase)
// => None
```

5. `Traversal#modifyOrFail` — Modify with detailed error on failure

```scala
// Successful modification
Team.allMembers.modifyOrFail(team, _.toUpperCase)
// => Right(Team("Alpha", List("ALICE", "BOB", "CHARLIE"), Map(...)))

// Failed modification (empty collection)
val emptyTeam = Team("Empty", Nil, Map.empty)
Team.allMembers.modifyOrFail(emptyTeam, _.toUpperCase)
// => Left(OpticCheck(List(EmptySequence(...))))
```

## Debug-Friendly toString

All optic types (`Lens`, `Prism`, `Optional`, `Traversal`) have a custom `toString` that produces output matching the `optic` macro syntax. This makes debugging easier by showing exactly what path the optic represents:

```scala

case class Person(name: String, address: Address)
case class Address(street: String, city: String)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val street: Lens[Person, String] = optic(_.address.street)
}

println(Person.street)  // Output: Lens(_.address.street)
```

**Examples by optic type:**

| Optic | toString Output |
|-------|-----------------|
| `Lens` for field | `Lens(_.name)` |
| `Lens` for nested field | `Lens(_.address.street)` |
| `Prism` for variant case | `Prism(_.when[CreditCard])` |
| `Optional` combining prism + lens | `Optional(_.when[Success].data)` |
| `Traversal` over sequence | `Traversal(_.items.each)` |
| `Traversal` over map keys | `Traversal(_.metadata.eachKey)` |
| `Traversal` over map values | `Traversal(_.metadata.eachValue)` |

The output mirrors what you would write with the `optic` macro, making it easy to understand and reproduce the optic path.

## Composing Optics

All optics can be composed together to create more complex access paths. All optics that extend the base `Optic` trait support composition via the `apply` method, which takes another optic as an argument and returns a new optic representing the combined access path:
```scala
trait Optic[S, A] {
  def apply[B](that: Lens[A, B]): Optic[S, B]
  def apply[B <: A](that: Prism[A, B]): Optic[S, B]
  def apply[B](that: Optional[A, B]): Optic[S, B]
  def apply[B](that: Traversal[A, B]): Traversal[S, B]
}
```

All optics (`Lens`, `Prism`, `Optional`, `Traversal`) implement the above `apply` methods to support composition with other optics. Additionally, they have `apply` overloads on their companion objects that take two optics as arguments and return the composed optic.

The following table summarizes the composition rules for combining different optic types:

| `this` ↓ / `that` → | **`Lens`**  | **`Prism`** | **`Optional`** | **`Traversal`** |
|---------------------|-------------|-------------|----------------|-----------------|
| **`Lens`**          | `Lens`      | `Optional`  | `Optional`     | `Traversal`     |
| **`Prism`**         | `Optional`  | `Prism`     | `Optional`     | `Traversal`     |
| **`Optional`**      | `Optional`  | `Optional`  | `Optional`     | `Traversal`     |
| **`Traversal`**     | `Traversal` | `Traversal` | `Traversal`    | `Traversal`     |

Here are some important notes regarding optic composition:

1. As the table demonstrates, `Traversal` is the most general optic type, as composing with it always results in a `Traversal`. This behavior occurs because `Traversal` focuses on zero or more elements, and once you have that level of generality, you cannot return to a more specific optic type. This is the **absorption property** encoded in the type system.

2. Using `Optic#apply` instead of the `apply` methods on companion objects allows for a more natural DSL-like syntax for composing optics. For example, instead of writing `Lens(Person.address, Address.postalCode)`, we can write `Person.address(Address.postalCode)`, which reads naturally as "address's postal code".

3. `Optional` is an absorbing optic for `Lens` and `Prism`—once you have partiality in your access path, composing with total optics (`Lens`) or other partial optics (`Prism`, `Optional`) preserves that partiality. `Traversal` is the most general optic, as composing with it always results in a `Traversal`.

### Examples

In this section, we explore the composition of `Lens` with other optics. The composition of other optics follows similar patterns.

#### Composing a Lens with Another Lens

We can chain two lenses to focus deeper into nested structures using the following composition operators:

```scala
object Lens {
  def apply[S, T, A](
    first : Lens[S, T],   // S => T
    second: Lens[T, A]    // T => A
  ): Lens[S, A] = ???     // S => A
}
```

This method takes two lenses: the first from `S` to `T`, and the second from `T` to `A`, and returns a new lens from `S` to `A`.

For example, if we have a `Person` case class that contains an `Address` and we want to create a lens to access the `street` field of the `Address` within `Person`, we can do so as follows:

```scala

case class Address(street: String, city: String)
object Address extends CompanionOptics[Address] { 
  implicit val schema: Schema[Address] = Schema.derived 
  
  val street: Lens[Address, String] =
    Lens[Address, String](
      Schema[Address].reflect.asRecord.get,
      Schema[String].reflect.asTerm("street")
    )
    
  val city: Lens[Address, String] =
    Lens[Address, String](
      Schema[Address].reflect.asRecord.get,
      Schema[String].reflect.asTerm("city")
    )
}
case class Person(name: String, age: Int, address: Address)
object Person {

  implicit val schema: Schema[Person] = Schema.derived[Person]

  val address: Lens[Person, Address] =
    Lens[Person, Address](
      Schema[Person].reflect.asRecord.get,
      Schema[Address].reflect.asTerm("address")
    )
    
  val street: Lens[Person, String] = 
    Lens[Person, Address, String](
      Person.address, // Lens from Person to Address
      Address.street  // Lens from Address to String (street field)
    )
}
```

To make the DSL more convenient, we can use the `Lens#apply` method:

```scala
object Person {
  val street: Lens[Person, String] = Person.address(Address.street)
}
```

The `optic` macro (or its alias `$`) provides a more concise way to derive composed lenses. By extending `CompanionOptics[T]` and using selector syntax, you can derive the same lenses with significantly less boilerplate:

```scala

case class Address(street: String, city: String)
object Address extends CompanionOptics[Address] 
case class Person(name: String, age: Int, address: Address)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  // Simple field lens
  val address: Lens[Person, Address] = optic(_.address)
  
  // Composed lens - directly access nested field via path syntax
  val street: Lens[Person, String] = optic(_.address.street)
  val city  : Lens[Person, String] = optic(_.address.city)
}
```

The `optic` macro inspects the selector path and automatically composes the necessary lenses. The path `_.address.street` is expanded into a composition of `Person → Address` and `Address → String` lenses.

#### Composing a Lens with a Prism

When you compose a `Lens` with a `Prism`, the result is an `Optional`. This makes sense because a `Lens` always succeeds (fields always exist in records), whereas a `Prism` may fail (a value may not match the particular case being targeted). Therefore, the composition may fail, which is exactly what `Optional` represents:

```scala
sealed trait Lens[S, A] extends Optic[S, A] {
  def apply[B <: A](that: Prism[A, B]): Optional[S, B]
}
```

Consider a scenario where you have a record containing a field whose type is a sealed trait (sum type), and you want to focus on a specific case of that sum type.

For instance, suppose we have an `Employee` case class that contains a `ContactInfo` field, where `ContactInfo` is a sealed trait with different cases:

```scala

sealed trait ContactInfo

object ContactInfo {
  case class Email(address: String) extends ContactInfo

  object Email {
    implicit val schema: Schema[Email] = Schema.derived[Email]
    
    val address: Lens[Email, String] =
      Lens[Email, String](
        Schema[Email].reflect.asRecord.get,
        Schema[String].reflect.asTerm("address")
      )
  }

  case class Phone(number: String) extends ContactInfo

  object Phone {
    implicit val schema: Schema[Phone] = Schema.derived[Phone]
  }

  case object NoContact extends ContactInfo

  implicit val schema: Schema[ContactInfo] = Schema.derived[ContactInfo]

  // Prism to focus on the Email case
  lazy val email: Prism[ContactInfo, Email] =
    Prism[ContactInfo, Email](
      Schema[ContactInfo].reflect.asVariant.get,
      Schema[Email].reflect.asTerm("Email")
    )

  // Prism to focus on the Phone case
  lazy val phone: Prism[ContactInfo, Phone] =
    Prism[ContactInfo, Phone](
      Schema[ContactInfo].reflect.asVariant.get,
      Schema[Phone].reflect.asTerm("Phone")
    )
}

case class Employee(name: String, contact: ContactInfo)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived[Employee]

  // Lens to focus on the contact field
  val contact: Lens[Employee, ContactInfo] =
    Lens[Employee, ContactInfo](
      Schema[Employee].reflect.asRecord.get,
      Schema[ContactInfo].reflect.asTerm("contact")
    )

  // Compose Lens with Prism to get an Optional
  // This focuses on the email address, but only if contact is an Email
  val contactEmail: Optional[Employee, ContactInfo.Email] =
    Employee.contact(ContactInfo.email)
  
  val emailAddress: Optional[Employee, String] =
    Employee.contactEmail(ContactInfo.Email.address)
}
```

The `Employee.contactEmail` is an `Optional[Employee, ContactInfo.Email]` that allows you to access or modify the email contact of an employee, but only if their contact information is indeed an `Email`. If the contact information is a `Phone` or `NoContact`, the operations will fail gracefully, returning `None`:

```scala
val employee1 = Employee("Alice", ContactInfo.Email("alice@example.com"))
// getOption returns Some when the contact is an Email
Employee.contactEmail.getOption(employee1) // => Some(Email("alice@example.com"))

// replace only succeeds if the contact is already an Email
Employee.contactEmail.replaceOption(employee1, ContactInfo.Email("newalice@example.com"))
// => Some(Employee("Alice", Email("newalice@example.com")))

val employee2 = Employee("Bob", ContactInfo.Phone("555-1234"))
// getOption returns None when the contact is NOT an Email
Employee.contactEmail.getOption(employee2) // => None

Employee.contactEmail.replaceOption(employee2, ContactInfo.Email("bob@example.com"))
// => None (Bob's contact is a Phone, not an Email)
```

You can further compose this `Optional` to reach deeper into the structure:

```scala
// Now you can get/set the email address string directly
Employee.emailAddress.getOption(employee1)
// => Some("alice@example.com")

Employee.emailAddress.getOption(employee2)
// => None
```

The `optic` macro supports case selection using the `.when[T]` syntax, which makes composing lenses with prisms much more concise:

```scala

sealed trait ContactInfo

object ContactInfo extends CompanionOptics[ContactInfo] {
  case class  Email(address: String) extends ContactInfo
  case class  Phone(number: String)  extends ContactInfo
  case object NoContact              extends ContactInfo

  implicit val schema: Schema[ContactInfo] = Schema.derived

  // Derive prisms using the optic macro with .when[T] syntax
  val email: Prism[ContactInfo, Email] = optic(_.when[Email])
  val phone: Prism[ContactInfo, Phone] = optic(_.when[Phone])
}

case class Employee(name: String, contact: ContactInfo)

object Employee extends CompanionOptics[Employee] {
  implicit val schema: Schema[Employee] = Schema.derived

  // Simple field lens
  val contact: Lens[Employee, ContactInfo] = optic(_.contact)

  // Compose Lens with Prism using path syntax - result is an Optional
  val contactEmail: Optional[Employee, ContactInfo.Email] = 
    optic(_.contact.when[ContactInfo.Email])
  
  // Chain even deeper to get the email address string
  val emailAddress: Optional[Employee, String] = 
    optic(_.contact.when[ContactInfo.Email].address)
    
  // Similarly for phone
  val phoneNumber: Optional[Employee, String] = 
    optic(_.contact.when[ContactInfo.Phone].number)
}
```

The path `_.contact.when[Email].address` is automatically composed into a `Lens → Prism → Lens` chain, producing an `Optional[Employee, String]` optic.

#### Composing a Lens with an Optional

When you compose a `Lens` with an `Optional`, the result is also an `Optional`. This follows naturally: a `Lens` always succeeds, and an `Optional` may fail; therefore, the composition may fail.

Building on our previous example, suppose we have a `Company` that contains an `Employee`:

```scala
case class Company(name: String, ceo: Employee)
object Company {
  implicit val schema: Schema[Company] = Schema.derived[Company]

  // Lens to focus on the CEO
  val ceo: Lens[Company, Employee] =
    Lens[Company, Employee](
      Schema[Company].reflect.asRecord.get,
      Schema[Employee].reflect.asTerm("ceo")
    )

  // Compose Lens with Optional to get another Optional
  // This chains: Company -> ceo (Lens) -> contactEmail (Optional)
  val ceoEmailContact: Optional[Company, ContactInfo.Email] =
    Company.ceo(Employee.contactEmail)

  // Further composition to get the email address string
  val ceoEmailAddress: Optional[Company, String] =
    Company.ceo(Employee.emailAddress)
}
```

Now you can work with the CEO's email contact through the company:

```scala
val techCorp = Company("TechCorp", Employee("Alice", ContactInfo.Email("alice@tech.com")))
val retailCo = Company("RetailCo", Employee("Bob", ContactInfo.Phone("555-9999")))

// Get the CEO's email contact
Company.ceoEmailContact.getOption(techCorp)
// => Some(Email("alice@tech.com"))

Company.ceoEmailContact.getOption(retailCo)
// => None (Bob's contact is a Phone)

// Get the CEO's email address string
Company.ceoEmailAddress.getOption(techCorp)
// => Some("alice@tech.com")

// Update the CEO's email address
Company.ceoEmailAddress.replaceOption(techCorp, "ceo@tech.com")
// => Some(Company("TechCorp", Employee("Alice", Email("ceo@tech.com"))))

Company.ceoEmailAddress.replaceOption(retailCo, "bob@retail.com")
// => None (cannot replace because Bob does not have an Email contact)
```

This composition pattern is powerful for navigating through complex nested structures where some paths may not be valid for all values.

With the `optic` macro, you can express the entire path in a single selector expression:

```scala

case class Company(name: String, ceo: Employee)
object Company extends CompanionOptics[Company] {
  implicit val schema: Schema[Company] = Schema.derived

  // Simple field lens
  val ceo: Lens[Company, Employee] = optic(_.ceo)

  // Compose through multiple levels using path syntax
  // Lens (ceo) + Lens (contact) + Prism (when[Email]) = Optional
  val ceoEmailContact: Optional[Company, ContactInfo.Email] =
    optic(_.ceo.contact.when[ContactInfo.Email])

  // Go even deeper to the email address string
  val ceoEmailAddress: Optional[Company, String] =
    optic(_.ceo.contact.when[ContactInfo.Email].address)
    
  // Similarly for phone number
  val ceoPhoneNumber: Optional[Company, String] =
    optic(_.ceo.contact.when[ContactInfo.Phone].number)
}
```

The macro automatically determines the correct output type based on the composition rules: since the path includes a `.when[Email]` prism selector, the result is an `Optional`.

#### Composing a Lens with a Traversal

When you compose a `Lens` with a `Traversal`, the result is a `Traversal`. This combination allows you to:

- First zoom into a specific field of a record (via the `Lens`)
- Then iterate over all elements in a collection at that field (via the `Traversal`)

If you have a record containing a collection field and you want to operate on all elements of that collection, you can use this composition.

For example, let's create a `Department` that has a list of employees, and we want to access all employee names:

```scala
case class Department(name: String, employees: List[Employee])
object Department {
  implicit val schema: Schema[Department] = Schema.derived[Department]

  // Lens to focus on the employees list
  val employees: Lens[Department, List[Employee]] =
    Lens[Department, List[Employee]](
      Schema[Department].reflect.asRecord.get,
      Schema[List[Employee]].reflect.asTerm("employees")
    )

  // Traversal to iterate over all elements in the list
  val eachEmployee: Traversal[List[Employee], Employee] =
    Traversal.listValues(Schema[Employee].reflect)

  // Compose Lens with Traversal
  // This focuses on all employees in the department
  val allEmployees: Traversal[Department, Employee] =
    Department.employees(Department.eachEmployee)
}
```

With this `Traversal`, you can fold over all employees or modify them:

```scala
val engineering = Department(
  "Engineering",
  List(
    Employee("Alice", ContactInfo.Email("alice@company.com")),
    Employee("Bob", ContactInfo.Phone("555-1234")),
    Employee("Charlie", ContactInfo.Email("charlie@company.com"))
  )
)

// Fold to collect all employee names
Department.allEmployees.fold(engineering)(List.empty[String], (acc, emp) => acc :+ emp.name)
// => List("Alice", "Bob", "Charlie")

// Modify all employees (e.g., update their contact to NoContact)
Department.allEmployees.modify(engineering, emp => emp.copy(contact = ContactInfo.NoContact))
// => Department("Engineering", List(
//      Employee("Alice", NoContact),
//      Employee("Bob", NoContact),
//      Employee("Charlie", NoContact)
//    ))
```

You can chain further to go even deeper. For example, to access all employee names in a department:

```scala
object Employee {
  val name: Lens[Employee, String] =
    Lens[Employee, String](
      Schema[Employee].reflect.asRecord.get,
      Schema[String].reflect.asTerm("name")
    )
}

object Department {
  // Chain: Department -> employees (Lens) -> each (Traversal) -> name (Lens)
  // Result type: Traversal[Department, String]
  val allEmployeeNames: Traversal[Department, String] =
    Department.allEmployees(Employee.name)
}

// Fold to get all names
Department.allEmployeeNames.fold(engineering)(List.empty[String], (acc, name) => acc :+ name)
// => List("Alice", "Bob", "Charlie")

// Modify all names (e.g., convert them to uppercase)
Department.allEmployeeNames.modify(engineering, _.toUpperCase)
// => Department("Engineering", List(
//      Employee("ALICE", ...),
//      Employee("BOB", ...),
//      Employee("CHARLIE", ...)
//    ))
```

You can even compose a `Traversal` with a `Prism` to filter elements. For example, to retrieve only the email addresses from employees who have email contacts:

```scala
object Department {
  // Chain: Department -> allEmployees (Traversal) -> contactEmail (Optional)
  // Traversal + Optional = Traversal
  val allEmailContacts: Traversal[Department, ContactInfo.Email] =
    Department.allEmployees(Employee.contactEmail)

  // Further chain to get email address strings
  val allEmailAddresses: Traversal[Department, String] =
    Department.allEmailContacts(ContactInfo.Email.address)
}

// This only folds over employees who have Email contacts
Department.allEmailAddresses.fold(engineering)(List.empty[String], (acc, addr) => acc :+ addr)
// => List("alice@company.com", "charlie@company.com")
// Note: Bob is skipped because he has a Phone contact
```

This example demonstrates the power of optics composition: you can build complex data access paths by combining simple, reusable building blocks, and the type system ensures the resulting optic has the correct semantics (always succeeding, potentially failing, or iterating over multiple values).

The `optic` macro supports collection traversal using the `.each` syntax. This allows you to express traversals over lists, vectors, and other sequences in a concise path expression:

```scala

case class Department(name: String, employees: List[Employee])
object Department extends CompanionOptics[Department] {
  implicit val schema: Schema[Department] = Schema.derived

  // Lens to the employees list
  val employees: Lens[Department, List[Employee]] = optic(_.employees)

  // Traversal over all employees using .each syntax
  val allEmployees: Traversal[Department, Employee] = optic(_.employees.each)

  // Chain deeper: Lens + Traversal + Lens = Traversal
  val allEmployeeNames: Traversal[Department, String] = optic(_.employees.each.name)
  
  // Lens + Traversal + Lens + Prism = Traversal
  val allEmailContacts: Traversal[Department, ContactInfo.Email] = 
    optic(_.employees.each.contact.when[ContactInfo.Email])

  // Go even deeper to get email address strings
  val allEmailAddresses: Traversal[Department, String] = 
    optic(_.employees.each.contact.when[ContactInfo.Email].address)
}
```

The path `_.employees.each.contact.when[Email].address` composes:
1. `employees` — a `Lens` to the `List`
2. `.each` — a `Traversal` over list elements
3. `contact` — a `Lens` to `ContactInfo`
4. `.when[Email]` — a `Prism` to the `Email` case
5. `address` — a `Lens` to the `String`

The result is a `Traversal[Department, String]` that focuses on all email addresses in the department.

For maps, the macro provides `.eachKey` and `.eachValue` for traversing keys and values, respectively:

```scala

case class Inventory(items: Map[String, Int])
object Inventory extends CompanionOptics[Inventory] {
  implicit val schema: Schema[Inventory] = Schema.derived

  // Lens to the items map
  val items: Lens[Inventory, Map[String, Int]] = optic(_.items)

  // Traversal over all keys
  val allItemNames: Traversal[Inventory, String] = optic(_.items.eachKey)
  
  // Traversal over all values
  val allQuantities: Traversal[Inventory, Int] = optic(_.items.eachValue)
}
```
