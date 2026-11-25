# Zivariant

> `Zivariant` describes a type constructor with three parameters: an environment `R` that is contravariant, and an error `E` and a value `A` that are both covariant. ZIO effects such as `ZIO[R, E, A]` have this shape, so `Zivariant` describes how to transform an effect's environment, error, and value.

It is the three-parameter version of the earlier abstractions. `Bicovariant` maps two covariant parameters, and `Divariant` maps a contravariant input and a covariant output. `Zivariant` combines the two, because the environment works like a contravariant input and the error and value work like covariant outputs.

## Motivation

ZIO, `ZStream` and `ZSTM` each have their own `map`, `mapError` and `provideSomeEnvironment`, but no common type class links them, so a helper that adapts the error type of "any ZIO-like thing" has to be written once per type. `Zivariant` abstracts over the three-parameter shape so that such a helper is written once. It is also the building block behind generic wrappers that must change the environment, error and value of whatever effect they receive, for example to translate errors at a module boundary.

Use `Zivariant` for types with an environment, an error and a value. For types with only an error and a value, such as `Either`, use [Bicovariant](./bicovariant.md). The three-parameter operation is `zimap`, and every other method (`map`, `mapLeft`, `bimap`, `contramap`, `dimap`) is derived from it. The type class also provides a [Covariant](./covariant.md) for the value parameter through `deriveCovariant`, and one for the error parameter wrapped in the `Failure` newtype through `deriveFailureCovariant`.

## Mapping Values and Errors

`map` transforms the success value, and `mapLeft` transforms the error. Both return a function from the original effect to the new one:

```scala
import zio._
import zio.prelude._
```

Next, we map the success value of an effect:

```scala
val succeeded: ZIO[Any, String, Int] = ZIO.succeed(20)

val mapped: ZIO[Any, String, Int] =
  implicitly[Zivariant[ZIO]].map((i: Int) => i + 1).apply(succeeded)

val failedWithLength: ZIO[Any, Int, Int] =
  implicitly[Zivariant[ZIO]].mapLeft((s: String) => s.length).apply(ZIO.fail("boom"))
```

## Transforming the Environment

`contramap` changes the environment that an effect requires. The function takes the new environment and returns the old one, which is why it runs in the opposite direction:

```scala
val needsNumber: ZIO[Int, String, Int] =
  ZIO.serviceWith[Int](_ * 2)

val needsString: ZIO[String, String, Int] =
  implicitly[Zivariant[ZIO]].contramap((env: ZEnvironment[String]) => ZEnvironment(env.get[String].length)).apply(needsNumber)
```

`Zivariant` has instances for `ZIO`, `ZStream`, and `ZSTM`, so the same operators apply to each of them. It also has instances for the plain function types `ZEnvironment[R] => Either[E, A]` and `ZEnvironment[R] => (E, A)`.

## Writing Generic Code

The syntax `zimap`, `contramap`, `mapLeft`, `map`, `bimap` and `dimap` is available on any `Z[R, E, A]` that has a `Zivariant[Z]`. The following helper translates the error of any such type into an exception, so one definition serves effects and streams alike:

```scala
import zio._
import zio.stream.ZStream
import zio.prelude._

def asException[Z[-_, +_, +_]: Zivariant, R, A](value: Z[R, String, A]): Z[R, RuntimeException, A] =
  value.mapLeft(message => new RuntimeException(message))

val effect: ZIO[Any, RuntimeException, Int] =
  asException[ZIO, Any, Int](ZIO.fail("boom"))

val stream: ZStream[Any, RuntimeException, Int] =
  asException[ZStream, Any, Int](ZStream.fail("boom"))
```

## Laws

Every `Zivariant` instance must satisfy these laws, each checked by a method on the type class:

| Law                    | Method                                     | Statement                                                                   |
| ---------------------- | ------------------------------------------ | --------------------------------------------------------------------------- |
| Identity               | `Zivariant#zimapIdentity`                  | `zimap(identity, identity, identity)` leaves a value unchanged              |
| Composition            | `Zivariant#zimapComposition`               | `zimap` of composed functions equals composing two `zimap` calls            |
| Coherence              | `Zivariant#zimapCoherentWithMapAndContramap` | `zimap(r, e, a)` equals `contramap(r)`, then `map(a)`, then `mapLeft(e)`  |

## Related Abstractions

[Bicovariant](./bicovariant.md) and [Divariant](./divariant.md) are the two-parameter abstractions that `Zivariant` combines, and [Covariant](./covariant.md) and [Contravariant](./contravariant.md) are their one-parameter forms.
