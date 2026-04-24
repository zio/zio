# Introduction

> In addition to abstractions for concrete types, ZIO Prelude provides a set of functional abstractions to describe the common structure of _parameterized types_.

In addition to abstractions for concrete types, ZIO Prelude provides a set of functional abstractions to describe the common structure of _parameterized types_.

A parameterized type is a type that is parameterized on one or more other types. For example, a `List[A]` is parameterized on the element type `A`.

When we are describing the common structure of parameterized types we are talking about the structure of the parameterized type without knowing about the type it is parameterized on. For example, consider the following two abstractions to describe associative ways of combining two values of a data type.

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}

trait AssociativeBoth[F[_]] {
  def both[A, B](left: => F[A], right: => F[B]): F[(A, B)]
}
```

The `Associative` abstraction describes a way of associatively combining two concrete data types.

The syntax is relatively straightforward here. The `Associative` is parameterized on a type `A` that has an associative `combine` operator.

When we define an instance of `Associative` we know the type of the values we are combining and can use that information in implementing the `combine` operator. For example we know we have two `Int` values and can combine them by adding them together.

```scala
implicit val IntAssociative: Associative[Int] =
  new Associative[Int] {
    def combine(left: => Int, right: => Int): Int =
      left + right
  }
// IntAssociative: Associative[Int] = repl.MdocSession$MdocApp$$anon$1@59ce9e30
```

In contrast, the `AssociativeBoth` abstraction describes a way of associatively combining two parameterized types.

The syntax here may look a little new. Instead of being parameterized on an `A`, `AssociativeBoth` is parameterized on an `F[_]`.

There is nothing special about the `F` here, just like the `A` in `Associative` it is just a placeholder for some type. By convention we start with `A` for concrete types and `F` for parameterized types but we can call these types whatever we want.

What is important is the `[_]` after `F`. This tells the Scala compiler that this is a parameterized type, such as a `List` rather than a `List[Int]`.

The importance of this becomes clear when we define an instance of `AssociativeBoth` because we do not know anything about the types `A` and `B` that the `left` and `right` values are parameterized on. This prevents us from doing anything with the `A` and `B` values that requires knowledge of their types and so essentially forces us to work exclusively at the level of the `F` structure.

To see this, consider how we would implement an instance of `AssociativeBoth` for `List`.

```scala
implicit val ListAssociativeBoth: AssociativeBoth[List] =
  new AssociativeBoth[List] {
    def both[A, B](left: => List[A], right: => List[B]): List[(A, B)] =
      left.flatMap(a => right.map(b => (a, b)))
  }
