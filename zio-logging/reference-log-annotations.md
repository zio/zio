# Log Annotations and LogContext Reference

> LogAnnotation, LogContext, the logContext fiber reference, logAnnotate syntax, and the loggerName and appendLoggerName aspects.

`LogAnnotation[A]` is a **typed key for structured log data**: a name, a value type `A`, a rule for combining two values, and a rule for rendering a value as text. `LogContext` is the immutable map from those keys to their values, and the fiber reference `zio.logging.logContext` holds the `LogContext` of the current fiber. Together they let you attach values such as a trace id or a user id to every log line emitted by an effect, without passing them to each `ZIO.log*` call. The fundamental operation is `LogAnnotation#apply`, which turns a value into a `ZIOAspect` that you apply with `@@`.

This page also covers the logger-name aspects `zio.logging.loggerName` and `zio.logging.appendLoggerName`. They set a plain string annotation, not a `LogAnnotation`, but they are the other half of "adding context to log lines".

The types and values have these shapes:

```scala
final case class LogAnnotation[A: Tag](name: String, combine: (A, A) => A, render: A => String) {
  type Id
  type Type = A
  def apply(value: A): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
  def id: Id
  def tag: Tag[A]
}

final case class LogContext private (private val map: Map[LogAnnotation[_], Any])

package object logging {
  val logContext: FiberRef[LogContext]
}
```

`LogAnnotation`:
- Is typed: a `LogAnnotation[java.util.UUID]` only accepts a `UUID`, and `LogContext#get` gives the `UUID` back, not a string.
- Merges on nesting: when the same annotation is applied inside an effect that already has it, `combine(outer, inner)` decides the new value.
- Is compared by name and type: two annotations are equal when their `name` and their type `A` are equal, whatever their `LogAnnotation#combine` and `LogAnnotation#render` functions are.

:::caution[Two classes are called LogAnnotation]
ZIO core has its own `zio.LogAnnotation`, a plain `(key: String, value: String)` pair. With `import zio._` and `import zio.logging._` together, the name `LogAnnotation` is ambiguous and does not compile. Import ZIO Logging's class by name, as in `import zio.logging.LogAnnotation`, or import only the `zio` members you use.
:::

## Usage

To tag every log line of a request with a trace id and a user id, and to print both values next to the message:

```scala
import zio.{ Runtime, ZIO, ZIOAppDefault, ZLayer }
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, consoleLogger }

import java.util.UUID

object AnnotatedApp extends ZIOAppDefault {

  val format: LogFormat =
    LogFormat.level |-| LogFormat.line |-|
      LogFormat.logAnnotation(LogAnnotation.TraceId) |-|
      LogFormat.logAnnotation(LogAnnotation.UserId)

  override val bootstrap: ZLayer[Any, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>>
      consoleLogger(ConsoleLoggerConfig(format, LogFilter.LogLevelByNameConfig.default))

  val handleRequest: ZIO[Any, Nothing, Unit] =
    ZIO.logInfo("Request received") *> ZIO.logInfo("Request done")

  val run =
    handleRequest @@
      LogAnnotation.TraceId(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")) @@
      LogAnnotation.UserId("user-42")
}
```

Both lines carry both values:

```text
INFO Request received trace_id=7c9e6679-7425-40de-944b-e07fc1f90ae7 user_id=user-42
INFO Request done trace_id=7c9e6679-7425-40de-944b-e07fc1f90ae7 user_id=user-42
```

[Logger Context and Annotations](logger-context-and-annotations.md) explains when to use typed annotations, and [How to Add a Correlation ID to Every Log Line](how-to-correlation-id.md) builds a full request-scoped example.

## Creating Annotations

A custom annotation is an instance of the case class. Its declaration is:

```scala
final case class LogAnnotation[A: Tag](name: String, combine: (A, A) => A, render: A => String)
```

The three fields have these roles:

| Field     | Type             | Role                                                                                                                                        |
| --------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `name`    | `String`         | The key under which formats write the value, such as `trace_id`. It is also the key that `LogContext#get(name)` and `LogContext#asMap` use. |
| `combine` | `(A, A) => A`    | Called as `combine(current, added)` when a value is added for an annotation that already has one. The result replaces the current value.   |
| `render`  | `A => String`    | Turns the value into the text that formats write.                                                                                           |

The implicit `Tag[A]` records the value type. `LogAnnotation#tag` returns it, and `LogAnnotation#id` returns the pair of name and tag that `equals` and `hashCode` use.

The `LogAnnotation#combine` function is where the design choice lies. Return the added value to make an inner scope override the outer one. Concatenate or merge to accumulate. To define an annotation for feature flags that accumulates flags as scopes nest, and an annotation for a tenant id where the innermost value wins:

```scala
import zio.logging.LogAnnotation

val FeatureFlags: LogAnnotation[Set[String]] =
  LogAnnotation[Set[String]](
    name = "feature_flags",
    combine = _ ++ _,
    render = _.toList.sorted.mkString(",")
  )

val TenantId: LogAnnotation[String] =
  LogAnnotation[String](
    name = "tenant_id",
    combine = (_, added) => added,
    render = identity
  )
```

Define each annotation once, as a `val`, and reuse it. Because equality ignores `LogAnnotation#combine` and `LogAnnotation#render`, a second `LogAnnotation[String]("tenant_id", ...)` with different functions is the same key as the first one, and which functions run depends on which instance the context stored first.

## Predefined Annotations

The `LogAnnotation` companion object defines three annotations:

| Annotation                 | Type             | `name`        | `combine(current, added)` | `render`                                        |
| -------------------------- | ---------------- | ------------- | ------------------------- | ----------------------------------------------- |
| `LogAnnotation.TraceId`    | `java.util.UUID` | `trace_id`    | `added`                   | `UUID#toString`                                 |
| `LogAnnotation.UserId`     | `String`         | `user_id`     | `added`                   | the string itself                               |
| `LogAnnotation.TraceSpans` | `List[String]`   | `trace_spans` | `current ++ added`        | the elements joined with `:`, such as `http:db` |

`LogAnnotation.TraceId` and `LogAnnotation.UserId` keep the innermost value. `LogAnnotation.TraceSpans` builds a path of span names, outermost first, as the effect nests.

## Applying Annotations

An annotation does nothing until you give it a value and apply it to an effect. There are three ways to do that, and they all end in the same aspect.

### As an Aspect

`LogAnnotation#apply` returns a `ZIOAspect`. Applying it with `@@` runs the effect with `logContext` locally set to the current context plus the new value, so every log call inside the effect, including calls in fibers it forks, sees the value. When the effect ends, `logContext` goes back to what it was:

```scala
final case class LogAnnotation[A: Tag](name: String, combine: (A, A) => A, render: A => String) {
  def apply(value: A): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
}
```

`effect @@ a @@ b` reads as `(effect @@ a) @@ b`, so `b` is the outer aspect and `a` the inner one. For an annotation applied twice, the context first gets the outer value, then `combine(outer, inner)`. To nest `LogAnnotation.TraceSpans` by applying it at two levels:

```scala
import zio.ZIO
import zio.logging.LogAnnotation

val query: ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Running query") @@ LogAnnotation.TraceSpans(List("db"))

val request: ZIO[Any, Nothing, Unit] =
  (ZIO.logInfo("Handling request") *> query) @@ LogAnnotation.TraceSpans(List("http"))
```

With a format that writes `LogFormat.logAnnotation(LogAnnotation.TraceSpans)`, the first line carries `trace_spans=http` and the second `trace_spans=http:db`.

Two annotation aspects composed with each other, as in `LogAnnotation.UserId("a") @@ LogAnnotation.TraceId(id)` without an effect on the left, become one aspect holding both values. When both sides set the same annotation, the right-hand value replaces the left-hand one without calling `LogAnnotation#combine`.

### With the logAnnotate Syntax

The implicit class `LogAnnotationZIOSyntax` adds `LogAnnotationZIOSyntax#logAnnotate` to every `ZIO`. It is the same as `effect @@ key(value)`, written as a method call:

```scala
implicit final class LogAnnotationZIOSyntax[R, E, A](self: ZIO[R, E, A]) {
  def logAnnotate[V: Tag](key: LogAnnotation[V], value: V): ZIO[R, E, A]
}
```

Bring the syntax into scope with `import zio.logging.LogAnnotationZIOSyntax` or `import zio.logging._`. To annotate a call with the user id in method style:

```scala
import zio.ZIO
import zio.logging.{ LogAnnotation, LogAnnotationZIOSyntax }

val audited: ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Password changed").logAnnotate(LogAnnotation.UserId, "user-42")
```

### Typed Annotations Versus ZIO String Annotations

ZIO core has its own annotations: `ZIO.logAnnotate(key, value)` and `ZIOAspect.annotated(key, value)` attach a `String` value under a `String` key. They are stored in a different place from `logContext`, and the two kinds never mix:

| Aspect                     | ZIO core string annotations                         | ZIO Logging typed annotations                                   |
| -------------------------- | --------------------------------------------------- | --------------------------------------------------------------- |
| Created with               | `ZIO.logAnnotate(key, value)`, `ZIOAspect.annotated` | `LogAnnotation#apply` with `@@`, or `effect.logAnnotate(annotation, value)` |
| Value type                 | `String`                                            | Any `A`, rendered with `LogAnnotation#render`                   |
| Nested value for same key  | Inner value replaces the outer one                  | `LogAnnotation#combine(outer, inner)`                           |
| Stored in                  | ZIO's log annotations, passed to every `ZLogger`    | The `zio.logging.logContext` fiber reference                    |
| Read in code with          | `ZIO.logAnnotations`                                | `logContext.get`                                                |
| Written by formats         | `LogFormat.annotation(name)`, `LogFormat.annotations` | `LogFormat.logAnnotation(annotation)`, `LogFormat.logAnnotations` |
| Seen by other `ZLogger`s   | Yes, as the `annotations` argument                  | Only by loggers that read `logContext` from their `FiberRefs` argument, such as a logger built from a `LogFormat` that writes it |

Use a string annotation for a quick key that every `ZLogger`, including ZIO's default logger, should see. The default formats of the [SLF4J](slf4j2.md) and [JPL](jpl.md) backends write both kinds. Use a typed annotation when the value has a type worth keeping, needs a merge rule, or is defined once and shared across a code base.

## The logContext Fiber Reference

`zio.logging.logContext` is an ordinary `FiberRef[LogContext]`. It starts as `LogContext.empty`, a forked fiber starts with a copy of its parent's context, and a joined fiber's context is merged into its parent's with `LogContext#++`:

```scala
package object logging {
  val logContext: FiberRef[LogContext]
}
```

You rarely touch it directly; the aspects above are the idiomatic way to change it. Reading it is useful in tests and in custom loggers. To read the user id that the surrounding aspect set:

```scala
import zio.ZIO
import zio.logging.{ LogAnnotation, logContext }

val currentUser: ZIO[Any, Nothing, Option[String]] =
  logContext.get.map(_.get(LogAnnotation.UserId))

val program: ZIO[Any, Nothing, Option[String]] =
  currentUser @@ LogAnnotation.UserId("user-42")
```

`FiberRef#update` and `FiberRef#set` change the context for the rest of the fiber, not only for one block, which is rarely what you want. `FiberRef#locallyWith` gives the scoped behaviour of the aspect. To add a value with `LogContext#annotate` for one block:

```scala
import zio.ZIO
import zio.logging.{ LogAnnotation, logContext }

val withUser: ZIO[Any, Nothing, Unit] =
  logContext.locallyWith(_.annotate(LogAnnotation.UserId, "user-42"))(ZIO.logInfo("Inside"))
```

A custom `ZLogger` receives the fiber refs as its `context: FiberRefs` argument and reads the context with `context.get(logContext)`, which returns an `Option[LogContext]`.

## LogContext Operations

`LogContext` is an immutable map from annotations to values. Its constructor is private: start from `LogContext.empty` and add values with `LogContext#annotate`.

### Creating and Adding Values

`LogContext.empty` is the context with no values. `LogContext#annotate` adds a value, combining it with the current value when the annotation already has one:

```scala
object LogContext {
  val empty: LogContext
}

final case class LogContext private (private val map: Map[LogAnnotation[_], Any]) {
  def annotate[A](annotation: LogAnnotation[A], newA: A): LogContext
}
```

To build a context with a user id and two trace spans:

```scala
import zio.logging.{ LogAnnotation, LogContext }

val context: LogContext =
  LogContext.empty
    .annotate(LogAnnotation.UserId, "user-1")
    .annotate(LogAnnotation.TraceSpans, List("http"))
    .annotate(LogAnnotation.TraceSpans, List("db"))
```

### Reading Values

`LogContext#get` with an annotation returns the typed value. `LogContext#apply` returns the rendered value. `LogContext#get` with a name returns the rendered value of the annotation with that name. `LogContext#asMap` renders every entry:

```scala
final case class LogContext private (private val map: Map[LogAnnotation[_], Any]) {
  def get[A](annotation: LogAnnotation[A]): Option[A]
  def apply[A](logAnnotation: LogAnnotation[A]): Option[String]
  def get(name: String): Option[String]
  def asMap: Map[String, String]
}
```

Reading the context built above gives the typed list, the rendered list, the rendered user id, and the whole context as strings:

```scala
context.get(LogAnnotation.TraceSpans)
// res7: Option[List[String]] = Some(value = List("http", "db"))
context(LogAnnotation.TraceSpans)
// res8: Option[String] = Some(value = "http:db")
context.get("user_id")
// res9: Option[String] = Some(value = "user-1")
context.asMap
// res10: Map[String, String] = Map(
//   "user_id" -> "user-1",
//   "trace_spans" -> "http:db"
// )
```

