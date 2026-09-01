# Path Interpolator

> :::note
> To use the path interpolator `p"..."`, you must import the schema package: `import zio.blocks.schema._`
> :::

The path interpolator `p"..."` is a compile-time string interpolator for constructing `DynamicOptic` instances in ZIO Blocks. It provides a clean, concise syntax for building optic paths that navigate through complex data structures, with all parsing and validation happening at compile time for zero runtime overhead.

**Why use the path interpolator?**

Instead of manually constructing optics like this:

```scala
import zio.blocks.schema._

DynamicOptic(Vector(
  DynamicOptic.Node.Field("users"),
  DynamicOptic.Node.Elements,
  DynamicOptic.Node.Field("email")
))
```

You can write:

```scala
import zio.blocks.schema._

p".users[*].email"
```

The interpolator is **type-safe**, **compile-time validated**, and **performance-optimized** with zero runtime parsing overhead.

## Getting Started

Import the schema package to enable the path interpolator:

```scala
import zio.blocks.schema._

// Now you can use p"..." anywhere
val path = p".users[0].name"
```

## Key Features

- **✅ Zero Runtime Overhead**: All parsing happens at compile time
- **✅ Cross-Platform**: Works on Scala 2.13.x and Scala 3.x
- **✅ Compile-Time Safety**: Invalid paths are rejected during compilation
- **✅ No Runtime Interpolation**: Prevents accidental use of runtime values
- **✅ Rich Syntax**: Supports all `DynamicOptic` operations

## Syntax Reference

This section documents the complete path syntax with examples for each component type.

### Field Access

Access fields in records using dot notation. The leading dot is optional:

```scala
// With leading dot
p".name"           // Field("name")
p".firstName"      // Field("firstName")

// Without leading dot
p"name"            // Field("name")
p"firstName"       // Field("firstName")

// Chained fields
p".user.address.street"
// Equivalent to: Field("user") → Field("address") → Field("street")
```

Field names can also include special characters and keywords:

```scala
p"._private"       // Fields starting with underscore
p".field123"       // Fields with digits
p".café"           // Unicode field names
p".true"           // Keywords as field names (true, false, null)
```

### Index Access

Access sequence elements by index, multiple indices, or ranges.

To access a single element by index:

```scala
p"[0]"             // AtIndex(0)
p"[42]"            // AtIndex(42)
p"[2147483647]"    // AtIndex(Int.MaxValue)
```

To access multiple elements at specific indices:

```scala
p"[0,1,2]"         // AtIndices(Seq(0, 1, 2))
p"[0, 2, 5]"       // AtIndices(Seq(0, 2, 5)) - spaces allowed
p"[5,2,8,1]"       // Order preserved
```

To select a range of consecutive elements:

```scala
p"[0:5]"           // AtIndices(Seq(0, 1, 2, 3, 4))
p"[5:8]"           // AtIndices(Seq(5, 6, 7))
p"[3:4]"           // AtIndices(Seq(3)) - single element
p"[5:5]"           // AtIndices(Seq.empty) - empty range
p"[10:5]"          // AtIndices(Seq.empty) - inverted range
```

### Element Selectors

Select all elements in a sequence using wildcard syntax:

```scala
p"[*]"             // Elements - all elements
p"[:*]"            // Elements - alternative syntax
```

To navigate nested sequences:

```scala
p"[*][*]"          // Nested sequences: all elements of all elements
p"[*][0]"          // First element of each sequence
```

### Map Access

Access map values by key, where keys can be strings, integers, booleans, or characters.

To use string keys:

```scala
p"""{"host"}"""              // AtMapKey(String("host"))
p"""{"foo bar"}"""           // Keys with spaces
p"""{"日本語"}"""             // Unicode keys
p"""{"🎉"}"""                 // Emoji keys
p"""{""}"""                  // Empty string key
```

To use integer keys:

```scala
p"{42}"                      // AtMapKey(Int(42))
p"{0}"                       // AtMapKey(Int(0))
p"{-42}"                     // AtMapKey(Int(-42))
p"{2147483647}"              // AtMapKey(Int.MaxValue)
p"{-2147483648}"             // AtMapKey(Int.MinValue)
```

To use boolean keys:

