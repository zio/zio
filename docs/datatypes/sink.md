---
id: datatypes_sink
title:  "Sink"
---

A `Sink[E, A0, A, B]` is used to consume elements produced by `stream`
with `E` error in case of failure, `A0` remainder, `A` input elements and
`B` result type. `Sink` is passed as `stream.run` argument:

```scala mdoc:silent
import zio._
import zio.stream._

val stream = Stream.fromIterable(1 to 1000)

val sink = Sink.await[Int]

stream.run(sink)
```

## Creating sinks

The `zio.stream` provides numerous kinds of sinks to use.

Await where sink anticipates for first produced element and returns it:

```scala mdoc:silent
Sink.await[Int]
```

Collecting all elements into `List[A]`:

```scala mdoc:silent
Sink.collectAll[Int]
```

Collecting the first element into an option (returns `None` for empty streams):

```scala mdoc:silent
Sink.identity[Int].optional
```

Collecting elements until the condition is not satisfied:

```scala mdoc:silent
Sink.collectAllWhile[Int](_ > 2)
```

Ignoring incoming values unless some element satisfies the condition:

```scala mdoc:silent
Sink.ignoreWhile[Int](_ > 2)
```

Ignoring all the input, used in implementation of `stream.runDrain`:

```scala mdoc:silent
Sink.drain
```

Sink that intentionally fails with given type:

```scala mdoc:silent
import sun.reflect.generics.reflectiveObjects.NotImplementedException

Sink.fail[Exception](new NotImplementedException)
```

Basic fold accumulation of received elements:

```scala mdoc:silent
Sink.foldLeft[Int, Int](0)(_ + _)
```

Fold where each fold step has to be described in sink `Step` API.
A `foldLeft` uses `Step.more` in its implementation:

```scala mdoc:silent
Sink.fold[Nothing, Int, Int](0)((acc, e) => ZSink.Step.more(acc + e))
```

Mapping over the received input elements:

```scala mdoc:silent
Sink.fromFunction[Int, Int](_ * 2).collectAll[Int, Int]
```

`pull1` fails with given type in case of empty stream, otherwise continues with provided sink:

```scala mdoc:silent
Sink
  .pull1[String, Int, Int, Int](IO.fail("Empty stream, no value to pull"))(init => Sink
  .fold(init)((acc, e) => ZSink.Step.more(acc + e)))
```

`read1` tries to read head element from stream,
fails if isn't present or doesn't satisfy given condition:

```scala mdoc:silent
Sink.read1[String, Int] {
  case Some(_) => "Stream is not empty but failed condition"
  case None => "Stream is empty"
}(_ > 3).collectAll
```

## Transforming sinks

Having created the sink, we can transform it with provided operations.
One of them already appeared in previous section - `collectAll` in `read1`.

Sink that after collecting input - filters it:

```scala mdoc:silent
Sink.collectAll[Int].filter[Int](_ > 100)
```

Running two sinks in parallel and returning the one that completed earlier:

```scala mdoc:silent
Sink.fold[Int, Int, Int](0)((acc, e) => ZSink.Step.more(acc + e))
  .race(Sink.identity[Int])
```

For transforming given input into some sink we can use `contramap` which
is `C => A` where `C` is input type and `A` is sink elements type:

```scala mdoc:silent
Sink.collectAll[String].contramap[Int](_.toString + "id")
```

A `dimap` is an extended `contramap` that additionally transforms sink's output:

```scala mdoc:silent
Sink.collectAll[String].dimap[Int, List[String]](_.toString + "id")(_.take(10))
```
