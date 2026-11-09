# JSON AST

> The JSON AST is a tree of values that represents any JSON document, using the `zio.json.ast.Json` type.
> Most code decodes JSON straight into domain types, but the AST is useful when the shape of the document is not known in advance, when you need to edit a document, or when you want to combine several documents.
> For navigating the AST with cursors, see [Cursors](./decoding.md#cursors) in the decoding guide.

## The Json Type

`Json` is a sealed type with six cases: `Json.Obj`, `Json.Arr`, `Json.Str`, `Json.Num`, `Json.Bool`, and `Json.Null`.
Objects keep their fields in insertion order as a `Chunk` of key-value pairs.

The cases can be constructed directly:

```scala
import zio.json._
import zio.json.ast.{ Json, JsonCursor }

val user: Json = Json.Obj(
  "name"   -> Json.Str("Alice"),
  "age"    -> Json.Num(30),
  "admin"  -> Json.Bool(false),
  "tags"   -> Json.Arr(Json.Str("ops"), Json.Str("dev")),
  "avatar" -> Json.Null
)
// user: Json = Obj(
//   fields = IndexedSeq(
//     ("name", Str(value = "Alice")),
//     ("age", Num(value = 30)),
//     ("admin", Bool(value = false)),
//     ("tags", Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))),
//     ("avatar", null)
//   )
// )
```

`Json.Num` has constructors for `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal` and `java.math.BigInteger`, so numbers keep their precision.

## Converting Between Types and the AST

Every `JsonEncoder` has a `toJsonAST` method that returns `Either[String, Json]`, and every `JsonDecoder` has a `fromJsonAST` method that goes from a `Json` value to a domain type. The `Json#as` method is the shortcut for decoding:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

To encode a value into the AST, call `toJsonAST` on the value:

```scala
Person("Bob", 41).toJsonAST
// res0: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(("name", Str(value = "Bob")), ("age", Num(value = 41)))
//   )
// )
```

To decode an AST into a domain type, call `as` on the `Json` value:

```scala
user.as[Person]
// res1: Either[String, Person] = Right(
//   value = Person(name = "Alice", age = 30)
// )
```

Decoding with `as` fails when the fields do not match, which lets you check the shape of a document:

```scala
Json.Obj("name" -> Json.Str("Carol")).as[Person]
// res2: Either[String, Person] = Left(value = ".age(missing)")
```

## Reading Values

The `Json#get` method takes a `JsonCursor` and returns the node at that location, with its type refined by the cursor. The `Obj#get` method reads one field by name, and `Obj#contains` checks whether a field exists:

```scala
user.asObject.map(_.get("name"))
// res3: Option[Option[Json]] = Some(
//   value = Some(value = Str(value = "Alice"))
// )
```

Checking for a field that is not present returns `false` inside an `Option`:

```scala
user.asObject.map(_.contains("nickname"))
// res4: Option[Boolean] = Some(value = false)
```

Each case has an `as*` method that returns an `Option` of the matching type, for example `asString`, `asNumber`, `asArray`, `asObject`, `asBoolean` and `asNull`:

```scala
user.get(JsonCursor.field("age")).map(_.asNumber).toOption.flatten
// res5: Option[Json.Num] = Some(value = Num(value = 30))
```

## Transforming Values

The `mapXxx` methods change one kind of node without touching the rest. For example, `mapObjectKeys` renames every key in an object:

```scala
user.mapObjectKeys(_.toUpperCase)
// res6: Json = Obj(
//   fields = IndexedSeq(
//     ("NAME", Str(value = "Alice")),
//     ("AGE", Num(value = 30)),
//     ("ADMIN", Bool(value = false)),
//     ("TAGS", Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))),
//     ("AVATAR", null)
//   )
// )
```

`mapObjectValues` and `mapArrayValues` apply a function to each direct child:

```scala
user.mapObjectValues {
  case Json.Num(value) => Json.Num(value.add(java.math.BigDecimal.ONE))
  case other           => other
}
// res7: Json = Obj(
//   fields = IndexedSeq(
//     ("name", Str(value = "Alice")),
//     ("age", Num(value = 31)),
//     ("admin", Bool(value = false)),
//     ("tags", Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))),
//     ("avatar", null)
//   )
// )
```

The `transformDown` method walks the whole tree from the top and applies a function to every node. The `transformUp` method works from the bottom, so the function sees each node after its children were transformed.
Use the `transformDownSome` and `transformUpSome` variants when the function only applies to some nodes. The following replaces every string with its upper-case form and leaves other values unchanged:

```scala
user.transformDownSome { case Json.Str(value) => Json.Str(value.toUpperCase) }
// res8: Json = Obj(
//   fields = IndexedSeq(
//     ("name", Str(value = "ALICE")),
//     ("age", Num(value = 30)),
//     ("admin", Bool(value = false)),
//     ("tags", Arr(elements = IndexedSeq(Str(value = "OPS"), Str(value = "DEV")))),
//     ("avatar", null)
//   )
// )
```

To change a single node located by a cursor, use `transformAt`. It returns an `Either` because the cursor may not match the document:

```scala
user.transformAt(JsonCursor.field("age").isNumber)(age => Json.Num(age.value.add(java.math.BigDecimal.TEN)))
// res9: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       ("name", Str(value = "Alice")),
//       ("age", Num(value = 40)),
//       ("admin", Bool(value = false)),
//       (
//         "tags",
//         Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))
//       ),
//       ("avatar", null)
//     )
//   )
// )
```

## Deleting and Relocating Values

The `delete` method removes the node at a cursor. The `relocate` method moves a node from one cursor to another, removing it from its old location:

```scala
user.delete(JsonCursor.field("avatar"))
// res10: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       ("name", Str(value = "Alice")),
//       ("age", Num(value = 30)),
//       ("admin", Bool(value = false)),
//       (
//         "tags",
//         Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))
//       )
//     )
//   )
// )
```

Relocating `tags` to `avatar` replaces the `null` value there, and removes `tags` from its original location:

```scala
user.relocate(JsonCursor.field("tags"), JsonCursor.field("avatar"))
// res11: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       ("name", Str(value = "Alice")),
//       ("age", Num(value = 30)),
//       ("admin", Bool(value = false)),
//       (
//         "avatar",
//         Arr(elements = IndexedSeq(Str(value = "ops"), Str(value = "dev")))
//       )
//     )
//   )
// )
```

## Combining Documents

The `merge` method combines two values. When both values are objects, the result contains the fields of both, and the right-hand side wins when a key appears on both sides. When both values are arrays, their elements are merged. For any other combination, the right-hand side replaces the left:

```scala
val defaults: Json = Json.Obj("admin" -> Json.Bool(false), "locale" -> Json.Str("en"))
// defaults: Json = Obj(
//   fields = IndexedSeq(
//     ("admin", Bool(value = false)),
//     ("locale", Str(value = "en"))
//   )
// )
val overrides: Json = Json.Obj("locale" -> Json.Str("de"))
// overrides: Json = Obj(fields = IndexedSeq(("locale", Str(value = "de"))))

defaults.merge(overrides)
// res12: Json = Obj(
//   fields = IndexedSeq(
//     ("admin", Bool(value = false)),
//     ("locale", Str(value = "de"))
//   )
// )
```

The `intersect` method keeps only the fields or elements that are present in both values. It returns an error for values that are not both objects or both arrays:

```scala
val left: Json  = Json.Obj("a" -> Json.Num(1), "b" -> Json.Num(2))
// left: Json = Obj(
//   fields = IndexedSeq(("a", Num(value = 1)), ("b", Num(value = 2)))
// )
val right: Json = Json.Obj("b" -> Json.Num(2), "c" -> Json.Num(3))
// right: Json = Obj(
//   fields = IndexedSeq(("b", Num(value = 2)), ("c", Num(value = 3)))
// )

left.intersect(right)
// res13: Either[String, Json] = Right(
//   value = Obj(fields = IndexedSeq(("b", Num(value = 2))))
// )
```

## Folding Over a Document

The `foldDown` and `foldUp` methods reduce the whole tree into one value. They visit every node, with `foldDownSome` and `foldUpSome` as the variants for partial functions. For example, to count the string values in a document:

```scala
user.foldDown(0) {
  case (count, Json.Str(_)) => count + 1
  case (count, _)           => count
}
// res14: Int = 3
```

## Rendering and Parsing

The `toJson` extension on `Json` renders the AST as a compact string, and `toJsonPretty` renders it with indentation. The `fromJson[Json]` method parses a string into an AST:

```scala
user.toJsonPretty
// res15: String = """{
//   "name" : "Alice",
//   "age" : 30,
//   "admin" : false,
//   "tags" : [
//     "ops",
//     "dev"
//   ],
//   "avatar" : null
// }"""
```

Parsing a string into the AST uses the same `fromJson` method as domain types, with `Json` as the type:

```scala
"""{"id": 7, "ok": true}""".fromJson[Json]
// res16: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(("id", Num(value = 7)), ("ok", Bool(value = true)))
//   )
// )
```

For encoding and decoding in streams, see [Streaming](./streaming.md). For YAML, the AST is the intermediate form, as described in [YAML](./yaml.md).
