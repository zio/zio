# Logger Context and Annotations

> The `logContext` fiber reference stores typed, structured log annotations, which backends can use to enrich log messages.

Because `logContext` is an ordinary `zio.FiberRef`, you can get, set, and update it like any other fiber reference. However, the idiomatic way to interact with `logContext` is by using `zio.logging.LogAnnotation`.

To add the built-in `LogAnnotation.UserId` to every log message emitted by an effect, apply it with the `@@` operator. The `myResponseHandler` function below is illustrative and does not exist in this library:

```scala
myResponseHandler(request) @@ LogAnnotation.UserId(request.userId)
```

This code adds the structured log annotation `LogAnnotation.UserId` to all log messages emitted by the `myResponseHandler(request)` effect.

You can also add a custom `LogAnnotation`. Define it with a name, a function that combines two values, and a function that renders the value as a string:

```scala
import zio.logging.LogAnnotation

val customLogAnnotation = LogAnnotation[Int]("custom_annotation", _ + _, _.toString)
```
