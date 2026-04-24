# Register System

> The register system is one of the key innovations in ZIO Blocks (ZIO Schema 2) that enables **zero-allocation, box-free construction and deconstruction** of data types.

The register system is one of the key innovations in ZIO Blocks (ZIO Schema 2) that enables **zero-allocation, box-free construction and deconstruction** of data types. 

## The Problem: Boxing/Unboxing Overheads

When building generic abstractions over data types (like serialization libraries), you need to describe all possible constructions and deconstructions uniformly. The traditional approach uses tuples and boxed primitives. For example assume we have a simple record data type:

```scala
case class Person(name: String, age: Int)
```

A traditional library might represent it as tuple when serializing/deserializing:

```scala
trait Tuple
case class Tuple2[A, B]( _1: A, _2: B)          extends Tuple
case class Tuple3[A, B, C](_1: A, _2: B, _3: C) extends Tuple
// ...
```

Tuple is generic data structure that can hold values of any type. So serializing `Person` would involve converting it to/from `Tuple2[String, Int]`:

```scala
case class Person(name: String, age: Int)
val person = Person("john", 42)

// Generic construction via tuples:
val tuple: (String, Int) = ("john", 42)  // Tuple2[String, Int]
```

The problem is that `Int` is a primitive type, and in order to fit it into a tuple, it must be **boxed** into `java.lang.Integer`. Why? Because tuples can only hold references to objects, not raw primitive values. They are generic containers that work uniformly for any type.

So the actual memory representation of the tuple looks like this:

```text
Stack:                          Heap:

                     
                     ┌────────────────────────┐      ┌─────────────────┐
┌─────────────┐      │ Tuple2 object          │      │ String "john"   │
│ tuple (ref) │─────▶│  _1: ────────────────────────▶│  char[]/byte[]: ───────▶[j][o][h][n]
└─────────────       │  _2: ──────┐           │      └─────────────────┘
                     └────────────────────────┘      
                                  │
                                  ▼
                          ┌─────────────────┐
                          │ Integer object  │
                          │  value: 42      │  ← 4 bytes for int
                          └─────────────────┘
```

This boxing creates significant runtime overhead because:

1. **Primitive boxing**: Values like `Int`, `Long`, `Double` must be wrapped in heap-allocated objects (`java.lang.Integer`, etc.)
2. **Tuple allocation**: All constructor arguments get wrapped in tuple objects. This creates extra allocations for every construction/deconstruction.
3. **Garbage collection pressure**: Each serialization/deserialization creates temporary objects

## The Solution: Register-Based Architecture

ZIO Blocks introduces a novel register-based design that completely eliminates tupling and boxing:

> "Zero allocation, zero boxing, construction and deconstruction of records. It doesn't get faster than this. You can't make generic code in Scala faster than what's been done here."
> — John De Goes, LambdaConf 2025

Instead of tuples, ZIO Blocks uses the `Registers` data structure, which contains:

1. A **byte array** for storing primitives (Int, Long, Double, Float, Boolean, Byte, Char, Short)
2. An **object array** for storing references to heap-allocated objects (AnyRef, which is the supertype of all reference types in Scala including String, custom classes, etc.)

This classification determines where values are stored in the `Registers` data structure. All primitive types use the same `bytes` register to store raw values, and all reference types use the same `objects` register to store references:

```scala
// Conceptual structure of Registers
class Registers {
  var bytes: Array[Byte]     = new Array[Byte](byteArrayLength)      // Stores all primitives efficiently
  var objects: Array[AnyRef] = new Array[AnyRef](objectArrayLength)  // Stores object references
}
```

The `Registers` class is a mutable data structure that serves as an intermediate buffer for **construction** and **deconstruction** operations. So it has methods to set and get values for each primitive type and for object references:

```scala
class Registers {
  private var bytes: Array[Byte]     = new Array[Byte](byteArrayLength)
  private var objects: Array[AnyRef] = new Array[AnyRef](objectArrayLength)

  // Methods to get/set primitive values in byte array (getInt/setInt, getBoolean/setBoolean, etc.)
  def getInt(offset: RegisterOffset): Int = ???
  def setInt(offset: RegisterOffset, value: Int): Unit = ???
  
  def setBoolean(offset: RegisterOffset, value: Boolean): Unit = ???
  def getBoolean(offset: RegisterOffset): Boolean = ???

  // Two methods to get/set object references in object array
  def getObject(offset: RegisterOffset): AnyRef = ???
  def setObject(offset: RegisterOffset, value: AnyRef): Unit = ???
}
```

This design allows primitives to be stored directly in their native binary representation without boxing, while objects are stored as simple references. So, when you use registers, the library adds zero overhead to construction and deconstruction — no tuples and no boxing of primitives.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Registers                                │
├─────────────────────────────────────────────────────────────────┤
│  Byte Array (primitives)          │  Object Array (references)  │
│  ┌───┬───┬───┬───┬───┬───┬───┐    │  ┌───────┬───────┬───────┐  │
│  │ B │ B │ S │ S │ I │ I │...│    │  │ Obj0  │ Obj1  │ Obj2  │  │
│  └───┴───┴───┴───┴───┴───┴───┘    │  └───────┴───────┴───────┘  │
│  (raw bytes, no boxing)           │  (String, etc.)             │
└─────────────────────────────────────────────────────────────────┘
```

One powerful aspect of the register system is that you can reuse registers. If you reuse registers, then without any allocation you can construct and deconstruct things all day long. This is particularly valuable in high-throughput scenarios like:
- Deserializing streams of records
- Batch processing operations
- Real-time data pipelines

## RegisterOffset: Tracking Positions

`RegisterOffset` is a compact way to track positions within the `Registers` structure. It uses a single `Int` to encode two pieces of information:
1. Byte offset (for primitives) in the upper 16 bits
2. Object offset (for references) in the lower 16 bits

You can think of `RegisterOffset` as a cursor that tells you where to read/write the next primitive or object value. For example:
1. `RegisterOffset.Zero` represents the starting position with zero indexes for both primitives and objects.
2. `RegisterOffset(objects = 1)` indicates that the next object reference should be stored at index 1 in the object array, with zero byte offset for primitives.
3. `RegisterOffset(bytes = 10, objects = 3)` indicates that the next primitive value should be retrieved/stored starting at byte index 10 in the byte array, and the next object reference should be stored at index 3 in the object array.
4. `RegisterOffset(bytes = 4, ints = 2, objects = 3)` indicates that the next primitive value should be retrieved/stored starting at byte index 8 (4 bytes + 2 ints × 4 bytes each) in the byte array, and the next object reference should be stored at index 3 in the object array.

The byte offset is calculated by weighting each type by its size:

```
primitiveBytes = booleans + bytes 
               + (chars + shorts) × 2 
               + (floats + ints) × 4 
               + (doubles + longs) × 8
