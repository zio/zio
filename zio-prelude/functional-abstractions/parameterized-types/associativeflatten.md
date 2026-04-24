# AssociativeFlatten

> `AssociativeFlatten[F]` describes a way of combining two layers of a value of type `F[F[A]]` into a `F[A]` in a way that is associative.

`AssociativeFlatten[F]` describes a way of combining two layers of a value of type `F[F[A]]` into a `F[A]` in a way that is associative.

Its signature is:

```scala
trait AssociativeFlatten[F[+_]] {
  def flatten[A](ffa: F[F[A]]): F[A]
}
```

If we import `zio.prelude._` we can use the `flatten` operator on any data type for which an `AssociativeFlatten` instance is defined to flatten a nested value `F[F[A]]` down to a single value.

This may appear to be a unary operator but it is actually a binary operator because there are two layers of `F` values here, the outer `F` value and the inner `F` value. The `flatten` operator combines these two layers of `F` values into a single value.

The `flatten` operator must be associative, so if we have three layers of nested values flattening the outer and the middle layers and then flattening the resulting layer with the inner layer must be the same as flattening the middle and the inner layers and flattening the outer layer with that.

```scala
fffa.flatten.flatten == fffa.map(_.flatten).flatten
```

This looks slightly different than the signatures of the other associative laws we have seen because of our need to map over the value to flatten it but it is conceptually the same as the associative laws for the `Associative`, `AssociativeBoth`, and `AssociativeEither` functional abstractions.

The `flatten` operator corresponds to running the outer value to produce a new value and then running that value.

We can see this by recognizing that at this point we know that `F` is covariant so it produces values of type `A`. We start with a value `F[F[A]]`, which is essentially a producer of producers of values of type `A`.

So to get a final value `F[A]` that just produces `A` values we are going to need to run the outer `F` value to get those producers of `A` values, then run those producers of `A` values to get actual `A` values, and finally put the whole thing back together in the structure of `F`.

This turns out to be extremely powerful.

To see what this looks like let's start as we have before with `ZIO`.

A common mistake we might make when getting started with `ZIO` is accidentally creating a nested `ZIO` workflow like this:

```scala

val greet: ZIO[Random, Nothing, ZIO[Console, IOException, Unit]] =
  Random.nextIntBounded(100).map { n =>
    Console.printLine(s"The number is $n")
  }
// greet: ZIO[Random, Nothing, ZIO[Console, IOException, Unit]] = FlatMap(
//   trace = "repl.MdocSession.MdocApp.greet(associativeflatten.md:23)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp.greet(associativeflatten.md:23)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@5b6966c1
//   ),
//   successK = zio.ZIO$$Lambda$17101/0x00000008049d5040@40e884fc
// )
```

This is very understandable.

We want to perform a second `ZIO` workflow, in this case printing to the console, based on the result of the first workflow. So we use the `map` operator, which is described by the `Covariant` abstraction, to get the success value of the first workflow and transform it with a function.

It is worth pointing out here that `Covariant` is the only abstraction in ZIO Prelude that describes the ability to transform the output of a covariant type with a function. So based on the structure described by the other functional abstractions in ZIO Prelude there is nothing we could do other than use `map` to make progress towards implementing this.

However, we are now faced with a problem. We have a `ZIO` workflow that produces another `ZIO` workflow instead of producing a value. This is normally not what we want.

This workflow is going to be difficult to compose with other workflows. If we want to get the result of the second workflow we are now going to have to use the `map` operator twice just to access the inner value.

In addition, running this workflow will probably not do what we intend, because it will just generate a random number and then return another workflow. We will not see anything printed to the console.

Conceptually what we would like to do is run this workflow to produce its result, which is another workflow that will actually print something to the console, and then run that workflow. However, we don't want to have to run these nested workflows ourselves and we want to be able to use this workflow in an ergonomic way without going through these multiple layers of nesting.

This is exactly what the `flatten` operator does for us. It takes a `ZIO` workflow that produces another workflow and flattens it down to a single workflow that conceptually runs the first workflow and then runs the resulting workflow.

Let's see how we can use the `flatten` operator to fix our code from above.

```scala
val greet: ZIO[Random with Console, IOException, Unit] =
  Random.nextIntBounded(100).map { n =>
    Console.printLine(s"The number is $n")
  }.flatten
// greet: ZIO[Random with Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp.<local MdocApp>.greet(associativeflatten.md:35)",
//   first = FlatMap(
//     trace = "repl.MdocSession.MdocApp.<local MdocApp>.greet(associativeflatten.md:33)",
//     first = Stateful(
//       trace = "repl.MdocSession.MdocApp.<local MdocApp>.greet(associativeflatten.md:33)",
//       onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@175ec8
//     ),
//     successK = zio.ZIO$$Lambda$17101/0x00000008049d5040@50a19db9
//   ),
//   successK = <function1>
// )
```

Now we have eliminated the nested workflows. This workflow will do exactly what we want, first generating a random number and then printing it to the console.

Of course if we are working with `ZIO` we quickly learn to use the `flatMap` operator, which just combines `map` and `flatten`.

```scala
val greet: ZIO[Random with Console, IOException, Unit] =
  Random.nextIntBounded(100).flatMap { n =>
    Console.printLine(s"The number is $n")
  }
// greet: ZIO[Random with Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp.<local MdocApp>.greet(associativeflatten.md:43)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp.<local MdocApp>.greet(associativeflatten.md:43)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@604c9635
//   ),
//   successK = <function1>
// )
```

