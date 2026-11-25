# How to Add a Correlation ID to Every Log Line

> You want every log line written while one request is handled to carry the same ID, so that you can search the logs for that ID and see the whole story of the request, even when many requests run at the same time. ZIO Logging does this with a log annotation: a named value attached to an effect with `@@`, which every log call inside the effect, and every fiber it starts, picks up.

In this guide, we attach a request ID to a request handler, make sure the ID reaches the work the handler runs in parallel, and change the log format so the ID is printed. We finish with the JSON and SLF4J variants and a test.

The steps are:

1. Choose the annotation for the ID.
2. Read the ID from the request or generate one.
3. Apply the annotation around the handler.
4. Check that forked fibers carry the ID.
5. Print the ID in the log format.
6. Send the ID to JSON and SLF4J output.
7. Test that every line carries the ID.

## The Problem

Without an annotation, the only way to get the ID into each line is to pass it to every function and write it into every message. Each function that logs needs an extra parameter, and one forgotten call leaves a line that cannot be traced back to its request:

```scala
import zio._

def checkStockManually(item: String, requestId: String): UIO[Unit] =
  ZIO.logInfo(s"[$requestId] Checking stock for $item")

def handleManually(path: String, requestId: String): UIO[Unit] =
  for {
    _ <- ZIO.logInfo(s"[$requestId] Handling $path")
    _ <- ZIO.foreachParDiscard(List("book", "pen"))(item => checkStockManually(item, requestId))
    _ <- ZIO.logInfo("Request done") // the ID is missing here
  } yield ()
```

The ID is also baked into the message text, so a log tool cannot filter on it as a field. In this guide, the ID lives in the fiber's log context instead, and the logger adds it to each line.

## Prerequisites

Add the core module to your build. Use `%%%` instead of `%%` for Scala.js or Scala Native:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.5"
```

The examples use these imports and a small request handler. The handler is a plain function, `handle`, standing in for whatever HTTP library you use. It logs once at the start, checks two items in parallel, and logs once at the end:

```scala
import java.time.format.DateTimeFormatter
import java.util.UUID
import zio._
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat, consoleJsonLogger, consoleLogger }

case class Request(path: String, headers: Map[String, String])
case class Response(status: Int)

def checkStock(item: String): UIO[Unit] =
  ZIO.logInfo(s"Checking stock for $item")

def handle(req: Request): UIO[Response] =
  for {
    _ <- ZIO.logInfo(s"Handling ${req.path}")
    _ <- ZIO.foreachParDiscard(List("book", "pen"))(checkStock)
    _ <- ZIO.logInfo("Request done")
  } yield Response(200)
```

This guide assumes you know how to install a console logger. See [Console Logger](console-logger.md) if not.

## Choose the Annotation

A `LogAnnotation[A]` has a name, which becomes the key in the output, a `combine` function, and a `render` function that turns the value into a string. ZIO Logging ships `LogAnnotation.TraceId`, a `LogAnnotation[UUID]` named `trace_id`. Use it when your IDs are always UUIDs.

When IDs come from outside, for example from a load balancer or another service, they are often not UUIDs. Then define your own annotation over `String`:

```scala
val RequestId: LogAnnotation[String] =
  LogAnnotation[String](
    name = "request_id",
    combine = (_, newer) => newer,
    render = identity
  )
```

The `combine` function decides what happens when the same annotation is applied twice, once inside the other. Keeping the newer value means an inner `@@ RequestId(...)` replaces the outer one, which is what `LogAnnotation.TraceId` does too. For more on annotations, see [Log Annotations](reference-log-annotations.md).

## Read the ID From the Request or Generate One

If the caller sent an ID, reuse it, so that your lines match the caller's lines. If not, generate a fresh one with `Random.nextUUID`, which ZIO Test can make deterministic. The `requestIdOf` function reads an `X-Request-Id` header and falls back to a new UUID:

```scala
def requestIdOf(req: Request): UIO[String] =
  req.headers.get("X-Request-Id").filter(_.nonEmpty) match {
    case Some(id) => ZIO.succeed(id)
    case None     => Random.nextUUID.map(_.toString)
  }
