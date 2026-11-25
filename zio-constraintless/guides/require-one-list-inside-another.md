# How to Require One Type List to Fit Inside Another

> Use AreElementsOf to accept only expressions whose allowed types are a subset of a larger type list.

In this guide, we write a function that accepts a value of a small list's type and runs it with instances of a larger list. By the end, you will have a function whose signature uses [`AreElementsOf`](../reference.md#areelementsof) to say that every type of the first list must be in the second.

We'll define the two lists, state the requirement in a signature, and see the compiler reject a list that does not fit.

## The Problem

Two parts of a program can work with different type lists. A library may build expressions over `Int :: End`, while the application that runs them allows `Int :: String :: Boolean :: End`. Passing the library's values to the application's code must be safe only if every type the library may use is also allowed by the application. Without a way to state this, the check is left to comments or to casts.

## Prerequisites

We use the dependency from [Installation](../index.md#installation) and the following setup. The setup defines a small and a large list, and a type class with an instance for each type:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._

type Small = Int :: End
type Large = Int :: String :: Boolean :: End

trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val string: Show[String] = (a: String) => a
  implicit val boolean: Show[Boolean] = (a: Boolean) => a.toString
}
```

## State the Requirement

The function takes the evidence `AreElementsOf[H :: T, Large]` as an implicit parameter: the list that starts with `H` must fit in `Large`. The function also asks for the `Show` instances of the large list:

```scala
def prettyFirst[H, T <: TypeList, Large <: TypeList](h: H)(implicit
    fits: AreElementsOf[H :: T, Large],
    show: Instances[Show, Large]
): String =
  fits match {
    case AreElementsOf.TypeCollection(isElement, _) =>
      show.withInstance((s: Show[H]) => s.show(h))(isElement)
  }
```

The signature is the useful part: callers can only supply a `Large` that contains every type of `H :: T`. The first case of the evidence, `TypeCollection`, holds the [`IsElementOf`](../reference.md#iselementof) evidence for `H`, which is what `Instances#withInstance` needs. To call the function with the small list `Int :: End` and the large list:

```scala
prettyFirst[Int, End, Large](7)
// res1: String = "7"
```

## Summon the Evidence on Its Own

The compiler builds the evidence from the two lists, and `AreElementsOf.apply` summons it. A smaller list fits in a larger one, and the order of the types does not matter:

```scala
AreElementsOf[Small, Large]
// res2: AreElementsOf[Small, Large] = TypeCollection(
//   ev1 = Head(),
//   ev2 = NilCollection()
// )

AreElementsOf[Boolean :: Int :: End, Large]
// res3: AreElementsOf[Boolean :: Int :: End, Large] = TypeCollection(
//   ev1 = Tail(ev = Tail(ev = Head())),
//   ev2 = TypeCollection(ev1 = Head(), ev2 = NilCollection())
// )
```

## Reject a List That Does Not Fit

If the larger list lacks one of the smaller list's types, no `AreElementsOf` evidence exists, and the call does not compile. Here the list `Other` has no `Int`:

```scala
type Other = String :: Boolean :: End

AreElementsOf[Small, Other]
// error: could not find implicit value for parameter ev: zio.constraintless.AreElementsOf[MdocApp0.this.Small,MdocApp0.this.Other]
```

:::note
`AreElementsOf` is evidence only. The library has no operation that converts an expression from one list to another, so as shown above, the conversion is code you write using the evidence.
:::

## Going Further

For the members of `AreElementsOf`, see the [Reference](../reference.md#areelementsof). To add a type to a list rather than relate two lists, see [How to Allow a New Type in a Type List](./allow-a-new-type.md).
