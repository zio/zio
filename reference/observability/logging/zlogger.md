# ZLogger

> ZIO's functional logging trait: ZLogger[-Message, +Output] with composable fan-out, contramap, filterLogLevel, and ZTestLogger utilities.

A `ZLogger[-Message, +Output]` is a pure functional interface for processing log events in a ZIO application. It receives a fully structured log event — including a source `Trace`, fiber ID, log level, lazy message thunk, cause, fiber-ref context, spans, and string annotations — and produces a value of type `Output`. Because a `ZLogger` is a plain immutable value, it can be composed, transformed, and filtered like any other data type, without global mutable state or service-locator registries.

Key properties of `ZLogger`:

- **Contravariant in `Message`** — a `ZLogger[Any, Output]` can stand in wherever a `ZLogger[String, Output]` is required; the type parameter tracks what kind of messages the logger accepts.
- **Covariant in `Output`** — a `ZLogger[Message, String]` is also a `ZLogger[Message, Any]`; you can widen the output freely.
- **Effect-free interface** — `ZLogger#apply` is a synchronous, non-throwing function; it does not return a `ZIO` effect.
- **Lazy message evaluation** — `message` is a thunk (`() => Message`) so string interpolation is skipped when the logger is filtered out before calling `apply`.
- **Composable** — `ZLogger#++`, `ZLogger#+>`, and `ZLogger#<+` fan a single log event to multiple loggers; `ZLogger#map` and `ZLogger#contramap` adapt the output and input types.

The following snippet shows the core `ZLogger` trait and its companion object:

```scala
import zio._

trait ZLogger[-Message, +Output] { self =>

  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Output

  // combinators: ++, +>, <+, contramap, filterLogLevel, map, test
}

object ZLogger {
  val default: ZLogger[String, String]  = ???
  val none: ZLogger[Any, Unit]          = ???

  def simple[A, B](log: A => B): ZLogger[A, B] = ???
  def succeed[A](a: => A): ZLogger[Any, A]      = ???
}
```

## Motivation

ZIO's standard logging calls — `ZIO.log`, `ZIO.logWarning`, `ZIO.logError`, and their siblings — do not hard-code a destination. Instead, the runtime consults `FiberRef.currentLoggers`, a `Set[ZLogger[String, Any]]` propagated through each fiber's context. Every time a fiber logs, the runtime invokes each logger in that set, passing the full event structure, and discards the return value.

This design means we can install, replace, or compose loggers at layer construction time without touching call sites. A single `ZIO.log("request started")` can simultaneously write to the console, ship to a structured JSON endpoint, and accumulate entries in an in-memory buffer for test assertions — by composing three loggers and installing the combined value via `Runtime.addLogger`.

## Quick Showcase

The following block demonstrates the four core ideas: building a custom logger with `ZLogger.simple`, adapting its input type with `ZLogger#contramap`, filtering by level with `ZLogger#filterLogLevel`, and exercising a logger's formatting in isolation with `ZLogger#test`:

```scala
import zio._

// 1. Build a minimal logger from a pure function
val msgLogger: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](msg => println(s"LOG: $msg"))
// msgLogger: ZLogger[String, Unit] = zio.ZLogger$$anon$6@5efa4b31

// 2. Adapt it to accept integers as messages
val intLogger: ZLogger[Int, Unit] =
  msgLogger.contramap[Int](_.toString)
// intLogger: ZLogger[Int, Unit] = zio.ZLogger$$anon$2@7a64102

// 3. Filter so only Warning and above produce output
val warnLogger: ZLogger[String, Option[Unit]] =
  msgLogger.filterLogLevel(_ >= LogLevel.Warning)
// warnLogger: ZLogger[String, Option[Unit]] = zio.ZLogger$$anon$3@63d4fa47

// 4. Test the formatted output of ZLogger.default without a running fiber
val sampleLine: String = ZLogger.default.test("something happened")
// sampleLine: String = "timestamp=2026-08-31T13:34:57.609553904Z level=INFO thread=#zio-fiber- message=\"something happened\""

// 5. Fan both loggers together; Zippable[String, Unit].Out = String
val combined: ZLogger[String, String] = ZLogger.default ++ msgLogger
// combined: ZLogger[String, String] = zio.ZLogger$$anon$1@6875378d
```

## Construction / Creating Instances

We can construct a `ZLogger` in three ways: via `ZLogger.simple` for message-only loggers, via `ZLogger.succeed` for constant-value loggers, or by implementing the `apply` SAM directly when access to every log-event field is needed. The SAM approach is covered under the Core Interface section.

