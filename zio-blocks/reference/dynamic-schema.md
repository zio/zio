# DynamicSchema

> `DynamicSchema` is a **type-erased schema container** that wraps a `Reflect.Unbound[_]` tree — all the structural information from a `Schema[A]` (field names, case names, type identities, validations, annotations) with no Scala functions attached. The two fundamental uses are validating `DynamicValue` instances at runtime, and transporting schemas across process boundaries.

`DynamicSchema` is a **type-erased schema container** that wraps a `Reflect.Unbound[_]` tree — all the structural information from a `Schema[A]` (field names, case names, type identities, validations, annotations) with no Scala functions attached. The two fundamental uses are validating `DynamicValue` instances at runtime, and transporting schemas across process boundaries.

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_])
```

`DynamicSchema`:

- Holds the full structural shape of a type without capturing constructors or deconstructors
- Can validate any `DynamicValue` against its structure using `DynamicSchema#check` and `DynamicSchema#conforms`
- Can be serialized to a `DynamicValue` and deserialized back, making it storable in databases or transmittable over the network
- Can be rehydrated into a fully operational `Schema[A]` by supplying runtime bindings via `DynamicSchema#rebind`

## Motivation

A `Schema[A]` carries both *structure* (field names, type identities, validations) and *behaviour* (constructors and deconstructors as Scala closures). Closures cannot cross process boundaries. `DynamicSchema` is the structural half — a schema stripped of all Scala functions:

```
Schema[OrderPlaced]   ←── compile-time type, closures attached
       │
       │  Schema[A]#toDynamicSchema
       ▼
DynamicSchema         ←── serializable, no closures
(Reflect.Unbound[_])       stored in registry or sent over the wire
       │
       │  DynamicSchema.toDynamicValue / DynamicSchema.fromDynamicValue
       ▼
DynamicValue          ←── uniform, format-neutral blob
       │
       │  DynamicSchema#fromDynamicValue (on the consumer side)
       ▼
DynamicSchema         ←── reconstructed from storage
       │
       │  DynamicSchema#rebind[OrderPlaced](resolver)
       ▼
Schema[OrderPlaced]   ←── operational again, can encode and decode
```

This pattern enables schema registries: the Checkout Service registers its event schema on startup; the Fulfillment Service fetches it and rebinds it against its own type definitions, guaranteeing it uses the exact schema that was in effect when the event was encoded. See [BindingResolver](./binding-resolver.md) for the complete rebinding API.

## Creating a DynamicSchema

There are two ways to obtain a `DynamicSchema`: strip bindings from an existing typed schema, or reconstruct one from a serialized blob.

### `Schema[A]#toDynamicSchema`

`Schema[A]#toDynamicSchema` strips all runtime bindings from a typed schema, returning the structural skeleton as a `DynamicSchema`. The method is defined on `Schema`:

```scala
trait Schema[A] {
  def toDynamicSchema: DynamicSchema
}
```

This is the standard entry point. All structural information — field names, case names, type IDs, `Validation` constraints, `Modifier` annotations, docs, default values, and examples — is preserved:

```scala

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

object Address { implicit val schema: Schema[Address] = Schema.derived[Address] }
object Person  { implicit val schema: Schema[Person]  = Schema.derived[Person]  }

val dynamic: DynamicSchema = Schema[Person].toDynamicSchema
```

We can inspect the schema's structure by printing it:

```scala
dynamic
// res1: DynamicSchema = DynamicSchema(
//   Record(
//     fields = IndexedSeq(
//       Term(
//         name = "name",
//         value = Primitive(
//           primitiveType = String(None),
//           typeId = String,
//           primitiveBinding = zio.blocks.schema.binding.NoBinding$$anon$1@5ca5d848,
//           doc = Doc(blocks = IndexedSeq(), metadata = Map()),
//           modifiers = List(),
//           storedDefaultValue = None,
//           storedExamples = List()
//         ),
//         doc = Doc(blocks = IndexedSeq(), metadata = Map()),
//         modifiers = List()
//       ),
//       Term(
//         name = "age",
//         value = Primitive(
//           primitiveType = Int(None),
//           typeId = Int,
//           primitiveBinding = zio.blocks.schema.binding.NoBinding$$anon$1@5ca5d848,
//           doc = Doc(blocks = IndexedSeq(), metadata = Map()),
//           modifiers = List(),
//           storedDefaultValue = None,
//           storedExamples = List()
//         ),
//         doc = Doc(blocks = IndexedSeq(), metadata = Map()),
//         modifiers = List()
//       ),
//       Term(
//         name = "address",
//         value = Record(
//           fields = IndexedSeq(
//             Term(
//               name = "street",
//               value = Primitive(
//                 primitiveType = String(None),
//                 typeId = String,
//                 primitiveBinding = zio.blocks.schema.binding.NoBinding$$anon$1@5ca5d848,
//                 doc = Doc(blocks = IndexedSeq(), metadata = Map()),
//                 modifiers = List(),
//                 storedDefaultValue = None,
//                 storedExamples = List()
//               ),
// ...
```

### `DynamicSchema.fromDynamicValue`

`DynamicSchema.fromDynamicValue` reconstructs a `DynamicSchema` from a previously serialized `DynamicValue` blob. This is the consumer-side entry point when schemas are loaded from a registry or database:

```scala
object DynamicSchema {
  def fromDynamicValue(dv: DynamicValue): DynamicSchema
}
```

Example showing the full store/retrieve round-trip:

```scala

case class Product(id: Long, name: String, price: Double)
object Product { implicit val schema: Schema[Product] = Schema.derived[Product] }

val original: DynamicSchema = Schema[Product].toDynamicSchema
val blob:     DynamicValue  = DynamicSchema.toDynamicValue(original)
val restored: DynamicSchema = DynamicSchema.fromDynamicValue(blob)
```

We inspect the restored schema's type name to confirm the round-trip preserved it:

```scala
restored.typeId.name
// res3: String = "Product"
```

## Serializing a DynamicSchema

`DynamicSchema.toDynamicValue` converts a `DynamicSchema` to a `DynamicValue` for storage or transmission. The result contains only field names, type names, validations, and annotations — no Scala closures:

```scala
object DynamicSchema {
  def toDynamicValue(ds: DynamicSchema): DynamicValue
}
```

The following example converts a simple case class schema and stores the blob:

```scala

case class Tag(name: String)
object Tag { implicit val schema: Schema[Tag] = Schema.derived[Tag] }

val ds:   DynamicSchema = Schema[Tag].toDynamicSchema
val blob: DynamicValue  = DynamicSchema.toDynamicValue(ds)
```

We inspect the blob's value type to confirm the record structure was preserved:

```scala
blob.valueType
// res5: DynamicValueType = <function1>
```

## Validating DynamicValues

`DynamicSchema` can check whether a `DynamicValue` conforms to its structure. Validation is recursive: field counts and names, variant case names, collection element types, and primitive type + validation constraints are all checked.

### `DynamicSchema#check`

`DynamicSchema#check` returns `None` if the value is valid, or `Some(SchemaError)` describing the first validation failure:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def check(value: DynamicValue): Option[SchemaError]
}
```

The following example shows all three outcomes — a valid value, a missing field, and a type mismatch:

```scala

case class Point(x: Int, y: Int)
object Point { implicit val schema: Schema[Point] = Schema.derived[Point] }

val dynSchema = Schema[Point].toDynamicSchema

val valid = DynamicValue.Record(Chunk(
  "x" -> DynamicValue.int(3),
  "y" -> DynamicValue.int(7)
))

val missing = DynamicValue.Record(Chunk(
  "x" -> DynamicValue.int(3)
  // "y" missing
))

