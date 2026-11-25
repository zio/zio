# Logger Context and Annotations

> A service handles many requests at once, so its log lines from different requests interleave. To follow one request through the log, each line needs to carry who and what it belongs to: a trace id, a user id, the part of the program that wrote it. Writing those values into every message by hand is tedious and easy to forget, and it buries them in free text that log tools cannot filter on.

Annotations solve this. You attach a value to an effect once, and every log line that the effect and the fibers it forks emit carries the value as a separate key and value, which a format writes next to the message or into a JSON field. This page explains the two kinds of annotations, how logger names fit in, and what happens when annotations nest. Every member it mentions is listed in [Log Annotations and LogContext Reference](reference-log-annotations.md).

## Typed Annotations

ZIO Logging's annotations are typed. A `LogAnnotation[A]` is a key with a name, a value type `A`, a `LogAnnotation#combine` rule for nested values, and a `LogAnnotation#render` rule that turns the value into text. The values for the current fiber live in a `LogContext`, which the fiber reference `zio.logging.logContext` holds. You define an annotation once and apply it to an effect with `@@`:

```scala
import zio.ZIO
import zio.logging.LogAnnotation

import java.util.UUID

val Tenant: LogAnnotation[String] =
  LogAnnotation[String](name = "tenant", combine = (_, inner) => inner, render = identity)

def handle(requestId: UUID): ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Handling request") @@ LogAnnotation.TraceId(requestId) @@ Tenant("acme")
```

Compared with a plain string key, the typed form gives you three things. The compiler checks the value: `LogAnnotation.TraceId` accepts a `UUID`, so a user id cannot end up in the trace id field. The annotation decides what happens when an inner effect sets it again, through `LogAnnotation#combine`. And the name and rendering live in one `val` that the whole code base shares, so every module writes `trace_id` the same way.

ZIO Logging predefines three annotations: `LogAnnotation.TraceId` for a `UUID` named `trace_id`, `LogAnnotation.UserId` for a `String` named `user_id`, and `LogAnnotation.TraceSpans` for a `List[String]` named `trace_spans`.

## A Complete Example

The program below handles a checkout. It names its loggers, tags each request with a trace id and a user id, and adds a ZIO string annotation for the cart size. The format writes the level, the logger name, the quoted message, and the three annotations:

```scala
import zio.{ Runtime, ZIO, ZIOAppDefault, ZLayer }
import zio.logging.{
  ConsoleLoggerConfig,
  LogAnnotation,
  LogFilter,
  LogFormat,
  LoggerNameExtractor,
  appendLoggerName,
  consoleLogger,
  loggerName
}

import java.util.UUID

object CheckoutApp extends ZIOAppDefault {

  val format: LogFormat =
    LogFormat.level |-|
      LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat() |-|
      LogFormat.quoted(LogFormat.line) |-|
      LogFormat.logAnnotation(LogAnnotation.TraceId) |-|
      LogFormat.logAnnotation(LogAnnotation.UserId) |-|
      LogFormat.annotation("cart_size")

  override val bootstrap: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>>
      consoleLogger(ConsoleLoggerConfig(format, LogFilter.LogLevelByNameConfig.default))

  val chargeCard: ZIO[Any, Nothing, Unit] =
    ZIO.logInfo("Charging card") @@ appendLoggerName("payment")

  def checkout(userId: String, items: Int): ZIO[Any, Nothing, Unit] =
    ZIO.logAnnotate("cart_size", items.toString) {
      ZIO.logInfo("Checkout started") *> chargeCard
    } @@ LogAnnotation.UserId(userId) @@ loggerName("checkout")

  val run =
    checkout("user-42", 3) @@ LogAnnotation.TraceId(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"))
}
```

Running it prints two lines:

```text
INFO checkout "Checkout started" trace_id=7c9e6679-7425-40de-944b-e07fc1f90ae7 user_id=user-42 cart_size=3
INFO checkout.payment "Charging card" trace_id=7c9e6679-7425-40de-944b-e07fc1f90ae7 user_id=user-42 cart_size=3
```

Neither `ZIO.logInfo` call mentions the trace id, the user id or the cart size. The aspects attach them to everything inside: the outermost `LogAnnotation.TraceId`, then `LogAnnotation.UserId` and the logger name, then the string annotation. `chargeCard` adds `payment` to the logger name it inherits. With `zio.logging.consoleJsonLogger` and the same format, each annotation becomes a JSON field instead; see [How to Log JSON](how-to-json-logs.md).