```scala
p"{true}"                    // AtMapKey(Boolean(true))
p"{false}"                   // AtMapKey(Boolean(false))
```

To use character keys:

```scala
p"{'a'}"                     // AtMapKey(Char('a'))
p"{' '}"                     // AtMapKey(Char(' '))
p"{'9'}"                     // AtMapKey(Char('9'))
```

To use multiple keys of the same or mixed types:

```scala
p"""{"foo", "bar", "baz"}""" // AtMapKeys(Seq(...))
p"{1, 2, 3}"                 // Multiple integer keys
p"{true, false}"             // Multiple boolean keys

// Mixed types
p"""{"foo", 42}"""           // AtMapKeys(Seq(String("foo"), Int(42)))
p"""{"s", 'c', 42, true}"""  // All supported types
```

### Map Selectors

Select all keys or all values in a map:

```scala
p"{*}"             // MapValues - all values
p"{:*}"            // MapValues - alternative syntax
p"{*:}"            // MapKeys - all keys
```

To apply map selectors to nested maps:

```scala
p"{*}{*}"          // Nested maps: all values of all values
p"{*:}{*:}"        // All keys of all keys
```

### Variant Case Access

Navigate into a specific variant case using angle brackets:

```scala
p"<Left>"          // Case("Left")
p"<Right>"         // Case("Right")
p"<Some>"          // Case("Some")
p"<None>"          // Case("None")
```

Variant case names can include special characters and keywords:

```scala
p"<_Empty>"        // Cases starting with underscore
p"<Case1>"         // Cases with digits
p"<café>"          // Unicode case names
```

To navigate nested variant cases:

```scala
p"<A><B><C>"       // Nested variants
```

### Schema Search

Search for values matching a schema pattern anywhere in a data structure using the `#` prefix.

To search for nominal types by name:

```scala
p"#Person"         // Find all values of type Person
p"#User"           // Find all values of type User
p"#Address"        // Find all values of type Address
```

To search for primitive types:

```scala
p"#string"         // Find all string values
p"#int"            // Find all integer values
p"#boolean"        // Find all boolean values
p"#uuid"           // Find all UUID values
```

To search for records with specific field structures:

```scala
p"#record { name: string }"                // Find records with a string 'name' field
p"#record { name: string, age: int }"      // Find records with both fields
p"#record { items: list(Person) }"         // Nested schema
```

To search for variants with specific case structures:

```scala
p"#variant { Left: int, Right: string }"   // Find Either-like variants
```

To search for collection types:

```scala
p"#list(string)"                           // Find lists of strings
p"#list(Person)"                           // Find lists of Person
p"#map(string, int)"                       // Find maps from string to int
p"#option(Person)"                         // Find optional Person values
```

To match any value regardless of type:

```scala
p"#_"              // Find any value (matches everything)
```

To combine path navigation with schema search:

```scala
p".users#Person"               // Search for Person in users field
p"#Person.name"                // Find all Person values, then get name
p".items[*]#Person.email"      // Elements then search then field
p"#list(Person)#Person"        // Chained searches
```

## Escape Sequences

String and character literals support standard escape sequences:

| Escape | Result  | Description     |
|--------|---------|-----------------|
| `\n`   | newline | Line feed       |
| `\t`   | tab     | Horizontal tab  |
| `\r`   | return  | Carriage return |
| `\'`   | `'`     | Single quote    |
| `\"`   | `"`     | Double quote    |
| `\\`   | `\`     | Backslash       |

Here are some examples of escape sequences in use:

```scala
p"""{"foo\nbar"}"""          // String key with newline
p"""{"foo\tbar"}"""          // String key with tab
p"""{'\n'}"""                // Char key with newline
p"""{"foo\"bar"}"""          // Escaped quote in string
p"""{"foo\\bar"}"""          // Escaped backslash in string
```

## Combined Paths

Combine different path elements to navigate complex nested structures.

### Field → Sequence

Access sequence elements by index or range through a field:

```scala
p".items[0]"                 // First item
p".items[*]"                 // All items
p".items[0,1,2]"             // Items at indices 0, 1, 2
p".items[0:5]"               // Items 0 through 4
```

### Field → Map

Access map values by key through a field:

```scala
p""".config{"host"}"""       // Map lookup
p".settings{42}"             // Integer key
p".lookup{*}"                // All map values
p".lookup{*:}"               // All map keys
```

### Field → Variant

Navigate into variant cases through a field:

```scala
p".result<Success>"          // Variant case
p".response<Ok>"             // HTTP response variant
```

### Nested Structures

Combine different path elements to build complex navigation through nested data:

```scala
// Record in sequence
p".users[0].name"
// Equivalent to: Field("users") → AtIndex(0) → Field("name")

