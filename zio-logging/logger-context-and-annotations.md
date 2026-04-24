# Logger Context and Annotations

> The `logContext` fiber reference is used to store typed, structured log
annotations, which can be utilized by backends to enrich log messages.

The `logContext` fiber reference is used to store typed, structured log
annotations, which can be utilized by backends to enrich log messages.

Because `logContext` is an ordinary `zio.FiberRef`, it may be get, set,
and updated like any other fiber reference. However, the idiomatic way to
interact with `logContext` is by using `zio.logging.LogAnnotation`.

For example:

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
myResponseHandler(request) @@ LogAnnotation.UserId(request.userId)
```

This code would add the structured log annotation `LogAnnotation.UserId`
to all log messages emitted by the `myResponseHandler(request)` effect.

The user of the library is allowed to add a custom `LogAnnotation`:

```scala

val customLogAnnotation = LogAnnotation[Int]("custom_annotation", _ + _, _.toString)
```
