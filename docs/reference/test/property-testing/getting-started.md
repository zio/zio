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
