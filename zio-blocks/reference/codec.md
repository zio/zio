# Codec

> `Codec[DecodeInput, EncodeOutput, Value]` is the base abstraction for encoding and decoding values between a specific input representation and a specific output representation. It forms the foundation of ZIO Blocks' multi-format serialization system, enabling a single `Schema[A]` to derive codecs for JSON, Avro, TOON, MessagePack, Thrift, and other formats that are integrated via the `Codec`/`Format` system. BSON support is provided separately via `BsonSchemaCodec`, which is not a subtype of `codec.Codec` and is not derived via `Schema.derive(format)`.

`Codec[DecodeInput, EncodeOutput, Value]` is the base abstraction for encoding and decoding values between a specific input representation and a specific output representation. It forms the foundation of ZIO Blocks' multi-format serialization system, enabling a single `Schema[A]` to derive codecs for JSON, Avro, TOON, MessagePack, Thrift, and other formats that are integrated via the `Codec`/`Format` system. BSON support is provided separately via `BsonSchemaCodec`, which is not a subtype of `codec.Codec` and is not derived via `Schema.derive(format)`.

## Overview

`Codec` defines two abstract methods that every concrete codec must implement:

```scala
abstract class Codec[DecodeInput, EncodeOutput, Value] {
  def encode(value: Value, output: EncodeOutput): Unit
  def decode(input: DecodeInput): Either[SchemaError, Value]
}
```

- **`encode`** writes the encoded form of `value` into `output`. The output parameter is typically a mutable buffer (`ByteBuffer`, `CharBuffer`) that the caller provides.
- **`decode`** reads from `input` and returns either a `SchemaError` describing the failure or the decoded value.

End users rarely interact with `Codec` directly. Instead, they work with format-specific subclasses like `JsonCodec[A]` or `ToonCodec[A]`, which add convenience methods for common input/output types.

Given a `Schema[A]`, you can derive a codec for any supported format by calling `Schema[A].derive(format)`, which uses the `Deriver` associated with that format to generate the appropriate codec instance. For example, to derive a JSON codec:

```scala

case class Person(name: String, age: Int)

object Person {
  // Derive a schema for Person (required for codec derivation)
  implicit val schema: Schema[Person] = Schema.derived
   // Derive a JSON codec from the schema 
  val codec: JsonCodec[Person] = schema.derive(JsonFormat)
}

// Encode
val bytes: Array[Byte] = Person.codec.encode(Person("Alice", 30))

// Decode
val result: Either[SchemaError, Person] = Person.codec.decode(bytes)
```

## Installation

To include the base schema module with JSON support, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

Additional format modules are separate artifacts:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro"        % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson"        % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-csv"         % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift"      % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon"        % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-xml"         % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-yaml"        % "0.0.33"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## BinaryCodec and TextCodec

The codec system in ZIO Blocks is organized as a layered hierarchy:

```
Codec[DecodeInput, EncodeOutput, Value]        
├── BinaryCodec[A] =  Codec[ByteBuffer, ByteBuffer, A]   (ByteBuffer ↔ A)
│   ├── AvroCodec[A]                   
│   ├── JsonCodec[A]                    
│   ├── MessagePackCodec[A]         
│   ├── ThriftCodec[A]               
│   ├── ToonCodec[A]                  
│   ├── XmlCodec[A]         
│   └── YamlCodec[A]         
└── TextCodec[A] = Codec[CharBuffer, CharBuffer, A]      (CharBuffer ↔ A)
    └── CsvCodec[A]                    
```

1. **`BinaryCodec[A]`** fixes both the input and output to `ByteBuffer` and is the base class for all codecs that operate on binary data:

```scala
abstract class BinaryCodec[A] extends Codec[ByteBuffer, ByteBuffer, A]
```

2. **`TextCodec[A]`** fixes both the input and output to `CharBuffer`:

```scala
abstract class TextCodec[A] extends Codec[CharBuffer, CharBuffer, A]
```

All built-in serialization formats (JSON, Avro, TOON, MessagePack, Thrift) extend `BinaryCodec`. Despite JSON being a text format, the JSON codec operates on UTF-8 encoded bytes for performance.

`TextCodec` exists for formats that operate on character data rather than raw bytes. No built-in formats currently use `TextCodec`, but it is available for custom text-based formats.

## Deriving Codecs

### Using Schema.derive

The primary way to obtain a codec is through `Schema[A].derive`:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Pass a Format object to get a codec for that format
val jsonCodec: JsonCodec[Person] = Schema[Person].derive(JsonFormat)
```

This works with any format:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val jsonCodec = Schema[Person].derive(JsonFormat)
val toonCodec = Schema[Person].derive(ToonFormat)
```

### Using Schema.deriving for Customization

For more control over the derived codec, use `deriving` to get a `DerivationBuilder`. This lets you override instances for specific substructures or inject modifiers before finalizing:

```scala

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name = $(_.name)
  val age  = $(_.age)
}

// Override the codec for the "name" field
val customNameCodec = new JsonCodec[String] {
  def decodeValue(in: JsonReader): String = in.readString()
  def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x.toUpperCase)
}

val codec: JsonCodec[Person] = Schema[Person]
  .deriving(JsonFormat.deriver)
  .instance(Person.name, customNameCodec)
  .derive
```

### Using Schema#decode and Schema#encode

`Schema` also provides `decode` and `encode` methods that internally call `derive` (with caching) and then delegate to the codec:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Encode directly from Schema
val buffer = ByteBuffer.allocate(1024)
Schema[Person].encode(JsonFormat)(buffer)(Person("Alice", 30))

