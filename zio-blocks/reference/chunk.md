# Chunk

> `Chunk[A]` is an immutable, indexed sequence optimized for high-performance operations. Unlike Scala's built-in collections, Chunk is designed for zero-allocation access patterns, efficient concatenation, and unboxed primitive storage.

`Chunk[A]` is an immutable, indexed sequence optimized for high-performance operations. Unlike Scala's built-in collections, Chunk is designed for zero-allocation access patterns, efficient concatenation, and unboxed primitive storage.

## Why Chunk?

Chunk addresses several limitations of standard Scala collections:

| Feature | Chunk | Vector | Array |
|---------|-------|--------|-------|
| Immutable | ✓ | ✓ | ✗ |
| O(1) indexed access | ✓ | ~O(1) | ✓ |
| Unboxed primitives | ✓ | ✗ | ✓ |
| Efficient concatenation | ✓ | ✓ | ✗ |
| Safe functional interface | ✓ | ✓ | ✗ |
| Lazy slicing | ✓ | ✗ | ✗ |

Key advantages:

- **Zero-boxing for primitives**: `Chunk[Int]`, `Chunk[Double]`, etc. store values unboxed in specialized arrays
- **Lazy concatenation**: Uses balanced tree structures (based on Conc-Trees) for O(log n) concatenation
- **Efficient slicing**: `drop`, `take`, and `slice` create views without copying
- **Automatic materialization**: Deep operation chains are materialized when depth exceeds thresholds
- **Scala collections integration**: Implements `IndexedSeq` for seamless interoperability

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-chunk" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x

## Creating Chunks

### From Varargs

```scala

val numbers = Chunk(1, 2, 3, 4, 5)
val strings = Chunk("hello", "world")
val empty   = Chunk.empty[Int]
```

### From a Single Element

```scala

val single = Chunk.single(42)
val unit   = Chunk.unit // Chunk(())
```

### From Arrays

When you have an existing array, use `fromArray`. Note that the array should not be mutated after wrapping:

```scala

val arr   = Array(1, 2, 3)
val chunk = Chunk.fromArray(arr)
```

### From Iterables and Iterators

```scala

val fromList   = Chunk.fromIterable(List(1, 2, 3))
val fromVector = Chunk.fromIterable(Vector("a", "b"))
val fromIter   = Chunk.fromIterator(Iterator.range(0, 10))
```

### From Java Collections

```scala

val javaList = new util.ArrayList[String]()
javaList.add("one")
javaList.add("two")

val chunk = Chunk.fromJavaIterable(javaList)
```

### From NIO Buffers

Chunk provides direct integration with Java NIO buffers:

```scala

val buffer = ByteBuffer.wrap(Array[Byte](1, 2, 3, 4))
val bytes  = Chunk.fromByteBuffer(buffer)
```

Available buffer constructors:
- `Chunk.fromByteBuffer(ByteBuffer): Chunk[Byte]`
- `Chunk.fromCharBuffer(CharBuffer): Chunk[Char]`
- `Chunk.fromIntBuffer(IntBuffer): Chunk[Int]`
- `Chunk.fromLongBuffer(LongBuffer): Chunk[Long]`
- `Chunk.fromShortBuffer(ShortBuffer): Chunk[Short]`
- `Chunk.fromFloatBuffer(FloatBuffer): Chunk[Float]`
- `Chunk.fromDoubleBuffer(DoubleBuffer): Chunk[Double]`

### Generator Functions

```scala

val filled   = Chunk.fill(5)("x")         // Chunk("x", "x", "x", "x", "x")
val iterated = Chunk.iterate(1, 5)(_ * 2) // Chunk(1, 2, 4, 8, 16)
val unfolded = Chunk.unfold(0)(n => if (n < 5) Some((n, n + 1)) else None)
```

## Core Operations

### Element Access

```scala

val chunk = Chunk(10, 20, 30, 40, 50)

val first  = chunk(0)          // 10
val second = chunk(1)          // 20
val head   = chunk.head        // 10
val last   = chunk.last        // 50
val len    = chunk.length      // 5

val maybeHead = chunk.headOption // Some(10)
val maybeLast = chunk.lastOption // Some(50)
```

