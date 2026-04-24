# Patching

> The Patching system in ZIO Blocks provides a type-safe, serializable way to describe and apply transformations to data structures. Unlike direct mutations or lens-based updates, patches are **first-class values** that can be serialized, transmitted over the network, stored for audit logs, and composed together.

The Patching system in ZIO Blocks provides a type-safe, serializable way to describe and apply transformations to data structures. Unlike direct mutations or lens-based updates, patches are **first-class values** that can be serialized, transmitted over the network, stored for audit logs, and composed together.

## Overview

A `Patch[S]` represents a sequence of operations that transform a value of type `S`. Because patches use serializable operations and reflective optics for navigation, they enable powerful use cases:

- **Remote Patching** — Send patches over the network to update remote state without transmitting entire objects
- **Audit Logs** — Record patches as a log of changes for compliance, debugging, or undo functionality
- **CRDT-like Operations** — Use commutative operations like `increment` that can be safely applied in any order
- **Optimistic Updates** — Apply patches locally while syncing with a server
- **Schema Evolution** — Patches work with the schema system, adapting as data structures evolve

:::note
For **untyped JSON patching** without a schema, use [`JsonPatch`](./json-patch.md) instead. `JsonPatch` is optimized for diff-and-apply workflows on raw JSON values and provides compact delta representations without requiring typed optics.
:::

```scala

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name: Lens[Person, String] = optic(_.name)
  val age: Lens[Person, Int] = optic(_.age)
}

// Create patches using smart constructors
val patch1 = Patch.set(Person.name, "John")
val patch2 = Patch.increment(Person.age, 1)

// Compose patches
val combined = patch1 ++ patch2

// Apply the patch
val jane = Person("Jane", 25)
val result = combined(jane)  // Person("John", 26)
```

## Creating Patches

Patches are created using smart constructors on the `Patch` companion object. Each constructor takes an optic to specify the target location and the operation parameters.

### Setting Values

The `set` operation replaces a value at the specified location:

```scala
case class Address(street: String, city: String, zip: String)
object Address extends CompanionOptics[Address] {
  implicit val schema: Schema[Address] = Schema.derived
  val street: Lens[Address, String] = optic(_.street)
  val city: Lens[Address, String] = optic(_.city)
}

case class Person(name: String, address: Address)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name: Lens[Person, String] = optic(_.name)
  val address: Lens[Person, Address] = optic(_.address)
  val city: Lens[Person, String] = optic(_.address.city)
}

// Set a simple field
val setName = Patch.set(Person.name, "Alice")

// Set a nested field
val setCity = Patch.set(Person.city, "San Francisco")

// Set an entire nested record
val newAddress = Address("123 Main St", "NYC", "10001")
val setAddress = Patch.set(Person.address, newAddress)
```

The `set` operation works with all optic types:
- **Lens** — Sets a single field
- **Optional** — Sets a value if the path exists
- **Traversal** — Sets all matching elements to the same value
- **Prism** — Sets a variant case

### Numeric Increments

The `increment` operation adds a delta to numeric fields. This is a **commutative operation** — applying increments in any order produces the same result, making it ideal for distributed systems:

```scala
case class Counter(count: Int, total: Long, balance: BigDecimal)
object Counter extends CompanionOptics[Counter] {
  implicit val schema: Schema[Counter] = Schema.derived
  val count: Lens[Counter, Int] = optic(_.count)
  val total: Lens[Counter, Long] = optic(_.total)
  val balance: Lens[Counter, BigDecimal] = optic(_.balance)
}

// Increment various numeric types
val addOne = Patch.increment(Counter.count, 1)
val addTen = Patch.increment(Counter.count, 10)
val subtractFive = Patch.increment(Counter.count, -5)

// Works with all numeric types
val addToTotal = Patch.increment(Counter.total, 1000L)
val addToBalance = Patch.increment(Counter.balance, BigDecimal("99.99"))

// Compose multiple increments
val combined = addOne ++ addTen ++ subtractFive
val counter = Counter(0, 0L, BigDecimal(0))
combined(counter)  // Counter(6, 0, 0)
```

Supported numeric types:
- `Int`, `Long`, `Short`, `Byte`
- `Float`, `Double`
- `BigInt`, `BigDecimal`

### Temporal Operations

Patches support temporal arithmetic with `addDuration` and `addPeriod`:

