# Functional Data Types in ZIO Prelude

> ZIO Prelude includes several data types to help us model our domains more accurately and solve common problems.

ZIO Prelude includes several data types to help us model our domains more accurately and solve common problems.

- **[Equivalence](equivalence.md)** - A description of an equivalence relationship between two data types.
- **[NonEmptyList](nonemptylist.md)** - A list that is guaranteed to be non-empty to more accurately model situations where we know a collection has at least one element.
- **[These](these.md)** - A data type that may either be a `Left` with an `A`, a `Right` with a `B`, or a `Both` with an `A` and a `B`, useful for modeling problems such as merging streams of data.
- **[Validation](validation.md)** - A data type that may be either a success or an accumulation of one or more errors, allowing modeling multiple failures for applications such as data validation.
- **[ZSet](zset.md)** - A generalization of a set that generalizes measures of "how many" of an element exist in a set, supporting multi-sets, "fuzzy" sets, and other data structures.
- **[ZValidation](zvalidation.md)** - A generalization of `Validation` that allows maintaining a log of warnings in addition to accumulating errors.
