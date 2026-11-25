# ZIO Constraintless Reference

> Reference for TypeList, IsElementOf, AreElementsOf and Instances, the four types of ZIO Constraintless.

ZIO Constraintless lets a data type record which types it may contain, and lets each interpreter supply its own type class instances for those types. It consists of four types in the package `zio.constraintless`: `TypeList`, `IsElementOf`, `AreElementsOf` and `Instances`. Their declarations are:

```scala
sealed trait TypeList
sealed trait IsElementOf[Type, Types <: TypeList]
sealed trait AreElementsOf[T1 <: TypeList, T2 <: TypeList]
trait Instances[TypeClass[_], As <: TypeList]
```

## How They Work Together

A program is described with a [`TypeList`](#typelist) as a type parameter. Each node holds [`IsElementOf`](#iselementof) evidence for its type, and each interpreter requires [`Instances`](#instances) of the type class it needs. The following steps show the typical flow:

1. Define the allowed types as a `TypeList`, for example `Int :: Boolean :: End`.
2. Define a data type with a `TypeList` parameter, whose nodes hold `A IsElementOf As` evidence.
3. Write an interpreter that requires `Instances[TypeClass, As]` and calls `Instances#withInstance` with the node's evidence.
4. Provide a type class instance for each allowed type where the interpreter is called.

The relationships between the four types look like this:

```
TypeList ─────────────── lists the allowed types ──────────────┐
   │                                                           │
   ├── IsElementOf[A, As] ── proves one type is in the list    │
   │                                                           │
   ├── AreElementsOf[As1, As2] ── proves a whole list fits     │
   │                              in another list              │
   │                                                           │
   └── Instances[C, As] ── holds C[A] for every A in As ◀──────┘
         withInstance(use)(IsElementOf[B, As]) ── picks C[B]
```

All four types are in the package `zio.constraintless`, and the list constructors are in the object `TypeList`:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._
```

## TypeList

`TypeList` is a type-level list. It has no values: it exists so that the compiler can track a set of types. Its declaration is:

```scala
sealed trait TypeList

object TypeList {
  sealed trait ::[H, T <: TypeList] extends TypeList
  sealed trait End extends TypeList
}
```

`End` is the empty list, and `H :: T` is a list with the head type `H` and the tail `T`. Because `::` is a type with two parameters, it can be written infix and it associates to the right. To write a list of `Int`, `Boolean` and `String`:

```scala
type Types = Int :: Boolean :: String :: End
```

This is the same type as `::[Int, ::[Boolean, ::[String, End]]]`. Both types are sealed, so no other kind of list exists.

## IsElementOf

`IsElementOf[Type, Types]` is evidence that `Type` is an element of the list `Types`. It is sealed and has two cases:

```scala
sealed trait IsElementOf[Type, Types <: TypeList]

object IsElementOf {
  def apply[A, As <: TypeList](implicit ev: IsElementOf[A, As]): IsElementOf[A, As]

  final case class Head[A, As <: TypeList]() extends IsElementOf[A, A :: As]
  final case class Tail[A, B, As <: TypeList](ev: IsElementOf[A, As]) extends IsElementOf[A, B :: As]

  implicit def IsElementOfHead[A, As <: TypeList]: IsElementOf[A, A :: As]
  implicit def IsElementOfTail[A, B, As <: TypeList](implicit ev: IsElementOf[A, As]): IsElementOf[A, B :: As]
}
```

`Head` says that the type is the first element of the list, and `Tail(ev)` says that it is in the tail, as proven by `ev`. The two implicit methods build these values, so the compiler derives the evidence for any type that is actually in the list. `IsElementOf.apply` summons it:

```scala
IsElementOf[Boolean, Types]
// res0: IsElementOf[Boolean, Types] = Tail(ev = Head())
```

The evidence for the first element is a `Head`, and the evidence for a later element is a chain of `Tail` values that ends in a `Head`. For a type that is not in the list, no evidence exists and the code does not compile:

```scala
IsElementOf[Double, Types]
// error: could not find implicit value for parameter ev: zio.constraintless.IsElementOf[Double,repl.MdocSession.MdocApp.Types]
```

Data types usually store the evidence in a node or take it as an implicit parameter, as in `Value(a: A, ev: A IsElementOf As)`. `IsElementOf` is invariant, so the type must appear in the list exactly, not as a subtype.

## AreElementsOf

`AreElementsOf[T1, T2]` is evidence that every type in the list `T1` is an element of the list `T2`. It is sealed and is built from `IsElementOf` evidence:

```scala
sealed trait AreElementsOf[T1 <: TypeList, T2 <: TypeList]

object AreElementsOf {
  def apply[T1 <: TypeList, T2 <: TypeList](implicit ev: AreElementsOf[T1, T2]): AreElementsOf[T1, T2]

  final case class TypeCollection[T1, T11 <: TypeList, T2 <: TypeList](
    ev1: T1 IsElementOf T2,
    ev2: T11 AreElementsOf T2
  ) extends AreElementsOf[T1 :: T11, T2]

  final case class NilCollection[T2 <: TypeList]() extends AreElementsOf[End, T2]

  implicit def typesElementsOfTypes[Head, Tail <: TypeList, Types <: TypeList](implicit
    ev1: Head IsElementOf Types,
    ev2: Tail AreElementsOf Types
  ): AreElementsOf[Head :: Tail, Types]

  implicit def endElementOfTypes[Types <: TypeList]: AreElementsOf[End, Types]
}
```

`TypeCollection` holds the `IsElementOf` evidence for the head of the first list and the `AreElementsOf` evidence for its tail. `NilCollection` is the base case: the empty list fits in any list. To summon the evidence that a smaller list fits in `Types`:

```scala
AreElementsOf[Int :: String :: End, Types]
// res2: AreElementsOf[Int :: String :: End, Types] = TypeCollection(
//   ev1 = Head(),
//   ev2 = TypeCollection(
//     ev1 = Tail(ev = Tail(ev = Head())),
//     ev2 = NilCollection()
//   )
// )
```

The order of the types does not matter, and the first list may be shorter than the second. If any type of the first list is missing from the second, no evidence exists:

```scala
AreElementsOf[Int :: Double :: End, Types]
// error: could not find implicit value for parameter ev: zio.constraintless.AreElementsOf[Int :: Double :: zio.constraintless.TypeList.End,repl.MdocSession.MdocApp.Types]
```

## Instances

`Instances[TypeClass, As]` holds a `TypeClass` instance for every type in the list `As`. Its only method selects the instance of one type, given evidence that the type is in the list:

```scala
trait Instances[TypeClass[_], As <: TypeList] {
  def withInstance[B, D](use: TypeClass[B] => D)(implicit ev: B IsElementOf As): D
}

object Instances {
  implicit def instancesCons[C[_], A, As <: TypeList](implicit
    c: C[A],
    ev: Instances[C, As]
  ): Instances[C, A :: As]

  implicit def instancesEnd[C[_]]: Instances[C, End]
}
```

The two implicit methods build an `Instances` value from the list: `Instances.instancesCons` needs the instance for the head `C[A]` and the `Instances` of the tail, and `Instances.instancesEnd` is the base case for `End`. The compiler therefore derives `Instances[C, As]` only if a `C` instance exists for every type in `As`. A missing instance makes the whole `Instances` value unavailable, and the error is reported where the interpreter is called.

`Instances#withInstance` takes a function from the instance to a result, and the evidence `ev` selects which instance is passed to it. The implementation matches on the evidence: for `Head` it uses the head instance, and for `Tail(x)` it continues with the tail. To use it, we define a type class and select an instance for a value of an allowed type:

```scala
trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val boolean: Show[Boolean] = (a: Boolean) => if (a) "yes" else "no"
  implicit val string: Show[String] = (a: String) => "\"" + a + "\""
}

def render[A](a: A)(implicit
  ev: A IsElementOf Types,
  instances: Instances[Show, Types]
): String =
  instances.withInstance((s: Show[A]) => s.show(a))
```

Calling `render` with a value of any type in `Types` finds its instance:

```scala
render(true)
// res4: String = "yes"

render("zio")
// res5: String = "\"zio\""
```

:::note
`Instances#withInstance` contains one `asInstanceOf`, inside the library, to treat the head instance as a `TypeClass[B]` once the evidence says `B` is the head. `instancesEnd` is the instance for the empty list, and its `Instances#withInstance` throws the error "unreachable": no `IsElementOf` evidence can exist for an element of `End`, so the method cannot be called with valid evidence.
:::

## Platforms and Scala Versions

The types are published for the JVM, Scala.js and Scala Native, and for Scala 2.12, 2.13 and 3. For the dependency lines, see [Installation](./index.md#installation). For the differences between the Scala versions in practice, see [How to Use Constraintless on Scala 2 and Scala 3](./guides/use-on-scala-2-and-3.md).
