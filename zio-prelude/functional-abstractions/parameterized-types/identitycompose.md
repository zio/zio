# IdentityCompose

> `IdentityCompose` extends [AssociativeCompose](./associativecompose.md) with an identity arrow. For functions, the identity is the function that returns its input unchanged. Composing any function with the identity gives back that same function.

The identity arrow is what makes the operation a monoid-like structure, the same way the number zero is the identity for addition. Chaining a transformation with nothing at all leaves it unchanged.

## Motivation

A pipeline built by composing steps needs a starting value for the case where there are no steps. Folding a list of transformations into one is the typical example: the fold needs an arrow to start from, and that arrow must not change the result. The identity arrow is that starting point. For layers it is `ZLayer.environment`, which passes its input environment through as its output, and for functions it is `scala.Predef.identity`.

Use `IdentityCompose` when you need to build a chain of arrows from a possibly empty list of steps. If composition alone is enough, [AssociativeCompose](./associativecompose.md) is the smaller requirement.

## The Identity Arrow

`IdentityCompose#identity` returns the identity for the type constructor. For `Function1`, it is the function that returns its argument:

```scala
import zio.prelude._
```

We first look at the identity arrow on a number:

```scala
implicitly[IdentityCompose[Function1]].identity[Int].apply(5)
// res1: Int = 5
```

## Composing With the Identity

Composing a function with the identity on either side gives the same function back. Here we compose with the identity on the right, so the identity runs first and changes nothing:

```scala
val lengthThenDouble: String => Int =
  implicitly[AssociativeCompose[Function1]].compose((n: Int) => n * 2, (s: String) => s.length)

val unchanged: String => Int =
  implicitly[AssociativeCompose[Function1]].compose(lengthThenDouble, implicitly[IdentityCompose[Function1]].identity[String])
```

Next, we compose a function with the identity and apply the result to a string:

```scala
unchanged("hello")
// res2: Int = 10
```

The result equals `lengthThenDouble` on `"hello"`, which is ten.
## Folding a List of Steps

Because every `IdentityCompose` has an identity, a list of arrows of the same type can be folded into one arrow, and an empty list gives the identity. In the following example the steps are plain functions on a string:

```scala
def pipeline[=>:[-_, +_]](steps: List[String =>: String])(implicit ev: IdentityCompose[=>:]): String =>: String =
  steps.foldLeft(ev.identity[String])((chain, step) => ev.compose(step, chain))
```

Next, we run a pipeline of two steps and an empty pipeline:

```scala
val trimAndShout = pipeline[Function1](List(_.trim, _.toUpperCase))
// trimAndShout: String => String = scala.Function1$$Lambda$18715/0x00007f348af09e98@5a257997

trimAndShout("  hello ")
// res3: String = "HELLO"

pipeline[Function1](Nil).apply("unchanged")
// res4: String = "unchanged"
```

## Laws

Every `IdentityCompose` instance must satisfy the laws of [AssociativeCompose](./associativecompose.md) and one more, checked by `IdentityCompose#identityCompose`:

| Law      | Statement                                                                    |
| -------- | ---------------------------------------------------------------------------- |
| Identity | `compose(identity, ab)` and `compose(ab, identity)` both equal `ab`          |

Like `AssociativeCompose#associativeCompose`, the check needs an `Equal` instance for the arrow type.

## Related Abstractions

`IdentityCompose` is to [AssociativeCompose](./associativecompose.md) what [Identity](../concrete-types/identity.md) is to [Associative](../concrete-types/associative.md): the same operation with a neutral element added. The library provides instances for `Function1`, for `URLayer` and for an environment-reading `URIO`.
