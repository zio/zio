# OpenTracing

> OpenTracing is a standard and API for distributed tracing, i.e. collecting timings,
and logs across process boundaries. Well known implementations are [Jaeger](https://www.jaegertracing.io) and [Zipkin](https://www.zipkin.io).

OpenTracing is a standard and API for distributed tracing, i.e. collecting timings,
and logs across process boundaries. Well known implementations are [Jaeger](https://www.jaegertracing.io) and [Zipkin](https://www.zipkin.io).

## Installation

First, add the following dependency to your build.sbt:

```
"dev.zio" %% "zio-opentracing" % "<version>"
```

## Usage

To use ZIO Telemetry, you will need an `OpenTracing` service in your
environment. You also need to provide a `tracer` (for this example we use `JaegerTracer.live` from `opentracing-example` module) implementation:

```scala

val app =
  ZIO.serviceWithZIO[OpenTracing] { tracing =>

    (for {
      _       <- tracing.tag(Tags.SPAN_KIND.getKey, Tags.SPAN_KIND_CLIENT)
      _       <- tracing.tag(Tags.HTTP_METHOD.getKey, "GET")
      _       <- tracing.setBaggageItem("proxy-baggage-item-key", "proxy-baggage-item-value")
      message <- Console.readline
      _       <- tracing.log("Message has been read")
    } yield message) @@ root("/app")
  }.provide(OpenTracing.live, JaegerTracer.live("my-app"))
```

After importing `import tracing.aspects._`, additional `ZIOAspect` combinators
on `ZIO`s are available to support starting child spans, tagging, logging and
managing baggage.

```scala
ZIO.serviceWithZIO[OpenTracing] { tracing =>

  // start a new root span and set some baggage item
  val zio1 = tracing.setBaggage("foo", "bar") @@ root("root span")

  // start a child of the current span, set a tag and log a message
  val zio2 = 
    (for {
      _ <- tracing.tag("http.status_code", 200)
      _ <- tracing.log("doing some serious work here!")
    } yield ()) @@ span("child span")
}
```

To propagate contexts across process boundaries, extraction and injection can be
used. The current span context is injected into a carrier, which is passed
through some side channel to the next process. There it is injected back and a
child span of it is started. For the example we use the standardized `TextMap`
carrier. For details about extraction and injection, please refer to 
[OpenTracing Documentation](https://opentracing.io/docs/overview/inject-extract/). 

Due to the use of the (mutable) OpenTracing carrier APIs, injection and extraction
are not referentially transparent.

```scala
ZIO.serviceWithZIO[OpenTracing] { tracing =>

  val buffer = new TextMapAdapter(mutable.Map.empty.asJava)
  for {
    _ <- tracing.inject(Format.Builtin.TEXT_MAP, buffer)
    _ <- ZIO.unit @@ spanFrom(Format.Builtin.TEXT_MAP, buffer, "child of remote span")
  } yield buffer
}
```