```

Real HTTP header names are case-insensitive; your HTTP library's header lookup takes care of that, while this `Map` lookup does not.

With `LogAnnotation.TraceId`, the header value must parse as a UUID. The `traceIdOf` function treats a missing or malformed header the same way and generates a new ID:

```scala
def traceIdOf(req: Request): UIO[UUID] =
  ZIO.attempt(UUID.fromString(req.headers.getOrElse("X-Trace-Id", ""))).orElse(Random.nextUUID)
```

## Apply the Annotation Around the Handler

`RequestId(id)` returns a `ZIOAspect`. Applying it with `@@` sets the annotation for the whole wrapped effect, and removes it again when the effect ends, so the next request starts clean. We wrap the handler once, at the point where the request enters the application:

```scala
def handleWithId(req: Request): UIO[Response] =
  requestIdOf(req).flatMap(id => handle(req) @@ RequestId(id))
```

The typed variant looks the same with `LogAnnotation.TraceId`:

```scala
def handleWithTraceId(req: Request): UIO[Response] =
  traceIdOf(req).flatMap(traceId => handle(req) @@ LogAnnotation.TraceId(traceId))
```

Neither `handle` nor `checkStock` changes. They log as before, and the ID comes from the context they run in.

:::tip
ZIO core has its own string annotations, set with `ZIO.logAnnotate("request_id", id)`. They work for this purpose too. The two kinds are stored in different places, so the format must read the right one: `LogFormat.logAnnotation` reads only `LogAnnotation` values, `LogFormat.annotation(name)` reads only ZIO core annotations, and `LogFormat.anyAnnotation(name)`, `LogFormat.allAnnotations` and the `%kv{...}` and `%kvs` patterns read both.
:::

## Check That Forked Fibers Carry the ID

The handler checks its two items with `ZIO.foreachParDiscard`, which runs each check on a new fiber. Those lines still carry the ID. The annotations live in `logContext`, a `FiberRef[LogContext]`, and a fiber that is forked copies its parent's value. When a child fiber is joined, its context is merged back into the parent's with each annotation's `combine` function.

The copy happens when the fiber starts. A fiber that was started before the request, such as a background worker that reads jobs from a `Queue`, does not see the request's ID. Pass the ID along with the job and apply the annotation again in the worker:

```scala
case class AuditJob(requestId: String, message: String)

def auditWorker(jobs: Queue[AuditJob]): UIO[Nothing] =
  jobs.take.flatMap(job => ZIO.logInfo(job.message) @@ RequestId(job.requestId)).forever
```

## Print the ID in the Log Format

The annotation is now attached to every line, but the console logger does not print it yet. `LogFormat.default`, the format the console and file loggers use when none is given, writes the timestamp, level, fiber, message and cause, and no annotations. We build a format that also writes the request ID. `LogFormat.logAnnotation(RequestId)` writes `request_id=<value>`, and writes nothing on lines that have no ID:

```scala
val requestIdFormat: LogFormat =
  LogFormat.label("timestamp", LogFormat.timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) |-|
    LogFormat.label("level", LogFormat.level) |-|
    LogFormat.label("thread", LogFormat.fiberId) |-|
    LogFormat.label("message", LogFormat.quoted(LogFormat.line)) |-|
    LogFormat.logAnnotation(RequestId) +
    (LogFormat.space + LogFormat.label("cause", LogFormat.cause)).filter(LogFilter.causeNonEmpty)