```scala

case class Event(
  timestamp: Instant,
  scheduledDate: LocalDate,
  scheduledTime: LocalDateTime,
  duration: Duration
)
object Event extends CompanionOptics[Event] {
  implicit val schema: Schema[Event] = Schema.derived
  val timestamp: Lens[Event, Instant] = optic(_.timestamp)
  val scheduledDate: Lens[Event, LocalDate] = optic(_.scheduledDate)
  val scheduledTime: Lens[Event, LocalDateTime] = optic(_.scheduledTime)
  val duration: Lens[Event, Duration] = optic(_.duration)
}

// Add duration to an Instant
val postpone1Hour = Patch.addDuration(Event.timestamp, Duration.ofHours(1))

// Add period to a LocalDate
val postpone1Week = Patch.addPeriod(Event.scheduledDate, Period.ofWeeks(1))

// Add both period and duration to a LocalDateTime
val postpone = Patch.addPeriodAndDuration(
  Event.scheduledTime,
  Period.ofDays(1),
  Duration.ofHours(2)
)

// Add duration to a Duration field

val extendDuration = Patch.addDuration(Event.duration, Duration.ofMinutes(30))
```

### String Edits

The `editString` operation applies character-level edits to strings:

```scala
case class Document(content: String)
object Document extends CompanionOptics[Document] {
  implicit val schema: Schema[Document] = Schema.derived
  val content: Lens[Document, String] = optic(_.content)
}

// Insert text at position
val insertHello = Patch.editString(
  Document.content,
  Vector(Patch.StringOp.Insert(0, "Hello "))
)

// Delete characters
val deleteFirst5 = Patch.editString(
  Document.content,
  Vector(Patch.StringOp.Delete(0, 5))
)

// Append text
val appendBang = Patch.editString(
  Document.content,
  Vector(Patch.StringOp.Append("!"))
)

// Replace a range (delete then insert)
val replaceWorld = Patch.editString(
  Document.content,
  Vector(Patch.StringOp.Modify(6, 5, "Universe"))
)

// Combine multiple edits (applied in sequence)
val doc = Document("Hello World")
val edits = Patch.editString(
  Document.content,
  Vector(
    Patch.StringOp.Delete(5, 6),      // "Hello"
    Patch.StringOp.Append(" there!")  // "Hello there!"
  )
)
edits(doc)  // Document("Hello there!")
```

String edit operations:
- `Insert(index, text)` — Insert text at the given position
- `Delete(index, length)` — Delete characters starting at index
- `Append(text)` — Append text to the end
- `Modify(index, length, text)` — Replace a range with new text

## Working with Collections

### Appending Elements

The `append` operation adds elements to the end of a sequence:

```scala
case class TodoList(items: Vector[String])
object TodoList extends CompanionOptics[TodoList] {
  implicit val schema: Schema[TodoList] = Schema.derived
  val items: Lens[TodoList, Vector[String]] = optic(_.items)
}

val addItems = Patch.append(
  TodoList.items,
  Vector("Buy groceries", "Walk the dog")
)

val list = TodoList(Vector("Existing task"))
addItems(list)  // TodoList(Vector("Existing task", "Buy groceries", "Walk the dog"))
```

Works with `Vector`, `List`, `Seq`, `IndexedSeq`, and `LazyList`. Use the appropriate implicit:

```scala

```

### Inserting at Index

The `insertAt` operation inserts elements at a specific position:

```scala

val insertAtStart = Patch.insertAt(
  TodoList.items,
  0,
  Vector("First priority")
)

val insertInMiddle = Patch.insertAt(
  TodoList.items,
  1,
  Vector("Second item", "Third item")
)

val list = TodoList(Vector("A", "B", "C"))
insertInMiddle(list)  // TodoList(Vector("A", "Second item", "Third item", "B", "C"))
```

### Deleting Elements

The `deleteAt` operation removes elements starting at an index:

```scala

// Delete one element at index 1
val deleteOne = Patch.deleteAt(TodoList.items, 1, 1)

// Delete three elements starting at index 0
val deleteThree = Patch.deleteAt(TodoList.items, 0, 3)

val list = TodoList(Vector("A", "B", "C", "D"))
deleteOne(list)  // TodoList(Vector("A", "C", "D"))
```

### Modifying Elements

The `modifyAt` operation applies a nested patch to an element at a specific index:

```scala
case class Task(title: String, priority: Int)
object Task extends CompanionOptics[Task] {
  implicit val schema: Schema[Task] = Schema.derived
  val title: Lens[Task, String] = optic(_.title)
  val priority: Lens[Task, Int] = optic(_.priority)
}

case class Project(tasks: Vector[Task])
object Project extends CompanionOptics[Project] {
  implicit val schema: Schema[Project] = Schema.derived
  val tasks: Lens[Project, Vector[Task]] = optic(_.tasks)
}

// Create a patch for the nested Task type
val increasePriority = Patch.increment(Task.priority, 1)

// Modify the task at index 0
val modifyFirst = Patch.modifyAt(Project.tasks, 0, increasePriority)

val project = Project(Vector(
  Task("Build feature", 1),
  Task("Write tests", 2)
))
modifyFirst(project)
// Project(Vector(Task("Build feature", 2), Task("Write tests", 2)))
```

## Working with Maps

### Adding Keys

