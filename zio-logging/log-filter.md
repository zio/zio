# Log Filter

> A `LogFilter` represents function/conditions for log filtering.

The following filter sets `Debug` as the root level and overrides the level for specific logger names:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val filter: LogFilter[Any] = LogFilter.logLevelByName[Any](
  LogLevel.Debug,
  "io.netty"              -> LogLevel.Info,
  "io.grpc.netty"         -> LogLevel.Info,
  "org.my.**.ServiceX"    -> LogLevel.Trace, // glob-like (any paths) filter
  "org.my.X*Layers"       -> LogLevel.Info   // glob-like (single or partial path) filter
)
```

This filter uses the `Debug` log level for everything except for log events with the logger name
prefixed by either `List("io", "netty")` or `List("io", "grpc", "netty")` or `List("org", "my", "**", "ServiceX")` or `List("org", "my", "X*Layers")`.
The logger name is extracted from log annotation or `zio.Trace`.

`LogFilter#filter` returns a version of `zio.ZLogger` that only logs messages when this filter is satisfied.

## Configuration

The configuration for the filter (`zio.logging.LogFilter.LogLevelByNameConfig`) has the following structure. In console and file logger configurations, it is set under the `filter` key:

```
{
    # LogLevel values: ALL, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, OFF

    # root LogLevel, default value: INFO
    rootLevel = DEBUG

    # LogLevel configurations for specific logger names, or prefixes, default value: empty
    mappings {
      "io.netty" = "INFO"
      "io.grpc.netty" = "INFO"
    }
}
```

The following config value is equivalent to the filter configuration above:

```scala
import zio.LogLevel
import zio.logging.LogFilter

val nettyConfig: LogFilter.LogLevelByNameConfig =
  LogFilter.LogLevelByNameConfig(LogLevel.Debug, Map("io.netty" -> LogLevel.Info, "io.grpc.netty" -> LogLevel.Info))

val configuredFilter: LogFilter[Any] = LogFilter.logLevelByName[Any](nettyConfig)
```
