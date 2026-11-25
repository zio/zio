# Log Filter

> A `LogFilter` decides whether a log event is written. Every console and file logger has one, and the usual filter sets a minimum level per logger name: one root level for everything, and other levels for chosen packages or classes. This lets you keep your own code at `Debug` while a chatty library such as Netty only writes `Info` and above. This page explains how such a filter matches logger names. Every `LogFilter` member is listed in [LogFilter and LogGroup Reference](reference-log-filter.md).

The following filter sets `Debug` as the root level and overrides the level for specific logger names:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val filter: LogFilter[Any] = LogFilter.logLevelByName[Any](
  LogLevel.Debug,
  "io.netty"              -> LogLevel.Info,
  "io.grpc.netty"         -> LogLevel.Info,
  "org.my.**.ServiceX"    -> LogLevel.Trace, // any number of segments between org.my and ServiceX
  "org.my.X*Layers"       -> LogLevel.Info   // one segment such as XLayers or XServiceLayers
)
```

This filter uses the `Debug` level for every logger except those whose name matches one of the mappings. A mapping covers the named package or class and everything below it, so `io.netty` also sets `Info` for `io.netty.channel.nio.NioEventLoop`. A level means "this level and above": for `io.netty`, an `Info` line is written and a `Debug` line is dropped.

The logger name comes from the `logger_name` annotation, which the `zio.logging.loggerName` aspect sets. When that annotation is missing, the name comes from the `zio.Trace` of the log call, which gives the enclosing class, such as `example.LivePingService`.

## How Logger Names Are Matched

The filter splits the logger name and each mapping at the dots into segments. A mapping matches when its segments match the first segments of the logger name, one by one; the logger name may have more segments after them. Each segment of a mapping takes one of these forms:

| Mapping segment                      | Matches                                         | Example                                                                           |
| ------------------------------------ | ----------------------------------------------- | --------------------------------------------------------------------------------- |
| A literal, such as `netty`           | Exactly that segment                            | `io.netty` matches `io.netty` and `io.netty.channel.Foo`, not `io.nettyx.Foo`     |
| `*`                                  | Exactly one segment, whatever its text          | `a.*.c` matches `a.b.c.Exec` and `a.b2.c.Exec`, not `a.c.Exec` or `a.b.b2.c.Exec` |
| `**`                                 | Any number of segments, including none          | `a.**.c` matches `a.c.Exec`, `a.b.c.Exec` and `a.b.b2.c.Exec`                     |
| `X*Y`, such as `*Impl` or `X*Layers` | One segment that contains `X` and ends with `Y` | `a.**.*y` matches `a.b.xy.Exec`, not `a.b.xyz.Exec`                               |

:::caution[Partial wildcards inside a segment]
For a segment with a `*` inside it, the filter looks for the pieces of text between the stars from left to right, each at its first occurrence, and the segment must end right after the last piece. This differs from a usual glob in three ways:

- A `*` at the end of a segment does not match a longer name. `org.my.Serv*` does not match `org.my.ServiceImpl`; it matches only segments that end in `Serv`, such as `Serv` or `MyServ`.
- A suffix is found at its first occurrence, not at the end. `*Impl` matches `ServiceImpl`, but not `ImplServiceImpl`.
- The text before the first `*` can appear anywhere in the segment. `X*Layers` also matches `MyXLayers`.

When you need a predictable match, use a whole-segment `*` or `**`, or write the full name.
:::

## Which Mapping Wins

A logger name often matches more than one mapping, for example a package and a class inside it. Before matching, the filter sorts the mappings from most specific to least specific. For each event, it uses the first mapping that matches the logger name, or the root level when none matches. The sort follows these rules:

- A mapping comes before every shorter mapping that is a prefix of it: `a.b.c` before `a.b` before `a`.
- At the first segment where two mappings differ, a literal segment comes before `*`, and `*` comes before `**`.
- When code maps the same name twice, the lower level comes first. A configuration file cannot map a name twice.

The order of the mappings in your code or configuration file does not matter. The following filter has a package mapping, a more specific sub-package mapping, and a wildcard mapping:

```scala
import zio.{ Cause, FiberId, FiberRefs, Trace }