### `ZLogger.simple` — Build a logger from a message function

`ZLogger.simple` creates a logger that only needs the message value and ignores all other event fields (trace, level, fiber ID, spans, annotations, cause). Its signature is:

```scala
object ZLogger {
  def simple[A, B](log: A => B): ZLogger[A, B]
}
```

The factory evaluates the message thunk and passes the result to `log`; all other fields are discarded. We can use it to build a console logger or any pure message transformer:

```scala
import zio._

// A logger that prints only the raw message string
val consoleLogger: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](msg => println(msg))

// A logger that converts each message to uppercase and returns it
val upperLogger: ZLogger[String, String] =
  ZLogger.simple[String, String](_.toUpperCase)
```

### `ZLogger.succeed` — Constant-value logger

`ZLogger.succeed` builds a logger that always returns the same value, regardless of the log event. It is implemented as `simple(_ => a)`, so the message thunk is still evaluated even though its result is discarded. Its signature is:

```scala
object ZLogger {
  def succeed[A](a: => A): ZLogger[Any, A]
}
```

The primary uses are placeholder loggers in fan-out chains and test stubs where a specific return type is required:

```scala
import zio._

// Always returns 42, regardless of what is logged
val constLogger: ZLogger[Any, Int] = ZLogger.succeed(42)
```

## Predefined Instances

The `ZLogger` companion object declares two ready-made loggers that cover the most common runtime needs:

| Instance            | Type                      | Description                                                                                                                                                                                                           |
|---------------------|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ZLogger.default`   | `ZLogger[String, String]` | Formats each event as a single-line `key=value` string containing `timestamp`, `level`, `thread`, `message`, `cause` (if non-empty), spans with elapsed durations, source location, and any string annotations. This is the formatter used by the JVM platform default logging pipeline. |
| `ZLogger.none`      | `ZLogger[Any, Unit]`      | A no-op logger that immediately returns `Unit` without inspecting any field. Use it to silence logging in benchmarks, tests, or the unused branch of a fan-out combinator.                                            |

We can use `ZLogger.default` to inspect what the ZIO runtime actually emits before it is piped to `println`. For example, to produce a formatted line for a message without a running fiber:

```scala
import zio._

val formatted: String = ZLogger.default.test("order placed")
// e.g.: timestamp=2026-07-15T12:00:00Z level=INFO thread=#0 message="order placed"
```

`ZLogger.none` is useful when we need to silence one branch in a fan-out pipeline. For example:

```scala
import zio._

// Runs ZLogger.default but discards ZLogger.none — Zippable[String, Unit].Out = String
val withSilentBranch: ZLogger[String, String] = ZLogger.default ++ ZLogger.none
```

## Core Operations

The following sections cover every method on `ZLogger` and its companion object, grouped by purpose. We start with the single abstract method that defines what a logger IS, then cover transformations, filtering, combining, and testing utilities.

### Core Interface

The core interface consists of the single abstract method `apply`, which the ZIO runtime calls on every log event. Understanding its parameters is essential when implementing a custom logger directly.

#### `ZLogger#apply` — Process a log event

`ZLogger#apply` is the one method every `ZLogger` implementation must provide. The ZIO runtime fiber loop invokes it each time `ZIO.log*` fires. All parameters are eagerly supplied by the runtime except `message`, which is a thunk so that string interpolation is deferred until the logger actually needs the value. The full signature is:

```scala
import zio._

trait ZLogger[-Message, +Output] {
  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Output
}
```

We can implement a custom logger using an anonymous class — the SAM pattern — and have access to every log-event field inside the body:

```scala
import zio._

val structuredLogger: ZLogger[String, Unit] =
  new ZLogger[String, Unit] {
    def apply(
      trace: Trace,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => String,
      cause: Cause[Any],
      context: FiberRefs,
      spans: List[LogSpan],
      annotations: Map[String, String]
    ): Unit =
      println(s"[${logLevel.label}] fiber=${fiberId} ${message()}")
  }
```

:::caution
`apply` is called synchronously inside the fiber loop. It must not block, perform I/O that can throw, or attempt to run ZIO effects. For async or effect-based logging, install a logger that enqueues to a `Queue` and run a separate consumer fiber to drain and forward entries.
:::

### Transformations