// All elements then field
p".users[*].email"
// Equivalent to: Field("users") → Elements → Field("email")

// Map values then field
p".lookup{*}.value"
// Equivalent to: Field("lookup") → MapValues → Field("value")

// Variant then field
p".response<Ok>.body"
// Equivalent to: Field("response") → Case("Ok") → Field("body")
```

### Deeply Nested Paths

Chain multiple path operators for deeply nested data structures:

```scala
// Complex nested navigation
p""".root.children[*].metadata{"tags"}[0]"""
// Field("root") → Field("children") → Elements → 
// Field("metadata") → AtMapKey("tags") → AtIndex(0)

// All node types in one path
p""".a[0]{"k"}<V>.b[*]{*}.c{*:}"""
// Field("a") → AtIndex(0) → AtMapKey("k") → Case("V") →
// Field("b") → Elements → MapValues → Field("c") → MapKeys
```

## Root and Empty Paths

An empty path refers to the root of the data structure:

```scala
p""                          // Empty path = root
```

## Compile-Time Safety

The path interpolator **rejects runtime interpolation** to prevent unsafe dynamic path construction.

### Examples of Safety Checks

Runtime interpolation will fail to compile:

```scala
import zio.blocks.schema._

val fieldName = "email"
val path = p".$fieldName"
// Error: Path interpolator does not support runtime arguments.
// error:
// Path interpolator does not support runtime arguments. Use only literal strings like p".field[0]"
// val path = p".$fieldName"
//            ^^^^^^^^^^^^^
```

Array indices also cannot be interpolated at runtime:

```scala
import zio.blocks.schema._

val idx = 5
val path = p"[$idx]"
// Error: Path interpolator does not support runtime arguments.
// error:
// Path interpolator does not support runtime arguments. Use only literal strings like p".field[0]"
// val path = p"[$idx]"
//            ^^^^^^^^^
```

Instead, use only literal strings at compile time:

```scala
val path = p".users[0].email"  // ✓ Works
```

### Parse Error Examples

Invalid syntax is caught at compile time. Here are examples of common errors:

```scala
import zio.blocks.schema._

// Unterminated string
p"""{"foo"""
// Error: Unterminated string literal

// Invalid escape sequence  
p"""{"foo\x"}"""
// Error: Invalid escape sequence

// Unexpected character
p".field@"
// Error: Unexpected character '@'

// Invalid identifier
p"."
// Error: Invalid identifier
// error: 
// Unterminated string literal starting at position 1
// error: 
// Invalid escape sequence '\x' at position 6
// error: 
// Unexpected character '@' at position 6. Expected field, index, map, or variant accessor
// error: 
// Invalid identifier at position 1
// error: 
// Conflicting definitions:
// val path: zio.blocks.schema.DynamicOptic in object MdocApp at line 14 and
// val path: zio.blocks.schema.DynamicOptic in object MdocApp at line 114
//
```

## Performance

**Zero Runtime Overhead**

All path parsing and validation occurs at **compile time**. The interpolator generates the exact same bytecode as manual `DynamicOptic` construction. Here's the comparison:

```scala
// These produce identical bytecode:
p".users[*].email"

DynamicOptic(Vector(
  DynamicOptic.Node.Field("users"),
  DynamicOptic.Node.Elements,
  DynamicOptic.Node.Field("email")
))
```

There is **no runtime parsing**, **no reflection**, and **no performance penalty**.

## Examples

This section shows practical examples of using the path interpolator with realistic data structures.

### Accessing Nested Fields

To access deeply nested fields in a data structure:

```scala
import zio.blocks.schema._

case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, address: Address)

// Access nested street field
val streetPath = p".address.street"