For primitive chunks, specialized accessors avoid boxing:

```scala

val ints = Chunk(1, 2, 3)
val i: Int = ints.int(0)     // unboxed access

val bytes = Chunk[Byte](1, 2, 3)
val b: Byte = bytes.byte(0)  // unboxed access

val doubles = Chunk(1.0, 2.0, 3.0)
val d: Double = doubles.double(0)  // unboxed access
```

### Transformations

```scala

val chunk = Chunk(1, 2, 3, 4, 5)

val doubled  = chunk.map(_ * 2)           // Chunk(2, 4, 6, 8, 10)
val filtered = chunk.filter(_ > 2)        // Chunk(3, 4, 5)
val flatted  = chunk.flatMap(n => Chunk(n, n)) // Chunk(1, 1, 2, 2, ...)
val collected = chunk.collect { case n if n % 2 == 0 => n * 10 } // Chunk(20, 40)
```

### Concatenation

Concatenation is efficient—Chunk uses balanced tree structures to avoid copying:

```scala

val a = Chunk(1, 2, 3)
val b = Chunk(4, 5, 6)

val combined = a ++ b           // Chunk(1, 2, 3, 4, 5, 6)
val appended = a :+ 4           // Chunk(1, 2, 3, 4)
val prepended = 0 +: a          // Chunk(0, 1, 2, 3)
```

### Slicing

Slicing operations create views and don't copy data:

```scala

val chunk = Chunk(1, 2, 3, 4, 5, 6, 7, 8)

val firstThree = chunk.take(3)        // Chunk(1, 2, 3)
val lastThree  = chunk.takeRight(3)   // Chunk(6, 7, 8)
val dropped    = chunk.drop(2)        // Chunk(3, 4, 5, 6, 7, 8)
val sliced     = chunk.slice(2, 5)    // Chunk(3, 4, 5)

val (left, right) = chunk.splitAt(4)  // (Chunk(1,2,3,4), Chunk(5,6,7,8))
```

### Conditional Operations

```scala

val chunk = Chunk(1, 2, 3, 4, 5, 6)

val takeWhileSmall = chunk.takeWhile(_ < 4)    // Chunk(1, 2, 3)
val dropWhileSmall = chunk.dropWhile(_ < 4)    // Chunk(4, 5, 6)
val takeUntilBig   = chunk.takeWhile(_ <= 3)   // Chunk(1, 2, 3)
val dropUntilBig   = chunk.dropUntil(_ > 3)    // Chunk(5, 6)
```

### Folding and Reduction

```scala

val chunk = Chunk(1, 2, 3, 4, 5)

val sum     = chunk.foldLeft(0)(_ + _)    // 15
val product = chunk.foldRight(1)(_ * _)   // 120
val summed  = chunk.reduce(_ + _)         // 15

val runningSum = chunk.foldWhile(0)(_ < 10)(_ + _) // 10 (1+2+3+4)
```

### Searching and Predicates

```scala

val chunk = Chunk(1, 2, 3, 4, 5)

val hasEven  = chunk.exists(_ % 2 == 0)  // true
val allSmall = chunk.forall(_ < 10)      // true
val found    = chunk.find(_ > 3)         // Some(4)
val index    = chunk.indexWhere(_ > 3)   // 3
```

### Zipping

```scala

val as = Chunk("a", "b", "c")
val bs = Chunk(1, 2, 3)

val zipped      = as.zip(bs)              // Chunk(("a",1), ("b",2), ("c",3))
val withIndex   = as.zipWithIndex         // Chunk(("a",0), ("b",1), ("c",2))
val zipWith     = as.zipWith(bs)(_ + _)   // Chunk("a1", "b2", "c3")
val zipAll      = as.zipAll(Chunk(1, 2))  // handles different lengths
```

### Updating Elements

Updates are immutable and use efficient buffering:

```scala

val chunk   = Chunk(1, 2, 3, 4, 5)
val updated = chunk.updated(2, 100) // Chunk(1, 2, 100, 4, 5)
```

### Deduplication and Sorting