```

The object offset is simply the count of object references.

You don't need to do these calculations manually; the `RegisterOffset.getBytes` method computes the byte offset of the primitive register, and `RegisterOffset.getObjects` computes the offset of the object register.

```scala
RegisterOffset.getBytes(RegisterOffset(bytes = 4, ints = 2, objects = 3)) // output = (4 + 2*4) = 12
RegisterOffset.getObjects(RegisterOffset(bytes = 4, ints = 2, objects = 3)) // output = 3
```

## Creating Registers

`Registers` is a mutable container that holds values. It's created with an initial capacity:

```scala

// Create registers with space for 3 bytes, 1 ints, and 2 objects
val registers = Registers(RegisterOffset(bytes = 3, ints = 1, objects = 2))
```

While creating `Registers`, you specify how much space is needed for primitives and objects, using `RegisterOffset`. The library allocates the necessary arrays internally. After creation, when you set values, the library ensures that it has sufficient capacity, growing the arrays if necessary.

## Setting and Getting Values

The `Registers` data type has methods to set and get values for each primitive type and for object references. For example, the `setInt` method sets an `Int` value in the byte array, while `setObject` sets an object reference in the object array:

```scala
class Registers {
  private var bytes: Array[Byte]     = new Array[Byte](byteArrayLength)
  private var objects: Array[AnyRef] = new Array[AnyRef](objectArrayLength)

  // Methods to get/set primitive values in byte array (getInt/setInt, getBoolean/setBoolean, etc.)
  def getInt(offset: RegisterOffset): Int = ???
  def setInt(offset: RegisterOffset, value: Int): Unit = ???

  def setBoolean(offset: RegisterOffset, value: Boolean): Unit = ???
  def getBoolean(offset: RegisterOffset): Boolean = ???

  // Two methods to get/set object references in object array
  def getObject(offset: RegisterOffset): AnyRef = ???
  def setObject(offset: RegisterOffset, value: AnyRef): Unit = ???
}
```

Here are all the available methods:
- `setBoolean` / `getBoolean`
- `setByte` / `getByte`
- `setShort` / `getShort`
- `setInt` / `getInt`
- `setLong` / `getLong`
- `setFloat` / `getFloat`
- `setDouble` / `getDouble`
- `setChar` / `getChar`
- `setObject` / `getObject`

When setting or getting a value, you are required to provide one or two of the following parameters:

1. `offset`: The `RegisterOffset` indicating where the specific field starts in the `Registers`. It can be used to handle nested structures to point to where inner record starts, or for multiple records to point to each record's starting position, or for variant types to anchor the position of different cases.
2. `value`: The actual value to set. For the primitives, this value is a typed primitive (e.g. `Int`, `Double`); for objects, this is an `AnyRef`.

## Example: Encoding/Decoding a Record Data Type

Encoding data instances into registers involves mapping each field of a data type to its corresponding position in the two register arrays (byte array for primitives and object array for references). For example, assume we have a `Person` data type as below:

```scala
case class Person(
  name: String,
  email: String,
  age: Int,
  height: Double,
  weight: Double
)
```

We can encode it with registers, as follows:

```scala

// Person("John", "john@example.com", 42, 180.0, 67.0)
val registers = Registers(RegisterOffset(objects = 2, ints = 1, doubles = 2))

registers.setObject(
  RegisterOffset.Zero, // Object index: 0
  "John"
)

registers.setObject(
  RegisterOffset(objects = 1), // Object index: 1
  "john@example.com"
)

registers.setInt(
  RegisterOffset(objects = 2), // Byte index: 0
  42
)

registers.setDouble(
  RegisterOffset(objects = 2, ints = 1), // Byte index: (1 * 4) = 4
  180.0
)

registers.setDouble(
  RegisterOffset(objects = 2, ints = 1, doubles = 1), // Byte index: (1 * 4) + (1 * 8) = 12
  67.0
)
```

Conversely, to decode the `Person` data type from registers, you would read the values back from their respective positions:

```scala

// Decode Person from registers
val name = registers.getObject(
  RegisterOffset.Zero, // Object index: 0
).asInstanceOf[String]

val email = registers.getObject(
  RegisterOffset(objects = 1) // Object index: 1
).asInstanceOf[String]

val age = registers.getInt(
  RegisterOffset(objects = 2) // Object index: 2
)

val height = registers.getDouble(
  RegisterOffset(objects = 2, ints = 1) // Byte index: (1 * 4) = 4
)

val weight = registers.getDouble(
  RegisterOffset(objects = 2, ints = 1, doubles = 1) // Byte index: (1 * 4) + (1 * 8) = 12
)

val person = Person(name, email, age, height, weight)
```