The transformation methods `ZLogger#contramap` and `ZLogger#map` let us adapt the input and output types of an existing logger without reimplementing `apply`. Together they give `ZLogger` a profunctor structure: `contramap` adapts the `Message` type contravariantly, and `map` adapts the `Output` type covariantly.

#### `ZLogger#contramap` — Adapt the message type

`ZLogger#contramap` wraps the message thunk so that a `ZLogger[Message, Output]` can accept a different message type `Message1`. The conversion function `f` is applied lazily inside the thunk, so it is not evaluated if the underlying logger does not call `message()`. Its signature is:

```scala
trait ZLogger[-Message, +Output] {
  final def contramap[Message1](f: Message1 => Message): ZLogger[Message1, Output]
}
```

We use `ZLogger#contramap` to adapt `ZLogger.default` so it accepts a custom event type as a message:

```scala
import zio._

// Adapts ZLogger.default to accept integers as log messages
val intLogger: ZLogger[Int, String] =
  ZLogger.default.contramap[Int](n => s"count=$n")

// Adapts to accept a domain case class
case class OrderEvent(orderId: String, status: String)
val orderLogger: ZLogger[OrderEvent, String] =
  ZLogger.default.contramap[OrderEvent](e => s"order=${e.orderId} status=${e.status}")
```

#### `ZLogger#map` — Transform the output

`ZLogger#map` applies a pure function to every value this logger produces. The most common use is connecting a formatter logger to a side-effectful sink such as `println`. Its signature is:

```scala
trait ZLogger[-Message, +Output] {
  final def map[B](f: Output => B): ZLogger[Message, B]
}
```

Mapping `ZLogger.default` with `println` is exactly how the JVM platform wires up its default logging pipeline:

```scala
import zio._

// Equivalent to how Runtime.defaultLoggers is built on the JVM
val printingLogger: ZLogger[String, Unit] =
  ZLogger.default.map(println(_))
```

### Filtering

`ZLogger#filterLogLevel` gates a logger behind a predicate on `LogLevel`. It is distinct from the transformation methods because it changes the output type to `Option[Output]`.

`ZLogger#filterLogLevel` returns a new logger whose output is `Some(output)` when the predicate holds and `None` when it does not. The underlying logger's `apply` is never called when the predicate returns `false`, so the message thunk is also never evaluated for filtered events. Its signature is:

```scala
import zio._

trait ZLogger[-Message, +Output] {
  final def filterLogLevel(f: LogLevel => Boolean): ZLogger[Message, Option[Output]]
}
```

`ZLogger#filterLogLevel` is the last step in the JVM default logging pipeline, restricting console output to `Info` and above:

```scala
import zio._

// Equivalent to what Runtime.defaultLoggers installs on JVM
val defaultPipeline: ZLogger[String, Option[Unit]] =
  ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info)

// A formatter that only emits warnings and errors
val warnAndAbove: ZLogger[String, Option[String]] =
  ZLogger.default.filterLogLevel(_ >= LogLevel.Warning)
```

:::note
`filterLogLevel` changes the output type to `Option[Output]`. If you need to restore the original type after filtering, call `ZLogger#map` with `_.getOrElse(...)` or use `ZLogger.none` as the silent alternative in a `++` fan-out.
:::

### Combining

The combining methods `ZLogger#++`, `ZLogger#+>`, and `ZLogger#<+` fan a single log event to two loggers simultaneously, varying which output is returned. They are the building blocks for multi-destination logging pipelines.

We can read the operators left to right: `++` keeps both outputs, `+>` keeps the right output only, and `<+` keeps the left output only. All three invoke both loggers on every event.

#### `ZLogger#++` — Run both loggers and zip their outputs

`ZLogger#++` invokes both loggers on every log event and combines their outputs using the `Zippable` type class. When both outputs are `Unit`, the zipped result is also `Unit`. Its signature is:

```scala
import zio._

trait ZLogger[-Message, +Output] {
  def ++[M <: Message, O](
    that: ZLogger[M, O]
  )(implicit zippable: Zippable[Output, O]): ZLogger[M, zippable.Out]
}
```

Combining `ZLogger.default` with a custom console logger fans each event to both destinations:

```scala
import zio._

val customLogger: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](msg => println(s"CUSTOM: $msg"))

// Both loggers fire on every event; Zippable[String, Unit].Out = String
val both: ZLogger[String, String] = ZLogger.default ++ customLogger
```

#### `ZLogger#+>` — Run both loggers, keep the right output

