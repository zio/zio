# Read and Edit JSON of Unknown Shape

> Parse JSON you have no case class for into the Json AST, read nested values with cursors, and edit, move, merge and print it.

Goal: read a few values from a JSON document you have no case class for, such as a webhook payload, and forward a changed copy of it without losing the fields you do not model.

Use this guide when decoding into case classes would force you to model every level of nesting, or would drop the fields your case classes do not declare when you encode the document again. The `Json` AST keeps the whole document, and a `JsonCursor` points at one node inside it.

Step 1 parses the document, and step 2 defines the cursors the other steps use. Steps 3 to 8 are separate options that each start from the parsed document, so use the ones you need. Step 9 chains several edits into one function.

## What You Need

The AST and cursors are part of the core `zio-json` module:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

All code on this page uses these imports:

```scala
import zio.json._
import zio.json.ast.{ Json, JsonCursor }
```

The [JSON AST](../reference/json-ast.md) reference page lists every method, case and error message in detail.

## 1. Parse the Payload into `Json`

The example payload is an `order.paid` event from a payment provider. The order is nested two levels down, and `metadata` holds information we do not want to forward:

```scala
val payload: String =
  """{
    |  "id": "evt_1001",
    |  "type": "order.paid",
    |  "data": {
    |    "order": {
    |      "id": 42,
    |      "customer": { "name": "Ada", "email": "ada@example.com" },
    |      "items": [
    |        { "sku": "BOOK-1", "qty": 2, "price": 12.50 },
    |        { "sku": "PEN-7", "qty": 1, "price": 3.00 }
    |      ],
    |      "total": 28.00
    |    }
    |  },
    |  "metadata": { "source": "web", "debug": true }
    |}""".stripMargin
```

Decode it with `Json` as the target type. This accepts any valid JSON and returns `Either[String, Json]`, with a `Left` only when the text is not valid JSON:

```scala
val parsed: Either[String, Json] = payload.fromJson[Json]
```

To keep the next steps short, they work on the value taken out of the `Either`. In real code, stay inside the `Either`, as step 9 does:

```scala
val event: Json = parsed.fold(error => sys.error(error), identity)
```

## 2. Build Cursors for the Values You Need

A cursor is a path into a document. `JsonCursor#field` goes into a field of an object, `JsonCursor#element` goes into an element of an array, and the type steps (`JsonCursor#isObject`, `JsonCursor#isArray`, `JsonCursor#isString`, `JsonCursor#isNumber`, `JsonCursor#isBool` and `JsonCursor#isNull`) check the type of the current node. The `JsonCursor` companion has the same methods to start a path. Put `JsonCursor#isObject` before each deeper field step, and `JsonCursor#isArray` before an element step. Define the paths once and reuse them:

```scala
val order    = JsonCursor.field("data").isObject.field("order").isObject
val customer = order.field("customer").isObject
val email    = customer.field("email").isString
val items    = order.field("items").isArray
val total    = order.field("total").isNumber
```

Without the `JsonCursor#isObject` step, the path does not compile, because `JsonCursor#field` needs a cursor that ends on a `Json.Obj`:

```scala
JsonCursor.field("data").field("order")
// error: Cannot prove that zio.json.ast.Json <:< zio.json.ast.Json.Obj.
```

`Json#get` follows a cursor and returns the node it ends on. A cursor that ends with a type step returns that case of `Json`, so with `JsonCursor#isString` you get a `Json.Str`, and `Json.Str#value` gives the `String`. For a number, `Json.Num#value` is a `java.math.BigDecimal`. To read the event type, the customer's email and the order total from `event` (the payload parsed in step 1):

```scala
event.get(JsonCursor.field("type").isString).map(_.value)
// res2: Either[String, String] = Right(value = "order.paid")

event.get(email).map(_.value)
// res3: Either[String, String] = Right(value = "ada@example.com")

event.get(total).map(_.value)
// res4: Either[String, java.math.BigDecimal] = Right(value = 28.00)
```

To read an element of an array, add `JsonCursor#element` with a zero-based index after a `JsonCursor#isArray` step:

```scala
event.get(items.element(0).isObject.field("sku").isString).map(_.value)
// res5: Either[String, String] = Right(value = "BOOK-1")
```

