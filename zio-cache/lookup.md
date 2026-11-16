# Lookup

> A `Lookup` is a lookup function that, given a key of type `Key`, knows how to compute a value of type `Value`, requiring an environment of type `Environment` and potentially failing with an error of type `Error`.

`Lookup` is a case class that wraps a function from a key to a `ZIO` effect. Its type parameters are `Key`, `Environment`, `Error`, and `Value`. Because `Lookup` also extends the function type `Key => ZIO[Environment, Error, Value]`, calling `Lookup#apply` with a key runs the wrapped function on it.

You can think of a key as essentially an effectual function to compute a value. And in fact you can construct a `Lookup` from any effectual function using the `Lookup.apply` method on the `Lookup` companion object:

```scala
import zio._
import zio.cache.Lookup

def timeConsumingEffect(key: String): UIO[Int] =
  ZIO.sleep(5.seconds).as(key.hashCode)

val lookup: Lookup[String, Any, Nothing, Int] = Lookup(timeConsumingEffect)
```

Since the lookup function can return a `ZIO` effect it can either return its result synchronously or asynchronously. It can also use an environment and potentially fail with an error.

In short, if you can describe it with a ZIO effect you can use it as the lookup function for a cache.
