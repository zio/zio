# Concepts

> The ideas behind ZIO Schema: why schemas are values, what they are used for, and how derivation differs between Scala 2 and Scala 3.

The pages in this section explain the ideas behind ZIO Schema rather than how to call a particular API. Read them to understand why the library works the way it does; the Reference section lists the exact types and methods, starting from the Schema cases in [Basic Building Blocks](../basic-building-blocks.md).

- [The Motivation Behind ZIO Schema](../motivation.md) explains why ZIO Schema describes data types as ordinary values, and what that makes possible compared with type classes written by hand for every format.
- [ZIO Schema Use Cases](../use-cases.md) lists the tasks that a schema drives, such as codecs, validation, diffing, migration, and generators, with a link to the page for each.
- [Schema Derivation on Scala 2 and Scala 3](../derivations/derivation-scala-versions.md) compares how schemas are derived on each Scala version and which language features each one supports.
