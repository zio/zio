# JSON AST

> Reference for the Json AST: its six cases, equality, object operations, cursors, transforms, merging and folding.

The JSON AST is a tree of values that represents any JSON document, using the `zio.json.ast.Json` type. Most code decodes JSON straight into domain types, but the AST is useful when the shape of the document is not known in advance, when you need to edit a document, when you want to combine several documents, or when a case class has a field that holds arbitrary JSON. For navigating the AST with cursors, see [Cursors](#cursors).

`Json` is a sealed class with one case class per kind of JSON value:

```scala
sealed abstract class Json

object Json {
  final case class Obj(fields: Chunk[(String, Json)]) extends Json
  final case class Arr(elements: Chunk[Json])         extends Json
  final case class Str(value: String)                 extends Json
  final case class Num(value: java.math.BigDecimal)   extends Json
  final case class Bool(value: Boolean)               extends Json
  case object Null                                    extends Json
  type Null = Null.type
}
```

:::warning
`Json.Num` holds a `java.math.BigDecimal` of any size. Converting it yourself with the standard library (for example `num.value.toBigInteger`) can be a denial-of-service vector: `new java.math.BigDecimal("1e214748364").toBigInteger` tries to allocate gigabytes of heap. Decode numbers with `Json#as[Int]`, `Json#as[BigInt]` and so on, whose decoders check the size first. See [Security](../explanation/security.md#death-by-a-thousand-zeros).
:::

## The Json Type

`Json` has six cases: `Json.Obj`, `Json.Arr`, `Json.Str`, `Json.Num`, `Json.Bool`, and `Json.Null`. Objects keep their fields in insertion order as a `Chunk` of key-value pairs. This section shows how to build values and how they compare.

### Creating Values

The cases can be constructed directly:

```scala
import zio.json._
import zio.json.ast.{ Json, JsonCursor, JsonType }

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

`Json.Num` wraps a `java.math.BigDecimal`, and `Json.Num.apply` has overloads for `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal` and `java.math.BigInteger`, so numbers keep their precision.

The companions have a few more constructors and constants:

```scala
object Json {
  def apply(fields: (String, Json)*): Json // an object, typed as Json

  object Obj {
    val empty: Obj
    def apply(fields: (String, Json)*): Obj
    def apply(chunk: Chunk[(String, Json)]): Obj
    def apply(key: String, value: Json): Obj
  }

  object Arr {
    val empty: Arr
    def apply(elements: Json*): Arr
    def apply(chunk: Chunk[Json]): Arr
  }

  object Bool {
    val True: Bool
    val False: Bool
  }
}
```

`Json.apply` builds an object but returns it as `Json`, which helps when a `val` should have the general type. `Json#widen` does the same for any case, turning a `Json.Obj` into a `Json` without a type ascription:

```scala
val settings = Json("theme" -> Json.Str("dark"))
// settings: Json = Obj(fields = IndexedSeq(("theme", Str(value = "dark"))))

val single = Json.Obj("retries", Json.Num(3))
// single: Json.Obj = Obj(fields = IndexedSeq(("retries", Num(value = 3))))

println(single.widen)
// {"retries":3}
```

### Equality

`Json` overrides `Json#equals` and `Json#hashCode`. Two objects are equal when they have the same keys with equal values, in any order. Arrays compare element by element, in order:

```scala
Json.Obj("a" -> Json.Num(1), "b" -> Json.Num(2)) == Json.Obj("b" -> Json.Num(2), "a" -> Json.Num(1))
// res2: Boolean = true

Json.Arr(Json.Num(1), Json.Num(2)) == Json.Arr(Json.Num(2), Json.Num(1))
// res3: Boolean = false
```

Numbers compare with `java.math.BigDecimal#equals`, which also compares the scale. So `1` and `1.0` are different values, whether you build them or parse them:

```scala
Json.Num(1) == Json.Num(1.0)
// res4: Boolean = false

"1".fromJson[Json] == "1.0".fromJson[Json]
// res5: Boolean = false
```

Keep this in mind when you compare documents in tests, and when you use `Json#intersect`, which relies on the same equality.

## Converting Between Types and the AST

`JsonEncoder#toJsonAST` takes a value and returns `Either[String, Json]`, and the `value.toJsonAST` extension calls it with the implicit encoder. `JsonDecoder#fromJsonAST` goes from a `Json` value to a domain type. The `Json#as` method is the shortcut for decoding:

```scala
final case class Person(name: String, age: Int)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

To encode a value into the AST, call `toJsonAST` on the value:

```scala
Person("Bob", 41).toJsonAST
// res6: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(("name", Str(value = "Bob")), ("age", Num(value = 41)))
//   )
// )
```

To decode an AST into a domain type, call `as` on the `Json` value:

```scala
user.as[Person]
// res7: Either[String, Person] = Right(
//   value = Person(name = "Alice", age = 30)
// )
```

Decoding with `as` fails when the fields do not match, which lets you check the shape of a document:

```scala
Json.Obj("name" -> Json.Str("Carol")).as[Person]
// res8: Either[String, Person] = Left(value = ".age(missing)")
```

## Embedding Raw JSON in Case Classes

A field of type `Json` holds any JSON value, which is the main reason the AST exists: part of a business object can stay free-form while the rest is typed. `Json` and each of its cases have their own implicit `JsonEncoder`, `JsonDecoder` and `JsonCodec` (`Json.codec`, `Json.Obj.codec`, `Json.Arr.codec`, `Json.Str.codec`, `Json.Num.codec`, `Json.Bool.codec` and `Json.Null.codec`), so derivation finds them for fields of those types.

A field typed as one case, such as `Json.Obj`, accepts only that kind of value:

```scala
final case class Event(kind: String, payload: Json.Obj)

object Event {
  implicit val codec: JsonCodec[Event] = DeriveJsonCodec.gen[Event]
}
```

The payload keeps whatever object was sent:

```scala
"""{"kind":"click","payload":{"x":1,"y":2}}""".fromJson[Event]
// res9: Either[String, Event] = Right(
//   value = Event(
//     kind = "click",
//     payload = Obj(
//       fields = IndexedSeq(("x", Num(value = 1)), ("y", Num(value = 2)))
//     )
//   )
// )
```

When the payload is not an object, decoding from text fails with the usual parser message:

```scala
"""{"kind":"click","payload":1}""".fromJson[Event]
// res10: Either[String, Event] = Left(
//   value = ".payload(expected '{' got '1')"
// )
```

Decoding from a `Json` value (with `Json#as` or `JsonDecoder#fromJsonAST`) reports the mismatch with the message of the case decoder:

```scala
Json.Obj("kind" -> Json.Str("click"), "payload" -> Json.Num(1)).as[Event]
// res11: Either[String, Event] = Left(value = ".payload(Not an object)")
```

The case decoders use these messages when the value has a different type:

| Decoder          | Message              |
|------------------|----------------------|
| `Json.Obj`       | `Not an object`      |
| `Json.Arr`       | `Not an array`       |
| `Json.Str`       | `Not a string value` |
| `Json.Num`       | `Not a number`       |
| `Json.Bool`      | `Not a bool value`   |
| `Json.Null.type` | `Not null`           |

The `Json` decoder itself accepts every value.

## Reading Values

The simplest way to read a value is to test which case it is. `Json` has six `as*` methods (`asString`, `asNumber`, `asArray`, `asObject`, `asBoolean`, `asNull`). Each returns `Some` only when the value is that case: `asString` gives a `String`, `asNumber` a `Json.Num`, `asArray` a `Chunk[Json]`, `asObject` a `Json.Obj`, `asBoolean` a `Boolean`, and `asNull` a `Unit`:

```scala
user.asObject.map(_.keys)
// res12: Option[zio.Chunk[String]] = Some(
//   value = IndexedSeq("name", "age", "admin", "tags", "avatar")
// )

user.asString
// res13: Option[String] = None
```

The `Obj#get` method reads one field by name, and `Obj#contains` checks whether a field exists:

```scala
println(user.asObject.map(_.get("name")))
// Some(Some("Alice"))
```

Checking for a field that is not present returns `false` inside an `Option`:

```scala
user.asObject.map(_.contains("nickname"))
// res15: Option[Boolean] = Some(value = false)
```

The `Json#get` method takes a [cursor](#cursors) and returns the node at that location as `Either[String, A]`, where `A` is the node type the cursor refines to. A cursor that ends in a type filter such as `isNumber` returns that case directly:

```scala
println(user.get(JsonCursor.field("age").isNumber).map(_.value))
// Right(30)
```

`Json#arrayOrObject(or, jsonArray, jsonObject)` handles the two container cases with one function each and returns `or` for every other case:

```scala
user.arrayOrObject("scalar", elements => s"array of ${elements.size}", obj => s"object with ${obj.keys.size} keys")
// res17: String = "object with 5 keys"
```

## Working with Objects

`Json.Obj` has its own methods for reading and editing fields. They return a `Json.Obj`, so they chain:

```scala
final case class Obj(fields: Chunk[(String, Json)]) extends Json {
  def contains(key: String): Boolean
  def get(key: String): Option[Json]
  def isEmpty: Boolean
  def nonEmpty: Boolean
  lazy val keys: Chunk[String]
  lazy val values: Chunk[Json]
  def toMap: Map[String, Json]
  def add(key: String, value: Json): Json.Obj
  def +:(entry: (String, Json)): Json.Obj
  def remove(key: String): Json.Obj
  def mapValues(f: Json => Json): Json.Obj
  def filter(pred: ((String, Json)) => Boolean): Json.Obj
  def filterKeys(pred: String => Boolean): Json.Obj
  def merge(that: Json.Obj): Json.Obj
}
```

`Json.Arr` has `isEmpty`, `nonEmpty` and a typed `merge(that: Json.Arr): Json.Arr`.

To inspect an object's fields:

```scala
val server = Json.Obj(
  "host"  -> Json.Str("localhost"),
  "tls"   -> Json.Obj("enabled" -> Json.Bool(false)),
  "debug" -> Json.Bool(true)
)
// server: Json.Obj = Obj(
//   fields = IndexedSeq(
//     ("host", Str(value = "localhost")),
//     ("tls", Obj(fields = IndexedSeq(("enabled", Bool(value = false))))),
//     ("debug", Bool(value = true))
//   )
// )

server.keys
// res18: zio.Chunk[String] = IndexedSeq("host", "tls", "debug")
server.values
// res19: zio.Chunk[Json] = IndexedSeq(
//   Str(value = "localhost"),
//   Obj(fields = IndexedSeq(("enabled", Bool(value = false)))),
//   Bool(value = true)
// )
server.toMap.get("host")
// res20: Option[Json] = Some(value = Str(value = "localhost"))
server.isEmpty
// res21: Boolean = false
Json.Obj.empty.nonEmpty
// res22: Boolean = false
```

To remove, filter and rewrite fields:

```scala
println(server.remove("debug"))
// {"host":"localhost","tls":{"enabled":false}}
println(server.filterKeys(_ != "tls"))
// {"host":"localhost","debug":true}
println(server.filter { case (_, value) => value.asBoolean.isDefined })
// {"debug":true}
println(server.mapValues(_ => Json.Null))
// {"host":null,"tls":null,"debug":null}
```

`Obj#add` and its operator form `+:` add a field. A new key goes at the end of the object, even with `+:`:

```scala
println(server.add("port", Json.Num(8443)))
// {"host":"localhost","tls":{"enabled":false},"debug":true,"port":8443}
println(("id" -> Json.Num(1)) +: server)
// {"host":"localhost","tls":{"enabled":false},"debug":true,"id":1}
```

:::warning
`Obj#add` is implemented as `merge(Json.Obj(key -> value))`. When the key already exists and the old and the new value are both objects or both arrays, they are merged as `Json#merge` does (objects deep-merged, arrays element by element) instead of the new value replacing the old one.
:::

Adding a `tls` object to `server` merges it into the existing `tls` object. To replace the object, remove the key first:

```scala
println(server.add("tls", Json.Obj("port" -> Json.Num(8443))))
// {"host":"localhost","tls":{"enabled":false,"port":8443},"debug":true}
println(server.remove("tls").add("tls", Json.Obj("port" -> Json.Num(8443))))
// {"host":"localhost","debug":true,"tls":{"port":8443}}
```

For other combinations of values, `Obj#add` replaces the existing value in place:

```scala
println(server.add("debug", Json.Bool(false)))
// {"host":"localhost","tls":{"enabled":false},"debug":false}
```

## Cursors

A cursor, `JsonCursor[-From, +To <: Json]`, is a path into a `Json` document: a sequence of steps that go down into a field, down into an array element, or check the type of the current node. `From` is the type of node the path starts from and `To` is the type of node it ends on. Cursors are usually more convenient than pattern matching on `Json` directly, and the editing methods `Json#transformAt`, `Json#delete` and `Json#relocate` take them too. The trait has four cases, one per kind of step:

```scala
sealed trait JsonCursor[-From, +To <: Json]

object JsonCursor {
  case object Identity                                                      extends JsonCursor[Json, Json]
  final case class DownField(parent: JsonCursor[_, Json.Obj], name: String) extends JsonCursor[Json.Obj, Json]
  final case class DownElement(parent: JsonCursor[_, Json.Arr], index: Int) extends JsonCursor[Json.Arr, Json]
  final case class FilterType[A <: Json](parent: JsonCursor[_, _ <: Json], jsonType: JsonType[A])
      extends JsonCursor[Json, A]
}
```

For a step-by-step guide to reading and editing a document with cursors, see [Read and Edit JSON of Unknown Shape](../how-to/edit-json-with-cursors.md).

### Building Cursors

The `JsonCursor` companion starts a path:

```scala
object JsonCursor {
  val identity: JsonCursor[Json, Json]
  def field(name: String): JsonCursor[Json.Obj, Json]
  def element(index: Int): JsonCursor[Json.Arr, Json]
  def filter[A <: Json](jsonType: JsonType[A]): JsonCursor[Json, A]
  val isObject: JsonCursor[Json, Json.Obj]
  val isArray: JsonCursor[Json, Json.Arr]
  val isString: JsonCursor[Json, Json.Str]
  val isNumber: JsonCursor[Json, Json.Num]
  val isBool: JsonCursor[Json, Json.Bool]
  val isNull: JsonCursor[Json, Json.Null]
}
```

`JsonCursor.identity` points at the root. `JsonCursor.field` and `JsonCursor.element` check that the root is an object or an array, and then step into it. The `is*` values and `JsonCursor.filter` only check the type of the root.

The cursor instance has the same steps, to extend a path:

```scala
sealed trait JsonCursor[-From, +To <: Json] {
  def field(field: String)(implicit ev: To <:< Json.Obj): JsonCursor.DownField
  def element(index: Int)(implicit ev: To <:< Json.Arr): JsonCursor.DownElement
  def isObject: JsonCursor[Json, Json.Obj]
  def isArray: JsonCursor[Json, Json.Arr]
  def isString: JsonCursor[Json, Json.Str]
  def isNumber: JsonCursor[Json, Json.Num]
  def isBool: JsonCursor[Json, Json.Bool]
  def isNull: JsonCursor[Json, Json.Null]
  def filterType[B <: Json](jsonType: JsonType[B]): JsonCursor[Json, B]
  def >>>[Next <: Json](that: JsonCursor[To, Next]): JsonCursor[From, Next]
  def andThen[Next <: Json](that: JsonCursor[To, Next]): JsonCursor[From, Next]
}
```

`JsonCursor#field` compiles only when the cursor already ends on a `Json.Obj`, and `JsonCursor#element` only when it ends on a `Json.Arr`. A field step ends on a plain `Json`, so add a `JsonCursor#isObject` or `JsonCursor#isArray` step before going deeper:

```scala
val profile: Json = Json.Obj(
  "user" -> Json.Obj(
    "name"  -> Json.Str("Ada"),
    "langs" -> Json.Arr(Json.Str("scala"), Json.Str("ocaml"))
  ),
  "visits" -> Json.Num(12)
)
// profile: Json = Obj(
//   fields = IndexedSeq(
//     (
//       "user",
//       Obj(
//         fields = IndexedSeq(
//           ("name", Str(value = "Ada")),
//           (
//             "langs",
//             Arr(
//               elements = IndexedSeq(Str(value = "scala"), Str(value = "ocaml"))
//             )
//           )
//         )
//       )
//     ),
//     ("visits", Num(value = 12))
//   )
// )

println(profile.get(JsonCursor.field("user").isObject.field("name").isString))
// Right("Ada")
println(profile.get(JsonCursor.field("user").isObject.field("langs").isArray.element(1)))
// Right("ocaml")
```

`JsonCursor#>>>` and its alias `JsonCursor#andThen` append a second cursor to the first, so you can build reusable path pieces. `JsonCursor.identity` is the neutral element: appending it changes nothing, and reading it returns the whole document:

```scala
val userCursor = JsonCursor.field("user") >>> JsonCursor.isObject
// userCursor: JsonCursor[Json.Obj, Json.Obj] = FilterType(
//   parent = DownField(
//     parent = FilterType(parent = Identity, jsonType = Obj),
//     name = "user"
//   ),
//   jsonType = Obj
// )

println(profile.get(userCursor.andThen(JsonCursor.field("name"))))
// Right("Ada")

profile.get(JsonCursor.identity) == Right(profile)
// res35: Boolean = true
```

### Filtering by Type

`JsonType[A]` names one case of `Json`. It has six values, `JsonType.Obj`, `JsonType.Arr`, `JsonType.Str`, `JsonType.Num`, `JsonType.Bool` and `JsonType.Null`, and one method, `get(json: Json): Either[String, A]`, which returns the value as that case or an `expected ...` error. The `is*` steps are shortcuts for `JsonCursor#filterType` and `JsonCursor.filter` with one of these values:

```scala
println(profile.get(JsonCursor.field("visits").filterType(JsonType.Num)))
// Right(12)
println(profile.get(JsonCursor.filter(JsonType.Obj)).map(_.keys))
// Right(Chunk(user,visits))

JsonType.Str.get(Json.Num(1))
// res38: Either[String, Json.Str] = Left(value = "expected string")
```

### Cursor Errors

The methods that take a cursor (`Json#get`, `Json#transformAt`, `Json#delete` and `Json#relocate`) return a `Left` with one of these messages when the cursor does not match the document:

| Message                              | Cause                                                              |
|--------------------------------------|--------------------------------------------------------------------|
| `No such field: 'name'`              | A `field` step names a key that the object does not have           |
| `The array does not have index n`    | An `element` step goes past the end of the array                   |
| `expected object`, `expected array`  | A type check failed; `field` and `element` start with such a check |
| `expected string`, `expected number` | A type check failed                                                |
| `expected boolean`, `expected null`  | A type check failed                                                |

For example, reading a missing field, an element past the end, and a value of the wrong type:

```scala
profile.get(JsonCursor.field("email"))
// res39: Either[String, Json] = Left(value = "No such field: 'email'")

profile.get(JsonCursor.field("user").isObject.field("langs").isArray.element(5))
// res40: Either[String, Json] = Left(
//   value = "The array does not have index 5"
// )

profile.get(JsonCursor.field("visits").isString)
// res41: Either[String, Json.Str] = Left(value = "expected string")
```

## Transforming Values

The `mapXxx` methods change one kind of node without touching the rest. Each applies only to the value it is called on, never to nested values, and returns the value unchanged when it is a different case:

| Method             | Changes     | Function type                                  |
|--------------------|-------------|------------------------------------------------|
| `mapBoolean`       | `Json.Bool` | `Boolean => Boolean`                           |
| `mapNumber`        | `Json.Num`  | `java.math.BigDecimal => java.math.BigDecimal` |
| `mapString`        | `Json.Str`  | `String => String`                             |
| `mapArray`         | `Json.Arr`  | `Chunk[Json] => Chunk[Json]`                   |
| `mapArrayValues`   | `Json.Arr`  | `Json => Json`, applied to each element        |
| `mapObject`        | `Json.Obj`  | `Json.Obj => Json.Obj`                         |
| `mapObjectKeys`    | `Json.Obj`  | `String => String`, applied to each key        |
| `mapObjectValues`  | `Json.Obj`  | `Json => Json`, applied to each value          |
| `mapObjectEntries` | `Json.Obj`  | `((String, Json)) => (String, Json)`           |

For example, `mapObjectKeys` renames the top-level keys of an object; nested objects are not changed (use `transformDownSome` for that):

```scala
println(user.mapObjectKeys(_.toUpperCase))
// {"NAME":"Alice","AGE":30,"ADMIN":false,"TAGS":["ops","dev"],"AVATAR":null}
```

`mapObjectValues` and `mapArrayValues` apply a function to each direct child:

```scala
println(user.mapObjectValues {
  case Json.Num(value) => Json.Num(value.add(java.math.BigDecimal.ONE))
  case other           => other
})
// {"name":"Alice","age":31,"admin":false,"tags":["ops","dev"],"avatar":null}
```

The `Json#transformDown` method walks the whole tree from the top and applies a function to every node. The `Json#transformUp` method works from the bottom, so the function sees each node after its children were transformed. Use the `transformDownSome` and `transformUpSome` variants when the function only applies to some nodes. The following replaces every string with its upper-case form and leaves other values unchanged:

```scala
println(user.transformDownSome { case Json.Str(value) => Json.Str(value.toUpperCase) })
// {"name":"ALICE","age":30,"admin":false,"tags":["OPS","DEV"],"avatar":null}
```

To change a single node located by a cursor, use `Json#transformAt`. It returns an `Either` because the cursor may not match the document:

```scala
println(user.transformAt(JsonCursor.field("age").isNumber)(age => Json.Num(age.value.add(java.math.BigDecimal.TEN))))
// Right({"name":"Alice","age":40,"admin":false,"tags":["ops","dev"],"avatar":null})
```

## Deleting and Relocating Values

The `Json#delete` method removes the node at a cursor:

```scala
println(user.delete(JsonCursor.field("avatar")))
// Right({"name":"Alice","age":30,"admin":false,"tags":["ops","dev"]})
```

Deleting `JsonCursor.identity` deletes the whole document and returns `Right(Json.Null)`.

:::warning
`Json#delete` does not check a type filter that comes after the last `field` or `element` step. `Json.Obj("a" -> Json.Num(1)).delete(JsonCursor.field("a").isString)` returns `Right({})`: the number is removed although the cursor asks for a string. Type filters earlier in the path are checked. If the type matters, check it with `Json#get` before deleting.
:::

The `Json#relocate` method copies the node at the `from` cursor over the existing node at the `to` cursor, then deletes the `from` node. Relocating `tags` to `avatar` replaces the `null` value there, and removes `tags` from its original location:

```scala
println(user.relocate(JsonCursor.field("tags"), JsonCursor.field("avatar")))
// Right({"name":"Alice","age":30,"admin":false,"avatar":["ops","dev"]})
```

The `to` location must already exist. Relocating to a key that is not in the object fails:

```scala
user.relocate(JsonCursor.field("tags"), JsonCursor.field("roles"))
// res48: Either[String, Json] = Left(value = "No such field: 'roles'")
```

To move a value to a new key, read it with `Json.Obj#get`, add it with `Json.Obj#add`, and remove the old key:

```scala
println(user.asObject.map(obj => obj.get("tags").fold(obj)(tags => obj.remove("tags").add("roles", tags))))
// Some({"name":"Alice","age":30,"admin":false,"avatar":null,"roles":["ops","dev"]})
```

## Combining Documents

The `Json#merge` method combines two values:

- When both values are objects, the result contains the fields of both. A key that appears on both sides is merged recursively, so nested objects are merged, and for scalar values the right-hand side wins.
- When both values are arrays, elements at the same index are merged with each other, and the extra elements of the longer array are kept.
- For any other combination, the right-hand side replaces the left.

Merging a set of overrides into defaults keeps the keys that are not overridden:

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

println(defaults.merge(overrides))
// {"admin":false,"locale":"de"}
```

Merging two arrays works index by index:

```scala
println(Json.Arr(Json.Num(1), Json.Obj("a" -> Json.Num(1))).merge(Json.Arr(Json.Num(2), Json.Obj("b" -> Json.Num(2)), Json.Num(3))))
// [2,{"a":1,"b":2},3]
```

`Json.Obj#merge` and `Json.Arr#merge` are typed versions that return a `Json.Obj` and a `Json.Arr`.

The `Json#intersect` method keeps only the key-value pairs (for objects) or elements (for arrays) that are equal on both sides. It returns `Left("Non compatible types")` unless both values are objects or both are arrays. A key whose value differs between the two sides is dropped:

```scala
val left: Json  = Json.Obj("a" -> Json.Num(1), "b" -> Json.Num(2), "c" -> Json.Num(3))
// left: Json = Obj(
//   fields = IndexedSeq(
//     ("a", Num(value = 1)),
//     ("b", Num(value = 2)),
//     ("c", Num(value = 3))
//   )
// )
val right: Json = Json.Obj("a" -> Json.Num(9), "b" -> Json.Num(2))
// right: Json = Obj(
//   fields = IndexedSeq(("a", Num(value = 9)), ("b", Num(value = 2)))
// )

println(left.intersect(right))
// Right({"b":2})
println(left.intersect(Json.Arr()))
// Left(Non compatible types)
```

## Folding Over a Document

The `Json#foldDown` and `Json#foldUp` methods reduce the whole tree into one value, with `foldDownSome` and `foldUpSome` as the variants for partial functions. `foldDown` visits each node before its children (pre-order), and `foldUp` visits the children first (post-order). Both visit object fields in their insertion order. For example, to count the string values in a document:

```scala
user.foldDown(0) {
  case (count, Json.Str(_)) => count + 1
  case (count, _)           => count
}
// res54: Int = 3
```

The visiting order shows when the function collects the nodes it sees:

```scala
val small = Json.Obj("a" -> Json.Arr(Json.Num(1)))
// small: Json.Obj = Obj(
//   fields = IndexedSeq(("a", Arr(elements = IndexedSeq(Num(value = 1)))))
// )

small.foldDown(List.empty[String])((seen, json) => seen :+ json.toString)
// res55: List[String] = List("{\"a\":[1]}", "[1]", "1")

small.foldUp(List.empty[String])((seen, json) => seen :+ json.toString)
// res56: List[String] = List("1", "[1]", "{\"a\":[1]}")
```

## Rendering and Parsing

The `toJson` extension on `Json` renders the AST as a compact string, and `toJsonPretty` renders it with indentation. `Json#toString` also renders compact JSON, so string interpolation and logging show JSON text. The `fromJson[Json]` method parses a string into an AST:

```scala
println(user.toJsonPretty)
// {
//   "name" : "Alice",
//   "age" : 30,
//   "admin" : false,
//   "tags" : [
//     "ops",
//     "dev"
//   ],
//   "avatar" : null
// }

println(s"user = $user")
// user = {"name":"Alice","age":30,"admin":false,"tags":["ops","dev"],"avatar":null}
```

Parsing a string into the AST uses the same `fromJson` method as domain types, with `Json` as the type:

```scala
"""{"id": 7, "ok": true}""".fromJson[Json]
// res59: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(("id", Num(value = 7)), ("ok", Bool(value = true)))
//   )
// )
```

For encoding and decoding in streams, see [Streaming API](streaming.md). For YAML, the AST is the intermediate form, as described in [YAML](./yaml.md).