// Use with DynamicValue - example (requires actual DynamicValue instance)
val person: DynamicValue = ???
val street = person.get(streetPath)
```

### Working with Collections

To work with sequences and access elements by index or range:

```scala
import zio.blocks.schema._

case class User(id: Int, email: String, tags: Seq[String])
case class Company(name: String, users: Seq[User])

// Get all user emails
val emailsPath = p".users[*].email"

// Get first user's first tag
val firstTagPath = p".users[0].tags[0]"

// Get specific users by index
val specificUsersPath = p".users[0,2,5]"
```

### Map Lookups

To work with map data structures and access values by key:

```scala
import zio.blocks.schema._

case class Config(
  settings: Map[String, String],
  ports: Map[Int, String]
)

// Lookup by string key
val hostPath = p"""settings{"host"}"""

// Lookup by integer key
val httpPortPath = p"ports{80}"

// Get all config values
val allValuesPath = p"settings{*}"

// Get all port numbers (keys)
val allPortsPath = p"ports{*:}"
```

### Variant Case Handling

To navigate variant cases in your data structures:

```scala
import zio.blocks.schema._

sealed trait Result[+A]
case class Success[A](value: A) extends Result[A]
case class Failure(error: String) extends Result[Nothing]

case class Response(result: Result[User])
```

Use paths to navigate into specific cases:

```scala
// Navigate into Success case
val successValuePath = p".result<Success>.value"

// Navigate into Failure case
val errorPath = p".result<Failure>.error"
```

### Real-World Example: API Response

To extract specific data from complex nested API response structures:

```scala
import zio.blocks.schema._

case class Metadata(tags: Seq[String], version: Int)
case class Item(id: String, data: String, metadata: Metadata)
case class ApiResponse(
  status: String,
  items: Seq[Item],
  config: Map[String, String]
)

// Get the version from the first item's metadata
val versionPath = p".items[0].metadata.version"

// Get all item IDs
val allIdsPath = p".items[*].id"

// Get the first tag of each item
val firstTagsPath = p".items[*].metadata.tags[0]"

// Lookup config value
val apiKeyPath = p"""config{"api_key"}"""
```

## Before & After Comparison

This comparison shows how the path interpolator simplifies optic path construction compared to manual approaches.

### Manual Construction (Before)

Manual path construction requires verbose imports and careful construction:

```scala
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicOptic.Node
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue

// Simple path - verbose and error-prone
val path1 = DynamicOptic(Vector(
  Node.Field("users"),
  Node.AtIndex(0),
  Node.Field("email")
))

// Complex path - extremely verbose
val path2 = DynamicOptic(Vector(
  Node.Field("root"),
  Node.Field("children"),
  Node.Elements,
  Node.Field("metadata"),
  Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("tags"))),
  Node.AtIndex(0)
))

// Map with multiple keys
val path3 = DynamicOptic(Vector(
  Node.Field("data"),
  Node.AtMapKeys(Seq(
    DynamicValue.Primitive(PrimitiveValue.String("foo")),
    DynamicValue.Primitive(PrimitiveValue.String("bar")),
    DynamicValue.Primitive(PrimitiveValue.Int(42))
  ))
))
```

### Path Interpolator (After)

```scala
import zio.blocks.schema._

// Simple path - clean and readable
val path1 = p".users[0].email"

// Complex path - still clean and readable
val path2 = p""".root.children[*].metadata{"tags"}[0]"""

// Map with multiple keys - concise
val path3 = p"""data{"foo", "bar", 42}"""
```

The path interpolator provides significant benefits:

- **90% less code** for typical paths
- **Easier to read** and understand intent
- **Easier to write** and maintain
- **Compile-time validated** - catches errors immediately
- **No performance difference** - identical bytecode

## Practical Usage Patterns

These patterns demonstrate effective ways to use the path interpolator in real-world scenarios.

### Building Paths Dynamically (at Compile Time)

While runtime variables are not supported, you can compose literal paths at compile time:

```scala
import zio.blocks.schema._

// You can't use runtime variables, but you can compose literal paths:
val basePath = p".data.items"
val emailPath = basePath(p"[*].email")
// Same as: p".data.items[*].email"
```

### Working with DynamicValue

To navigate and extract values from dynamic data:

```scala
import zio.blocks.schema._