The [Cursors](../reference/json-ast.md#cursors) reference section covers the cursor type and its other ways to build a path.

## 3. Handle Missing Fields and Wrong Types

`Json#get` returns a `Left` when the document does not match the cursor. Here `event` is the parsed payload from step 1, and `order` and `items` are the cursors to `data.order` and `data.order.items` from step 2. A field that does not exist gives `No such field`:

```scala
event.get(order.field("coupon"))
// res6: Either[String, Json] = Left(value = "No such field: 'coupon'")
```

A type step that does not match gives `expected <type>`. The order `id` is a number, not a string:

```scala
event.get(order.field("id").isString)
// res7: Either[String, Json.Str] = Left(value = "expected string")
```

An index past the end of an array has its own message:

```scala
event.get(items.element(5))
// res8: Either[String, Json] = Left(value = "The array does not have index 5")
```

The messages name only the last step that failed, not the whole path. When you read several values, add the path to the error yourself, for example with `left.map(error => s"data.order.id: $error")`. The full list of messages is in [Cursor Errors](../reference/json-ast.md#cursor-errors).

## 4. Decode Part of the Document into a Case Class

When one part of the document has a stable shape, decode just that part. Define case classes with a `JsonDecoder` for the parts you need:

```scala
final case class Customer(name: String, email: String)

object Customer {
  implicit val decoder: JsonDecoder[Customer] = DeriveJsonDecoder.gen[Customer]
}

final case class LineItem(sku: String, qty: Int, price: BigDecimal)

object LineItem {
  implicit val decoder: JsonDecoder[LineItem] = DeriveJsonDecoder.gen[LineItem]
}
```

Get the sub-tree with a cursor, then call `Json#as`, which uses the `JsonDecoder` of the target type. With `customer` and `items` (the cursors from step 2) and `event` (the payload from step 1), the customer object and the items array decode into domain types, while the rest of the payload stays untyped:

```scala
event.get(customer).flatMap(_.as[Customer])
// res9: Either[String, Customer] = Right(
//   value = Customer(name = "Ada", email = "ada@example.com")
// )

event.get(items).flatMap(_.as[List[LineItem]])
// res10: Either[String, List[LineItem]] = Right(
//   value = List(
//     LineItem(sku = "BOOK-1", qty = 2, price = 12.50),
//     LineItem(sku = "PEN-7", qty = 1, price = 3.00)
//   )
// )
```

If the sub-tree does not match, `Json#as` returns the usual decoder error, with a path that starts at the sub-tree, not at the root of the document. Decoding the order object as a `Customer` fails on the first missing field:

```scala
event.get(order).flatMap(_.as[Customer])
// res11: Either[String, Customer] = Left(value = ".name(missing)")
```

## 5. Replace a Value

`Json#transformAt` replaces the node at a cursor with the result of a function. The function receives the node with the type the cursor ends on, so with `email` (the cursor to `data.order.customer.email` from step 2) it receives a `Json.Str`. To mask the customer's email in `event`, the payload from step 1:

```scala
val masked: Either[String, Json] = event.transformAt(email)(_ => Json.Str("***"))
```

The email in the new document is masked:

```scala
masked.flatMap(_.get(email)).map(_.value)
// res12: Either[String, String] = Right(value = "***")
```

:::note
`Json` values are immutable: `Json#transformAt` and the other editing methods return a new document and leave `event` unchanged. They return an `Either` because the cursor may not match, with the same messages as `Json#get`.
:::

## 6. Delete a Field

`Json#delete` removes the node at a cursor. To drop the `debug` flag from `metadata` in `event`, the payload from step 1:

```scala
println(
  event
    .delete(JsonCursor.field("metadata").isObject.field("debug"))
    .flatMap(_.get(JsonCursor.field("metadata")))
)
// Right({"source":"web"})
```

:::warning
`Json#delete` does not check a type step that comes after the last field or element step. A cursor ending in `field("debug").isString` still deletes `debug`, although it is a boolean. Type steps earlier in the path are checked. If the type matters, check it with `Json#get` before deleting.
:::

## 7. Move a Field to a New Key

`Json#relocate(from, to)` copies the node at `from` over the node at `to`, then deletes the node at `from`. The target must already exist. In this example, the downstream service expects `customer` at the top level of `event` (the payload from step 1), not inside `data.order`, and relocating `customer` (the cursor from step 2) to a key that is not in the document fails with `No such field`:

```scala
event.relocate(customer, JsonCursor.field("customer"))
// res14: Either[String, Json] = Left(value = "No such field: 'customer'")
```

To move a node to a new key, add a placeholder first, then relocate over it. `JsonCursor.isObject` points at the root object, so `Json#transformAt` with it can add a field through `Json.Obj#add`:

```scala
val moved: Either[String, Json] =
  event
    .transformAt(JsonCursor.isObject)(_.add("customer", Json.Null))
    .flatMap(_.relocate(customer, JsonCursor.field("customer")))
```

The customer object is now at the top level, and `data.order` no longer contains it:

```scala
println(moved.flatMap(_.get(JsonCursor.field("customer"))))
// Right({"name":"Ada","email":"ada@example.com"})

moved.flatMap(_.get(order)).map(_.keys)
// res16: Either[String, zio.Chunk[String]] = Right(
//   value = IndexedSeq("id", "items", "total")
// )
```

## 8. Fill In Missing Defaults with `Json#merge`

`Json#merge` combines two documents: objects are merged key by key, recursively, and for two scalar values the right-hand side wins. To add only the values the event lacks, write the defaults as a `Json` document:

```scala
val defaults: Json = Json.Obj(
  "metadata" -> Json.Obj("source" -> Json.Str("unknown"), "retries" -> Json.Num(0)),
  "currency" -> Json.Str("EUR")
)
```

Then merge `event` (the payload from step 1) into the defaults, with the event on the right. The event's `source` wins over the default, while `retries` and `currency` come from the defaults:

```scala
println(defaults.merge(event).get(JsonCursor.field("metadata")))
// Right({"source":"web","retries":0,"debug":true})

defaults.merge(event).get(JsonCursor.field("currency").isString).map(_.value)
// res18: Either[String, String] = Right(value = "EUR")
```

The keys of the left-hand document come first in the result. Put the defaults on the right (`event.merge(defaults)`) only when the defaults should overwrite the event. [Combining Documents](../reference/json-ast.md#combining-documents) describes how arrays and other pairs of values merge.

## 9. Chain the Edits in One Function

Every editing method returns an `Either`, so a for-comprehension chains them and stops at the first error. This `clean` function parses a raw payload and applies the edits of steps 5 to 8, using the cursors `email` and `customer` from step 2 and `defaults` from step 8:

```scala
def clean(raw: String): Either[String, Json] =
  for {
    json      <- raw.fromJson[Json]
    masked    <- json.transformAt(email)(_ => Json.Str("***"))
    noDebug   <- masked.delete(JsonCursor.field("metadata").isObject.field("debug"))
    withSlot  <- noDebug.transformAt(JsonCursor.isObject)(_.add("customer", Json.Null))
    relocated <- withSlot.relocate(customer, JsonCursor.field("customer"))
  } yield defaults.merge(relocated)
```

`toJsonPretty` renders a `Json` value with indentation, and `toJson` renders it on one line. To print the cleaned payload:

```scala
clean(payload).foreach(json => println(json.toJsonPretty))
// {
//   "metadata" : {
//     "source" : "web",
//     "retries" : 0
//   },
//   "currency" : "EUR",
//   "id" : "evt_1001",
//   "type" : "order.paid",
//   "data" : {
//     "order" : {
//       "id" : 42,
//       "items" : [
//         {
//           "sku" : "BOOK-1",
//           "qty" : 2,
//           "price" : 12.50
//         },
//         {
//           "sku" : "PEN-7",
//           "qty" : 1,
//           "price" : 3.00
//         }
//       ],
//       "total" : 28.00
//     }
//   },
//   "customer" : {
//     "name" : "Ada",
//     "email" : "***"
//   }
// }
```

A payload that does not match fails with the error of the first step that did not match. This one has no `customer`:

```scala
clean("""{"data": {"order": {"id": 7}}, "metadata": {}}""")
// res20: Either[String, Json] = Left(value = "No such field: 'customer'")
```

## See Also

- [JSON AST](../reference/json-ast.md) for the other ways to read and change a document: the `as*` methods, the `Json.Obj` field methods, `transformDown` and `transformUp`, `foldDown` and `foldUp`, `intersect`, and composing cursors with `>>>`.
- [Embedding Raw JSON in Case Classes](../reference/json-ast.md#embedding-raw-json-in-case-classes) to keep a free-form part of a typed model in a field of type `Json` or `Json.Obj`.
- [Load a YAML Configuration File](load-yaml-config.md) to read YAML into the same `Json` type.
