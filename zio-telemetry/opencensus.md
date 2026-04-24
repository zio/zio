# OpenCensus

> OpenCensus is a stats collection and distributed tracing framework. Well known implementations are [Jaeger](https://www.jaegertracing.io)
and [Zipkin](https://www.zipkin.io).

OpenCensus is a stats collection and distributed tracing framework. Well known implementations are [Jaeger](https://www.jaegertracing.io)
and [Zipkin](https://www.zipkin.io).

## Installation

First, add the following dependency to your build.sbt:
```
"dev.zio" %% "zio-opencensus" % "<version>"
```

## Usage

To use ZIO Telemetry, you will need a `Tracing` service in your environment. You also need to provide a `tracer` implementation:

```scala

val tracerLayer = ZLayer.succeed(io.opencensus.trace.Tracing.getTracer)

val errorMapper = ErrorMapper[Throwable] { case _ => Status.UNKNOWN }

val app =
  ZIO.serviceWithZIO[Tracing] { tracing =>

    (for {
      _       <- tracing.putAttributes(Map("foo" -> "bar"))
      message <- Console.readline
    } yield message) @@ root("/app")
  }.provide(Tracing.live, tracerLayer)
```

After importing `import tracing.aspects._`, additional `ZIOAspect` combinators
on `ZIO`s are available to support starting child spans and adding attributes.

```scala
ZIO.serviceWithZIO[Tracing] { tracing =>

  // start a new root span and set some attributes
  val zio1 = ZIO.unit @@ root("root span", attributes = ("foo", "bar))
  // start a child of the current span
  val zio2 = ZIO.unit @@ span("child span", attributes = Map.empty)
}
```

To propagate contexts across process boundaries, extraction and injection can be
used. The current span context is injected into a carrier, which is passed
through some side channel to the next process. There it is injected back and a
child span of it is started.

Due to the use of the (mutable) OpenCensus carrier APIs, injection and extraction
are not referentially transparent.

```scala
ZIO.serviceWithZIO[Tracing] { tracing =>

  val textFormat                           = Tracing.getPropagationComponent().getB3Format()
  val carrier: mutable.Map[String, String] = mutable.Map().empty

  val getter: TextFormat.Getter[mutable.Map[String, String]] = new TextFormat.Getter[mutable.Map[String, String]] {
    override def keys(carrier: mutable.Map[String, String]): lang.Iterable[String] =
      carrier.keys.asJava

    override def get(carrier: mutable.Map[String, String], key: String): String =
      carrier.get(key).orNull
  }

  val setter: TextFormat.Setter[mutable.Map[String, String]] = new TextFormat.Setter[mutable.Map[String, String]] {
    override def put(carrier: mutable.Map[String, String], key: String, value: String): Unit =
      carrier.update(key, value)
  }
  
  val zio1 = tracing.inject(textFormat, carrier, setter) @@ 
    root("root span", attributes = Map.empty)
  
  val zio2 = ZIO.unit @@ 
    span("child span", attributes = Map(("foo", "bar"))) @@ 
    fromRootSpan(textFormat, carrier, getter, "foo", attributes = Map.empty)
}
```