`LogFormat.default`, which the console and file loggers use when you configure no format, writes no annotations at all. You always choose which annotations reach the output, by name as above, or all at once with `LogFormat.allAnnotations` or the `%kvs` pattern. The full list of annotation formats is in [LogFormat Reference](reference-log-format.md#annotations).

## String Annotations and Typed Annotations

The `cart_size` value in the example is not a `LogAnnotation`. It comes from ZIO itself: `ZIO.logAnnotate(key, value)` and `ZIOAspect.annotated(key, value)` attach a string under a string key. These string annotations are stored apart from `logContext`, and ZIO passes them to every logger, including its own default logger.

Both kinds are useful, for different jobs. A string annotation suits a one-off key in one place of the code, or a value that every logger, including loggers outside ZIO Logging, should receive. A typed annotation suits a value used across the code base, a value with a real type, or a value that needs a merge rule when scopes nest.

Formats keep the two apart. `LogFormat.annotation(name)` and `LogFormat.annotations` read string annotations; `LogFormat.logAnnotation(annotation)` and `LogFormat.logAnnotations` read typed ones. `LogFormat.allAnnotations`, and in string patterns `%kv{name}` and `%kvs`, read both, so a format written as a pattern does not need to know which kind a key is.

:::caution
ZIO core also has a class named `zio.LogAnnotation`, the string pair. If a file has both `import zio._` and `import zio.logging._`, the name `LogAnnotation` is ambiguous. Import ZIO Logging's class by name with `import zio.logging.LogAnnotation`.
:::

## Logger Names

A logger name says which part of the program wrote a line. By default ZIO Logging derives it from the source location of the log call: a call inside `com.example.CheckoutService` has the logger name `com.example.CheckoutService`. That default works well for filtering, because per-name levels in the [log filter](log-filter.md) configuration, such as `"com.example" = "DEBUG"`, select by this name.

The aspects `zio.logging.loggerName` and `zio.logging.appendLoggerName` replace the default with a name you choose. `loggerName("checkout")` sets the name for everything inside; `appendLoggerName("payment")` adds a dot and `payment` to the name an outer aspect set, which is how `chargeCard` above logs as `checkout.payment`. A third form, `loggerName(fn)`, computes the new name from the current one. Use these aspects when the source location is not a good name: code generated by a library, a shared helper called from many services, or a name that must stay stable when you move code between packages.

The logger name is itself a string annotation with the key `logger_name`. So `LogFormat.annotations` and `LogFormat.allAnnotations` write it as `logger_name=checkout`, while the `%kvs` pattern leaves it out; write it with `%name` or `%kv{logger_name}` instead.

## Nesting and Combine

Effects nest, so an annotation is often applied to an effect that already has a value for it. The annotation's `LogAnnotation#combine` function decides the result, called as `combine(outer, inner)`. `LogAnnotation.TraceId` and `LogAnnotation.UserId` keep the inner value, so a nested call can switch to another id for its own scope. `LogAnnotation.TraceSpans` appends the inner list to the outer one and renders the result with `:` between the names, so nested spans build a path.

To record the path of nested operations with `LogAnnotation.TraceSpans`:

```scala
import zio.ZIO
import zio.logging.LogAnnotation

val loadUser: ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Loading user") @@ LogAnnotation.TraceSpans(List("db"))

val handleRequest: ZIO[Any, Nothing, Unit] =
  (ZIO.logInfo("Request received") *> loadUser) @@ LogAnnotation.TraceSpans(List("http"))
```

With `LogFormat.logAnnotation(LogAnnotation.TraceSpans)` in the format, the first line carries `trace_spans=http` and the second `trace_spans=http:db`. When the inner effect ends, the outer value comes back: a line logged after `loadUser` inside `handleRequest` carries `trace_spans=http` again.

A custom annotation picks its own rule. To collect the feature flags that are active along the call path, union the sets:

```scala
import zio.ZIO
import zio.logging.LogAnnotation

val FeatureFlags: LogAnnotation[Set[String]] =
  LogAnnotation[Set[String]](
    name = "feature_flags",
    combine = _ ++ _,
    render = _.toList.sorted.mkString(",")
  )

val newCheckout: ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Using the new checkout") @@ FeatureFlags(Set("new-checkout"))

val session: ZIO[Any, Nothing, Unit] =
  newCheckout @@ FeatureFlags(Set("dark-mode"))
```

With `LogFormat.logAnnotation(FeatureFlags)` in the format, the line logged when `session` runs carries `feature_flags=dark-mode,new-checkout`. Had `LogAnnotation#combine` returned the inner value, it would carry only `new-checkout`.

Annotations also follow fibers. A fiber forked inside an annotated effect, for example by `ZIO.foreachPar`, starts with the annotations of its parent, so parallel work keeps the trace id without any extra code.

## Next Steps

To put these pieces to work on a request handler, follow [How to Add a Correlation ID to Every Log Line](how-to-correlation-id.md). To read the context directly, combine contexts, or look up the exact behaviour of each aspect, see [Log Annotations and LogContext Reference](reference-log-annotations.md).