`ZLogger#+>` runs both loggers on every event and discards this logger's output, returning only `that`'s output. Its signature is:

```scala
trait ZLogger[-Message, +Output] {
  def +>[M <: Message, O](that: ZLogger[M, O]): ZLogger[M, O]
}
```

This operator is useful when we need the side effects of the left logger but only care about the right logger's return value:

```scala
import zio._

val sideEffect: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](_ => ()) // imagine a metrics counter here

// Runs sideEffect silently; return type is ZLogger[String, String] from ZLogger.default
val rightOnly: ZLogger[String, String] = sideEffect +> ZLogger.default
```

#### `ZLogger#<+` — Run both loggers, keep the left output

`ZLogger#<+` runs both loggers on every event and discards `that`'s output, returning only this logger's output. Its signature is:

```scala
trait ZLogger[-Message, +Output] {
  def <+[M <: Message](that: ZLogger[M, Any]): ZLogger[M, Output]
}
```

We use `ZLogger#<+` when we want to attach a supplementary logger without changing the return type of the primary one:

```scala
import zio._

val metrics: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](_ => ()) // increment a counter in practice

// Keeps ZLogger.default's String output; also fires metrics as a side effect
val leftOnly: ZLogger[String, String] = ZLogger.default <+ metrics
```

### Testing

`ZLogger#test` exercises a logger without starting a ZIO runtime. It is suitable for unit-testing a logger's output formatting in plain Scala.

`ZLogger#test` calls `apply` with all contextual fields set to empty or sentinel values: `Trace.empty`, `FiberId.None`, `LogLevel.Info`, `Cause.empty`, `FiberRefs.empty`, empty spans, and empty annotations. Only the supplied `input` value participates as the message. Its signature is:

```scala
trait ZLogger[-Message, +Output] {
  final def test(input: => Message): Output
}
```

`ZLogger#test` makes it straightforward to verify that a custom formatter produces the expected output without spinning up a ZIO runtime:

```scala
import zio._

// Produce a formatted line without a live fiber
val line: String = ZLogger.default.test("hello world")
// e.g. timestamp=2026-07-15T12:00:00Z level=INFO thread=#0 message="hello world"

// Assert the formatter includes the expected keys
assert(line.contains("message=\"hello world\""))
assert(line.contains("level=INFO"))
```

:::note
Because all contextual fields are empty, `test` exercises message formatting only. It does not render span durations, source locations, or string annotations, since those fields are all empty at call time.
:::

## Subtypes / Variants

`ZLogger` has one built-in subtype provided by the `zio-test` module: `ZTestLogger`, an in-memory logger designed for test assertions.

`ZTestLogger` lives in the `zio-test` module. It is a `sealed trait` that extends `ZLogger[Message, Output]` and adds a single extra method, `logOutput`, which returns all log events captured during the test scope:

```
ZLogger[-Message, +Output]
    └── ZTestLogger[-Message, +Output]   (zio-test module)
```

Unlike a production logger, `ZTestLogger` writes to no external destination. It accumulates every log event in an internal `AtomicReference[Chunk[LogEntry]]` and exposes the captured entries as a `Chunk`. This allows precise assertions about which messages were logged, at which level, and with which spans or annotations.

The `ZTestLogger` companion provides two values. `ZTestLogger.default` is a `ZLayer[Any, Nothing, Unit]` that installs an in-memory `ZTestLogger` for the duration of a scope. `ZTestLogger.logOutput` is a `UIO[Chunk[ZTestLogger.LogEntry]]` that locates the first `ZTestLogger` in `FiberRef.currentLoggers` and returns its captured entries. When running inside `ZIOSpecDefault`, the test executor installs `ZTestLogger.default` automatically, so `ZTestLogger.logOutput` is available in any test body without an explicit `provide`:

```scala
import zio._
import zio.test.ZTestLogger

// Demonstrate ZTestLogger usage as a plain ZIO effect (in real tests this runs inside a ZIOSpecDefault body
// where ZTestLogger.default is installed automatically by the test executor)
val logDebugProgram: ZIO[Any, Nothing, Unit] = for {
  _      <- ZIO.logDebug("It's alive!")
  output <- ZTestLogger.logOutput
} yield {
  assert(output.length == 1)
  assert(output(0).message() == "It's alive!")
  assert(output(0).logLevel == LogLevel.Debug)
}

// Provide the in-memory logger layer when running outside the test executor
val logDebugRunnable: ZIO[Any, Nothing, Unit] = logDebugProgram.provide(ZTestLogger.default)
```

