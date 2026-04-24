# Parallel Operators

> Like `ZStream`, two `ZSink` can be zipped together. Both of them will be run in parallel, and their results will be combined in a tuple:

## Parallel Zipping

Like `ZStream`, two `ZSink` can be zipped together. Both of them will be run in parallel, and their results will be combined in a tuple:

```scala

val kafkaSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.attempt(???))

val pulsarSink: ZSink[Any, Throwable, Record, Record, Unit] =
  ZSink.foreach[Any, Throwable, Record](record => ZIO.attempt(???))

val stream: ZSink[Any, Throwable, Record, Record, Unit] =
  kafkaSink zipPar pulsarSink 
```

## Racing

We are able to `race` multiple sinks, they will run in parallel, and the one that wins will provide the result of our program:

```scala
val stream: ZSink[Any, Throwable, Record, Record, Unit] =
  kafkaSink race pulsarSink 
```

To determine which one succeeded, we should use the `ZSink#raceBoth` combinator, it returns an `Either` result.
