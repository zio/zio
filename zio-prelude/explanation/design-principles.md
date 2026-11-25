# Design Principles

> ZIO Prelude does not copy the type class hierarchy of Haskell, Scalaz or Cats. It starts from the algebraic properties that operators can have, and builds small abstractions around each one.

## Properties Instead of a Hierarchy

The classic hierarchy bundles several ideas into one name. A `Monoid` is an associative operation plus an identity element. A `Monad` is a mapping operation, a flattening operation and an identity, all at once.

ZIO Prelude names each property on its own, and a type that has several properties just mixes in several traits:

- [Associative](../functional-abstractions/concrete-types/associative.md) - the order of grouping does not matter.
- [Commutative](../functional-abstractions/concrete-types/commutative.md) - the order of the two operands does not matter.
- [Identity](../functional-abstractions/concrete-types/identity.md) - there is a value that changes nothing when combined.

The same properties exist for type constructors, for example [AssociativeBoth](../functional-abstractions/parameterized-types/associativeboth.md) and [IdentityBoth](../functional-abstractions/parameterized-types/identityboth.md). The [abstraction diagrams](../functional-abstractions/abstraction-diagrams.md) show how they fit together.

## Orthogonality

Each abstraction does one thing, and no two abstractions overlap. That keeps each one small enough to teach in a page, and it lets you ask for exactly the capability you need. A function that only combines values asks for `Associative`, not for a `Monoid` it does not use.

## Laws

Every abstraction comes with laws that its instances must obey, such as `combine(a, combine(b, c)) == combine(combine(a, b), c)` for `Associative`. The laws are written down in the [laws module](../laws/index.md) as properties you can run against your own instances with ZIO Test.

## Pragmatism

Some useful data types break a law in a corner case. ZIO Prelude still provides an instance when the type is useful and the deviation is small, and the documentation of the abstraction says which laws to expect.

## Scala First

Where Haskell has no subtyping, Scala does, and ZIO Prelude uses it. Combined abstractions are ordinary traits, so a type that is both `Covariant` and `IdentityBoth` is automatically what you need for the combinations built on both. See [Syntax and Instances](../syntax-and-instances.md) for how instances and syntax are found.

## Data Types for Domain Modeling

Besides abstractions, the library provides data types that make illegal states hard to express: [NonEmptyList](../functional-data-types/nonemptylist.md), [Validation](../functional-data-types/validation.md), and [newtypes](../newtypes/index.md) with no runtime overhead.
