# Other Supported Monads

> As of RC5, zio-direct now supports ZStream and ZPure as well as `scala.concurrent.Future` and `scala.List`. The latter two are largely fully functional but largely for demonstration purposes.
> Note that all of these are currently only supported in Scala 3.

As of RC5, zio-direct now supports ZStream and ZPure as well as `scala.concurrent.Future` and `scala.List`. The latter two are largely fully functional but largely for demonstration purposes.
> Note that all of these are currently only supported in Scala 3.

## ZStream

To use zio-direct with ZStream, add the following to your `build.sbt` file.
```scala
libraryDependencies += "dev.zio" %% "zio-direct-streams" % "1.0.0-RC7"
```

You can then use zio-direct with ZStream:
```scala

val out =
  defer {
    val a = ZStream(1, 2, 3).each
    val b = ZStream("foo", "bar").each
    (a, b)
  }

out.runCollect
// ZIO.succeed(Chunk((1,"foo"),(1,"bar"),(2,"foo"),(2,"bar"),(3,"foo"),(3,"bar")))
```

Note that if you are also using zio-direct with ZIO, you should rename the `defer` function to avoid conflicts:

```scala

// The `run` function of ZStream is called `each`
val outStream: ZStream[Any, Nothing, (Int, String)] =
  deferStream {
    val a = ZStream(1, 2, 3).each
    val b = ZStream("foo", "bar").each
    (a, b)
  }

val outZio: ZIO[Any, Nothing, Chunk[(Int, String)]] =
  defer {
    val a: Chunk[(Int, String)] = outStream.runCollect.run
    val b = ZIO.succeed((123, "baz")).run
    a :+ b
  }

// Yields:
// ZIO.succeed(Chunk((1,"foo"),(1,"bar"),(2,"foo"),(2,"bar"),(3,"foo"),(3,"bar"),(123, "baz")))
```

## ZPure

> Note that Metals auto-complete/type-info popups may be sluggish when using ZPure, especially when try/catch constructs are being used.
> In some cases, you may need to wait for a "Loading..." popup message for 20-30 seconds although the actual (bloop) compile time
> will just be a few seconds.

To use zio-direct with ZPure, add the following to your `build.sbt` file.
```scala
libraryDependencies += "dev.zio" %% "zio-direct-pure" % "1.0.0-RC7"
```

In order to use zio-direct with ZPure, you first need to define a `deferWith[W, S]` context which will define the Logging (`W`) and State (`S`) types for your ZPure computation.

> Due to limitations of Scala 3, you may need to create the state object type in a separate file (or you may get cyclical-dependency compile-time errors).
```scala
val dc = deferWith[String, MyState]

// The `run` function of ZStream is called `eval`
val out =
  defer {
    val s1 = ZPure.get[MyState].eval.value
    ZPure.set(MyState("bar")).eval
    val s2 = ZPure.get[MyState].eval.value
    (s1, s2)
  }

out.provideState(MyState("foo")).run
// ("foo", "bar")
```

In order to avoid having to specify the state-type over and over again, several helpers are provided (they are imported from `dc._`).
```scala
val dc = deferWith[String, MyState]

// The `run` function of ZStream is called `eval`
val out =
  defer {
    val s1 = getState().value
    setState(MyState("bar"))
    val s2 = getState().value
    (s1, s2)
  }

out.provideState(MyState("foo")).run
// ("foo", "bar")
```

## List and Future

Support for Scala's `List` and `Future` objects is provided from zio-direct.

To use zio-direct with `List` do the following:
```scala

val out =
  defer {
    val a = List(1, 2, 3)
    val b = List("foo", "bar")
    (a, b)
  }

// Yields:
// List((1,"foo"),(1,"bar"),(2,"foo"),(2,"bar"),(3,"foo"),(3,"bar"))
```

To use zio-direct with `Future` do the following:
```scala

val out =
  defer {
    Future("a").run match {
      case "a" => Future(1).run
      case "b" => Future(2).run
    }
  }

// Yields: Future(1)
```

Note that it is not necessary to implement ExecutionContext.Implicits.global. You can
implicitly pass in any ExecutionContext you want. It just needs to be in-scope when you
call the `defer` function (i.e. `zio.direct.future.defer`).
```scala

def out(implicit ctx: ExecutionContext) =
  defer {
    Future("a").run match {
      case "a" => Future(1).run
      case "b" => Future(2).run
    }
  }

out(scala.concurrent.ExecutionContext.global)
// Yields: Future(1)
```