val data: DynamicValue = ???

// Navigate and extract
val value = data.get(p".users[0].email")

// Update at path (using appropriate constructor)
val ageValue: DynamicValue = ???
val updated = data.set(p".users[0].age", ageValue)
```

### Integration with Schema Optics

To use path interpolators with schema-based optics:

```scala
import zio.blocks.schema._

case class UserRecord(name: String, email: String)
object UserRecord extends CompanionOptics[UserRecord] {
  implicit val schema: Schema[UserRecord] = Schema.derived
  
  // Use path interpolator for complex lenses
  val email = $(_.email)
}

// DynamicOptic can be used for runtime path resolution
val dynamicPath = p".email"
```

## Tips and Best Practices

1. **Use the leading dot for clarity**: While optional, `p".field"` is more explicit than `p"field"`:

   ```scala mdoc:compile-only
   val userPath = p".users[0]"
   val emailPath = userPath(p".email")
   ```

4. **Use raw strings for map keys**: Triple-quoted strings avoid escape hell:

   ```scala mdoc:compile-only
   p"""config{"api.key"}"""  // Better than p"config{\"api.key\"}"
   ```

5. **Document complex paths**: Add comments explaining what nested paths navigate:

   ```scala mdoc:compile-only
   // Get the first tag from each user's metadata
   val tagsPath = p".users[*].metadata.tags[0]"
   ```

## Limitations

- **No runtime interpolation**: You cannot use variables in paths (this is by design for safety)
- **No arithmetic in ranges**: Ranges must be literal integers (e.g., `[0:5]` not `[0:n]`)
- **No string interpolation**: Only literal strings work with the interpolator
- **Map keys limited to primitives**: Only String, Int, Char, and Boolean keys are supported

These limitations ensure compile-time safety and zero runtime overhead.

## Debug-Friendly toString

`DynamicOptic` instances have a custom `toString` that produces output matching the `p"..."` interpolator syntax. This makes debugging easier because you can copy the output directly into your code:

```scala
val optic = DynamicOptic.root.field("users").elements.field("email")
// optic: DynamicOptic = DynamicOptic(
//   IndexedSeq(Field("users"), Elements, Field("email"))
// )
println(optic)  // Output: .users[*].email
// .users[*].email

// The output can be copy-pasted into p"..."
val same = p".users[*].email"
// same: DynamicOptic = DynamicOptic(
//   Vector(Field("users"), Elements, Field("email"))
// )
```

**Examples:**

| DynamicOptic Construction                            | Interpolator Syntax |
|------------------------------------------------------|---------------------|
| `DynamicOptic.root.field("name")`                    | `p".name"`           |
| `DynamicOptic.root.field("address").field("street")` | `p".address.street"` |
| `DynamicOptic.root.caseOf("Some")`                    | `p"<Some>"`          |
| `DynamicOptic.root.at(0)`                             | `p"[0]"`             |
| `DynamicOptic.root.atIndices(0, 2, 5)`                 | `p"[0,2,5]"`         |
| `DynamicOptic.elements`                                | `p"[*]"`             |
| `DynamicOptic.root.atKey("host")`                      | `p"{"host"}"`        |
| `DynamicOptic.root.atKey(80)`                          | `p"{80}"`            |
| `DynamicOptic.mapValues`                               | `p"{*}"`             |
| `DynamicOptic.mapKeys`                                 | `p"{*:}"`            |
| `DynamicOptic.wrapped`                                 | `p".~"`              |
| `DynamicOptic.root.searchSchema(SchemaRepr.Nominal("Person"))` | `p"#Person"` |
| `DynamicOptic.root.searchSchema(SchemaRepr.Primitive("string"))` | `p"#string"` |

## Summary

The `p"..."` path interpolator provides:

- **Concise syntax** for building optic paths
- **Compile-time parsing** with zero runtime overhead  
- **Type-safe navigation** through complex data structures
- **Cross-platform support** for Scala 2 and Scala 3
- **Rich feature set** covering all DynamicOptic operations

Use it whenever you need to construct `DynamicOptic` paths for navigating dynamic data structures in ZIO Blocks.
