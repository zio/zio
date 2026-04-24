# Debug

> `Debug[A]` describes the ability to render a value of type `A` to a human readable format for debugging purposes.

`Debug[A]` describes the ability to render a value of type `A` to a human readable format for debugging purposes.

Its signature is:

```scala
trait Debug[-A] {
  def debug(a: A): Debug.Repr
}

object Debug {

  sealed trait Renderer

  sealed trait Repr {
    def render: String
    def render(renderer: Renderer): String
  }
}
```

`Repr` here is a data structure that captures the information needed to render a data type in a structured format so that it can be displayed in different ways. A `Renderer` is a data type that knows how to render a `Repr` to a particular string representation, for example using short names for readability or fully qualified names for using the rendered output as valid code.

If we import `zio.prelude._` then we can use the `debug` operator on any data type with a `Debug` instance defined for it. We can also just use the `render` operator on any data type with a `Debug` instance defined for it to render it to a simple string representation.

The `Debug` abstraction is the functional equivalent of the `toString` operator but it has several advantages over just using `toString`.

First, as with other functional abstractions we can define when it makes sense to render a data type at all.

We can call `toString` on anything including classes whose string representation only points to their memory address. Fortunately this is more often the cause of annoyance than real bugs but it is certainly nice to know when there isn't much point in trying to render something.

Related to this, we can define our own way of rendering for data types that are not under our control. For example, if we are debugging code involving arrays we can frequently be frustrated when the string representation of the array consists of its memory location rather than the elements actually in the array.

With ZIO Prelude, we can easily define a `Debug` instance for `Array`. We can even handle nested arrays.

The final advantage that the `Debug` abstraction has over the Scala standard library and older functional programming libraries is that it captures rendering information in a structured data format instead of just a `String`.

When we render a data type we may want to do it in various ways. We may want to do it using short names for a readable description in the context of a discussion like this one, rendering it as something like `Validation[String, Int]`.

In another context like being able to copy and paste our rendering into a REPL, IDE, or worksheet and having it compile we may want to use fully qualified names, such as `zio.prelude.Validation[scala.String, scala.Int]`. Or we may decide that including the fully qualified name for standard library types is a little too much, and want to render this as `zio.prelude.Validation[String, Int]`.

All of these are valid choices, but just rendering values as a `String` forces us to choose one. A `String` does not have enough structure to preserve all the information we need for rendering different ways so we are forced to choose one arbitrarily.

This also allows us to define meaningful laws for the `Debug` abstraction. In other functional programming libraries interfaces that provide similar functionality are essentially lawless since there is nothing describing what this string representation should look like.

In contrast, in ZIO Prelude `Debug` follows a well defined law that the Scala rendering of any data type should itself be valid Scala code.

## Structured Rendering

The `Repr` data type preserves all the information we need to render a data type to a `String` in various ways. There is nothing magic about this data type, it is just an algebraic data type with a variety of cases representing the different possible pieces of information we could need for rendering.

If you just want to render data types from ZIO or the Scala standard library that have meaningful string representations then you can just use the `debug` operator but if you want to define `Debug` instances for your own data type it is helpful to understand usage of `Repr`.

Most of the cases of `Repr` are "simple" cases that just describe existing primitive data types.

```scala
sealed trait Repr

object Repr {
  case class Int(value: scala.Int)           extends Repr
  case class Double(value: scala.Double)     extends Repr
  case class Float(value: scala.Long)        extends Repr
  case class Long(value: scala.Long)         extends Repr
  case class Byte(value: scala.Byte)         extends Repr
  case class Char(value: scala.Char)         extends Repr
  case class Boolean(value: scala.Boolean)   extends Repr
  case class Short(value: scala.Short)       extends Repr
  case class String(value: java.lang.String) extends Repr
}
```

As you can see these cases are indeed quite straightforward other than the minor complexity of avoiding name collisions with the underlying data types.

With these representations we can describe the structure of primitive values in a way that supports structured rendering. For example, the `Repr` of `42` would be `Repr.Int(42)` and we could match on it to render it in various ways such as `42` or `Int: 42`.

The next case, `Object`, lets us define our own primitive types.

```scala
object Repr {
  case class Object(namespace: List[java.lang.String], name: java.lang.String) extends Repr
}
```

As its name implies, this lets us define representations for data types like objects that aren't existing primitive types but also do not depend on other data types for their rendering. The information needed to render an object is just the name of the object and its namespace.

For example, the `Repr` of `None` would be `Repr.Object(List("scala"), "None")`. Note how the namespace gives us the information we need to render the `Repr` as valid Scala code and gives us the ability to decide at the time of rendering whether we want to include some or all of this or just the name itself.

