# Divariant

> `Divariant` describes a binary type constructor, such as a function arrow, where the first parameter is contravariant and the second is covariant. The first parameter flips direction, so changing it requires a function in the opposite direction.

The standard example is a function `A => B`. We change its input by converting the new input type back to the old one, and we change its output by converting the old output to the new one. The combined operation, `dimap`, is the function-arrow version of `Bicovariant`'s `bimap`, except that the input side runs backwards.

`Divariant` extends `RightCovariant`, so the output side can be mapped on its own with `rightMap`. The type class can also produce a [Contravariant](./contravariant.md) for the first parameter, with the second fixed, through `deriveContravariant`.

## Motivation

A component that consumes one type and produces another, such as a parser, a handler, a serializer or a pricing rule, often has to be reused with different surrounding types. A handler written for `Int` input and `Int` output can be reused as a handler from text to text if we can adapt both ends. `Contravariant` adapts only the input and `Covariant` adapts only the output, and `Divariant` adapts both in one step for any arrow type that supports it. This lets a helper that adapts components be written once for every such arrow.

Use `Divariant` when the first parameter is an input and the second an output. If both parameters are outputs, as in `Either`, use [Bicovariant](./bicovariant.md). If the arrow also carries an error and an environment, see [Zivariant](./zivariant.md).

## Changing Input and Output

`dimap` takes a function that converts the new input into the old input, and a function that converts the old output into the new output. It returns a function from the new input type to the new output type.

To build a function from `String` to `String` out of an `Int` to `Int` function, we convert the string to an `Int` on the way in and the `Int` back to a string on the way out:

```scala
import zio.prelude._

val doubleLength = implicitly[Divariant[Function1]].dimap(
  (s: String) => s.length,
  (i: Int) => i.toString
)

val transform: Int => Int = (i: Int) => i * 2
```

Next, we apply the transformed function to a string:

```scala
doubleLength(transform)("hello")
// res1: String = "10"
```

The string `"hello"` has five characters. It becomes `5`, which is doubled to `10`, and that becomes `"10"`.

## Changing Only One Side

`leftContramap` changes only the input side, and `rightMap` changes only the output side. Because the input is contravariant, `leftContramap` takes a function from the new input type to the old input type:

```scala
implicitly[Divariant[Function1]].leftContramap((s: String) => s.length).apply(transform)("abc")
// res2: Int = 6
```

## Writing Generic Code

The syntax `dimap`, `leftMap` and `rightMap` is available on any `A =>: B` that has a `Divariant`. In this syntax `leftMap` changes the input side and takes a function from the new input to the old one. The following helper adapts any arrow from `Int` to `Int` into one from `String` to `String`:

```scala
def fromText[=>:[-_, +_]: Divariant](rule: Int =>: Int): String =>: String =
  rule.dimap((text: String) => text.trim.toInt, (n: Int) => n.toString)
```

Next, we apply the helper to a pricing rule written for plain integers:

```scala
val addTax: Int => Int = price => price + price / 10
// addTax: Int => Int = <function1>

val addTaxToText: String => String = fromText[Function1](addTax)
// addTaxToText: String => String = zio.prelude.Divariant$$anon$2$$Lambda$18863/0x00007f348afc6718@1b72818f

addTaxToText(" 200 ")
// res3: String = "220"
```

## Laws

Every `Divariant` instance must satisfy these laws, each checked by a method on the type class:

| Law                   | Method                              | Statement                                                         |
| --------------------- | ----------------------------------- | ----------------------------------------------------------------- |
| Identity              | `Divariant#dimapIdentity`           | `dimap(identity, identity)` leaves a value unchanged              |
| Composition           | `Divariant#dimapCompose`            | `dimap` of composed functions equals composing two `dimap` calls  |
| Coherence             | `Divariant#dimapCoherence`          | `dimap(f, g)` equals `rightMap(g)` followed by `leftContramap(f)` |
| Left identity         | `Divariant#leftContramapidentity`   | `leftContramap(identity)` leaves a value unchanged                |
| Left composition      | `Divariant#leftContramapCompose`    | `leftContramap(f compose g)` equals `leftContramap(g)` after `leftContramap(f)` |
| Right identity        | `RightCovariant#rightMapIdentity`   | `rightMap(identity)` leaves a value unchanged                     |
| Right composition     | `RightCovariant#rightMapCompose`    | `rightMap(g compose f)` equals `rightMap(g)` after `rightMap(f)`  |

## Related Abstractions

[Contravariant](./contravariant.md) and [Covariant](./covariant.md) are the one-parameter abstractions that `Divariant` combines. [Bicovariant](./bicovariant.md) is the version where both parameters are covariant, and [AssociativeCompose](./associativecompose.md) chains arrows rather than adapting their ends. The only instance in the library is for `Function1`.