`ZTestLogger.LogEntry` is a `final case class` holding all eight log-event fields. Its `ZTestLogger.LogEntry#call` method replays the captured entry through any formatter, making it straightforward to assert on the full formatted output:

```scala
import zio._
import zio.test.ZTestLogger

val formattingProgram: ZIO[Any, Nothing, Unit] = for {
  _      <- ZIO.log("order shipped")
  output <- ZTestLogger.logOutput
} yield {
  val line: String = output(0).call(ZLogger.default)
  assert(line.contains("level=INFO"))
  assert(line.contains("message=\"order shipped\""))
}

val formattingRunnable: ZIO[Any, Nothing, Unit] = formattingProgram.provide(ZTestLogger.default)
```

## Comparisons

The following tables contrast `ZLogger` with similar logging abstractions from the Java and Scala ecosystems.

### `ZLogger` vs `java.util.logging.Logger` / SLF4J `Logger`

| Aspect              | `ZLogger`                                                                          | `java.util.logging.Logger` / SLF4J `Logger`                                |
|---------------------|------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Registration        | Stored in `FiberRef.currentLoggers`; scoped to a fiber hierarchy, restored on exit | Registered in a global, process-wide registry via `LogManager` or SLF4J   |
| Mutation            | Replaced by providing a new `ZLayer`; previous value is restored when scope closes | Mutated globally via `LogManager.getLogger` or provider configuration      |
| Composition         | First-class combinators: `++`, `map`, `contramap`, `filterLogLevel`                | Appender/handler chaining at framework-configuration level                 |
| Effect in log call  | `apply` is synchronous and effect-free                                             | Log calls are synchronous but may schedule asynchronous appenders           |
| Testing             | `ZTestLogger.default` layer captures entries in memory for in-test assertions      | Requires in-memory appenders or mocking frameworks                         |

### `ZLogger` vs `ZSink[R, E, String, String, Unit]`

| Aspect        | `ZLogger`                                                            | `ZSink`                                                                 |
|---------------|----------------------------------------------------------------------|-------------------------------------------------------------------------|
| Invocation    | Called synchronously by the runtime fiber loop on every log event    | Pulled as part of a `ZStream` pipeline when the stream produces elements |
| Effect system | No `ZIO` effect in `apply`; cannot `flatMap` or fork inside `apply` | Fully within the ZIO effect system; arbitrary effects are allowed        |
| Context       | Receives fiber-level metadata: spans, `FiberRefs`, trace, fiber ID   | Operates on a stream of values with no built-in fiber metadata           |
| Use case      | Structured runtime logging integrated with the `ZIO.log*` API        | Streaming data processing, aggregation, and collection                   |

### `ZLogger` vs cats-effect / Odin `Logger[F]`

| Aspect             | `ZLogger`                                                              | Odin `Logger[F]`                                                       |
|--------------------|------------------------------------------------------------------------|------------------------------------------------------------------------|
| Log call result    | `Output` — a plain value, not wrapped in an effect type               | `F[Unit]` — each call returns an effect that must be flatMapped        |
| Context passing    | Via `FiberRef.currentLoggers`, automatically propagated to child fibers | Via `IOLocal`, `Kleisli`, or an implicit `LoggingContext`              |
| Composition        | `++`, `map`, `contramap` at value level; no effect threading           | `mapK`, effect-level `flatMap` chains                                  |
| Testability        | `ZLogger#test` for pure formatting; `ZTestLogger` for integration tests | Mutable `ListLogger[F]` or similar in-memory implementations          |

## Advanced Usage

The following sections cover patterns for customizing and extending ZIO's logging infrastructure beyond the defaults.

### Building the default logging pipeline

The JVM platform wires its default console logger as a chain of three operations applied to `ZLogger.default`. Knowing the chain helps when extending or replacing it:

```scala
import zio._

object Runtime {
  // core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala
  val defaultLoggers: Set[ZLogger[String, Any]] =
    Set(ZLogger.default.map(println(_)).filterLogLevel(_ >= LogLevel.Info))
}
```

We can replicate or extend this pattern for custom pipelines: format with `ZLogger.default` (or a custom formatter), route to a sink with `ZLogger#map`, and gate by level with `ZLogger#filterLogLevel`.

### Replacing the default loggers

To swap out the platform default loggers and install a custom one, we combine `Runtime.removeDefaultLoggers` and `Runtime.addLogger` in the application's `bootstrap` layer:

