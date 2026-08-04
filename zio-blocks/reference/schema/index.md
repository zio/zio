# Built-in Formats and Codecs

> ZIO Blocks Schema provides codec derivation for multiple serialization formats. Once you have a `Schema[A]` for your data type, you can derive codecs for most formats using the unified `Schema.derive(Format)` pattern. BSON uses a different API: `BsonSchemaCodec.bsonCodec(schema)`. See the [Format documentation](../format.md) for details on how formats work.

ZIO Blocks Schema provides codec derivation for multiple serialization formats. Once you have a `Schema[A]` for your data type, you can derive codecs for most formats using the unified `Schema.derive(Format)` pattern. BSON uses a different API: `BsonSchemaCodec.bsonCodec(schema)`. See the [Format documentation](../format.md) for details on how formats work.

## Built-in Codecs

Here's a summary of the codecs currently supported by ZIO Blocks. Most codecs provide a `BinaryFormat` or `TextFormat` object that can be passed to `derive`. BSON uses a different API (see below). See the dedicated codec documentation for installation, usage examples, and detailed type mappings:

| Derivation API      | Codec Type            | MIME Type             | Module                          | Documentation                   |
|---------------------|-----------------------|-----------------------|---------------------------------|---------------------------------|
| `JsonFormat`        | `JsonCodec[A]`        | `application/json`    | `zio-blocks-schema`             | [JSON](./json/index.md)         |
| `AvroFormat`        | `AvroCodec[A]`        | `application/avro`    | `zio-blocks-schema-avro`        | [Avro](./avro.md)               |
| `BsonSchemaCodec`   | `BsonCodec[A]`        | `application/bson`    | `zio-blocks-schema-bson`        | [BSON](./bson.md)               |
| `CsvFormat`         | `CsvCodec[A]`         | `text/csv`            | `zio-blocks-schema-csv`         | [CSV](./csv.md)                 |
| `MessagePackFormat` | `MessagePackCodec[A]` | `application/msgpack` | `zio-blocks-schema-messagepack` | [MessagePack](./messagepack.md) |
| `ThriftFormat`      | `ThriftCodec[A]`      | `application/thrift`  | `zio-blocks-schema-thrift`      | [Thrift](./thrift.md)           |
| `ToonFormat`        | `ToonCodec[A]`        | `text/toon`           | `zio-blocks-schema-toon`        | [TOON](./toon.md)               |
| `XmlFormat`         | `XmlCodec[A]`         | `application/xml`     | `zio-blocks-schema-xml`         | [XML](./xml.md)                 |
| `YamlFormat`        | `YamlCodec[A]`        | `application/yaml`    | `zio-blocks-schema-yaml`        | [YAML](./yaml.md)               |

## Supported Types

Most formats support the full set of ZIO Blocks Schema primitive types. Some formats have limitations:
- **CSV** supports only flat records and primitive types (no variants, sequences, maps, or dynamic types)
- **XML** supports records, sequences, and variants with configurable discriminator strategies
- Other formats support all composite types listed below

For format-specific limitations, see the dedicated codec documentation.

**Numeric Types**:
- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`
- `BigInt`, `BigDecimal`

**Text Types**:
- `String`

**Special Types**:
- `Unit`, `UUID`, `Currency`

**Java Time Types**:
- `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`
- `Duration`, `Period`
- `Year`, `YearMonth`, `MonthDay`
- `DayOfWeek`, `Month`
- `ZoneId`, `ZoneOffset`

**Composite Types**:
- Records (case classes)
- Variants (sealed traits)
- Sequences (`List`, `Vector`, `Set`, `Array`, etc.)
- Maps (`Map[K, V]`)
- Options (`Option[A]`)
- Eithers (`Either[A, B]`)
- Wrappers (newtypes)

## Cross-Platform Support

| Format      | JVM | Scala.js |
|-------------|-----|----------|
| JSON        | ✓   | ✓        |
| TOON        | ✓   | ✓        |
| MessagePack | ✓   | ✓        |
| CSV         | ✓   | ✓        |
| XML         | ✓   | ✓        |
| YAML        | ✓   | ✓        |
| Avro        | ✓   | ✗        |
| Thrift      | ✓   | ✗        |
| BSON        | ✓   | ✗        |

## Error Handling

All formats return `Either[SchemaError, A]` for decoding operations. Errors include path information for debugging, showing exactly where in nested structures a decoding failure occurred. See individual codec documentation for format-specific error details.
