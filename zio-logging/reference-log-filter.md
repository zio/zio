# LogFilter and LogGroup Reference

> `LogFilter` decides whether a log event is written. `LogGroup` extracts one value from a log event, such as its level or logger name, and a filter tests that value with a predicate. `LoggerNameExtractor` is a small helper that finds the logger name in a log event. This page lists the members of all three types. For how filters are set up in a logger configuration, see [Log Filter](./log-filter.md).

## LogFilter Members

`LogFilter` is a sealed trait, so you create filters with the companion functions and combinators, not by extending the trait. A filter has two abstract parts: a group that extracts a value, and a predicate that tests the value. The trait is declared as follows:

```scala
sealed trait LogFilter[-Message] {
  type Value
  def group: LogGroup[Message, Value]
  def predicate(value: Value): Boolean
}
```

The instance methods combine filters or change their input type. In the table, `LogFilter#and` means the `and` method called on a filter value, as in `warnings.and(failures)`; it is not a member of the `LogFilter` companion object.

| Instance method       | Signature                                                                                                                                                                                             | Description                                                                                                     |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `LogFilter#apply`     | `final def apply(trace: Trace, fiberId: FiberId, logLevel: LogLevel, message: () => Message, cause: Cause[Any], context: FiberRefs, spans: List[LogSpan], annotations: Map[String, String]): Boolean` | Runs the group on the event, then the predicate on the result. Returns `true` when the event passes.            |
| `LogFilter#and`       | `final def and[M <: Message](other: LogFilter[M]): LogFilter[M]`                                                                                                                                      | Accepts an event only when both filters accept it. The alphanumeric form of `&&`.                               |
| `LogFilter#&&`        | `final def &&[M <: Message](other: LogFilter[M]): LogFilter[M]`                                                                                                                                       | Same as `LogFilter#and`.                                                                                        |
| `LogFilter#or`        | `final def or[M <: Message](other: LogFilter[M]): LogFilter[M]`                                                                                                                                       | Accepts an event when either filter accepts it. The alphanumeric form of `\|\|`.                                |
| `LogFilter#\|\|`      | `final def \|\|[M <: Message](other: LogFilter[M]): LogFilter[M]`                                                                                                                                     | Same as `LogFilter#or`.                                                                                         |
| `LogFilter#not`       | `final def not: LogFilter[Message]`                                                                                                                                                                   | Negates the result. The alphanumeric form of `unary_!`.                                                         |
| `LogFilter#unary_!`   | `final def unary_! : LogFilter[Message]`                                                                                                                                                              | Same as `LogFilter#not`. Written as a prefix, as in `!LogFilter.causeNonEmpty`.                                 |
| `LogFilter#cached`    | `final def cached: LogFilter[Message]`                                                                                                                                                                | Remembers the predicate result for each group value. The cache has no size limit and never evicts entries.      |
| `LogFilter#contramap` | `final def contramap[M](f: M => Message): LogFilter[M]`                                                                                                                                               | Returns a filter for a different message type. The function `f` runs before the group.                          |
| `LogFilter#filter`    | `final def filter[M <: Message, O](logger: zio.ZLogger[M, O]): zio.ZLogger[M, Option[O]]`                                                                                                             | Wraps a logger so that it runs only for accepted events. The wrapped logger returns `None` for rejected events. |

Calling `LogFilter#cached` on an already cached filter creates a new, empty cache around the same inner filter. Use `LogFilter#cached` when the group is expensive to compute, or when many events share the same group value, such as the same logger name.

The next example combines three filters. It accepts events at WARN or above, unless the event has a cause:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val quietWarnings: LogFilter[Any] = LogFilter.logLevel(LogLevel.Warning) && !LogFilter.causeNonEmpty
```

`LogFilter#contramap` adapts a filter written for one message type to a logger whose messages have another type. The next example reuses a filter on the message text for a logger of audit events, by reading the text out of each event:

```scala
import zio.logging.{ LogFilter, LogGroup }

case class AuditEvent(action: String, user: String)

val messageText: LogGroup[String, String] =
  LogGroup[String, String]((_, _, _, message, _, _, _, _) => message())

val notHealthCheck: LogFilter[String] =
  LogFilter[String, String](messageText, (text: String) => !text.startsWith("GET /health"))

val auditFilter: LogFilter[AuditEvent] = notHealthCheck.contramap[AuditEvent](_.action)
```