// ListAssociativeBoth: AssociativeBoth[List] = repl.MdocSession$MdocApp$$anon$2@147352df
```

Notice how the `AssociativeBoth` instance is parameterized on `List` rather than a list of any specific type. This is important because it says the `ListAssociativeBoth` instance knows how to combine any two lists in an associative way, not just two lists of some specific type.

What would the implementation of such a way of combining look like? It would not be able to use any information about the `A` or `B` values, because they could be anything, so it would have to combine them in a way that worked for any `A` and `B` values.

The `AssociativeBoth` abstraction does this by combining the `A` and `B` values into a tuple. We will see later that this is one of two fundamental ways that we can combine two values in a generic way.

In our implementation for `List` we return the Cartesian product of the two lists, which is an associative operation after reassociating nested tuples.

We'll talk more about `AssociativeBoth` in the section on that abstraction. For now the goal of this discussion is not to discuss `AssociativeBoth` in detail but to get a sense of what it means to describe the structure of a parameterized type instead of a concrete one.

Just like for concrete types, the abstractions for parameterized types fall into two categories.

## Properties Of Parameterized Types

The first set of abstractions define properties of single values of a parameterized type `F[A]`. All of them describe the fundamental nature of the `A` parameter with respect to `F`.

### Invariant

The first relationship that `A` can have to `F` is that `F` both produces and consumes `A` values. For example, a `JsonCodec[A]` both produces `A` values by turning JSON into `A` values and consumes `A` values by turning `A` values into JSON.

Parameterized types that both produce and consume `A` values are _invariant_ in the `A` type parameter and we describe them with the `Invariant` abstraction.

An invariant type `F[A]` can be transformed into an `F[B]` with an `Equivalence[A, B]` using the `invmap` operator. Conceptually, if we have a data type that produces and consumes `A` values we can always turn it into a data type that produces and consumes `B` values by transforming all inputs into `A` values and all outputs back to `B` values with the equivalence relationship.

This is useful to "lift" equivalence relationships into the context of a parameterized type. For example, if we have defined an equivalence between new and old versions of our data model we can use `Invariant` to convert the `JsonCodec` for the old data model into a `JsonCodec` for the new data model.

### Covariant

The second relationship that `A` can have to `F` is that `F` produces `A` values but does not consume them. Data types that produce `A` values may either just contain existing `A` values, like a `Chunk`, or potentially produce `A` values at some point in the future, like a `ZIO`.

Parameterized types that produce but do not consume `A` values are _covariant_ in the `A` type parameter and we describe them with the `Covariant` abstraction.

A covariant type `F[A]` can be transformed into an `F[B]` with a function `A => B` using the `map` operator. Conceptually, if we have a data type that produces `A` values then we can create a data type that produces `B` values simply by taking each output and transforming it with the function.

This is useful to allow us to transform the output of a covariant type to build richer data pipelines. For example, if we had a function that decrypted some data and a `ZIO` effect that loaded the encrypted data from a file we could return a new `ZIO` effect that produced the decrypted data, which we could then compose with the rest of our program.

### Contravariant

The third relationship that `A` can have to `F` is that `F` consumes `A` values but does not produce them. Examples of data types that consume values of one or more types include Scala functions with respect to their input, `ZIO` with respect to its environment type, and `ZSink` with respect to stream elements.

Parameterized types that consume but do not produce `A` values are _contravariant_ in the `A` type parameter and we describe them with the `Contravariant` abstraction.

A contravariant type `F[A]` can be transformed into an `F[B]` with a function `B => A` using the `contramap` operator. Conceptually, if we have a data type that consumes `A` values then we can create a data type that consumes `B` values  simply by transforming each `B` value into an `A` value with the function before feeding it to the original data type.

This is useful to allow us to transform the input of a contravariant type to build richer data pipelines. For example, if we have a sink that takes chunks of bytes and writes them to a file we could transform it into a sink that takes strings and writes them to a file by providing a function to transform strings into bytes.

### ForEach

The `ForEach` abstraction builds on the `Covariant` abstraction by describing a data type that not only produces `A` values but actually contains zero or more `A` values as opposed to merely being able to potentially generate them at some point in the future. For example, a `Chunk` has zero or more existing `A` values and so has a `ForEach` instance defined for it whereas a `ZIO` merely may produce an `A` value at some point in the future.

A data type with a `ForEach` instance contains zero or more existing `A` values so we can iterate over it, potentially transforming its values while maintaining its structure using an operator like `ZIO.foreach`. We can also tear it down entirely and reduce it to a summary value with an operator like `foldLeft`.

This ability to get an `A` value out of the data type only exists because `ForEach` describes a data type where the `A` values already exist. If we had a data type like `ZIO` then we would not be able to get an `A` value out of it because a `ZIO` is only a description of a workflow that may produce an `A` value when it is run.

The `ForEach` abstraction is one of the most practically useful functional abstractions in ZIO Prelude. Most collection operators can be defined in terms of `ForEach` and especially in combination with the abstractions for describing ways of combining concrete types it provides very powerful ways of working with collections.

### NonEmptyForEach

The `NonEmptyForEach` abstraction further builds on `ForEach` to describe a parameterized type that contains one or more existing `A` values. For example, a `NonEmptyChunk` is guaranteed to contain at least one `A` value.

Because a data type with a `NonEmptyForEach` instance must always contain at least one existing `A` value we can define certain operators for it that would not be safe to define for a data type that might not contain any `A` values. For example, we can define a `reduce` operator that reduces the collection to a summary value with an associative operator whereas with a `ForEach` instance we can only define a `fold` operator that also requires an identity value to handle the empty case.

## Combining Parameterized Types

The second set of abstractions describe ways of combining parameterized types.

Because of the additional structure of parameterized types there are three separate ways that we can combine values of parameterized types, each of which may satisfy properties of associativity, commutativity, and identity.

The first way of combining an `F[A]` and an `F[B]` is to return both `A` and `B` values. This corresponds to some notion of doing both things, though what exactly this means will depend on the parameterized type and the combining operation.

This way of combining is described by the `AssociativeBoth`, `CommutativeBoth`, and `IdentityBoth` functional abstractions.

The second way of combining an `F[A]` and an `F[B]` is to return either an `A` or a `B` value. This corresponds to some notion of choosing which value to return, though again what exactly this means and how we choose will depend on the parameterized type and the combining operation.

This way of combining is described by the `AssociativeEither`, `CommutativeEither`, and `IdentityEither` functional abstractions.

The third way of combining actually combines two layers of `F` values, converting an `F[F[A]]` into an `F[A]`. This corresponds to some notion of evaluating the outer layer and then evaluating the inner layer.

This way of combining is described by the `AssociativeFlatten` and `IdentityFlatten` functional abstractions.

### AssociativeBoth

The `AssociativeBoth` abstraction describes a parameterized type `F[A]` with an associative operator `both` that can combine a value of type `F[A]` and a value of type `F[B]` into a value of type `F[(A, B)]`. This is described by the `zip` operator on `ZIO` and corresponds to running the left value and then running the right value.

### CommutativeBoth

`CommutativeBoth` describes a way of combining an `F[A]` and an `F[B]` to produce an `F[(A, B)]` that is both associative and commutative.

This is described by the `zipPar` operator on `ZIO` and corresponds to running the left value and the right value in parallel, since this is the only way that the order will not matter as required by the commutative property.

### IdentityBoth

The `IdentityBoth` abstraction describes a way of combining an `F[A]` and an `F[B]` to produce an `F[(A, B)]` that is both associative and has an identity value of type `F[Any]`. In ZIO the identity value is value is `ZIO.unit` since zipping any value with unit is equivalent to returning the original value unchanged

### AssociativeEither

The `AssociativeEither` abstraction describes a parameterized type `F[A]` with an associative operator `either` that can combine a value of type `F[A]` and a value of type `F[B]` into a value of type `F[Either[A, B]]`. This is described by the `orElseEither` operator on `ZIO` and corresponds to running the left value, but if it fails then running the right value.

### CommutativeEither

`CommutativeEither` builds on `AssociativeEither` by describing an operator that combines an `F[A]` and and `F[B]` to produce an `F[Either[A, B]]` in a way that is both associative and commutative. In ZIO this corresponds to the `raceEither` operator which runs both effects concurrently and returns the first to succeed, since running both effects concurrently is the only way that the order will not matter as required by the commutative property.

### IdentityEither

The `IdentityEither` abstraction describes a way of combining an `F[A]` and an `F[B]` to produce an `F[Either[A, B]]` that is both associative and has an identity value of type `F[Nothing]`. `ZIO` does not have such an identity value since it is not possible to fail without any error but conceptually such an identity would correspond to a failure that did not contain any information.

### AssociativeFlatten

The `AssociativeFlatten` abstraction describes the ability to flatten two layers of `F` structure in an associative way. In `ZIO` this corresponds to the `flatten` operator, and in combination with the `Covariant` abstraction the `flatMap` operator, corresponding to running one `ZIO` value and using its result to generate another `ZIO` value and running that value.

### IdentityFlatten

The `IdentityFlatten` abstraction describes an identity value of type `F[Any]` with respect to the associative flattening operation defined by `AssociativeFlatten`. In `ZIO` this corresponds to `ZIO.unit` and is the same as the identity value defined by the `IdentityBoth` instance.