val wrongType = DynamicValue.Record(Chunk(
  "x" -> DynamicValue.int(3),
  "y" -> DynamicValue.string("not an int")
))
```

We evaluate all three cases to observe the outcomes:

```scala
dynSchema.check(valid)
// res7: Option[SchemaError] = None
dynSchema.check(missing)
// res8: Option[SchemaError] = Some(
//   SchemaError(
//     List(MissingField(source = DynamicOptic(IndexedSeq()), fieldName = "y"))
//   )
// )
dynSchema.check(wrongType)
// res9: Option[SchemaError] = Some(
//   SchemaError(
//     List(
//       ExpectationMismatch(
//         source = DynamicOptic(IndexedSeq(Field("y"))),
//         expectation = "Expected Int, got String"
//       )
//     )
//   )
// )
```

Validation rules:

- **Records**: every field in the schema must be present; extra fields in the value are rejected.
- **Variants**: the case name must be valid; the case payload is validated recursively.
- **Sequences**: every element is validated against the element schema.
- **Maps**: every key and every value is validated against their respective schemas.
- **Primitives**: the `PrimitiveType` must match, and any `Validation` constraints (range, pattern, etc.) must pass.

### `DynamicSchema#conforms`

`DynamicSchema#conforms` is a convenience method that returns `true` when the value is valid:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def conforms(value: DynamicValue): Boolean
}
```

We pass a well-formed record to confirm the return value:

```scala

case class Tag(name: String)
object Tag { implicit val schema: Schema[Tag] = Schema.derived[Tag] }

val dynSchema = Schema[Tag].toDynamicSchema
val value     = DynamicValue.Record(Chunk("name" -> DynamicValue.string("scala")))
```

```scala
dynSchema.conforms(value)
// res11: Boolean = true
```

## Rebinding to a Typed Schema

`DynamicSchema#rebind` converts a structural `DynamicSchema` back into a fully operational `Schema[A]` by walking the `Reflect.Unbound` tree and attaching runtime bindings from a `BindingResolver`:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def rebind[A](resolver: BindingResolver): Schema[A]
}
```

The resolver must provide a binding for every concrete type referenced in the schema tree: record types, variant types, and wrapper types must be covered explicitly; primitives, sequences, and maps are covered by `BindingResolver.defaults`:

```scala

case class OrderId(value: String)
case class LineItem(sku: String, quantity: Int)
case class Order(id: OrderId, items: List[LineItem])

object OrderId  { implicit val schema: Schema[OrderId]  = Schema.derived[OrderId]  }
object LineItem { implicit val schema: Schema[LineItem] = Schema.derived[LineItem] }
object Order    { implicit val schema: Schema[Order]    = Schema.derived[Order]    }

val blob: DynamicValue     = DynamicSchema.toDynamicValue(Schema[Order].toDynamicSchema)
val dynamic: DynamicSchema = DynamicSchema.fromDynamicValue(blob)

val resolver: BindingResolver =
  BindingResolver.empty
    .bind(Binding.of[OrderId])
    .bind(Binding.of[LineItem])
    .bind(Binding.of[Order])
    ++ BindingResolver.defaults

val rebound: Schema[Order] = dynamic.rebind[Order](resolver)
val order                  = Order(OrderId("ORD-1"), List(LineItem("SKU-A", 2)))
val encoded                = rebound.toDynamicValue(order)
```

We decode the encoded value to confirm the round-trip is lossless:

```scala
rebound.fromDynamicValue(encoded)
// res13: Either[SchemaError, Order] = Right(
//   Order(
//     id = OrderId("ORD-1"),
//     items = List(LineItem(sku = "SKU-A", quantity = 2))
//   )
// )
```

:::warning
If `DynamicSchema#rebind` cannot find a binding for any type in the unbound schema tree, it throws a `RebindException` at runtime. Ensure the resolver covers every concrete type — records, variants, wrappers, primitives, and standard collections — that appears in the schema. `BindingResolver.defaults` covers all standard primitives, `java.time` types, `List`, `Map`, `Option`, and other standard collections.
:::

See [BindingResolver](./binding-resolver.md) for the full resolver API including `BindingResolver.reflection` for automatic binding via reflection.

## Structural Navigation