To run a filter in front of a logger, call `LogFilter#filter` with the logger, or call `logger.filter(logFilter)` on the logger. Both return a `FilteredLogger`. The second form is the `ZLoggerOps#filter` syntax from the `zio.logging` package and needs `import zio.logging._`, as shown on [Log Filter](log-filter.md#apply-a-filter-to-a-logger). The layers that install loggers, and `FilteredLogger` itself, are described in [Logger Layers](reference-logger-layers.md).

## Companion Constructors

The companion object creates filters from groups and predicates, and provides two ready-made filters.

| Member                    | Signature                                                                         | Description                                                                                  |
| ------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `LogFilter.apply`         | `def apply[M, V](group0: LogGroup[M, V], predicate0: V => Boolean): LogFilter[M]` | Creates a filter that accepts an event when `predicate0` returns `true` for the group value. |
| `LogFilter.apply`         | `def apply[M](group0: LogGroup[M, Boolean]): LogFilter[M]`                        | Creates a filter that accepts an event when the group value is `true`.                       |
| `LogFilter.acceptAll`     | `val acceptAll: LogFilter[Any]`                                                   | Accepts every event.                                                                         |
| `LogFilter.causeNonEmpty` | `val causeNonEmpty: LogFilter[Any]`                                               | Accepts an event only when its cause is not empty.                                           |

The next example builds a filter from a group and a predicate. It accepts only ERROR and FATAL events:

```scala
import zio.LogLevel
import zio.logging.{ LogFilter, LogGroup }

val errorsOnly: LogFilter[Any] = LogFilter(LogGroup.logLevel, (level: LogLevel) => level >= LogLevel.Error)
```

## Level Filters

The level filter functions accept events by log level, or by log level for each logger name. The rules for the name mappings are shown in [Log Filter](./log-filter.md).

| Member                      | Signature                                                                                                                                    | Description                                                                                                                                                        |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `LogFilter.logLevel`        | `def logLevel(predicate: LogLevel => Boolean): LogFilter[Any]`                                                                               | Accepts events whose level passes `predicate`.                                                                                                                     |
| `LogFilter.logLevel`        | `def logLevel(rootLevel: LogLevel): LogFilter[Any]`                                                                                          | Accepts events at `rootLevel` or above.                                                                                                                            |
| `LogFilter.logLevelByGroup` | `def logLevelByGroup[M, A](rootLevel: LogLevel, group: LogGroup[M, A], matcher: (A, A) => Boolean, groupings: (A, LogLevel)*): LogFilter[M]` | Uses the level of the first grouping that `matcher` accepts. When none matches, uses `rootLevel`.                                                                  |
| `LogFilter.logLevelByGroup` | `def logLevelByGroup[M](rootLevel: LogLevel, group: LogGroup[M, String], mappings: (String, LogLevel)*): LogFilter[M]`                       | Matches the dot-separated name against the mappings, most specific mapping first, with the glob rules in [Log Filter](log-filter.md#how-logger-names-are-matched). |
| `LogFilter.logLevelByGroup` | `def logLevelByGroup[M](group: LogGroup[M, String], config: LogLevelByNameConfig): LogFilter[M]`                                             | Same as the previous overload, with the root level and mappings read from the config argument.                                                                     |
| `LogFilter.logLevelByName`  | `def logLevelByName[M](rootLevel: LogLevel, mappings: (String, LogLevel)*): LogFilter[M]`                                                    | Same as `LogFilter.logLevelByGroup` with `LogGroup.loggerName`.                                                                                                    |
| `LogFilter.logLevelByName`  | `def logLevelByName[M](config: LogLevelByNameConfig): LogFilter[M]`                                                                          | Same as `LogFilter.logLevelByName` with the root level and mappings read from the config argument.                                                                 |

The example below sets DEBUG as the root level, and sets INFO for the `io.netty` logger and everything under it:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val nettyQuieter: LogFilter[Any] = LogFilter.logLevelByName[Any](LogLevel.Debug, "io.netty" -> LogLevel.Info)
```

The generic `LogFilter.logLevelByGroup` overload takes any group and your own `matcher`. Unlike the `String` overloads, it does not sort the groupings: it tries them in the order given and uses the first one for which `matcher(groupValue, grouping)` returns `true`. The next example matches logger names by plain string prefix instead of by dot-separated segments, so `com.acme.legacy` also covers `com.acme.legacyBilling`:

```scala
import zio.LogLevel
import zio.logging.{ LogFilter, LogGroup }

val byPrefix: LogFilter[Any] =
  LogFilter.logLevelByGroup[Any, String](
    LogLevel.Info,
    LogGroup.loggerName,
    (loggerName: String, prefix: String) => loggerName.startsWith(prefix),
    "com.acme.legacy" -> LogLevel.Warning,
    "com.acme"        -> LogLevel.Debug
  )
```

Because the order matters here, the more specific prefix comes first.

## LogLevelByNameConfig

`LogLevelByNameConfig` holds the root level and the name mappings for a level filter. It is the value that `LogFilter.logLevelByName` accepts when you build the filter from configuration. The class is declared as follows:

```scala
final case class LogLevelByNameConfig(rootLevel: LogLevel, mappings: Map[String, LogLevel])
```

The class has helper methods that return a new config. The companion object provides the defaults and the parser.

| Member                               | Signature                                                                                   | Description                                                                                                                                             |
| ------------------------------------ | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LogLevelByNameConfig#withRootLevel` | `def withRootLevel(rootLevel: LogLevel): LogLevelByNameConfig`                              | Returns a copy with a new root level.                                                                                                                   |
| `LogLevelByNameConfig#withMapping`   | `def withMapping(name: String, level: LogLevel): LogLevelByNameConfig`                      | Returns a copy with one more mapping, or with the level of an existing mapping replaced.                                                                |
| `LogLevelByNameConfig#withMappings`  | `def withMappings(mappings: Map[String, LogLevel]): LogLevelByNameConfig`                   | Returns a copy with the given mappings, replacing all current mappings.                                                                                 |
| `LogLevelByNameConfig#toFilter`      | `def toFilter[M]: LogFilter[M]`                                                             | Builds the filter from this config. Same as `LogFilter.logLevelByName` with this config.                                                                |
| `LogLevelByNameConfig.apply`         | `def apply(rootLevel: LogLevel, mappings: (String, LogLevel)*): LogLevelByNameConfig`       | Companion constructor that takes the mappings as pairs.                                                                                                 |
| `LogLevelByNameConfig.default`       | `val default: LogLevelByNameConfig`                                                         | Root level INFO, with no mappings.                                                                                                                      |
| `LogLevelByNameConfig.config`        | `val config: Config[LogLevelByNameConfig]`                                                  | Reads `rootLevel` and `mappings`. The keys are described in [Configuration Keys Reference](./reference-configuration.md).                               |
| `LogLevelByNameConfig.load`          | `def load(configPath: NonEmptyChunk[String]): ZIO[Any, Config.Error, LogLevelByNameConfig]` | Reads the config from the given path in the configuration source.                                                                                       |
| `LogLevelByNameConfig.equal`         | `implicit val equal: Equal[LogLevelByNameConfig]`                                           | Compares two configs by their root level and mappings. `ReconfigurableLogger` uses it through `ConsoleLoggerConfig.equal` and `FileLoggerConfig.equal`. |

## Filter Equality

`LogFilter.equal` is an implicit `zio.prelude.Equal[LogFilter[_]]`. `ReconfigurableLogger` uses `Equal` instances to decide whether a newly loaded configuration differs from the current one, and builds a new logger only when it does; see [Reconfigurable Logger](reconfigurable-logger.md). Two filters are equal when they have the same structure and equal parts. Filters made from functions are equal only when they hold the same function instance. Filters built from a `LogLevelByNameConfig`, through `LogFilter.logLevelByName(config)` or `LogFilter.logLevelByGroup(group, config)`, compare only their config, so two such filters with different groups and the same config are equal.

## LogGroup

`LogGroup` extracts one value from a log event. It is the first half of a filter: `LogFilter.apply` and `LogFilter.logLevelByGroup` take a group, and the filter tests the value it extracts. There is no API that turns a `LogGroup` into a `LogFormat`; to write a value into the log line, use `LogFormat.make`. The trait is declared as follows:

```scala
sealed trait LogGroup[-Message, Out] {
  def apply(
    trace: Trace,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    cause: Cause[Any],
    context: FiberRefs,
    spans: List[LogSpan],
    annotations: Map[String, String]
  ): Out
}
```

The companion object provides the ready-made groups and the constructor that accepts a function.

| Member                             | Signature                                                                                                                                  | Description                                                                                                   |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `LogGroup.apply`                   | `def apply[M, O](fn: (Trace, FiberId, LogLevel, () => M, Cause[Any], FiberRefs, List[LogSpan], Map[String, String]) => O): LogGroup[M, O]` | Creates a group from a function over the event parts.                                                         |
| `LogGroup.cause`                   | `val cause: LogGroup[Any, Cause[Any]]`                                                                                                     | Extracts the cause of the event.                                                                              |
| `LogGroup.constant`                | `def constant[O](value: O): LogGroup[Any, O]`                                                                                              | Always returns `value`.                                                                                       |
| `LogGroup.fromLoggerNameExtractor` | `def fromLoggerNameExtractor(loggerNameExtractor: LoggerNameExtractor, loggerNameDefault: String = "zio-logger"): LogGroup[Any, String]`   | Extracts the logger name with `loggerNameExtractor`, or uses `loggerNameDefault` when none is found.          |
| `LogGroup.logLevel`                | `val logLevel: LogGroup[Any, LogLevel]`                                                                                                    | Extracts the level of the event.                                                                              |
| `LogGroup.loggerName`              | `val loggerName: LogGroup[Any, String]`                                                                                                    | Extracts the logger name from the logger name annotation, or from the `Trace` when the annotation is missing. |
| `LogGroup.loggerNameAndLevel`      | `val loggerNameAndLevel: LogGroup[Any, (String, LogLevel)]`                                                                                | Extracts the logger name and the level as a pair.                                                             |

`LogGroup.apply` builds a group from a function over the parts of the event. The next example reads a `tenant` string annotation, and a filter built from it lets every line of one tenant through while the root level is WARN for the rest:

```scala
import zio.LogLevel
import zio.logging.{ LogFilter, LogGroup }

val tenant: LogGroup[Any, String] =
  LogGroup[Any, String]((_, _, _, _, _, _, _, annotations) => annotations.getOrElse("tenant", ""))

val tenantAndLevel: LogGroup[Any, (String, LogLevel)] = tenant ++ LogGroup.logLevel

val debugOneTenant: LogFilter[Any] =
  LogFilter[Any, (String, LogLevel)](
    tenantAndLevel,
    (value: (String, LogLevel)) => value._1 == "acme-beta" || value._2 >= LogLevel.Warning
  )
```

The instance methods change the output of a group or combine two groups. The `++` method is an alias for `LogGroup#zip`.

| Instance method      | Signature                                                                                                                       | Description                                                                         |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `LogGroup#++`        | `final def ++[M <: Message, O, Out2](other: LogGroup[M, O])(implicit zippable: Zippable.Out[Out, O, Out2]): LogGroup[M, Out2]`  | Same as `LogGroup#zip`.                                                             |
| `LogGroup#map`       | `final def map[O](f: Out => O): LogGroup[Message, O]`                                                                           | Applies `f` to the output of the group.                                             |
| `LogGroup#contramap` | `final def contramap[M](f: M => Message): LogGroup[M, Out]`                                                                     | Applies `f` to the message before the group runs.                                   |
| `LogGroup#zip`       | `final def zip[M <: Message, O, Out2](other: LogGroup[M, O])(implicit zippable: Zippable.Out[Out, O, Out2]): LogGroup[M, Out2]` | Returns both outputs as one value. The `Zippable` instance decides the tuple shape. |
| `LogGroup#zipWith`   | `final def zipWith[M <: Message, O, Out2](other: LogGroup[M, O])(f: (Out, O) => Out2): LogGroup[M, Out2]`                       | Combines both outputs with `f`.                                                     |

The next example builds a group that returns the logger name and the level as one string:

```scala
import zio.logging.LogGroup

val nameAndLevel: LogGroup[Any, String] =
  LogGroup.loggerName.zipWith(LogGroup.logLevel)((name, level) => s"$name/${level.label}")
```

## LoggerNameExtractor

`LoggerNameExtractor` finds the logger name from the parts of a log event. It returns `None` when it has no name, so you can chain extractors with `||` to try each one in turn. The trait is declared as follows:

```scala
sealed trait LoggerNameExtractor {
  def apply(trace: Trace, context: FiberRefs, annotations: Map[String, String]): Option[String]
}
```

The instance methods chain extractors and convert them into a format or a group.

| Instance method                   | Signature                                                                     | Description                                                                                         |
| --------------------------------- | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `LoggerNameExtractor#\|\|`        | `final def \|\|(other: LoggerNameExtractor): LoggerNameExtractor`             | Uses this extractor's name, or the name of `other` when this one returns `None`.                    |
| `LoggerNameExtractor#or`          | `final def or(other: LoggerNameExtractor): LoggerNameExtractor`               | Same as `\|\|`, with an alphanumeric name.                                                          |
| `LoggerNameExtractor#toLogFormat` | `final def toLogFormat(default: String = "zio-logger"): LogFormat`            | Converts the extractor into a `LogFormat` that writes the name, using `default` when none is found. |
| `LoggerNameExtractor#toLogGroup`  | `final def toLogGroup(default: String = "zio-logger"): LogGroup[Any, String]` | Converts the extractor into a `LogGroup`, using `default` when none is found.                       |

The companion object provides the constructors and the extractors that the library uses:

| Member                                            | Signature                                                                                      | Description                                                                                                                                                              |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `LoggerNameExtractor.make`                        | `def make(fn: (Trace, FiberRefs, Map[String, String]) => Option[String]): LoggerNameExtractor` | Creates an extractor from a function.                                                                                                                                    |
| `LoggerNameExtractor.trace`                       | `val trace: LoggerNameExtractor`                                                               | Takes the name from the `Trace`, without the method name. For `example.LivePingService.ping(PingService.scala:22)` the name is `example.LivePingService`.                |
| `LoggerNameExtractor.annotation`                  | `def annotation(name: String): LoggerNameExtractor`                                            | Takes the name from the string annotation `name`.                                                                                                                        |
| `LoggerNameExtractor.annotationOrTrace`           | `def annotationOrTrace(name: String): LoggerNameExtractor`                                     | Takes the name from the annotation `name`, or from the `Trace` when the annotation is missing.                                                                           |
| `LoggerNameExtractor.loggerNameAnnotationOrTrace` | `val loggerNameAnnotationOrTrace: LoggerNameExtractor`                                         | Same as `LoggerNameExtractor.annotationOrTrace` with the key `zio.logging.loggerNameAnnotationKey`. This is the default for `LogGroup.loggerName` and the `%name` token. |

The next example takes the name from a `component` annotation, and falls back to the trace:

```scala
import zio.logging.LoggerNameExtractor

val componentOrTrace: LoggerNameExtractor = LoggerNameExtractor.annotation("component") || LoggerNameExtractor.trace
```

`LoggerNameExtractor.make` builds an extractor from a function, for names that no predefined extractor gives. The next example puts every `component` annotation under one `app` namespace, so that a single mapping such as `app` -> INFO covers all components, and falls back to the trace:

```scala
import zio.LogLevel
import zio.logging.{ LogFilter, LoggerNameExtractor }

val appComponent: LoggerNameExtractor =
  LoggerNameExtractor.make((_, _, annotations) => annotations.get("component").map(component => s"app.$component"))

val appFilter: LogFilter[Any] =
  LogFilter.logLevelByGroup[Any](
    LogLevel.Debug,
    (appComponent || LoggerNameExtractor.trace).toLogGroup(),
    "app" -> LogLevel.Info
  )
```

For a logger name in a format, see `LogFormat.loggerName` in [LogFormat Reference](./reference-log-format.md#logger-name).