// Decode directly from Schema
buffer.flip()
val result: Either[SchemaError, Person] = Schema[Person].decode(JsonFormat)(buffer)
```

### Using a Deriver Directly

Each `Format` object contains a `Deriver[TC]` that can also be passed to `derive`:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// These are equivalent:
val codec1 = Schema[Person].derive(JsonFormat)
val codec2 = Schema[Person].derive(JsonFormat.deriver)
```

Passing a `Deriver` directly is useful when working with custom or configured derivers (see [Configuring Codecs](#configuring-codecs)).

## Convenience Methods on Format-Specific Codecs

While the base `Codec` class defines only `encode(value, output)` and `decode(input)`, format-specific subclasses like `JsonCodec` and `ToonCodec` add convenience overloads for common I/O types.

### JsonCodec Convenience Methods

`JsonCodec[A]` provides the following overloads beyond the base `ByteBuffer` API:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(JsonFormat)
val person = Person("Alice", 30)

// Array[Byte]
val bytes: Array[Byte] = codec.encode(person)
val fromBytes1: Either[SchemaError, Person] = codec.decode(bytes)
val fromBytes2: Either[SchemaError, Person] = codec.decode(bytes, 0, bytes.length)

// String
val jsonStr: String = codec.encodeToString(person)
val fromStr: Either[SchemaError, Person] = codec.decode("""{"name":"Alice","age":30}""")

// InputStream / OutputStream

val os = new ByteArrayOutputStream()
codec.encode(person, os)

val is = new ByteArrayInputStream(os.toByteArray)
val fromStream: Either[SchemaError, Person] = codec.decode(is)
```

### ToonCodec Convenience Methods

`ToonCodec[A]` provides the same set of overloads:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(ToonFormat)
val person = Person("Alice", 30)

// Array[Byte]
val bytes: Array[Byte] = codec.encode(person)
val fromBytes: Either[SchemaError, Person] = codec.decode(bytes)

// String
val toonStr: String = codec.encodeToString(person)
val fromStr: Either[SchemaError, Person] = codec.decode("name: Alice\nage: 30")
```

### Summary of Convenience Methods

`BinaryCodec` subclasses (JSON, TOON, MessagePack, Avro, Thrift) expose the following convenience overloads (availability may vary by format):

| Method                                                                   | Description                                      |
|--------------------------------------------------------------------------|--------------------------------------------------|
| `encode(value): Array[Byte]`                                             | Encode to a byte array                           |
| `decode(input: Array[Byte]): Either[SchemaError, A]`                     | Decode from a byte array                         |
| `decode(input: Array[Byte], from: Int, to: Int): Either[SchemaError, A]` | Decode from a byte array slice                   |
| `encode(value, output: ByteBuffer): Unit`                                | Encode into a `ByteBuffer`                       |
| `decode(input: ByteBuffer): Either[SchemaError, A]`                      | Decode from a `ByteBuffer`                       |
| `encode(value, output: OutputStream): Unit`                              | Encode into an `OutputStream` (JSON, TOON, Avro) |
| `decode(input: InputStream): Either[SchemaError, A]`                     | Decode from an `InputStream` (JSON, TOON, Avro)  |
| `encodeToString(value): String`                                          | Encode to a `String` (JSON, TOON)                |
| `decode(input: String): Either[SchemaError, A]`                          | Decode from a `String` (JSON, TOON)              |

The `String`-based methods are available on text-oriented binary codecs (JSON, TOON) but not on purely binary formats like Avro or Thrift.

## Configuring Codecs

Format-specific derivers support configuration options that control encoding behavior. Instead of passing a `Format` object to `derive`, you pass a configured `Deriver`:

### JSON Configuration

```scala

case class Person(
  firstName: String,
  lastName: String,
  middleName: Option[String] = None
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val customDeriver = JsonCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)
  .withTransientNone(true)
  .withRejectExtraFields(true)

val codec = Schema[Person].derive(customDeriver)

// Encodes as: {"first_name":"Alice","last_name":"Smith"}
// (middleName omitted because it is None and transientNone is true)
val json = codec.encodeToString(Person("Alice", "Smith"))
```

| Option                          | Description                                            | Default    |
|---------------------------------|--------------------------------------------------------|------------|
| `withFieldNameMapper`           | Transform field names (Identity, SnakeCase, KebabCase) | `Identity` |
| `withCaseNameMapper`            | Transform variant/case names                           | `Identity` |
| `withDiscriminatorKind`         | ADT discriminator style (Key, Field, None)             | `Key`      |
| `withRejectExtraFields`         | Error on unknown fields during decoding                | `false`    |
| `withEnumValuesAsStrings`       | Encode enum values as strings                          | `true`     |
| `withTransientNone`             | Omit `None` values from output                         | `true`     |
| `withTransientEmptyCollection`  | Omit empty collections from output                     | `true`     |
| `withTransientDefaultValue`     | Omit fields with default values                        | `true`     |
| `withRequireOptionFields`       | Require optional fields in input                       | `false`    |
| `withRequireCollectionFields`   | Require collection fields in input                     | `false`    |
| `withRequireDefaultValueFields` | Require fields with defaults in input                  | `false`    |

### TOON Configuration

```scala

case class Person(
  firstName: String,
  lastName: String
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val customDeriver = ToonCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)
  .withArrayFormat(ArrayFormat.Tabular)
  .withDiscriminatorKind(DiscriminatorKind.Field("type"))

val codec = Schema[Person].derive(customDeriver)
```

## Error Handling

All `decode` operations return `Either[SchemaError, A]`. `SchemaError` includes path information that pinpoints where in the data structure decoding failed:

```scala

case class Address(street: String, city: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Person(name: String, address: Address)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(JsonFormat)

// Missing required field
val result = codec.decode("""{"name":"Alice","address":{}}""")

result match {
  case Right(person) => println(person)
  case Left(error)   => error.errors.foreach(e => println(s"Error: ${e.message}"))
}
```