```

The application installs a console logger with this format and handles two requests at the same time, one with an `X-Request-Id` header and one without:

```scala
object OrdersApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(
      ConsoleLoggerConfig(requestIdFormat, LogFilter.LogLevelByNameConfig.default)
    )

  override def run: ZIO[Any, Any, Any] =
    ZIO.foreachParDiscard(
      List(
        Request("/orders/1", Map("X-Request-Id" -> "req-7f3a")),
        Request("/orders/2", Map.empty)
      )
    )(handleWithId)
}
```

The lines of the two requests interleave, but each carries its own ID, including the lines written by the forked stock checks. The shape of the output looks like this, with the timestamps and fiber numbers shortened:

```text
timestamp=2026-11-17T09:15:02.104+01:00 level=INFO thread=zio-fiber-9 message="Handling /orders/1" request_id=req-7f3a
timestamp=2026-11-17T09:15:02.104+01:00 level=INFO thread=zio-fiber-10 message="Handling /orders/2" request_id=5b0e8c1a-...
timestamp=2026-11-17T09:15:02.109+01:00 level=INFO thread=zio-fiber-12 message="Checking stock for book" request_id=req-7f3a
timestamp=2026-11-17T09:15:02.109+01:00 level=INFO thread=zio-fiber-13 message="Checking stock for pen" request_id=req-7f3a
...
```

If your logger reads its format from configuration, add `%kv{request_id}` to the pattern instead. It prints the annotation with that name, whether it is a `LogAnnotation` or a ZIO core annotation. `%kvs` prints all annotations except the logger name:

```hocon
logger {
  format = "%timestamp %level [%fiberId] %name:%line %message %kv{request_id} %cause"
}
```

See [Formatting Log Records](formatting-log-records.md#log-format-configuration) for every pattern element, and [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md) for loading the `logger` block.

## Send the ID to JSON and SLF4J Output

In a JSON format, the same `LogFormat.logAnnotation(RequestId)` becomes a `request_id` field, so a log tool can filter on it directly. The following layer writes the request ID as its own JSON key:

```scala
val requestIdJsonFormat: LogFormat =
  LogFormat.label("timestamp", LogFormat.timestamp(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) +
    LogFormat.label("level", LogFormat.level) +
    LogFormat.label("message", LogFormat.line) +
    LogFormat.logAnnotation(RequestId) +
    LogFormat.label("cause", LogFormat.cause).filter(LogFilter.causeNonEmpty)

val jsonLoggerLayer: ZLayer[Any, Nothing, Unit] =
  Runtime.removeDefaultLoggers >>> consoleJsonLogger(
    ConsoleLoggerConfig(requestIdJsonFormat, LogFilter.LogLevelByNameConfig.default)
  )
```

Each line then looks like `{"timestamp":"...","level":"INFO","message":"Handling /orders/1","request_id":"req-7f3a"}`. See [How to Log JSON](how-to-json-logs.md) for the other fields.

When ZIO Logging writes through SLF4J, you do not change the ZIO format. The default format of `SLF4J.slf4j` already passes every annotation except the logger name and the SLF4J marker to SLF4J:

```scala
import zio.logging.backend.SLF4J

val slf4jLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
```

What SLF4J does with the annotations depends on the module. The `zio-logging-slf4j` module (SLF4J 1.x) puts them in the MDC for the duration of the call, so a Logback 1.2 pattern prints the ID with `%X{request_id}`, or all of them with `%mdc`. The `zio-logging-slf4j2` module adds them as SLF4J 2 key-value pairs, which a Logback 1.3+ pattern prints with `%kvp`. The Log4j 2 example in this repository prints them with `%X`. A Logback 1.3+ pattern that prints the key-value pairs looks like this:

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%kvp] %msg%n</pattern>
```

See [SLF4J v2](slf4j2.md), [SLF4J v1](slf4j1.md), and [How to Use ZIO Logging With Logback or Log4j 2](how-to-logback-log4j2.md).

## Test That Every Line Carries the ID

A test can check the context of every recorded line instead of parsing text. ZIO Test records each log call with `ZTestLogger`, and each entry's `context` holds the fiber's `logContext`. The following test handles one request and checks that every line, including those from forked fibers, has the request's ID:

```scala
import zio.logging.logContext
import zio.test._

val everyLineHasTheId = test("every line of a request carries its ID") {
  for {
    _       <- handleWithId(Request("/orders/1", Map("X-Request-Id" -> "req-1")))
    entries <- ZTestLogger.logOutput
  } yield assertTrue(
    entries.nonEmpty,
    entries.forall(_.context.get(logContext).flatMap(_.get(RequestId)).contains("req-1"))
  )
}
```

For more ways to check log output, see [How to Test Log Output](how-to-test-log-output.md).

## See also

- [Log Annotations](reference-log-annotations.md) for `LogAnnotation`, `LogContext`, `logContext` and `LogAnnotationZIOSyntax#logAnnotate`.
- [Logger Context and Annotations](logger-context-and-annotations.md) for an overview of annotations.
- [LogFormat Reference](reference-log-format.md) for `LogFormat.logAnnotation`, `LogFormat.anyAnnotation` and `LogFormat.allAnnotations`.
- [How to Write a Custom Log Format](how-to-custom-log-format.md) for building the rest of the line.
