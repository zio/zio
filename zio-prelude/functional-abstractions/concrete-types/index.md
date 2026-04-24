# Introduction

> ZIO Prelude features a set of fundamental functional abstractions for working with concrete data types.

ZIO Prelude features a set of fundamental functional abstractions for working with concrete data types.

These abstractions fall into two categories.

## Properties Of Concrete Types

The first set of abstractions describe different properties that values of a data type may have. For example, we may be able to compare them for equality, hash them, or order them.

All of these properties can be thought of as functions `A => ???` or `(A, A) => ???`. They give us a way of taking one or more values of a data type and converting them into some other data type, whether that is a `Boolean` indicating whether two values are equal or an `Int` that is the result of hashing a single value.

These properties are often already described in the Scala standard library in some way, but the abstractions in ZIO Prelude give us the tools to define them in ways that are more type safe, extensible, and compositional.

For example, the `Equal` type class prevents us from doing equality checks on unrelated types or types that do not have a meaningful definition of equality, which can be a source of bugs when using the `==` operator from the Scala standard library. We can also define these properties for data types that we do not control, for example providing a custom way of hashing a data type implemented by someone else.

### Equal

The `Equal` abstraction describes a type that can be compared for equality. Its fundamental representation is as a function `(A, A) => Boolean`.

The `Equal` abstraction allows us to define what it means for two instances of a type to be equal, avoiding pitfalls in Scala such as reference equality for arrays. It also lets us avoid bugs caused by accidentally comparing two unrelated types or checking equality for types such as functions that do not have well-defined notions of equality.

### Ord

The `Ord` abstraction builds on the `Equal` abstraction and describes a type that has a total ordering. Its fundamental representation is as a function `(A, A) => Ordering`, where `Ordering` can either be `LessThan`, `EqualTo` or `GreaterThan`.

The `Ord` abstraction serves much the same purpose as the `Ordering` type class from the Scala standard library, but it has improved type inference due to its use of variance. It also integrates with the other functional abstractions in ZIO Prelude, so for example defining an ordering for a type also defines a way of comparing instances of that type for equality that is by definition consistent with the ordering.

### Hash

The `Hash` abstraction builds on the `Equal` abstraction in a different way, describing a type that can be hashed. Its fundamental representation is as a function `A => Int`.

The `Hash` abstraction allows us to define our own way of hashing data types instead of being forced to use the implementation of `hashCode` for existing data types. It also ensures that the definition of the hash is consistent with the definition of equality, another common source of bugs.

### Debug

The `Debug` abstraction describes a type that can be rendered for debugging purposes. Its fundamental representation is as a function `A => Repr`, where `A` is a structured representation of the value that supports rendering in various formats.

The `Debug` abstraction allows us to define how a data type should be rendered, for example rendering an `Array` to display its values rather than just its memory location. The more structured representation also allows rendering in different formats, for example including fully qualified names in the rendering of the data type so the rendering is itself valid Scala code.

## Combining Concrete Types

The second set of abstractions describe different ways of combining two values of a given type. These can all be thought of as functions `(A, A) => A`.

Combining is quite fundamental because it is how we build more complex structures from simpler ones.

Generally unary operators are not very interesting because they don't add additional structure, they just modify existing structure.

In contrast, given the ability to combine two values of a given type we can create a new value of that type with richer structure. And if we can do that once we can do it again and again after that, creating values with arbitrarily complex structure.

The abstractions in ZIO Prelude for describing combining values are each based on fundamental algebraic properties that the combining operation can have.

### Associative

The most basic such abstraction is `Associative`, which describes a combining operation that is associative.

```scala
(a <> b) <> c === a <> (b <> c)
```

This essentially means that if we are combining three values then the order of operations doesn't matter. We can combine `a` and `b` and then combine the result with `c` or we can combine `b` and `c` and then combine `a` with the result.

This is quite an important property for being able to build more complex structures from simpler ones. Otherwise the result of sequentially building more complex structures from simpler ones is not well defined because it depends on the order of operations and there are an exponentially increasing number of ways we could order these operations as we combine more and more values.

There are also an extremely large number of data types that can be combined using associative operations.

For example integers can be combined associatively using addition and multiplication, among other operations. String concatenation is also associative, as is concatenation of lists of any concrete type.

For these reasons the `Associative` abstraction forms the root of the set of abstractions for describing ways to combine concrete types in ZIO Prelude.

### Commutative

The `Commutative` abstraction builds on the `Associative` abstraction by describing a combining operation that is not only associative but also commutative.

```scala
a <> b === b <> a
```

This means that not only does the order of operations not matter, but the order in which we combine the values also does not matter.

Commutativity is a powerful property that gives us additional ability to reason about our code. For instance, if the values we are combining are in an arbitrary order, say because they are being produced asynchronously by multiple concurrent processes, then commutativity guarantees that we will get the same result no matter what order we combine them in.

Commutativity is a stronger property than associativity but that also means there are fewer ways of combining values that are commutative than there are ways of combining that are associative. For example, integer addition and multiplication are commutative but string concatenation is not.

### Identity

The `Identity` abstraction builds on the `Associative` abstraction in a different way. It describes a combining operation that is not only associative but also has an identity element.

```scala
a <> identity === a
identity <> a === a
```

This identity element is a "neutral value" that can be combined with any other value an unlimited number of times with the combining function and does not change the other value.

For example `0` is an identity element with respect to addition, `1` is an identity element with respect to multiplication, the empty string is an identity element with respect to string concatenation, and the empty list is an identity element with respect to list concatenation.

An identity element gives us a "starting point" for combining values that we know will never change the value. It also tells us when we can safely eliminate a value from the combining operation because we know the value will never change the result.

### Inverse

The `Inverse` abstraction further builds on the `Identity` abstraction with the concept of an inverse to the combining operation.

```scala
inverse(a, a) === identity
```

Whereas the combining operation adds structure the inverse operation takes it away, so that combining any value with itself using the inverse operation removes all structure and just returns the identity element.

Defining the inverse as a binary operator rather than a unary operator like this allows us to define an inverse operation even for types that do not have inverse values. 

For example subtraction would be an inverse operation with addition being the combining operation and zero being the identity element. And we can define it on the natural numbers as well as the integers.