```scala
import zio._

object MyApp extends ZIOAppDefault {

  val customLogger: ZLogger[String, Unit] =
    ZLogger.simple[String, Unit](msg => println(s"[CUSTOM] $msg"))

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++ Runtime.addLogger(customLogger)

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    ZIO.log("application started")
}
```

`Runtime.removeDefaultLoggers` is itself a scoped `ZLayer` that modifies `FiberRef.currentLoggers`, so the original loggers are restored if the layer's scope is closed.

### Scoped logger installation

For finer-grained control — for example, to install a logger only for the duration of a single request — we can use `ZIO.withLogger` for a delimited region or `ZIO.withLoggerScoped` for a `Scope`-based lifetime:

```scala
import zio._

val requestLogger: ZLogger[String, Unit] =
  ZLogger.simple[String, Unit](msg => println(s"[REQ] $msg"))

// Applies requestLogger only within the wrapped effect; outer loggers are unaffected
val program: ZIO[Any, Nothing, Unit] =
  ZIO.withLogger(requestLogger) {
    ZIO.log("processing request")
  }
```

`ZIO.withLogger` adds the new logger to `FiberRef.currentLoggers` for the duration of the wrapped effect and removes it when the effect completes. Child fibers forked inside the region inherit the modified logger set.

### Inspecting active loggers at runtime

We can retrieve the full set of currently active loggers via `ZIO.loggers` and react to it with `ZIO.loggersWith`:

```scala
import zio._

// Read the current logger set as an effect
val inspect: UIO[Set[ZLogger[String, Any]]] = ZIO.loggers

// Branch on the logger count
val conditional: UIO[Int] =
  ZIO.loggersWith(loggers => ZIO.succeed(loggers.size))
```

## Integration

`ZLogger` operates within a larger ecosystem of ZIO types that manage its lifecycle and ambient availability:

- **`FiberRef.currentLoggers`** — The `FiberRef[Set[ZLogger[String, Any]]]` that every fiber inherits from its parent. Modifying it with `locallyScoped` or `locallyScopedWith` is the low-level mechanism underlying `Runtime.addLogger` and `Runtime.removeDefaultLoggers`. This fiber-ref is documented in the state management reference.
- **`Runtime.addLogger`** — `def addLogger(logger: ZLogger[String, Any])(implicit trace: Trace): ZLayer[Any, Nothing, Unit]` — a `ZLayer` that adds a logger for the duration of a scope by delegating to `ZIO.withLoggerScoped`. See the [Runtime](../../core/runtime.md) reference.
- **`Runtime.removeDefaultLoggers`** — A `ZLayer` that removes all platform default loggers from `FiberRef.currentLoggers` for the duration of the scope.
- **`ZIO.withLogger`** — `def withLogger[R, E, A <: ZLogger[String, Any], B](logger: => A)(zio: => ZIO[R, E, B])(implicit tag: Tag[A], trace: Trace): ZIO[R, E, B]` — installs a logger for the lifetime of a single ZIO effect.
- **`ZIO.withLoggerScoped`** — `def withLoggerScoped[A <: ZLogger[String, Any]](logger: => A)(implicit tag: Tag[A], trace: Trace): ZIO[Scope, Nothing, Unit]` — installs a logger tied to a `Scope`.
- **`ZIO.loggers`** and **`ZIO.loggersWith`** — Read the active logger set or branch on it within a ZIO effect, without manual `FiberRef` access.
- **`ZTestLogger`** — The `zio-test` subtype that replaces the console with in-memory capture; described in the Subtypes section above.
## See Also

- **[Introduction to Logging in ZIO](index.md)** — Overview of ZIO's logging facade, log levels, log spans, and contextual annotations that feed into every `ZLogger` invocation.
- **[Runtime](../../core/runtime.md)** — Documents `Runtime.addLogger`, `Runtime.removeDefaultLoggers`, and the fiber-ref plumbing that makes logger installation scoped.
- **[Tutorial: How to Enable Logging in a ZIO Application](../../../guides/tutorials/enable-logging-in-a-zio-application.md)** — Step-by-step guide to using `ZIO.log*` in a real application, configuring log levels, spans, and annotations.
- **[Tutorial: How to Create a Custom Logger for a ZIO Application](../../../guides/tutorials/create-custom-logger-for-a-zio-application.md)** — Shows how to implement a `ZLogger` from scratch and wire it into the ZIO runtime with `Runtime.addLogger`.
