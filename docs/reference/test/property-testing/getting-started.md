---
id: getting-started
title: "Getting Started With Property Checking"
sidebar_label: "Getting Started"
---

The fundamental idea behind property checking is to test the properties of the target function using random inputs.

So to test a system using property checking, two things are required:

1. Properties
2. Generators

A property of a system is a predicate that is always true regardless of the system's input. For example, the addition of two numbers is commutative. So it doesn't matter what numbers we pass to the addition function, for any pair of `a` and `b`, the result of `add(a, b)` is always the same as `add(b, a)`:

```scala mdoc:compile-only
def add(a: Int, b: Int): Int = ???

def is_add_commutative(a: Int, b: Int): Boolean =
  add(a, b) == add(b, a)
```

The `is_add_commutative` predicate takes two inputs and checks if the `add` function is commutative or not. To check this property, we need some random integer pairs. This is where generators come in.

The `Gen[A]` data type is used to generate random values of type `A`. ZIO Test provides numerous `Gen` instances for common types:

```scala mdoc:silent
import zio.test._

val intGen: Gen[Any, Int] = Gen.int
val stringGen: Gen[Sized, String] = Gen.string
```

It is also composable, so we can combine them to generate random values of more complex types:

```scala mdoc:silent
val stringIntGen: Gen[Sized, (String, Int)] = stringGen <*> intGen

case class Person(name: String, age: Int)
val personGen: Gen[Sized, Person] = stringIntGen.map(Person.tupled)
```

ZIO Test provides the `check` function for this purpose. It takes a list of generators and provides them to another taken function, which is a property checker:

```scala
def property[T1, T2](input1: T1, input2: T2, ...): Boolean = ???

val input1Gen: Gen[_, T1] = ???
val input2Gen: Gen[_, T2] = ???

check(input1Gen, input2Gen, ...) { (input1, input2, ...) =>
  assertTrue(property(input1, input2, ...))   
}
```

In our example, the `is_add_commutative` predicate takes two inputs. So we need to pass two generators of type `Int` to the `check` function:

```scala mdoc:compile-only
def add(a: Int, b: Int): Int = ???

test("add is commutative") {
  check(Gen.int, Gen.int) { (a, b) =>
    assertTrue(add(a, b) == add(b, a))
  }
}
```

## Number of Samples

In the previous example, we used `check` to test if the `add` function is commutative. In other words, we try to generate samples of random pairs of integers and try to falsify the `is_add_commutative` predicate. If we find a pair of integers that falsifies the predicate, then we know that the property is violated.

By default, the `check` function, try to generate 200 samples. We can change this by using the `sample` test aspect:

```scala mdoc:invisible
def add(a:Int, b:Int):Int = a + b
```

```scala mdoc:compile-only
import zio.test._

object AdditionSpec extends ZIOSpecDefault {
  def spec =
    test("add is commutative") {
      check(Gen.int, Gen.int) { (a, b) =>
        assertTrue(add(a, b) == add(b, a))
      }
    } @@ TestAspect.samples(10)
}
```

To debug the test, we added a `println` statement inside the `check` function to see the generated samples.

## How Generators Work?

We have two types of generators:

- **Deterministic** generators are used to generate fixed samples. For example, `Gen.const(1)` is a deterministic generator that always produces `1`.
- **Non-deterministic** generators are used to generate random samples. For example, `Gen.int` is a non-deterministic generator that produces random integers.

We can think of `Gen[R, A]` as a `ZStream[R, Nothing, A]`. For example the `Gen.int` is a stream of random integers `ZStream.fromZIO(Random.nextInt)`.

To find out how a generator works, Let's take a look at the following snippet.  It shows how the `Gen` data type is implemented. Note that it is a simplified version of the `Gen` data type but the API is the same:

```scala mdoc:compile-only
import zio._
import zio.test._
import zio.stream._

case class Gen[R, A](sample: ZStream[R, Nothing, A]) {
  def map[B](f: A => B): Gen[R, B] = Gen(sample.map(f))

  def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B] = ???

  def runCollect: ZIO[R, Nothing, List[A]] = sample.runCollect.map(_.toList)
}

object Gen {
  // A constant generator of the specified value (deterministic).
  def const[A](a: => A): Gen[Any, A] = 
    Gen(ZStream.succeed(a))
  
  // A random generator of integers (non-deterministic).
  def int: Gen[Any, Int] = 
    Gen(ZStream.fromZIO(Random.nextInt))
  def int(min: Int, max: Int): Gen[Any, Int] = 
    ???
  
  // A random generator of specified values (non-deterministic).
  def elements[A](as: A*): Gen[Any, A] = 
    if (as.isEmpty) Gen(ZStream.empty) else int(0, as.length - 1).map(as)
  
  // A deterministic generator of specified fixed values (deterministic).
  def fromIterable[A](xs: Iterable[A]): Gen[Any, A] = 
    Gen(ZStream.fromIterable(xs))
}

Gen.const(42).runCollect.debug
// Output: List(42)

Gen.int.runCollect.debug
// Output: List(82) or List(3423) or List(-352) or ...

Gen.elements(1, 2, 3).runCollect.debug
// Output: List(1) or List(2) or List(3)

Gen.fromIterable(List(1, 2, 3))
// Output: List(1, 2, 3)
```

So we can see that the `Gen` data type is nothing more than the stream of random/constant values.

## How Samples Are Generated?

When we run the `check` function, the `check` function repeatedly run the underlying stream of generators (using the `forever` combinator), and then it takes `n` samples from the stream, where `n` is by default 200.

We can modify the default sample size by using the `samples` test aspect. So if we the `check` function, with `TestAspect.samples(5)`. Let's see how the samples are produced for each of the following generators:

- `check(Gen.const(42))(n => ???)` it will repeatedly run the `Zstream.succeed(42)` stream, and then take `n` samples from it: 42, 42, 42, 42, 42.
- `check(Gen.int)(n => ???)` it will repeatedly run the `ZStream.fromZIO(Random.nextInt)` stream, and then take `n` samples from it: e.g. 2, -3422, 33, 3991334, 98138.
- `check(Gen.elements(1, 2, 3))(n => ???)` it will repeatedly run the `ZStream.fromIterable(List(1, 2, 3))` stream, and then take `n` samples from it: e.g. 3, 1, 1, 3, 2.
- `check(Gen.fromIterable(List(1, 2, 3)))(n => ???)` it will repeatedly run the `ZStream.fromIterable(List(1, 2, 3))` stream, and then take `n` samples from it: 1, 2, 3, 1, 2.

When we run the `check` function with multiple generators, the samples will be the cartesian product of their streams. Let's try some examples:

```scala mdoc:compile-only
import zio.test._

test("two deterministic generators") {
  check(Gen.const(1), Gen.fromIterable(List("a", "b", "c"))) { (a, b) =>
    println((a, b))
    assertTrue(true)
  }
} @@ TestAspect.samples(5)
```

The output will be:

```scala
(1,a)
(1,b)
(1,c)
(1,a)
(1,b)
+ two deterministic generators
1 tests passed. 0 tests failed. 0 tests ignored.
```

So the example above is something like this:

```scala mdoc:compile-only
import zio.stream._

{
  for {
    a <- ZStream.succeed(1)
    b <- ZStream.fromIterable(List("a", "b", "c"))
  } yield (a, b)
}.forever.take(5).runCollect.debug
```

Now let's try use one non-deterministic generator and one deterministic generator:

```scala mdoc:compile-only
import zio.test._

test("one non-deterministic generator and one deterministic generator") {
  check(Gen.int(1, 3), Gen.fromIterable(List("a", "b", "c"))) { (a, b) =>
    println((a, b))
    assertTrue(true)
  }
} @@ TestAspect.samples(5)
```

Here is one example output:

```scala
(3,a)
(3,b)
(3,c)
(2,a)
(2,b)
+ one non-deterministic generator and one deterministic generator
1 tests passed. 0 tests failed. 0 tests ignored.
```

This is the same as the previous example, it is like we have the following stream:

```scala mdoc:compile-only
import zio._
import zio.stream._

{
  for {
    a <- ZStream.fromZIO(Random.nextIntBetween(1, 3))
    b <- ZStream.fromIterable(List("a", "b", "c"))
  } yield (a, b)
}.forever.take(5).runCollect.debug
```
