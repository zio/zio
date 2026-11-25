# AssociativeCompose

> `AssociativeCompose` describes a way to chain two transformations, where the transformation from the first input to the second output is itself formed by chaining. Functions are the standard example: given a function from `A` to `B` and a function from `B` to `C`, composing them gives a function from `A` to `C`.

The type class is parameterized over a binary type constructor `=>:`, which plays the role of the arrow. It is contravariant in its first parameter and covariant in its second, like `Function1`. For plain functions, that is `Function1`. The type class has a single operation, `compose`, and one law: composing is associative, so chaining three transformations gives the same result however the chain is grouped. `associativeCompose` is not a second operation. It is the method that checks that law for a given instance.

## Motivation

Some pipelines are built from steps whose outputs feed the next step's inputs, and the steps are not always plain functions. A `ZLayer` that needs a `Config` and produces a `Database` can be chained with one that needs a `Database` and produces a `UserRepo`. A function that reads an environment can be chained with another function of the produced environment. `AssociativeCompose` abstracts over what these have in common, so that code that wires steps together can be written once for any arrow.

Use `AssociativeCompose` when the thing being chained is a transformation from one type to another. If the steps also need a neutral "do nothing" step, use [IdentityCompose](./identitycompose.md). If the steps are effects that produce values inside a container, see [AssociativeFlatten](./associativeflatten.md) instead, which chains containers rather than arrows.

## Composing Functions

`compose` takes the function to apply second and the function to apply first. For functions, it returns a function that applies the first one, then the second. The function that runs first takes a `String` and gives an `Int`, and the function that runs second takes that `Int` and gives a new `Int`:

```scala
import zio.prelude._

val lengthThenDouble: String => Int =
  implicitly[AssociativeCompose[Function1]].compose((n: Int) => n * 2, (s: String) => s.length)
```

Next, we apply the composed function to a string:

```scala
lengthThenDouble("hello")
// res1: Int = 10
```

The result doubles the length of `"hello"`, which is ten.

## Composing Layers

The library provides instances for `Function1`, for `URLayer`, and for an environment-reading `URIO` that returns a `ZEnvironment`. For `URLayer`, `compose` wires the output of one layer into the input of another, which gives the same result as the `>>>` operator on layers:

```scala
import zio._
import zio.prelude._

final case class Config(url: String)
final case class Database(url: String)
final case class UserRepo(database: Database)

val database: URLayer[Config, Database] =
  ZLayer.fromFunction((config: Config) => Database(config.url))

val userRepo: URLayer[Database, UserRepo] =
  ZLayer.fromFunction((database: Database) => UserRepo(database))

val wired: URLayer[Config, UserRepo] =
  implicitly[AssociativeCompose[URLayer]].compose(userRepo, database)
```

The first argument of `compose` is always the step that runs second, so `userRepo` comes before `database`.

## Associativity

Because composition is associative, a chain of three functions gives the same result whichever pair we group first. Any instance of `AssociativeCompose` must satisfy this rule, so the grouping of a chain never changes its result.

`AssociativeCompose#associativeCompose` is the law written as a check. Given three arrows `ab`, `bc` and `cd` and an `Equal` instance for the arrow type, it composes them right-nested and left-nested and compares the results:

```scala
trait AssociativeCompose[=>:[-_, +_]] {
  def compose[A, B, C](bc: B =>: C, ab: A =>: B): A =>: C

  def associativeCompose[A, B, C, D](ab: A =>: B, bc: B =>: C, cd: C =>: D)(implicit eq: Equal[A =>: D]): Boolean
}
```

The law is `compose(cd, compose(bc, ab)) === compose(compose(cd, bc), ab)`. `Function1` has no `Equal` instance, so this check cannot be applied to functions directly. An instance for your own arrow type needs an `Equal` for that type before `associativeCompose` can be used in a property test.

## Related Abstractions

`AssociativeCompose` is the arrow-shaped counterpart of [Associative](../concrete-types/associative.md), which combines two values of one type, and of [AssociativeBoth](./associativeboth.md), which combines two containers. [IdentityCompose](./identitycompose.md) adds the identity arrow. [Divariant](./divariant.md) and [Bicovariant](./bicovariant.md) describe how to adapt the ends of a binary type constructor rather than chain its steps.
