# JSON Schema

> `JsonSchema` provides first-class support for [JSON Schema 2020-12](https://json-schema.org/specification-links.html#2020-12) in ZIO Blocks. It enables parsing, construction, validation, and serialization of JSON Schemas as native Scala values.

`JsonSchema` provides first-class support for [JSON Schema 2020-12](https://json-schema.org/specification-links.html#2020-12) in ZIO Blocks. It enables parsing, construction, validation, and serialization of JSON Schemas as native Scala values.

## Overview

The `JsonSchema` type is a sealed ADT representing all valid JSON Schema documents:

```
JsonSchema
 ├── JsonSchema.True          (accepts all values - equivalent to {})
 ├── JsonSchema.False         (rejects all values - equivalent to {"not": {}})
 └── JsonSchema.Object        (full schema with all keywords)
```

Key features:

- **Full JSON Schema 2020-12 support** - All standard vocabularies (core, applicator, validation, format, meta-data)
- **Type-safe construction** - Smart constructors and builder pattern
- **Validation** - Validate JSON values against schemas with detailed error messages
- **Round-trip serialization** - Parse from JSON and serialize back without loss
- **Combinators** - Compose schemas with `&&` (allOf), `||` (anyOf), `!` (not)
- **817 of 844 official tests passing** (97%+)

## Deriving JSON Schema from Schema

The most common use case is deriving a JSON Schema from an existing `Schema[A]`.

### Basic Derivation

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Get JSON Schema directly from Schema
val jsonSchema: JsonSchema = Schema[Person].toJsonSchema

// The derived schema validates JSON values
val valid = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
val invalid = Json.Object("name" -> Json.Number(123))

jsonSchema.conforms(valid)   // true
jsonSchema.conforms(invalid) // false
```

### Through JsonCodec

For more control, derive through `JsonCodec`:

```scala

case class User(email: String, active: Boolean)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// Derive codec first, then get JSON Schema
val codec = Schema[User].derive(JsonFormat)
val jsonSchema = codec.toJsonSchema
```

## Creating Schemas

### Boolean Schemas

The simplest schemas accept or reject all values:

```scala

// Accepts any valid JSON value
val acceptAll = JsonSchema.True

// Rejects all JSON values
val rejectAll = JsonSchema.False
```

### Type Schemas

Create schemas that validate specific JSON types:

```scala

// Single type
val stringSchema = JsonSchema.ofType(JsonSchemaType.String)
val numberSchema = JsonSchema.ofType(JsonSchemaType.Number)
val integerSchema = JsonSchema.ofType(JsonSchemaType.Integer)
val booleanSchema = JsonSchema.ofType(JsonSchemaType.Boolean)
val arraySchema = JsonSchema.ofType(JsonSchemaType.Array)
val objectSchema = JsonSchema.ofType(JsonSchemaType.Object)
val nullSchema = JsonSchema.ofType(JsonSchemaType.Null)

// Convenience aliases
val isNull = JsonSchema.nullSchema
val isBoolean = JsonSchema.boolean
```

### String Schemas

Create schemas for string validation:

```scala

// String with length constraints (compile-time validated literals)
val username = JsonSchema.string(
  NonNegativeInt.literal(3),
  NonNegativeInt.literal(20)
)

// String with pattern
val hexColor = JsonSchema.string(
  pattern = RegexPattern.unsafe("^#[0-9a-fA-F]{6}$")
)

// String with format
val email = JsonSchema.string(format = Some("email"))
val dateTime = JsonSchema.string(format = Some("date-time"))
val uuid = JsonSchema.string(format = Some("uuid"))
```

### Numeric Schemas

Create schemas for number validation:

```scala

// Number with range
val percentage = JsonSchema.number(
  minimum = Some(BigDecimal(0)),
  maximum = Some(BigDecimal(100))
)

// Integer with exclusive bounds
val positiveInt = JsonSchema.integer(
  exclusiveMinimum = Some(BigDecimal(0))
)

// Number divisible by a value
val evenNumber = JsonSchema.integer(
  multipleOf = PositiveNumber.fromInt(2)
)
```

### Array Schemas

Create schemas for array validation:

```scala

// Array of strings
val stringArray = JsonSchema.array(
  items = Some(JsonSchema.ofType(JsonSchemaType.String))
)

// Array with length constraints
val shortList = JsonSchema.array(
  JsonSchema.ofType(JsonSchemaType.Number),
  NonNegativeInt.literal(1),
  NonNegativeInt.literal(5)
)

// Array with unique items
val uniqueNumbers = JsonSchema.array(
  items = Some(JsonSchema.ofType(JsonSchemaType.Number)),
  uniqueItems = Some(true)
)

// Tuple-like array with prefixItems
val point2D = JsonSchema.array(
  prefixItems = Some(NonEmptyChunk(
    JsonSchema.ofType(JsonSchemaType.Number),
    JsonSchema.ofType(JsonSchemaType.Number)
  ))
)
```

### Object Schemas

Create schemas for object validation:

```scala

// Object with properties
val person = JsonSchema.obj(
  properties = Some(ChunkMap(
    "name" -> JsonSchema.ofType(JsonSchemaType.String),
    "age" -> JsonSchema.ofType(JsonSchemaType.Integer)
  )),
  required = Some(Set("name"))
)

// Object with no additional properties
val strictPerson = JsonSchema.obj(
  properties = Some(ChunkMap(
    "name" -> JsonSchema.ofType(JsonSchemaType.String),
    "age" -> JsonSchema.ofType(JsonSchemaType.Integer)
  )),
  required = Some(Set("name")),
  additionalProperties = Some(JsonSchema.False)
)
```

### Enum and Const

```scala

// Enum of string values
val status = JsonSchema.enumOfStrings(NonEmptyChunk("pending", "active", "completed"))

// Enum of mixed values
val mixed = JsonSchema.enumOf(NonEmptyChunk(
  Json.String("auto"),
  Json.Number(0),
  Json.Boolean(true)
))

// Constant value
val alwaysTrue = JsonSchema.constOf(Json.Boolean(true))
```

## Schema Combinators

### Logical Composition

Combine schemas using logical operators:

```scala

val stringSchema = JsonSchema.ofType(JsonSchemaType.String)
val numberSchema = JsonSchema.ofType(JsonSchemaType.Number)
val nullSchema = JsonSchema.ofType(JsonSchemaType.Null)

// allOf - must match all schemas
val stringAndNotEmpty = stringSchema && JsonSchema.string(
  minLength = Some(zio.blocks.schema.json.NonNegativeInt.literal(1))
)

// anyOf - must match at least one schema
val stringOrNumber = stringSchema || numberSchema

// not - must not match the schema
val notNull = !nullSchema

// Combining operators
val nullableString = stringSchema || nullSchema
```

### Nullable Schemas

Make any schema nullable:

```scala

val stringSchema = JsonSchema.ofType(JsonSchemaType.String)

// Accepts string or null
val nullableString = stringSchema.withNullable
```

## Conditional Schemas

### if/then/else

Apply different schemas based on conditions:

```scala

// If type is string, require minLength
val conditionalSchema = JsonSchema.Object(
  `if` = Some(JsonSchema.ofType(JsonSchemaType.String)),
  `then` = Some(JsonSchema.string(minLength = Some(NonNegativeInt.literal(1)))),
  `else` = Some(JsonSchema.True)
)
```

### Dependent Schemas

Apply schemas when properties are present:

```scala

// If "credit_card" exists, require "billing_address"
val paymentSchema = JsonSchema.Object(
  properties = Some(ChunkMap(
    "credit_card" -> JsonSchema.ofType(JsonSchemaType.String),
    "billing_address" -> JsonSchema.ofType(JsonSchemaType.String)
  )),
  dependentRequired = Some(ChunkMap(
    "credit_card" -> Set("billing_address")
  ))
)
```

## Validation

### Basic Validation

```scala

val schema = JsonSchema.obj(
  properties = Some(ChunkMap(
    "name" -> JsonSchema.ofType(JsonSchemaType.String),
    "age" -> JsonSchema.integer(minimum = Some(BigDecimal(0)))
  )),
  required = Some(Set("name"))
)

val validJson = Json.Object(
  "name" -> Json.String("Alice"),
  "age" -> Json.Number(30)
)

val invalidJson = Json.Object(
  "age" -> Json.Number(-5)
)

// Using check() - returns Option[SchemaError]
schema.check(validJson)   // None (valid)
schema.check(invalidJson) // Some(SchemaError(...))

// Using conforms() - returns Boolean
schema.conforms(validJson)   // true
schema.conforms(invalidJson) // false
```

### Validation Options

Control validation behavior:

```scala

val schema = JsonSchema.string(format = Some("email"))
val value = Json.String("not-an-email")

// With format validation (default)
val strictOptions = ValidationOptions.formatAssertion
schema.check(value, strictOptions) // Some(error)

// Without format validation (format as annotation only)
val lenientOptions = ValidationOptions.annotationOnly
schema.check(value, lenientOptions) // None
```

### Error Messages

Validation errors include path information:

```scala

val schema = JsonSchema.obj(
  properties = Some(ChunkMap(
    "users" -> JsonSchema.array(
      items = Some(JsonSchema.obj(
        properties = Some(ChunkMap(
          "email" -> JsonSchema.string(format = Some("email"))
        ))
      ))
    )
  ))
)

val invalid = Json.Object(
  "users" -> Json.Array(
    Json.Object("email" -> Json.String("invalid"))
  )
)

schema.check(invalid) match {
  case Some(err) => println(err.message)
  // "String 'invalid' is not a valid email address"
  case None => println("Valid")
}
```

## Parsing and Serialization

### Parsing from JSON

```scala

// From JSON string
val parsed = JsonSchema.parse("""
  {
    "type": "object",
    "properties": {
      "name": { "type": "string" }
    },
    "required": ["name"]
  }
""")

// From Json value
val json = Json.Object(
  "type" -> Json.String("string"),
  "minLength" -> Json.Number(1)
)
val fromJson = JsonSchema.fromJson(json)
```

### Serializing to JSON

```scala

val schema = JsonSchema.string(
  NonNegativeInt.literal(1),
  NonNegativeInt.literal(100)
)

val json = schema.toJson
// {"type":"string","minLength":1,"maxLength":100}

val jsonString = json.print
```

## Format Validation

The following formats are supported for validation:

| Format | Description | Example |
|--------|-------------|---------|
| `date-time` | RFC 3339 date-time | `2024-01-15T10:30:00Z` |
| `date` | RFC 3339 full-date | `2024-01-15` |
| `time` | RFC 3339 full-time | `10:30:00Z` |
| `email` | Email address | `user@example.com` |
| `uuid` | RFC 4122 UUID | `550e8400-e29b-41d4-a716-446655440000` |
| `uri` | RFC 3986 URI | `https://example.com/path` |
| `uri-reference` | RFC 3986 URI-reference | `/path/to/resource` |
| `ipv4` | IPv4 address | `192.168.1.1` |
| `ipv6` | IPv6 address | `2001:db8::1` |
| `hostname` | RFC 1123 hostname | `example.com` |
| `regex` | ECMA-262 regex | `^[a-z]+$` |
| `duration` | ISO 8601 duration | `P3Y6M4DT12H30M5S` |
| `json-pointer` | RFC 6901 JSON Pointer | `/foo/bar/0` |

Format validation is enabled by default. Use `ValidationOptions.annotationOnly` to treat `format` as annotation only (per JSON Schema spec).

## Unevaluated Properties and Items

JSON Schema 2020-12 introduces `unevaluatedProperties` and `unevaluatedItems` for validating properties/items not matched by any applicator keyword:

```scala

// Reject any properties not defined in properties or patternProperties
val strictObject = JsonSchema.Object(
  properties = Some(ChunkMap(
    "name" -> JsonSchema.ofType(JsonSchemaType.String)
  )),
  unevaluatedProperties = Some(JsonSchema.False)
)

// Reject extra array items not matched by prefixItems or items
val strictArray = JsonSchema.Object(
  prefixItems = Some(NonEmptyChunk(
    JsonSchema.ofType(JsonSchemaType.String),
    JsonSchema.ofType(JsonSchemaType.Number)
  )),
  unevaluatedItems = Some(JsonSchema.False)
)
```

## Schema Object Fields

`JsonSchema.Object` supports all JSON Schema 2020-12 keywords:

### Core Vocabulary
- `$id`, `$schema`, `$anchor`, `$dynamicAnchor`
- `$ref`, `$dynamicRef` (limited support - see Limitations)
- `$defs`, `$comment`

### Applicator Vocabulary
- `allOf`, `anyOf`, `oneOf`, `not`
- `if`, `then`, `else`
- `properties`, `patternProperties`, `additionalProperties`
- `propertyNames`, `dependentSchemas`
- `prefixItems`, `items`, `contains`

### Unevaluated Vocabulary
- `unevaluatedProperties`, `unevaluatedItems`

### Validation Vocabulary
- `type`, `enum`, `const`
- `multipleOf`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`
- `minLength`, `maxLength`, `pattern`
- `minItems`, `maxItems`, `uniqueItems`, `minContains`, `maxContains`
- `minProperties`, `maxProperties`, `required`, `dependentRequired`

### Format Vocabulary
- `format`

### Content Vocabulary
- `contentEncoding`, `contentMediaType`, `contentSchema`

### Meta-Data Vocabulary
- `title`, `description`, `default`, `deprecated`
- `readOnly`, `writeOnly`, `examples`

## Limitations

### Not Implemented

The following features require reference resolution and are **not supported**:

| Feature | Description |
|---------|-------------|
| `$ref` to external URIs | References to other files or URLs |
| `$dynamicRef` / `$dynamicAnchor` | Dynamic reference resolution |
| `$id` resolution | Base URI changing and resolution |
| Remote references | Fetching schemas from URLs |
| Recursive schemas via `$ref` | Self-referential schemas using references |

Local `$ref` within the same schema is partially supported for `#/$defs/...` references only.

### Known Edge Cases

| Case | Behavior |
|------|----------|
| Float/integer numeric equality | `1.0` is not treated as equal to `1` for `const`/`enum` |
| String length | Measured in codepoints, not grapheme clusters |
| Some `unevaluatedItems` with `contains` | Edge cases involving item evaluation tracking |

### Test Suite Compliance

The implementation passes **817 of 844 tests** (97%+) from the official JSON Schema Test Suite for draft2020-12. The remaining tests require reference resolution features listed above.

## Complete Example

```scala

// Define a complex schema
val userSchema = JsonSchema.obj(
  properties = Some(ChunkMap(
    "id" -> JsonSchema.string(format = Some("uuid")),
    "email" -> JsonSchema.string(format = Some("email")),
    "name" -> JsonSchema.string(
      NonNegativeInt.literal(1),
      NonNegativeInt.literal(100)
    ),
    "age" -> JsonSchema.integer(
      minimum = Some(BigDecimal(0)),
      maximum = Some(BigDecimal(150))
    ),
    "roles" -> JsonSchema.array(
      items = Some(JsonSchema.enumOfStrings(
        NonEmptyChunk("admin", "user", "guest")
      )),
      minItems = Some(NonNegativeInt.literal(1)),
      uniqueItems = Some(true)
    ),
    "metadata" -> JsonSchema.obj(
      additionalProperties = Some(JsonSchema.ofType(JsonSchemaType.String))
    )
  )),
  required = Some(Set("id", "email", "name", "roles")),
  additionalProperties = Some(JsonSchema.False)
)

// Validate some data
val validUser = Json.Object(
  "id" -> Json.String("550e8400-e29b-41d4-a716-446655440000"),
  "email" -> Json.String("alice@example.com"),
  "name" -> Json.String("Alice"),
  "roles" -> Json.Array(Json.String("admin"), Json.String("user"))
)

val invalidUser = Json.Object(
  "id" -> Json.String("not-a-uuid"),
  "email" -> Json.String("invalid-email"),
  "name" -> Json.String(""),
  "roles" -> Json.Array(),
  "extra" -> Json.String("not allowed")
)

userSchema.conforms(validUser)   // true
userSchema.conforms(invalidUser) // false

// Get detailed errors
userSchema.check(invalidUser) match {
  case Some(err) => println(err.message)
  case None => println("Valid!")
}

// Serialize the schema
val schemaJson = userSchema.toJson.print
// Can be sent to other tools, stored, or shared
```

## Cross-Platform Support

`JsonSchema` works across 2 platforms:

- **JVM** - Full functionality
- **Scala.js** - Browser and Node.js

All features work identically across platforms.