val serviceLevels: LogFilter[Any] = LogFilter.logLevelByName[Any](
  LogLevel.Info,
  "org.my"         -> LogLevel.Warning,
  "org.my.service" -> LogLevel.Debug,
  "org.*.Repo"     -> LogLevel.Error
)

def accepts(loggerName: String, level: LogLevel): Boolean =
  serviceLevels(
    Trace.empty,
    FiberId.None,
    level,
    () => "",
    Cause.empty,
    FiberRefs.empty,
    Nil,
    Map(zio.logging.loggerNameAnnotationKey -> loggerName)
  )
```

The class under `org.my.service` gets `Debug`. The logger `org.my.Repo` gets `Warning` from `org.my`, not `Error` from `org.*.Repo`: at the second segment, the literal `my` sorts before `*`, even though `org.*.Repo` is longer. A logger in another package, such as `org.other.Repo`, gets `Error`:

```scala
accepts("org.my.service.UserService", LogLevel.Debug)
// res0: Boolean = true
accepts("org.my.Repo", LogLevel.Warning)
// res1: Boolean = true
accepts("org.other.Repo", LogLevel.Warning)
// res2: Boolean = false
```

## Configuration

The configuration for the filter (`zio.logging.LogFilter.LogLevelByNameConfig`) has the following structure. In console and file logger configurations, it is set under the `filter` key, so with the default `logger` path the full keys are `logger.filter.rootLevel` and `logger.filter.mappings`:

```hocon
logger {
  filter {
    # LogLevel values: ALL, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, OFF

    # root LogLevel, default value: INFO
    rootLevel = DEBUG

    # LogLevel configurations for specific logger names or glob patterns, default value: empty
    mappings {
      "io.netty" = "INFO"
      "io.grpc.netty" = "INFO"
      "org.my.**.ServiceX" = "TRACE"
      "org.my.X*Layers" = "INFO"
    }
  }
}
```

Quote every key under `mappings`. HOCON reads an unquoted key with dots as a path, so `io.netty = INFO` becomes an object `io` that holds a key `netty`, not a mapping named `io.netty`. An unquoted key also cannot contain `*`. Level names are not case-sensitive.

The following config value is equivalent to the first two mappings of the configuration above:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val nettyConfig: LogFilter.LogLevelByNameConfig =
  LogFilter.LogLevelByNameConfig(LogLevel.Debug, Map("io.netty" -> LogLevel.Info, "io.grpc.netty" -> LogLevel.Info))

val configuredFilter: LogFilter[Any] = LogFilter.logLevelByName[Any](nettyConfig)
```

To read the filter from a configuration file at startup, see [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md).

## Apply a Filter to a Logger

The console and file logger layers apply the filter from their configuration for you. When you build a `ZLogger` yourself, for example from a `LogFormat` or for a custom backend, you apply the filter yourself. `LogFilter#filter` wraps a logger so that it runs only for accepted events, and the `ZLoggerOps#filter` syntax, written `logger.filter(logFilter)` after `import zio.logging._`, does the same with the arguments the other way round. Both return a `FilteredLogger`, whose output is `None` for a rejected event:

```scala
import zio.{ LogLevel, Runtime, ZLayer, ZLogger }
import zio.logging._

val nettyQuiet: LogFilter[String] =
  LogFilter.logLevelByName[String](LogLevel.Debug, "io.netty" -> LogLevel.Info)

val lineLogger: ZLogger[String, String] = LogFormat.colored.toLogger

val filteredByFilter: ZLogger[String, Option[String]] = nettyQuiet.filter(lineLogger)
val filteredBySyntax: ZLogger[String, Option[String]] = lineLogger.filter(nettyQuiet)

val printing: ZLogger[String, Unit] = filteredBySyntax.map(_.foreach(line => println(line)))

val printingLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> Runtime.addLogger(printing)
```

For the ready-made layers and constructors that install a logger, see [Logger Layers](reference-logger-layers.md).