`DynamicSchema#get` navigates into the schema tree using a `DynamicOptic` path, returning the nested `Reflect.Unbound[_]` at that location:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def get(optic: DynamicOptic): Option[Reflect.Unbound[_]]
}
```

The following example navigates to a field nested two levels deep:

```scala

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

object Address { implicit val schema: Schema[Address] = Schema.derived[Address] }
object Person  { implicit val schema: Schema[Person]  = Schema.derived[Person]  }

val dynSchema = Schema[Person].toDynamicSchema
val streetSchema: Option[Reflect.Unbound[_]] =
  dynSchema.get(DynamicOptic.root.field("address").field("street"))
```

We map over the result to read the type name at that path:

```scala
streetSchema.map(_.typeId.name)
// res15: Option[String] = Some("String")
```

See [DynamicOptic](./dynamic-optic.md) for the full path DSL.

## Metadata Access and Updates

`DynamicSchema` provides read and write access to the metadata stored in the `Reflect` tree.

### `DynamicSchema#typeId`

Returns the `TypeId` of the root type:

```scala

case class Event(id: Long, kind: String)
object Event { implicit val schema: Schema[Event] = Schema.derived[Event] }

val dynSchema = Schema[Event].toDynamicSchema
```

```scala
dynSchema.typeId.name
// res17: String = "Event"
dynSchema.typeId.fullName
// res18: String = "repl.MdocSession.MdocApp16.Event"
```

### `DynamicSchema#doc`

Reads and writes the documentation annotation on the schema. The zero-argument form reads the current `Doc`; the single-argument form returns a copy with updated documentation:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def doc: Doc
  def doc(value: String): DynamicSchema
}
```

We attach a description and read it back to confirm it was applied:

```scala

case class Event(id: Long, kind: String)
object Event { implicit val schema: Schema[Event] = Schema.derived[Event] }

val updated: DynamicSchema = Schema[Event].toDynamicSchema.doc("An event in the event log")
```

```scala
updated.doc
// res20: Doc = Doc(
//   blocks = IndexedSeq(Paragraph(IndexedSeq(Text("An event in the event log")))),
//   metadata = Map()
// )
```

### `DynamicSchema#modifiers` and `DynamicSchema#modifier`

`DynamicSchema#modifiers` returns the `Modifier.Reflect` annotations attached to the root node. `DynamicSchema#modifier` returns a copy with an additional modifier appended:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def modifiers: Seq[Modifier.Reflect]
  def modifier(m: Modifier.Reflect): DynamicSchema
}
```

### `DynamicSchema#getDefaultValue` and `DynamicSchema#defaultValue`

`DynamicSchema#getDefaultValue` returns the stored default `DynamicValue`, if one is set. `DynamicSchema#defaultValue` returns a copy with the given default:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def getDefaultValue: Option[DynamicValue]
  def defaultValue(value: DynamicValue): DynamicSchema
}
```

We attach a default value and then retrieve it:

```scala

case class Config(retries: Int)
object Config { implicit val schema: Schema[Config] = Schema.derived[Config] }

val withDefault: DynamicSchema =
  Schema[Config].toDynamicSchema
    .defaultValue(DynamicValue.Record(
      zio.blocks.chunk.Chunk("retries" -> DynamicValue.int(3))
    ))
```

```scala
withDefault.getDefaultValue
// res22: Option[DynamicValue] = Some(
//   Record(IndexedSeq(("retries", Primitive(Int(3)))))
// )
```

### `DynamicSchema#examples`

The zero-argument form returns stored examples as a `Seq[DynamicValue]`. The multi-argument form returns a copy with the given examples set:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def examples: Seq[DynamicValue]
  def examples(value: DynamicValue, values: DynamicValue*): DynamicSchema
}
```

## Converting to a Typed Schema

`DynamicSchema#toSchema` returns a `Schema[DynamicValue]` — it stays fully in the dynamic world and requires no bindings. Use it when you have received a `DynamicSchema` over the wire and need a codec-compatible schema that enforces structural conformance without binding any Scala types.

