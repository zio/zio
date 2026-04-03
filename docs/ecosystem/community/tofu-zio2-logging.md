---
id: tofu-zio2-logging
title: "Tofu ZIO 2 Logging"
---

[Tofu](https://docs.tofu.tf/) is a functional toolkit modules providing a comprehensive set of tools adressing
real-world problems through the prism of the tagless-final approach and functional programming in general.
Tofu consists of several independent modules, one of them is [Tofu Logging](https://docs.tofu.tf/docs/tofu.logging.home)
, which provides first-class [ZIO support](https://docs.tofu.tf/docs/tofu.logging.recipes.zio2).

Key features of Tofu Logging:

- **100% structured logging**: you can easily log json-s with nested objects, arrays, numeric and boolean fields.
- logging context: implemented on top of `FiberRef`.
- built upon [Logback](https://logback.qos.ch/),
  supports [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder).

## Installation

Add the following lines in your `build.sbt` file:

```scala
libraryDependencies += "tf.tofu" %% "tofu-zio2-logging" % "version"
```

For `Loggable` derivation (see below):

```scala
libraryDependencies += "tf.tofu" %% "tofu-logging-derivation" % "version"
```

And choose a logback layout: Tofu's own implementation or logstash-encoder. See
the [logback configuration](#logback-layout) section.

```scala
libraryDependencies += "tf.tofu" %% "tofu-logging-logstash-logback" % "version"
// OR:
libraryDependencies += "tf.tofu" %% "tofu-logging-layout" % "version"
```

## Quick start

To use Tofu with ZIO logging facade just add `TofuZLogger` to your app runtime:

```scala
import tofu.logging.zlogs._
import zio._

object Main extends ZIOAppDefault {
  val program: UIO[Unit] = ZIO.log("Hello, ZIO logging!")

  override def run = {
    program.logSpan("full_app") @@ ZIOAspect.annotated("foo", "bar")
  }.provide(
    Runtime.removeDefaultLoggers,
    TofuZLogger.addToRuntime
  )
}
```

The log message will be:

```json
{
  "level": "INFO",
  "logger_name": "my.package.Main",
  "message": "Hello, ZIO logging!",
  "zSpans": {
    "full_app": 534
  },
  "zAnnotations": {
    "foo": "bar"
  }
}
```

* __logger_name__ is parsed from `zio.Trace` which contains the location where log method is called
* all `zio.LogSpan` are collected in the json object named __zSpans__
* all `zio.LogAnnotation` are collected in the json object named __zAnnotations__ (to avoid conflicts with Tofu
  annotations)

### ZLogAnnotation and TofuDefaultContext

`ZLogAnnotation` allows you to store typed, structured data on the default logging context (implemented on `FiberRef`).
It also solves another problem:
having a list of annotations in front of your eyes, you can easily make sure that the same names are not assigned to
different values (which can lead to an invalid json and corrupt your structured logs).
That's why we recommend to group all your log annotations in one place.

`TofuDefaultContext` is a service which allows to look up an element from the context added via `ZLogAnnotation`:

```scala
trait TofuDefaultContext {
  def getValue[A](key: LogAnnotation[A]): UIO[Option[A]]
}
```

It has two implementations:

- `TofuDefaultContext.layerZioContextOff: ULayer[TofuDefaultContext]`
- `TofuDefaultContext.layerZioContextOn: ULayer[TofuDefaultContext]`

It doesn't matter which one to use with ZIO Logging facade. The difference will be only when working
with Tofu's own loggers and using `TofuDefaultContext` as
a [ContextProvider](https://docs.tofu.tf/docs/tofu.logging.recipes.zio2#contextprovider)
(this feature is Tofu-specific, and we won't cover it here).

```scala
val httpCode: ZLogAnnotation[Int] = ZLogAnnotation.make("httpCode")

val program: UIO[Unit] = {
  for {
    _ <- ZIO.log("Hello, ZIO logging!")
    maybeCode <- ZIO.serviceWithZIO[TofuDefaultContext](_.getValue(httpCode)) // Some(204)
  } yield ()
}.provide(TofuDefaultContext.layerZioContextOn) @@ httpCode(204) @@ ZLogAnnotation.loggerName("MyLogger")
```

will produce:

```json
{
  "level": "INFO",
  "logger_name": "MyLogger",
  "message": "Hello, ZIO logging!",
  "httpCode": 204
}
```

You can change the logger name via `ZLogAnnotation.loggerName`.

`ZLogAnnotation.make[A](name: String)` implicitly requires a `Loggable[A]` instance.

### Loggable

`Loggable[A]` is a typeclass that describes how a value of some type can be logged.
Given an instance of `Loggable` for a type, a value of the type can be converted into the final internal representation
called `LoggedValue` and thus logged in a way that you provided.
There are multiple predefined ways to create an instance of `Loggable`, many of them can be found
in `tofu.logging.Loggable` object:

- provided instances for all primitive types, as well as stdlib's collections and collections from Cats
- `Loggable.empty` for no-op logging of value
- `Loggable.either` for logging either of A and B
- provided `java.time.*` instances

Of course, you can describe your `Loggable` instance yourself:

- by extending trait `DictLoggable` for multi-field objects
- using `Loggable[A]#contramap[B](f: B => A)` method
- using configurable auto derivation

Tofu has a macros that allows you to easily derive instances of `Loggable[YourClass]` for case classes or ADTs.
In additional, there are several annotations to configure generation of `Loggable`:

- `@hidden`: when applied to the field means "do not log\write this field"
- `@masked`: when applied to the field means "mask field value with given mode"
- `@unembed`: when applied to the field means "log subfields along with fields of owner"

```scala
import tofu.logging.derivation._
import tofu.logging.derivation.loggable.generate
import tofu.logging.zlogs._
import zio._

case class User(
  id: Int,
  @hidden
  password: String,
  @masked(MaskMode.ForLength(3))
  login: String,
  godMode: Boolean = false
)

val user = User(100, "secret", "username")
val userAnnotation: ZLogAnnotation[User] = ZLogAnnotation.make("user")

val program: UIO[Unit] =
  ZIO.log("Hello, ZIO logging!") @@ userAnnotation(user)
```

The output of this program will be:

```json
{
  "level": "INFO",
  "logger_name": "my.package.Main",
  "message": "Hello, ZIO logging!",
  "user": {
    "id": 100,
    "login": "use*****",
    "godMode": false
  }
}
```

Read more on [the website](https://docs.tofu.tf/docs/tofu.logging.loggable).

## Logback layout

Tofu has a [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) provider
named `tofu.logging.TofuLoggingProvider`.
Enter the following content into the `logback.xml` file to get JSON logs:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<configuration>
    <appender name="logstash" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <pattern>
                    <pattern>
                        { "env": "prod" } <!-- optional static fields -->
                    </pattern>
                </pattern>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <message/>
                <provider class="tofu.logging.TofuLoggingProvider"/>
            </providers>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="logstash"/>
    </root>

</configuration>
```

Read more about logback layouts configuration on [the website](https://docs.tofu.tf/docs/tofu.logging.layouts).