The `addKey` operation adds a new key-value pair to a map:

```scala
case class Config(settings: Map[String, Int])
object Config extends CompanionOptics[Config] {
  implicit val schema: Schema[Config] = Schema.derived
  val settings: Lens[Config, Map[String, Int]] = optic(_.settings)
}

val addTimeout = Patch.addKey(Config.settings, "timeout", 30)
val addRetries = Patch.addKey(Config.settings, "retries", 3)

val config = Config(Map("port" -> 8080))
val combined = addTimeout ++ addRetries
combined(config)  // Config(Map("port" -> 8080, "timeout" -> 30, "retries" -> 3))
```

### Removing Keys

The `removeKey` operation removes a key from a map:

```scala
val removePort = Patch.removeKey(Config.settings, "port")

val config = Config(Map("port" -> 8080, "timeout" -> 30))
removePort(config)  // Config(Map("timeout" -> 30))
```

### Modifying Values

The `modifyKey` operation applies a nested patch to the value at a specific key:

```scala
case class UserSettings(preferences: Map[String, Int])
object UserSettings extends CompanionOptics[UserSettings] {
  implicit val schema: Schema[UserSettings] = Schema.derived
  val preferences: Lens[UserSettings, Map[String, Int]] = optic(_.preferences)
}

// Create a patch that increments an Int
val incrementValue: Patch[Int] = {
  implicit val intSchema: Schema[Int] = Schema[Int]
  Patch(
    DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(10))),
    intSchema
  )
}

val increaseVolume = Patch.modifyKey(UserSettings.preferences, "volume", incrementValue)

val settings = UserSettings(Map("volume" -> 50, "brightness" -> 80))
increaseVolume(settings)  // UserSettings(Map("volume" -> 60, "brightness" -> 80))
```

## Composing Patches

Patches compose with the `++` operator. The result applies the first patch, then the second:

```scala
case class Account(name: String, balance: Int, active: Boolean)
object Account extends CompanionOptics[Account] {
  implicit val schema: Schema[Account] = Schema.derived
  val name: Lens[Account, String] = optic(_.name)
  val balance: Lens[Account, Int] = optic(_.balance)
  val active: Lens[Account, Boolean] = optic(_.active)
}

val rename = Patch.set(Account.name, "Premium Account")
val deposit = Patch.increment(Account.balance, 100)
val activate = Patch.set(Account.active, true)

// Compose all patches
val upgrade = rename ++ deposit ++ activate

val account = Account("Basic", 50, false)
upgrade(account)  // Account("Premium Account", 150, true)
```

Composition is associative: `(a ++ b) ++ c` equals `a ++ (b ++ c)`.

The empty patch acts as an identity element:

```scala
val empty = Patch.empty[Account]
val patch = Patch.increment(Account.balance, 100)

(patch ++ empty)(account) == patch(account)  // true
(empty ++ patch)(account) == patch(account)  // true
```

## Applying Patches

### Basic Application

The simplest way to apply a patch uses the `apply` method, which uses `Lenient` mode and returns the original value on failure:

```scala
val patch = Patch.increment(Account.balance, 100)
val account = Account("Test", 50, true)

val result: Account = patch(account)  // Account("Test", 150, true)
```

### Application Modes

For more control, use the overload that takes a `PatchMode`:

```scala
val result: Either[SchemaError, Account] = patch(account, PatchMode.Strict)
```

#### PatchMode.Strict

Fails immediately if any operation cannot be applied:

```scala
case class Data(items: Vector[Int])
object Data extends CompanionOptics[Data] {
  implicit val schema: Schema[Data] = Schema.derived
  val items: Lens[Data, Vector[Int]] = optic(_.items)
}

// Try to delete at an invalid index
val badDelete = Patch.deleteAt(Data.items, 10, 1)
val data = Data(Vector(1, 2, 3))

badDelete(data, PatchMode.Strict)
// Left(SchemaError(...index out of bounds...))

badDelete(data, PatchMode.Lenient)
// Right(Data(Vector(1, 2, 3)))  // Operation skipped
```

Use `Strict` when you need to know if every operation succeeded.

#### PatchMode.Lenient

Skips operations that fail preconditions and continues with the rest:

```scala
val patch1 = Patch.deleteAt(Data.items, 10, 1)  // Will fail
val patch2 = Patch.increment(Counter.count, 5)   // Will succeed

val combined = patch1 ++ patch2

combined(data, PatchMode.Lenient)
// Skips the invalid delete, applies the increment
```

Use `Lenient` for best-effort patching where partial success is acceptable.

#### PatchMode.Clobber

Attempts to force operations through, using fallback behaviors:

```scala
// Adding a key that already exists
val addExisting = Patch.addKey(Config.settings, "port", 9000)
val config = Config(Map("port" -> 8080))

addExisting(config, PatchMode.Strict)
// Left(SchemaError(...key already exists...))

addExisting(config, PatchMode.Clobber)
// Right(Config(Map("port" -> 9000)))  // Overwrites existing key
```