After transporting a `DynamicSchema`, you may not have the Scala types that `DynamicSchema#rebind` requires. For example, when the consumer is a validation gateway or format converter that handles arbitrary event shapes, any codec pipeline that accepts `Schema[DynamicValue]` can use the result directly. `DynamicSchema#toSchema` is the right choice for schema validation middleware, event-store gateways, and format converters that must enforce structure without knowing the concrete type.

:::warning
Use `DynamicSchema#rebind` instead when you have a `BindingResolver` and need a fully operational `Schema[A]` for typed encoding and decoding.
:::
`DynamicSchema#toSchema` is defined as:

```scala
final case class DynamicSchema(reflect: Reflect.Unbound[_]) {
  def toSchema: Schema[DynamicValue]
}
```

The following example shows a schema-validation gateway: we receive a `DynamicSchema` from a registry, convert it to a `Schema[DynamicValue]`, and use the result to validate an incoming payload, rejecting it on structural mismatch:

```scala

case class OrderEvent(orderId: String, amount: Double)
object OrderEvent { implicit val schema: Schema[OrderEvent] = Schema.derived[OrderEvent] }

val blob: DynamicValue          = DynamicSchema.toDynamicValue(Schema[OrderEvent].toDynamicSchema)
val received: DynamicSchema     = DynamicSchema.fromDynamicValue(blob)
val gatewaySchema: Schema[DynamicValue] = received.toSchema

val incoming: DynamicValue = DynamicValue.Record(
  zio.blocks.chunk.Chunk(
    "orderId" -> DynamicValue.string("ORD-42"),
    "amount"  -> DynamicValue.double(99.95)
  )
)
```

We validate the payload and observe the result:

```scala
gatewaySchema.fromDynamicValue(incoming)
// res24: Either[SchemaError, DynamicValue] = Right(
//   Record(
//     IndexedSeq(
//       ("orderId", Primitive(String("ORD-42"))),
//       ("amount", Primitive(Double(99.95)))
//     )
//   )
// )
```

## Integration

`DynamicSchema` connects to several other ZIO Blocks types, each serving a distinct role in the dynamic layer.

### With `DynamicValue`

`DynamicSchema` and `DynamicValue` are the two halves of ZIO Blocks' dynamic layer. `DynamicValue` holds runtime data without compile-time types; `DynamicSchema` holds structural metadata without runtime bindings. Together they enable fully type-erased validation and serialization pipelines. See [DynamicValue](./dynamic-value.md).

### With `BindingResolver`

The primary consumer of `DynamicSchema` in a type-safe context is `DynamicSchema#rebind`, which requires a `BindingResolver` to reattach runtime bindings. See [BindingResolver](./binding-resolver.md) for the complete rebinding API, including `BindingResolver.reflection` for automatic binding discovery.

### With `DynamicOptic`

`DynamicSchema#get` accepts a `DynamicOptic` path to navigate the structural tree. This allows you to inspect nested schemas, extract type information, or validate sub-trees independently. See [DynamicOptic](./dynamic-optic.md).

### With `Schema`

`Schema[A]#toDynamicSchema` is defined on `Schema` and is the standard way to obtain a `DynamicSchema`. See [Schema](./schema.md).

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Validate DynamicValues against a DynamicSchema**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/dynamicschema/DynamicSchemaValidationExample.scala))

```bash
sbt "schema-examples/runMain dynamicschema.DynamicSchemaValidationExample"
```

**Serialize and deserialize a DynamicSchema**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/dynamicschema/DynamicSchemaSerializationExample.scala))

```bash
sbt "schema-examples/runMain dynamicschema.DynamicSchemaSerializationExample"
```

**Rebind a restored DynamicSchema to a typed Schema**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/dynamicschema/DynamicSchemaRebindExample.scala))

```bash
sbt "schema-examples/runMain dynamicschema.DynamicSchemaRebindExample"
```

**Complete schema registry pipeline**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/dynamicschema/DynamicSchemaRegistryExample.scala))

```bash
sbt "schema-examples/runMain dynamicschema.DynamicSchemaRegistryExample"
```
