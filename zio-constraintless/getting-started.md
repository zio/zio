# Getting Started with ZIO Constraintless

> Install ZIO Constraintless and write a first function that gets its type class instances from a type-level list.

This page takes us from an empty project to a first working function that uses [`Instances`](./reference.md#instances) to pick a type class instance for a value whose type it does not know. To add the library to a project, copy the dependency from the [Installation](./index.md#installation) section.

## Describe the Allowed Types

A [`TypeList`](./reference.md#typelist) is a type-level list of the types a program may work with. We build one with the infix type `::` and end it with `End`. To allow `Int` and `String`:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._

type Allowed = Int :: String :: End
```

`Allowed` exists only at the type level. It has no values, and it does not mention any type class yet.

## Prove a Type Is Allowed

[`IsElementOf`](./reference.md#iselementof) is evidence that a type is in a list. The compiler builds it for us when the type is in the list. To ask for the evidence explicitly:

```scala
IsElementOf[Int, Allowed]
// res0: IsElementOf[Int, Allowed] = Head()
```

When the type is not in the list, no evidence exists and the code does not compile:

```scala
IsElementOf[Double, Allowed]
// error: could not find implicit value for parameter ev: zio.constraintless.IsElementOf[Double,repl.MdocSession.MdocApp.Allowed]
```

## Supply Instances at the Point of Use

Now we define a type class. It lives outside the library and knows nothing about `Allowed`:

```scala
trait Describe[A] {
  def describe(a: A): String
}

object Describe {
  implicit val int: Describe[Int] =
    (a: Int) => s"the number $a"

  implicit val string: Describe[String] =
    (a: String) => s"the text '$a'"
}
```

[`Instances[Describe, Allowed]`](./reference.md#instances) bundles the `Describe` instance of every type in `Allowed`. Its `Instances#withInstance` method selects the instance for one type, using the membership evidence as proof that the instance exists. A function that describes any allowed value:

```scala
def describe[A, As <: TypeList](a: A)(implicit
    isElement: A IsElementOf As,
    instances: Instances[Describe, As]
): String =
  instances.withInstance((d: Describe[A]) => d.describe(a))
```

The function says nothing about `Int` or `String`. It works for any list `As` for which `Describe` instances exist, and `A` must be an element of that list. To call it, we fix the list explicitly:

```scala
describe[Int, Allowed](42)
// res2: String = "the number 42"

describe[String, Allowed]("hello")
// res3: String = "the text 'hello'"
```

## What Happens When an Instance Is Missing

If a type is in the list but has no `Describe` instance, the compiler cannot build `Instances[Describe, As]` for the whole list. We add `Boolean` to the list without writing `Describe[Boolean]`:

```scala
type WithBoolean = Int :: String :: Boolean :: End

describe[Int, WithBoolean](42)
// error: could not find implicit value for parameter instances: zio.constraintless.Instances[repl.MdocSession.MdocApp.Describe,repl.MdocSession.MdocApp.WithBoolean]
// describe[Int, WithBoolean](42)
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

The error is reported where the interpreter is used, which is exactly where the instance should be supplied. Writing the missing instance fixes it.

## Next Steps

To build a larger program from these pieces, follow the [tutorial](./tutorial.md). For the full description of every type, see the [Reference](./reference.md). To understand why the library is shaped like this, read [Concepts](./explanation/index.md).