The final cases of `Repr` let us build more complex data types such as sum types, product types, and collections from simpler ones.

```scala

object Repr {
  case class VConstructor(
    namespace: List[java.lang.String],
    name: java.lang.String,
    reprs: List[Repr]
  ) extends Repr
  case class Constructor(
    namespace: List[java.lang.String],
    name: java.lang.String,
    reprs: ListMap[java.lang.String, Repr]
  ) extends Repr
  case class KeyValue(
    key: Repr,
    value: Repr
  ) extends Repr
}
```

The `VConstructor` case mirrors the constructor arguments of a class or case class. Just like the `Object` constructor it contains a namespace and name but now it also has a list of `Repr` values describing how each of the constructor parameters can be rendered.

For example, the `Repr` of `Some(42)` would be `Repr.VConstructor(List("scala"), "Some", List(Repr.Int(42)))`. Again, this gives us everything we need to render the value in different ways.

The `Constructor` case is like `VConstructor` but mirrors a data type like a case class with named fields. The list of constructor arguments now included the name of each constructor argument along with its representation.

For example, the `Repr` of `Person("John", 42)` would be `Repr.Constructor(List.empty, "Person", Map("name" -> Repr.String("John"), "age" -> Repr.Int(42)))`. The names of the constructor arguments may or may not be shown depending on the renderer used.

The final case is `KeyValue`, which covers situations where the constructor arguments are key value pairs, like in a `Map`. We capture this separately so we can render them appropriately, for example using `->` to ensure that we can render the key value pairs as valid code.

Most of the time you won't have to use most of these constructors yourself but it is helpful to know what they are since you will often have to use one or two in defining your own `Debug` instances, typically the `Object`, `VConstructor`, and `Constructor` cases.

## Defining Debug Instances

ZIO Prelude comes with `Debug` instances defined for all data types with meaningful string representations in ZIO and the Scala standard library, as well as data types composed of those types.

To define a `Debug` instance for your own data type you can use the `make` operator which requires you to specify how to create a representation of your own data type. Typically you can do this just by using one of the `Repr` constructors such as `Constructor`, `Object`, or `VConstructor` and using the `debug` operator on any values that are inside your data type.

For example, here is how we could define a `Debug` instance for a `Person` data type:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val PersonDebug: Debug[Person] =
    Debug.make { case Person(name, age) =>
      Debug.Repr.Constructor(
        List.empty,
        "Person",
        "name" -> name.debug,
        "age"  -> age.debug
      )
    }
}
```

Notice how we used the `debug` operator on `name` and `age`. In this simple case we could have used `Repr.String(name)` and `Repr.Int(age)` but this would not have worked if the constructor values were more complex and would have forced us to spend more time working with `Repr` directly.

We can use the same strategy to define `Debug` instances for polymorphic data types. We just need to add a constraint that there is a `Debug` instance for the different types that we want to render.

For example, here is how we could define a `Debug` instance for a simplified version of the `Validation` data type from ZIO Prelude.

```scala

sealed trait Validation[+E, +A]

object Validation {

  case class Success[+A](a: A) extends Validation[Nothing, A]
  case class Failure[+E](es: NonEmptyChunk[E]) extends Validation[E, Nothing]

  implicit def ValidationDebug[E: Debug, A: Debug]: Debug[Validation[E, A]] =
    Debug.make {
      case Success(a)  => Debug.Repr.VConstructor(List("zio", "prelude"), "Validation.Success", List(a.debug))
      case Failure(es) => Debug.Repr.VConstructor(List("zio", "prelude"), "Validation.Failure", List(es.debug))
    }
}
```

Here we used context bounds of `Debug` for the `E` and `A` type parameters to require that `Debug` instances for them exist. This makes sense because there is no way we can create a meaningful string representation of a value if we can't create meaningful string representations of its components.

## Rendering

The `Repr` data type returned by the `debug` operator is just a representation of the data type in a way that supports meaningful string rendering. To actually get a string we still need to render it with its `render` operator.

The easiest way to do that is to call the `render` operator directly, which uses a default renderer that renders data in a simple way that is being read by humans but is not necessarily valid Scala code. If we want to, we can call `render` with an argument and provide a `Renderer` to render the representation in a different way.

ZIO Prelude comes with two other renderers.

The `Scala` renderer renders data as valid Scala code that you can copy and paste into an IDE, REPL, or worksheet. The `Full` renderer renders data with as much detail as possible, including information like field names where available, and can be helpful for debugging purposes.

A `Renderer` is just a function that takes a `Repr` and returns a `String`, so you can also define your own `Renderer` if you want to. ZIO Prelude's `Debug` abstraction preserves all the information so you can render your data types the way you want to.