```scala

val withDupes = Chunk(1, 1, 2, 2, 2, 3, 3)
val deduped   = withDupes.dedupe  // Chunk(1, 2, 3) - removes adjacent duplicates

val unsorted = Chunk(3, 1, 4, 1, 5)
val sorted   = unsorted.sorted    // Chunk(1, 1, 3, 4, 5)
```

### Splitting

```scala

val chunk = Chunk(1, 2, 3, 4, 5, 6)

val parts = chunk.split(3) // Chunk(Chunk(1,2), Chunk(3,4), Chunk(5,6))
val (before, after) = chunk.splitWhere(_ > 3) // splits at first element > 3
```

### String Conversion

```scala

val bytes = Chunk[Byte](72, 101, 108, 108, 111)
val str   = bytes.asString  // "Hello"

val chars = Chunk('H', 'e', 'l', 'l', 'o')
val str2  = chars.asString  // "Hello"

val withCharset = bytes.asString(StandardCharsets.UTF_8) // "Hello"
val base64      = bytes.asBase64String // base64-encoded string
```

### Materialization

For complex operation chains, you can force materialization to an array-backed chunk:

```scala

val complex = Chunk(1, 2, 3) ++ Chunk(4, 5) ++ Chunk(6, 7)
val materialized = complex.materialize // backed by a single array
```

## NonEmptyChunk

`NonEmptyChunk[A]` is a chunk guaranteed to contain at least one element. This enables safe use of operations like `head` and `reduce`:

```scala

val nec = NonEmptyChunk(1, 2, 3)

val first: Int = nec.head      // always safe
val sum: Int   = nec.reduce(_ + _)  // always safe

val mapped: NonEmptyChunk[Int] = nec.map(_ * 2)
val flatMapped: NonEmptyChunk[Int] = nec.flatMap(n => NonEmptyChunk(n, n + 1))
```

### Creating NonEmptyChunk

```scala

val fromValues = NonEmptyChunk(1, 2, 3)
val single     = NonEmptyChunk.single(42)
val fromCons   = NonEmptyChunk.fromCons(::(1, List(2, 3)))
val fromIterable = NonEmptyChunk.fromIterable(1, List(2, 3))

val maybeNec: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(Chunk(1, 2))
val empty: Option[NonEmptyChunk[Int]] = NonEmptyChunk.fromChunk(Chunk.empty) // None
```

### Converting Between Chunk and NonEmptyChunk

```scala

val nec = NonEmptyChunk(1, 2, 3)
val chunk: Chunk[Int] = nec.toChunk

val chunk2 = Chunk(1, 2, 3)
chunk2.nonEmptyOrElse(0)(_.reduce(_ + _)) // 6 if non-empty, 0 if empty
```

### Operations That Preserve NonEmptiness

These operations return `NonEmptyChunk`:
- `map`, `flatMap`, `flatten`
- `append`, `prepend`, `++`
- `zip`, `zipWith`, `zipWithIndex`
- `sorted`, `sortBy`, `reverse`
- `distinct`, `materialize`

Operations that might produce empty results return `Chunk`:
- `filter`, `filterNot`
- `collect`
- `tail`, `init`

## ChunkBuilder

`ChunkBuilder` is a mutable builder for creating chunks efficiently. It's specialized for primitives to avoid boxing:

```scala

val builder = ChunkBuilder.make[Int]()
builder.addOne(1)
builder.addOne(2)
builder.addAll(List(3, 4, 5))
val result: Chunk[Int] = builder.result() // Chunk(1, 2, 3, 4, 5)
```

### Specialized Builders

For primitives, use specialized builders for best performance:

```scala

val intBuilder = new ChunkBuilder.Int
intBuilder.addOne(1)
intBuilder.addOne(2)
val ints: Chunk[Int] = intBuilder.result()

val byteBuilder = new ChunkBuilder.Byte
val longBuilder = new ChunkBuilder.Long
val doubleBuilder = new ChunkBuilder.Double
val boolBuilder = new ChunkBuilder.Boolean
```

Available specialized builders: `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double`

## Bit Operations

Chunk provides efficient bit-level operations for working with binary data:

### Converting to Bits

