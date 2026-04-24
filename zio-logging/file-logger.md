# File Logger

> logger layer with configuration from config provider:

logger layer with configuration from config provider:

```scala

val configProvider: ConfigProvider = ???

val logger = Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> fileLogger()
```

logger layer with given configuration:

```scala

val config: FileLoggerConfig = ???

val logger = Runtime.removeDefaultLoggers >>> fileLogger(config)
```

there are other versions of file loggers:
* `zio.logging.fileJsonLogger` - output in json format
* file async:
    * `zio.logging.fileAsynLogger` - output in string format
    * `zio.logging.fileAsyncJsonLogger` - output in json format

## Configuration

the configuration for file logger (`zio.logging.FileLoggerConfig`) has the following configuration structure:

```
logger {
  # log format, default value: LogFormat.default
  format = "%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}"
  
  # URI to file
  path = "file:///tmp/console_app.log"
    
  # charset configuration, default value: UTF-8
  charset = "UTF-8"

  # auto flush batch size, default value: 1
  autoFlushBatchSize = 1

  # if defined, buffered writer is used, with given buffer size
  # bufferedIOSize = 8192
  
  # if defined, file log rolling policy is used
  rollingPolicy {
    type = TimeBasedRollingPolicy # time based file rolling policy based on date - currently only this one is supported
  }
  
  # log filter
  filter {
    # see filter configuration
    rootLevel = INFO
  }
}
```

see also [log format configuration](formatting-log-records.md#log-format-configuration) and [filter configuration](log-filter.md#configuration)

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples)

### File Logger 

[//]: # (TODO: make snippet type-checked using mdoc)

```scala
package zio.logging.example

object FileApp extends ZIOAppDefault {

  val configString: String =
    s"""
       |logger {
       |  format = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause"
       |  path = "file:///tmp/file_app.log"
       |  rollingPolicy {
       |    type = TimeBasedRollingPolicy
       |  }
       |}
       |""".stripMargin

  val configProvider: ConfigProvider = TypesafeConfigProvider.fromHoconString(configString)

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> fileLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success
}
```

Expected file name: `file_app-2023-03-05.log`

Expected file content:

```
2023-03-05T12:37:31+0100 INFO    [zio-fiber-4] zio.logging.example.FileApp:40 Start
2023-03-05T12:37:31+0100 ERROR   [zio-fiber-0] zio-logger:  Exception in thread "zio-fiber-4" java.lang.String: FAILURE
        at zio.logging.example.FileApp.run(FileApp.scala:41)
```