Use `Clobber` when you want to force updates regardless of preconditions.

### Option-Based Application

Use `applyOption` for a simpler API that returns `None` on any failure:

```scala
val patch = Patch.increment(Account.balance, 100)
val result: Option[Account] = patch.applyOption(account)
// Some(Account("Test", 150, true))
```

## Diffing Values

The `diff` method computes a minimal patch that transforms one value into another:

```scala
val old = Person("Alice", 25)
val new = Person("Alice", 26)

// Compute the difference
val dynamicOld = Schema[Person].toDynamicValue(old)
val dynamicNew = Schema[Person].toDynamicValue(new)
val patch = dynamicOld.diff(dynamicNew)

// Apply to transform old into new
patch(dynamicOld, PatchMode.Strict)
// Right(dynamicNew)
```

The differ produces minimal patches using type-appropriate operations:
- **Numeric fields** — Uses delta operations (`+1` instead of `set 26`)
- **Strings** — Uses edit operations when more compact than replacement
- **Records** — Only includes changed fields
- **Sequences** — Uses LCS algorithm to compute minimal insert/delete operations
- **Maps** — Produces add/remove/modify operations for changed entries

### Diff Example

```scala
case class User(
  name: String,
  scores: Vector[Int],
  metadata: Map[String, String]
)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val v1 = User(
  "Alice",
  Vector(10, 20, 30),
  Map("level" -> "1", "status" -> "active")
)

val v2 = User(
  "Alice",
  Vector(10, 25, 30, 40),
  Map("level" -> "2", "status" -> "active")
)

val d1 = Schema[User].toDynamicValue(v1)
val d2 = Schema[User].toDynamicValue(v2)
val patch = d1.diff(d2)

// The patch contains:
// - scores: delete at index 1, insert 25 at index 1, append 40
// - metadata["level"]: increment from "1" to "2" (or set if strings)
```

## Serializable Operations

All patch operations are serializable through the schema system. Each operation type has an implicit `Schema` instance:

```scala
// Patches can be converted to/from DynamicValue
val patch = Patch.increment(Account.balance, 100)
val dynamicPatch = Schema[DynamicPatch].toDynamicValue(patch.dynamicPatch)

// Serialize to JSON, Avro, MessagePack, etc.

val json = JsonEncoder.encode(patch.dynamicPatch)
```

This enables storing patches in databases, sending them over APIs, or logging them for audit purposes.

## Operation Reference

### Value Operations

| Operation | Description |
|-----------|-------------|
| `Patch.set(optic, value)` | Set a value at the optic path |
| `Patch.replace(optic, value)` | Alias for `set` |
| `Patch.empty[S]` | Empty patch (identity) |

### Numeric Operations

| Operation | Description |
|-----------|-------------|
| `Patch.increment(optic, delta)` | Add delta to numeric field |

### Temporal Operations

| Operation | Description |
|-----------|-------------|
| `Patch.addDuration(optic, duration)` | Add duration to `Instant` or `Duration` |
| `Patch.addPeriod(optic, period)` | Add period to `LocalDate` or `Period` |
| `Patch.addPeriodAndDuration(optic, period, duration)` | Add both to `LocalDateTime` |

### String Operations

| Operation | Description |
|-----------|-------------|
| `Patch.editString(optic, edits)` | Apply string edit operations |
| `StringOp.Insert(index, text)` | Insert text at position |
| `StringOp.Delete(index, length)` | Delete characters |
| `StringOp.Append(text)` | Append text |
| `StringOp.Modify(index, length, text)` | Replace range |

### Sequence Operations

| Operation | Description |
|-----------|-------------|
| `Patch.append(optic, elements)` | Append elements to end |
| `Patch.insertAt(optic, index, elements)` | Insert at index |
| `Patch.deleteAt(optic, index, count)` | Delete elements |
| `Patch.modifyAt(optic, index, patch)` | Apply nested patch at index |

### Map Operations

| Operation | Description |
|-----------|-------------|
| `Patch.addKey(optic, key, value)` | Add key-value pair |
| `Patch.removeKey(optic, key)` | Remove key |
| `Patch.modifyKey(optic, key, patch)` | Apply nested patch to value |

## Best Practices

1. **Use increment for counters** — Increment operations are commutative and merge-friendly
2. **Prefer fine-grained patches** — Instead of replacing entire records, patch individual fields
3. **Check isEmpty before applying** — Skip no-op patches for efficiency
4. **Use Strict mode for validation** — Catch errors early in development
5. **Use Lenient mode for resilience** — Handle partial updates gracefully in production
6. **Serialize patches for audit** — Store the patch representation, not just before/after snapshots