```scala

val bytes = Chunk[Byte](0x0F, 0xF0.toByte)
val bits  = bytes.asBitsByte  // Chunk of 16 booleans

val ints = Chunk(0x12345678)
val intBits = ints.asBitsInt(Chunk.BitChunk.Endianness.BigEndian)

val longs = Chunk(0x123456789ABCDEF0L)
val longBits = longs.asBitsLong(Chunk.BitChunk.Endianness.BigEndian)
```

### Bitwise Operations

```scala

val a = Chunk(true, false, true, false)
val b = Chunk(true, true, false, false)

val andResult = a & b  // Chunk(true, false, false, false)
val orResult  = a | b  // Chunk(true, true, true, false)
val xorResult = a ^ b  // Chunk(false, true, true, false)
val negated   = a.negate // Chunk(false, true, false, true)
```

### Packing Booleans

```scala

val bits = Chunk(true, false, true, false, true, true, true, true)
val packedBytes: Chunk[Byte] = bits.toPackedByte  // Efficient byte representation

val packedInts: Chunk[Int] = bits.toPackedInt(Chunk.BitChunk.Endianness.BigEndian)
val packedLongs: Chunk[Long] = bits.toPackedLong(Chunk.BitChunk.Endianness.BigEndian)
```

### Binary String

```scala

val bits = Chunk(true, false, true, true)
val binary: String = bits.toBinaryString // "1011"
```

## ChunkMap

`ChunkMap[K, V]` is an order-preserving immutable map backed by parallel chunks. It maintains insertion order during iteration:

```scala

val map = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)

val value = map.get("b")      // Some(2)
val updated = map.updated("d", 4)
val removed = map.removed("b")
```

### Creating ChunkMap

```scala

val empty = ChunkMap.empty[String, Int]
val fromPairs = ChunkMap("x" -> 1, "y" -> 2)
val fromChunk = ChunkMap.fromChunk(Chunk(("a", 1), ("b", 2)))
val fromChunks = ChunkMap.fromChunks(Chunk("a", "b"), Chunk(1, 2))
```

### Indexed Access

ChunkMap provides O(1) positional access:

```scala

val map = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)

val first = map.atIndex(0)      // ("z", 1)
val key   = map.keyAtIndex(1)   // "a"
val value = map.valueAtIndex(2) // 3

val keys: Chunk[String] = map.keysChunk
val values: Chunk[Int] = map.valuesChunk
```

### Optimized Lookup

For frequent lookups, create an indexed version with O(1) key access:

```scala

val map = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
val indexed = map.indexed  // O(1) lookups, extra memory for index

val value = indexed.get("b")  // O(1) instead of O(n)
```

## Scala Collections Integration

Chunk implements `IndexedSeq` and integrates seamlessly with Scala collections:

```scala

val chunk = Chunk(1, 2, 3, 4, 5)

val list: List[Int] = chunk.toList
val vector: Vector[Int] = chunk.toVector
val array: Array[Int] = chunk.toArray

val fromSeq: Chunk[Int] = Chunk.from(Vector(1, 2, 3))
```

Standard collection operations work as expected:

```scala

val chunk = Chunk(1, 2, 3)
val result = chunk
  .filter(_ > 1)
  .map(_ * 2)
  .flatMap(n => Chunk(n, n + 1))
```

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|-----------------|-------|
| `apply(i)` | O(1) | Direct array access for materialized chunks |
| `length` | O(1) | Cached |
| `head`, `last` | O(1) | |
| `++` | O(log n) | Balanced tree concatenation |
| `:+`, `+:` | O(1) amortized | Buffered appends |
| `take`, `drop`, `slice` | O(1) | Creates view |
| `map`, `filter`, `flatMap` | O(n) | |
| `updated` | O(1) amortized | Buffered updates |
| `materialize` | O(n) | Copies to array |

### When to Materialize

Chunk automatically materializes when:
- Tree depth exceeds internal thresholds
- You call `materialize` explicitly
- Converting to array with `toArray`

Consider explicit materialization when:
- Performing many random accesses on a deeply nested chunk
- Passing data to APIs that need arrays
- Optimizing a hot loop
