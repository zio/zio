# Understanding Type Tags

> A tutorial for newcomers: why types vanish at runtime, and how izumi-reflect type tags keep them, step by step.

Welcome to Understanding Type Tags! This tutorial is for Scala developers who know generics (`List[A]`, `def f[A]`) and context bounds (`def f[A: Ordering]`), but have never used `izumi-reflect` or any other type tag library. You don't need any prior experience with runtime reflection to follow along.

## Learning Objectives

By the end of this tutorial, you will understand:

- Why the JVM forgets type arguments such as the `Int` in `List[Int]`
- How a `Tag` keeps the full type of a value available at runtime
- How tags travel into generic code through context bounds, and how bigger tags are built from smaller ones
- What a `TagK` is, and how to read a type constructor such as `λ %0 → Option[+0]`
- How to ask questions about types at runtime: equality and subtyping

We'll learn these concepts through:

1. Types Disappear at Runtime
2. Capturing a Type with Tag
3. Passing Tags into Generic Code
4. Building Bigger Tags from Smaller Ones
5. Type Constructors and TagK
6. Asking Questions About Types

We recommend reading from top to bottom — each section builds on the previous one.

## Background

Scala checks types while it compiles your program, and then mostly throws them away. The JVM keeps the class of each object, but not the type arguments of generic classes, a process called type erasure. Most of the time this doesn't matter. It starts to matter when a program has to make decisions based on types at runtime: a dependency injection container finding a service, a map holding values of different types, a message bus routing messages by their type.

A type tag is the fix: a value, created by the compiler, that describes a type completely. Your program carries it around like any other value and asks it questions when it needs to. `izumi-reflect` creates these tags at compile time, on Scala 2 and Scala 3, on the JVM, Scala.js and Scala Native.

## 1. Types Disappear at Runtime

Let's start by seeing the problem for ourselves. A list of numbers and a list of strings are different types, but let's ask the JVM what it knows about them:

```scala
val numbers = List(1, 2, 3)
// numbers: List[Int] = List(1, 2, 3)
val words = List("a", "b")
// words: List[String] = List("a", "b")

numbers.getClass == words.getClass
// res0: Boolean = true
numbers.getClass.getName
// res1: String = "scala.collection.immutable.$colon$colon"
```

The code above:

- `numbers` and `words` have the types `List[Int]` and `List[String]`.
- `numbers.getClass == words.getClass` compares the runtime classes of the two values, and it is `true`: at runtime, both are just lists.
- `numbers.getClass.getName` shows the class name, the `::` cell of a list (encoded as `$colon$colon`), with no trace of `Int`.

Notice that nothing in the runtime class says "of `Int`". That information exists only while compiling, and it is gone at runtime.

## 2. Capturing a Type with Tag

A `Tag[T]` is a value that describes the type `T`, including its type arguments. We ask for one with `Tag[...]`, and the compiler builds it from the type it sees in our code:

```scala
import izumi.reflect.Tag

val numbersType = Tag[List[Int]]
// numbersType: Tag[List[Int]] = Tag[List[+Int]]
val wordsType = Tag[List[String]]
// wordsType: Tag[List[String]] = Tag[List[+String]]

numbersType == wordsType
// res2: Boolean = false
```

The code above:

- `import izumi.reflect.Tag` brings the `Tag` type class into scope.
- `Tag[List[Int]]` and `Tag[List[String]]` create two tags at compile time.
- `numbersType == wordsType` is `false`, because the tags remember the type arguments that the runtime classes lack.

The printed form of a tag shows the type with a marker in front of each type argument: `+` means the type parameter is covariant, as in `List[+A]`. You'll also meet `-` for contravariant and `=` for invariant parameters. Try `Tag[Map[String, Int]]` to see an invariant key: it prints as `Map[=String,+Int]`.

## 3. Passing Tags into Generic Code

Tags are most useful in generic code, where the concrete type is only known at the call site. Inside a method `def describe[A]`, the compiler can't build a tag for `A`, because `A` could be anything. Instead, we ask the caller to pass the tag in, with a context bound:

```scala
def describe[A: Tag](value: A): String =
  s"$value has type ${Tag[A].tag}"

describe(42)
// res3: String = "42 has type Int"
describe(List(Some("hello")))
// res4: String = "List(Some(hello)) has type List[+Some[+String]]"
```

The code above:

- `A: Tag` is a context bound: it asks every caller to provide a `Tag[A]`.
- `Tag[A]` inside the method summons the tag the caller passes, and `Tag#tag` gives its `LightTypeTag`, the part that renders and compares types.
- `describe(42)` and `describe(List(Some("hello")))` are ordinary calls: the compiler creates `Tag[Int]` and `Tag[List[Some[String]]]` at each call site and passes them in. Notice that the tag describes the type the compiler inferred, `Some[String]`, which is more specific than `Option[String]`.

Notice that the caller never mentions tags. As long as the caller knows the concrete type, the tag comes for free.

## 4. Building Bigger Tags from Smaller Ones

Here comes the part that makes `izumi-reflect` special. Inside `describe`, we have a tag for `A`. What if we need a tag for a type that contains `A`, such as `Map[String, A]`? The compiler can't build it directly, since it still doesn't know `A`, but `izumi-reflect` can combine the tag of `A` with the rest of the type:

```scala
def settingsOf[A: Tag]: Tag[Map[String, A]] = Tag[Map[String, A]]

settingsOf[Boolean]
// res5: Tag[Map[String, Boolean]] = Tag[Map[=String,+Boolean]]
settingsOf[List[Double]]
// res6: Tag[Map[String, List[Double]]] = Tag[Map[=String,+List[+Double]]]
```

The code above:

- `Tag[Map[String, A]]` asks for a tag of a type that mentions `A`.
- The macro behind `Tag` finds the `Tag[A]` from the context bound and writes code that plugs it into `Map[String, _]` at runtime.
- `settingsOf[Boolean]` and `settingsOf[List[Double]]` show complete types, built from the caller's tag and the method's own type.

This is the key idea of the library: a generic method only needs tags for its type parameters, and it can describe any type built from them.

## 5. Type Constructors and TagK

Some type parameters are not types but type constructors: `F` in `def f[F[_]]` stands for something like `List` or `Option`, which still needs a type argument. A plain `Tag` can't describe those, so `izumi-reflect` has `TagK`, the tag of a type constructor with one parameter:

```scala
import izumi.reflect.TagK

TagK[Option].tag
// res7: LightTypeTag = ? %0 ? Option[+0]
TagK[Option].tag.combine(Tag[Int].tag)
// res8: LightTypeTag = Option[+Int]
```

The code above:

- `import izumi.reflect.TagK` brings the type class for type constructors into scope.
- `TagK[Option].tag` prints as `λ %0 → Option[+0]`. Read it as "a function that takes a type `%0` and returns `Option[+%0]`": a type that is still waiting for its argument.
- `LightTypeTag#combine` with `Tag[Int].tag` gives that function its argument and returns the tag of `Option[Int]`.

With a `TagK` context bound, a generic method can build tags of types that apply `F`, the same way as in section 4:

```scala
def boxOf[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

boxOf[Vector, String]
// res9: Tag[Vector[String]] = Tag[Vector[+String]]
```

The code above:

- `F[_]: TagK` asks the caller for the tag of the type constructor `F`, and `A: Tag` for the tag of `A`.
- `Tag[F[A]]` is built at runtime by applying the `TagK` of `F` to the `Tag` of `A`, exactly the `LightTypeTag#combine` call we did by hand above.

For type constructors with more parameters, there are `TagKK` (for `F[_, _]`, like `Either`) and more siblings.

<details>
  <summary>Runnable version, with both examples of this section in one program: <code>Concept5TagKExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/understandingtypetags/Concept5TagKExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run Concept5TagKExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.understandingtypetags.Concept5TagKExample"

package izumi.reflect.examples.understandingtypetags

import izumi.reflect.{Tag, TagK}

/**
  * Understanding Type Tags ? 5. Type Constructors and TagK
  *
  * Renders the tag of a type constructor, applies it with combine, and builds Tag[F[A]] from TagK[F] and Tag[A].
  */
object Concept5TagKExample {
  def boxOf[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

  def main(args: Array[String]): Unit = {
    println(s"TagK[Option].tag: ${TagK[Option].tag}")
    println(s"TagK[Option].tag.combine(Tag[Int].tag): ${TagK[Option].tag.combine(Tag[Int].tag)}")
    println(s"boxOf[Vector, String]: ${boxOf[Vector, String]}")
  }
}
```

</details>

## 6. Asking Questions About Types

Now that types are values, we can ask them questions while the program runs. The two most useful questions are "is this the same type?" (`=:=`) and "is this a subtype?" (`<:<`):

```scala
Tag[List[Int]] =:= Tag[scala.collection.immutable.List[Int]]
// res10: Boolean = true
Tag[List[Int]] <:< Tag[Seq[Int]]
// res11: Boolean = true
Tag[Seq[Int]] <:< Tag[List[Int]]
// res12: Boolean = false
Tag[List[String]] <:< Tag[Seq[Any]]
// res13: Boolean = true
```

The code above:

- The first line is `true`: the two spellings name the same type.
- The second line is `true`, because `List` extends `Seq`.
- The third line is `false`: not every `Seq` is a `List`.
- The last line is `true` because `List` and `Seq` are covariant, so a `List[String]` is also a `Seq[Any]`. The tag knows the variance of each type parameter.

Try changing `Seq[Any]` to `Seq[Int]` in the last line and see the answer flip to `false`.

## Running the Examples

All runnable code from this tutorial is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/understandingtypetags/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/understandingtypetags). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.understandingtypetags.<ObjectName>"`.

## What You've Learned

In this tutorial, you learned:

- Why the JVM forgets type arguments: `List[Int]` and `List[String]` share one runtime class
- How a `Tag` keeps the full type, type arguments included, as a value at runtime
- How a context bound such as `A: Tag` carries tags into generic code, and how `izumi-reflect` builds tags of bigger types such as `Map[String, A]` from them
- What a `TagK` is, how to read `λ %0 → Option[+0]`, and how `LightTypeTag#combine` applies a type constructor
- How to compare types with `=:=` and check subtyping with `<:<`, variance included

You now have a solid foundation in type tags. The next step is to put them to work in a real program.

## Where to Go Next

- **Ready to use this in practice?** [Build a Type-Safe Heterogeneous Map](heterogeneous-map.md) uses tags as keys of a map of values of different types.
- **Using ZIO?** [Use izumi-reflect Tags with the ZIO Environment](zio-environment.md) shows where ZIO asks for tags and why.
- **Want to dive deeper into the API?** Read [Tag and the TagK Family](../reference/tag.md) and [LightTypeTag](../reference/light-type-tag.md).
