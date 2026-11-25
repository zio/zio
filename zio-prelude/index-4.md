# Functional Data Types in ZIO Prelude

> ZIO Prelude includes several data types to help us model our domains more accurately and solve common problems. Most of them take a fact about the data, such as "this collection is never empty", "these errors accumulate" or "this value has no effect", and move it into the type so that the compiler enforces it and callers do not need to handle cases that cannot happen.

The data types fall into a few groups:

- **Non-empty collections** (`NonEmptyList`, `NonEmptyMap`, `NonEmptySet`, `NonEmptySortedMap`, `NonEmptySortedSet`, `ZNonEmptySet`) guarantee at least one element, so operations such as `head` are total.
- **Sets with weights** (`ZSet`, `ZNonEmptySet`) generalize a set to count how many times, or how much, an element is present.
- **Results with several outcomes** (`Validation`, `ZValidation`, `These`) model computations that can fail with many errors, succeed with warnings, or have both a value and an error.
- **Descriptions of structure** (`ParSeq`, `Recursive`, `Equivalence`) describe events, recursive data and conversions between types.
- **Helpers for abstractions** (`Id and Const`, `Optional`) give simple meaning to type constructors or wrap optional fields of external models.

Each page below explains what the type models and how to use it, and links to the related abstractions in [Functional Abstractions](../functional-abstractions/index.md).

- **[Equivalence](equivalence.md)** - A description of an equivalence relationship between two data types.
- **[NonEmptyList](nonemptylist.md)** - A list that is guaranteed to be non-empty to more accurately model situations where we know a collection has at least one element.
- **[Id and Const](id-and-const.md)** - Two newtypes that give effect-free meaning to a type constructor's parameters, for use with operators such as `ForEach`.
- **[NonEmptyMap](nonemptymap.md)** - A map that is guaranteed to contain at least one entry.
- **[NonEmptySet](nonemptyset.md)** - A set that is guaranteed to contain at least one element.
- **[NonEmptySortedMap and NonEmptySortedSet](nonemptysorted.md)** - Sorted versions of `NonEmptyMap` and `NonEmptySet`, keeping their keys or elements in `Ordering` order.
- **[Optional](optional.md)** - A value that may be present or absent, with instances for the functional abstractions.
- **[ParSeq](parseq.md)** - A description of events that happen in sequence or in parallel, useful for modeling several failures or several requests.
- **[Recursive](recursive.md)** - A way to describe recursive data structures and fold them without writing the recursion by hand.
- **[These](these.md)** - A data type that may either be a `Left` with an `A`, a `Right` with a `B`, or a `Both` with an `A` and a `B`, useful for modeling problems such as merging streams of data.
- **[Validation](validation.md)** - A data type that may be either a success or an accumulation of one or more errors, allowing modeling multiple failures for applications such as data validation.
- **[ZNonEmptySet](znonemptyset.md)** - A non-empty weighted set, such as a multiset that is guaranteed to contain at least one element.
- **[ZSet](zset.md)** - A generalization of a set that generalizes measures of "how many" of an element exist in a set, supporting multi-sets, "fuzzy" sets, and other data structures.
- **[ZValidation](zvalidation.md)** - A generalization of `Validation` that allows maintaining a log of warnings in addition to accumulating errors.
