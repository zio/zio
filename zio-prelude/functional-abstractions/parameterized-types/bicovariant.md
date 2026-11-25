# Bicovariant

> `Bicovariant` describes a type constructor with two type parameters that can be mapped covariantly in both. A value of type `F[A, B]` can have its first parameter mapped with a function from `A` to `AA`, its second parameter mapped with a function from `B` to `BB`, or both at once with `bimap`.

`Either` is the standard example. An `Either[A, B]` holds either an `A` on the left or a `B` on the right, and we can transform either side independently.

`Bicovariant` extends `RightCovariant`, which lets the second parameter be mapped on its own. Because of that, the same instance also gives a [Covariant](./covariant.md) for the second parameter through `deriveCovariant`. It also gives a `Covariant` for the first parameter, wrapped in the `Failure` newtype, through `deriveFailureCovariant`.

## Motivation

Code that works with results of the shape `F[Error, Value]` often needs to adapt both sides: convert a low-level error into a domain error, and convert the value at the same time. Writing that for each concrete type duplicates the logic, and `Covariant` cannot help because it only maps one type parameter. `Bicovariant` abstracts over any binary type constructor that can be mapped on both sides, so a helper written once works for `Either`, for a pair, and for `Exit`.

Use `Bicovariant` when both parameters are covariant. If the first parameter is an input, as in a function, use [Divariant](./divariant.md). If there are three parameters and an environment, use [Zivariant](./zivariant.md).

## Mapping Both Sides

`bimap` takes a function for each side and returns a function from the original `Either` to the transformed one. Here the left side becomes its length and the right side is doubled:

```scala
import zio.prelude._

val transform = implicitly[Bicovariant[Either]].bimap((s: String) => s.length, (i: Int) => i * 2)
```

Next, we apply the mapped function to each side of an `Either`:

```scala
transform(Left("hello"))
// res1: Either[Int, Int] = Left(value = 5)

transform(Right(21))
// res2: Either[Int, Int] = Right(value = 42)
```

## Mapping One Side

`leftMap` transforms only the first parameter, and `rightMap` transforms only the second. The other side is left alone:

```scala
implicitly[Bicovariant[Either]].leftMap((s: String) => s.toUpperCase).apply(Left("error"))
// res3: Either[String, Nothing] = Left(value = "ERROR")

implicitly[Bicovariant[Either]].rightMap((i: Int) => i + 1).apply(Right(41))
// res4: Either[Nothing, Int] = Right(value = 42)
```

`Tuple2` and `Exit` also have `Bicovariant` instances, so the same operators apply to pairs and to ZIO `Exit` values.

## Writing Generic Code

The syntax `bimap`, `leftMap` and `rightMap` is available on any `F[A, B]` that has a `Bicovariant[F]`. A helper can therefore be written once and used for every such type:

```scala
def render[F[+_, +_]: Bicovariant](result: F[Int, String]): F[String, Int] =
  result.bimap(code => s"E$code", text => text.length)
```

Next, the same helper is applied to an `Either` and to a tuple:

```scala
render[Either](Left(404))
// res5: Either[String, Int] = Left(value = "E404")

render[Either](Right("hello"))
// res6: Either[String, Int] = Right(value = 5)

render[Tuple2]((500, "ok"))
// res7: (String, Int) = ("E500", 2)
```

## Laws

Every `Bicovariant` instance must satisfy these laws, each checked by a method on the type class:

| Law                | Method                           | Statement                                                    |
| ------------------ | -------------------------------- | ------------------------------------------------------------ |
| Identity           | `Bicovariant#bimapIdentity`      | `bimap(identity, identity)` leaves a value unchanged         |
| Composition        | `Bicovariant#bimapCompose`       | `bimap(g compose f, h compose i)` equals `bimap(g, h)` after `bimap(f, i)` |
| Coherence          | `Bicovariant#bimapCoherence`     | `bimap(f, g)` equals `rightMap(g)` followed by `leftMap(f)`  |
| Left identity      | `Bicovariant#leftMapIdentity`    | `leftMap(identity)` leaves a value unchanged                 |
| Left composition   | `Bicovariant#leftMapCompose`     | `leftMap(g compose f)` equals `leftMap(g)` after `leftMap(f)` |
| Right identity     | `RightCovariant#rightMapIdentity`| `rightMap(identity)` leaves a value unchanged                |
| Right composition  | `RightCovariant#rightMapCompose` | `rightMap(g compose f)` equals `rightMap(g)` after `rightMap(f)` |

## Related Abstractions

[Covariant](./covariant.md) maps the single parameter of a one-parameter container, and `Bicovariant` is its two-parameter version. [Divariant](./divariant.md) differs in that its first parameter is contravariant, and [Zivariant](./zivariant.md) extends the idea to three parameters.