:::caution[Two annotations with the same name]
Annotations with the same name but different types, such as a `LogAnnotation[String]("id", ...)` and a `LogAnnotation[Int]("id", ...)`, are different keys, so a context can hold both. `LogContext#get(name)` then returns one of them, and `LogContext#asMap` keeps only one entry for the name; which one is not specified. Give every annotation a distinct name.
:::

### Merging Contexts

`LogContext#merge` and its alias `LogContext#++` combine two contexts. For an annotation present in both, the result is `combine(left, right)`; otherwise the value comes from whichever side has it:

```scala
final case class LogContext private (private val map: Map[LogAnnotation[_], Any]) {
  def ++(that: LogContext): LogContext
  def merge(that: LogContext): LogContext
}
```

To merge two contexts that both have trace spans:

```scala
val left: LogContext  = LogContext.empty.annotate(LogAnnotation.TraceSpans, List("a"))
val right: LogContext = LogContext.empty.annotate(LogAnnotation.TraceSpans, List("b"))

val merged: LogContext = left ++ right
```

The merged context holds both spans, left first, because `LogAnnotation.TraceSpans` concatenates:

```scala
merged(LogAnnotation.TraceSpans)
// res11: Option[String] = Some(value = "a:b")
```

## Logger Names

A logger name groups log lines by the part of the program that wrote them. Level filters with per-name mappings, such as `filter.mappings` in the [configuration](reference-configuration.md), select lines by it, and the `%name` pattern and `LogFormat.loggerName` write it. By default the name comes from the `Trace` of the log call: the enclosing class or object, such as `com.example.UserService`. These aspects override it:

```scala
package object logging {
  val loggerNameAnnotationKey: String // "logger_name"

  def loggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
  def loggerName(fn: Option[String] => String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
  def appendLoggerName(value: String): ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any]
}
```

The name is stored as a ZIO core string annotation under the key `zio.logging.loggerNameAnnotationKey`, whose value is `logger_name`, not in `logContext`:

| Aspect                                 | New name                                                                                       |
| -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `zio.logging.loggerName(value)`        | `value`, replacing any name set by an outer aspect.                                            |
| `zio.logging.loggerName(fn)`           | `fn(current)`, where `current` is the current `logger_name` annotation, usually set by an outer aspect, or `None` when there is none. The trace-based default is not passed in. |
| `zio.logging.appendLoggerName(value)`  | `current.value` when an outer aspect set a name, otherwise `value`.                             |

To name a service once and let each method add its own suffix:

```scala
import zio.ZIO
import zio.logging.{ appendLoggerName, loggerName }

val charge: ZIO[Any, Nothing, Unit] =
  ZIO.logInfo("Charging card") @@ appendLoggerName("charge")

val payments: ZIO[Any, Nothing, Unit] =
  charge @@ loggerName("payments")

val versioned: ZIO[Any, Nothing, Unit] =
  charge @@ loggerName((current: Option[String]) => current.fold("payments")(_ + ".v2"))
```

The line from `payments` has the logger name `payments.charge`. In `versioned`, no outer aspect set a name, so the function returns `payments`, and `charge` then appends to it, giving the same `payments.charge`.

Because the name is a string annotation, `LogFormat.annotations` and `LogFormat.allAnnotations` write it as `logger_name=...` next to your other annotations. The `%kvs` pattern leaves it out.

## Writing Annotations to the Output

`LogFormat.default` and `LogFormat.colored`, which the console and file loggers use unless configured otherwise, write no annotations. Add one of these members to your format:

| Member or pattern                      | Writes                                                                                              |
| -------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `LogFormat.logAnnotation(annotation)`  | One typed annotation from `logContext`, as `name=rendered value`.                                   |
| `LogFormat.logAnnotations`             | Every typed annotation in `logContext`.                                                             |
| `LogFormat.annotation(name)`           | One ZIO string annotation.                                                                          |
| `LogFormat.annotations`                | Every ZIO string annotation, including `logger_name`.                                               |
| `LogFormat.allAnnotations`             | Every string annotation, then every typed annotation.                                               |
| `%kv{name}` pattern                    | The string annotation `name`, or when there is none, the typed annotation with that name.           |
| `%kvs` pattern                         | Every string and typed annotation, except `logger_name`.                                            |

In a text format each entry is written as `name=value`, with a space between entries. In a JSON format each entry becomes a field. The full signatures, including the `excludeKeys` overloads, are in [LogFormat Reference](reference-log-format.md#annotations), and the pattern syntax is in [Formatting Log Records](formatting-log-records.md).
