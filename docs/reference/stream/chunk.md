---
id: chunk
title: "Chunk"
---
A `Chunk[A]` represents a chunk of values of type `A`. Chunks are usually backed by arrays, but expose a purely functional, safe interface to the underlying elements, and they become lazy on operations that would be costly with arrays, such as repeated concatenation. Like lists and arrays, Chunk is an ordered collection. 

```scala mdoc:invisible
import zio._
```

## Why Chunk?
Arrays are fast and don’t box primitive values but due to `ClassTag` requirements and mutability they are painful to use and don't integrate well info functional code. ZIO chunks are backed by arrays so they also have zero boxing for primitives while providing an immutable interface and avoiding `ClassTag` requirements.

Lets to get more details behind why Chunk invented:

### Immutability
In Scala, there is no immutable data type that can efficiently represent primitive data types. There is Array, but Array is a mutable interface. The Array data type can efficiently represent primitives without boxing but only by exposing some unsafe mutable methods like `update`.

### Ergonomic Design
Every time, when we create an array of generic types in Scala, we need a [ClassTag](https://www.scala-lang.org/api/current/scala/reflect/ClassTag.html) to provide runtime information about that generic type, which is very inconvenient and isn't ergonomic. It leads us to a very cumbersome API.

Chunk does not have the inconvenience of Array in Scala. **Chunk dispenses with the need to have ClassTags**. It utilizes a different approach to solve that problem. 

### High Performance
In addition to being an immutable array and zero boxing of Chunks that leads us to a high performant data type, Chunk has specialized operations for things like appending a single element or concatenating two Chunks together which have significantly higher performance than doing these same operations on the Array. Many Chunk methods have been handwritten to achieve better performance than their corresponding Array implementations in the Scala standard library.

Although Chunk is a common data type in ZIO, it exists primarily to support streaming use cases. 

When we are doing data streaming, a lot of times the source stream is a stream of bytes. Hence, internally we use a Chunk of bytes to represent that, so we don't have to box the bytes. Of course, it can be utilized for Chunks of Ints and many other types. Using Chunk is especially common when we are encoding and decoding at the level of streams. It is a very efficient, high-performance data type. 

## Operations

### Creating a Chunk

Creating empty `Chunk`:
```
val emptyChunk = Chunk.empty
```

Creating a `Chunk` with specified values:
```scala mdoc
val specifiedValuesChunk = Chunk(1,2,3)
```

Alternatively, we can create a `Chunk` by providing a collection of values:
```scala mdoc
val fromIterableChunk: Chunk[Int] = Chunk.fromIterable(List(1, 2, 3))
val fromArrayChunk: Chunk[Int] = Chunk.fromArray(Array(1, 2, 3))
```

Creating a `Chunk` using filling same n element into it:
```scala mdoc
val chunk: Chunk[Int] = Chunk.fill(3)(0)
```

Creating a `Chunk` using unfold method by repeatedly applying the given function, as long as it returns Some:
```scala mdoc
val unfolded = Chunk.unfold(0)(n => if (n < 8) Some((n*2, n+2)) else None)
```

### Concatenating chunk

`++` Returns the concatenation of this chunk with the specified chunk. For example:

```scala mdoc
Chunk(1,2,3) ++ Chunk(4,5,6)
```

### Collecting chunk

`collect` Returns a filtered, mapped subset of the elements of this chunk.
How to use `collect` function to cherry-pick all strings from Chunk[A]:

```scala mdoc
val collectChunk = Chunk("Hello ZIO", 1.5, "Hello ZIO NIO", 2.0, "Some string", 2.5)

collectChunk.collect { case string: String => string }
```
How to use `collect` function to cherry-pick all the digits from Chunk[A]:

```scala mdoc
collectChunk.collect { case digit: Double => digit }
```

`collectWhile` collects the elements (from left to right) until the predicate returns "false" for the first time:

```scala mdoc
Chunk("Sarah", "Bob", "Jane").collectWhile { case element if element != "Bob" => true }
```
or another example:

```scala mdoc
Chunk(9, 2, 5, 1, 6).collectWhile { case element if element >= 2 => true }
```
### Dropping chunk

`drop` drops the first `n` elements of the chunk:

```scala mdoc
Chunk("Sarah", "Bob", "Jane").drop(1)
```

`dropWhile` drops all elements so long as the predicate returns true:

```scala mdoc
Chunk(9, 2, 5, 1, 6).dropWhile(_ >= 2)
```

### Comparing chunks

```scala mdoc
Chunk("A","B") == Chunk("A", "C")
```

### Converting chunks

`toArray` converts the chunk into an Array.

```scala mdoc:silent
Chunk(1,2,3).toArray
```

`toSeq`converts the chunk into `Seq`.

``` scala mdoc
Chunk(1,2,3).toSeq
```
 