The `flatMap` operator is extremely convenient, especially in combination with Scala's syntax for for comprehensions. However, we are now in a position to see that there isn't really anything special about the `flatMap` operator itself.

The `flatMap` operator is just a combination of `map` and `flatten` and `map` is a very basic operator defined on all covariant data types. The real work here is in the `flatten` operator.

The `flatten` operator has to run one value to produce its output then run that output to produce a result.

In the case of `ZIO` this is at least conceptually relatively simple because a `ZIO` workflow always succeeds with exactly one value. So the interpretation of `flatten` is just to run the first workflow to produce its result and then if it is successful run that workflow.

In other cases the interpretation can be more complex.

For example, consider `ZStream`, which can succeed with zero or more values. In that case the interpretation of flattening a stream of streams is to concatenate the inner streams into a single stream.

Many other data types also support `flatten` operators, all of which have a similar interpretation of running the outer value to produce an inner value and then running the inner value.

Data types that model failure, such as `Either` and `Option`, are one example of this. Flattening a nested `Either` or `Option` corresponds to running the outer value and if it is a success running the inner value but otherwise failing immediately.

```scala
def flatten[E, A](eea: Either[E, Either[E, A]]): Either[E, A] =
  eea match {
    case Left(e)   => Left(e)
    case Right(ea) => ea
  }
```

```scala
def flatten[A](ooa: Option[Option[A]]): Option[A] =
  ooa match {
    case None     => None
    case Some(oa) => oa
  }
```

Collection types also have a `flatten` operator with a natural interpretation. If we think of running a collection as producing a set of values then flattening a collection corresponds to running a collection of collections and then running each of those collections to produce its values.

```scala
def flatten[A](aas: Chunk[Chunk[A]]): Chunk[A] =
  aas.foldLeft[Chunk[A]](Chunk.empty)(_ ++ _)
```

Notice the similarity here to the interpretation of `flatten` for `ZStream`, although `ZStream` can be potentially effectual and resourceful.

The `flatten` operator is so powerful because it allows the next value to be produced by running the first value.

To see this, consider the following two `ZIO` workflows.

```scala
val helloAssociativeBoth: ZIO[Console, IOException, Unit] =
  console.putStrLn("What's your name?") *>
    console.readLine *>
    console.putStrLn(s"Hello! Welcome to ZIO!")

val helloAssociativeFlatten: ZIO[Console, IOException, Unit] =
  for {
    _    <- console.putStrLn("What's your name?") *>
    name <- console.readLine *>
    _    <- console.putStrLn(s"Hello $name! Welcome to ZIO!")
  } yield ()
```

The first workflow is written using only the structure described by the `Covariant` and `AssociativeBoth` abstractions.

We can use these abstractions to describe running one workflow and then running another workflow. So the first workflow will ask us for our name, then read our name, and then say hello.

However, notice that in the first workflow we can't greet the user by name. Although we can describe printing to the console after reading from the console, we can't actually use the value we read from the console in printing to the console.

Workflows described solely in terms of the `Covariant` and `AssociativeBoth` abstractions are static like this. We can compose them together but later workflows can't use information produced by prior ones.

This can have significant advantages in terms of opportunities for optimization. The first workflow can potentially be analyzed before being run, allowing us to determine what kinds of things it will do or a more efficient way of running it.

The second workflow is written using the structure described by the `AssociativeFlatten` abstraction.

Now we can describe a more powerful notion of running one workflow that produces a workflow and then running that workflow. Now we are able not only to print a greeting after reading from the console but to use the value we read from the console in the greeting.

Workflows written with the structure of `AssociativeFlatten` are much more flexible and allow building workflows of arbitrary complexity. This is why the `flatMap` operator is so useful when working with `ZIO`.

At the same time, this power limits our ability to introspect on workflows written using the `flatten` operator.

As we saw from our discussion at the beginning, we have to run the outer value to produce the inner value. So we don't know what the next workflow will be until we actually run the current workflow.

There are some ways we can increase the flexibility of workflows that don't use the structure provided by `AssociativeFlatten`. 

For example the `AssociativeEither` abstraction allows us to describe a concept of failure.

So we could express some limited degree of conditionality, for example reading from the console again if reading the first time failed. However that would still not allow us to actually use the result from the previous workflow in the current one.

In some cases this more limited functionality can be acceptable. For example in parsing it can get us quite far, and as discussed above there are benefits to this approach.

But for general purposes programming the flexibility provided by the `flatten` operator is necessary to support the variety of things we may want to describe with our workflows.

As this discussion has hopefully made clear, `AssociativeFlatten` is a very important functional abstraction that gives us the ability to run a value that produces another value and then run that value, allowing us to describe workflows in a way that is extremely flexible.

If you are working with existing data types in ZIO or the Scala standard library they probably already define `flatMap` and `flatten`.

There are some operators defined on the `ForEach` and `NonEmptyForEach` abstractions that work with data types that have instances of `AssociativeFlatten` or `IdentityFlatten`. These can be helpful to do things like fold over a collection with the possibility of failure.

When defining your own parameterized data types the `flatMap` operator is so useful that you should be thinking about what an implementation of this abstraction would look like for your own data type. Or you may intentionally not want to expose it to allow for more static analysis, but that is definitely something worth thinking about.

For all the reasons discussed above the `AssociativeFlatten` and `IdentityFlatten` abstractions are also very important in writing generic code to express values that depend on other values in a parameterized context.
