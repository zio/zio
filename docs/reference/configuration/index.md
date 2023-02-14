---
id: index
title: "Introduction to Configuration in ZIO"
---


Configuration is a core concern for any cloud-native application. So ZIO ships with built-in support for configuration by providing a front-end for configuration providers as well as metrics and logging.

So, ZIO provides a unified way to configure our applications, while still enabling customizability, flexibility, and significant integrations with configuration backends via ecosystem projects, most notably ZIO Config.

This configuration front-end allows ecosystem libraries and applications to declaratively describe their configuration needs and delegates the heavy lifting to a ConfigProvider, which may be supplied by third-party libraries such as ZIO Config.

The ZIO Core ships with a simple default config provider, which reads configuration data from environment variables and if not found, from system properties. This can be used for development purposes or to bootstrap applications toward more sophisticated config providers.

## Primitive Configs

Let's start with a simple example of how to read configuration from environment variables and system properties:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = {
    for {
      host <- ZIO.config(Config.string("host"))
      port <- ZIO.config(Config.int("port"))
      _ <- Console.printLine(s"Application started: $host:$port")
    } yield ()
  }
}
```

If we run this application we will get the following output:

```bash
timestamp=2023-02-14T09:45:27.074151Z level=ERROR thread=#zio-fiber-0 message="" cause="Exception in thread "zio-fiber-4" zio.Config$Error$Or: ((Missing data at host: Expected HOST to be set in the environment) or (Missing data at host: Expected host to be set in properties))
```

This is because we have not provided any configuration. Let's try to run it with the following environment variables:

```bash
HOST=localhost PORT=8080 sbt "runMain MainApp"
```

Now we get the following output:

```bash
Application started: localhost:8080
```

We can also run it by setting system properties:

```bash
sbt -Dhost=localhost -Dport=8080 "runMain configMainApp"
```

## Custom Configs

Other than primitive types, we can also define a configuration for custom types. To do so, we need to use primitive configs and combine them together.

Let's say we have the `HostPort` data type, which consists of two fields: `host` and `port`:

```scala mdoc:compile-only
case class HostPort(host: String, port: Int)
```

We can define a config for this type in its companion object like this:

```scala mdoc:compile-only
object HostPort {
  val config: Config[HostPort] =
    (Config.string("host") ++ Config.int("port")).map { case (host, port) =>
      HostPort(host, port)
    }
}
```

If we use this customized config in our application, it tries to read corresponding values from environment variables (`HOST` and `PORT`) and system properties (`host` and `port`):

```scala mdoc:compile-only
for {
  config <- ZIO.config(HostPort.config)
  _      <- Console.printLine(s"Application started: $config")
} yield ()
```

## Top-level and Nested Configs

So far we have seen how to define configuration in a top-level manner, whether it is a primitive or a custom type. But we can also define a nested configuration.

Assume we have a `SerivceConfig` data type that consists of two fields: `hostPort` and `timeout`:

```scala mdoc:compile-only
final case class ServiceConfig(hostPort: HostPort, timeout: Int)
```

Let's define a config for this type in its companion object:

```scala mdoc:compile-only
object ServiceConfig {
  val config: Config[ServiceConfig] =
    (HostPort.config ++ Config.int("timeout")).map {
      case (a, b) => ServiceConfig(a, b)
    }
}
```

If we use this customized config in our application, it tries to read corresponding values from environment variables (`HOST`, `PORT`, and `TIMEOUT`) and, if not found from system properties (`host`, `port`, and `timeout`).

But in most circumstances, we don't want to read all the configurations from the top-level namespace. Instead, we want to nest them under a common namespace. In this case, we want to read both `HOST` and `PORT` from the `HOSTPORT` namespace, and `TIMEOUT` from the root namespace. In order to do that, we can use the `nested` combinator:

```diff
object ServiceConfig {
  val config: Config[ServiceConfig] =
-    (HostPort.config ++ Config.int("timeout")).map {
+    (HostPort.config.nested("hostport") ++ Config.int("timeout")).map {
      case (a, b) => ServiceConfig(a, b)
    }
}
```

Now, if we run our application, it tries to read corresponding values from environment variables (`HOSTPORT_HOST`, `HOSTPORT_PORT` and `TIMEOUT`) and, if not found it tries to read from system properties (`hostport.host`, `hostport.port` and `timeout`).

## Built-in Config Providers

ZIO has some built-in config providers:

- `ConfigProvider.defaultProvider` - reads configuration from environment variables and if not found, from system properties
- `ConfigProvider.envProvider` - reads configuration from environment variables
- `ConfigProvider.propsProvider` - reads configuration from system properties
- `ConfigProvider.constProvider` - reads configuration from interactive console prompts

Other than these built-in providers, we can also use third-party providers in ZIO ecosystem libraries, such as ZIO Config which provides a rich set of backends for reading configuration from different sources such as HOCON, JSON, YAML, etc.

## Custom Config Provider

We can also define our own custom config providers.

The default config provider is used by default, but we can also override it by using `Runtime#setConfigProvider`.

In the following example, we set the default config provider to `consoleProvider` which reads configuration from the console:

```scala mdoc:compile-only
object MainAppScoped extends ZIOAppDefault {
  override val bootstrap: ZLayer[Any, Nothing, Unit] =
    Runtime.setConfigProvider(ConfigProvider.consoleProvider)

  def run =
    for {
      host <- ZIO.config(Config.string("host"))
      port <- ZIO.config(Config.int("port"))
      _ <- Console.printLine(s"Application started: http://$host:$port")
    } yield ()
}
```

:::note
The console provider is stored inside a `FiberRef`, so we can override it in a scoped manner. This is useful for changing the config provider for a specific part of the application.
:::

## Testing Services

When testing services, we sometimes need to provide some configuration to them. So we should be able to mock any backend that we use for reading configuration data.

In order to do that, we can use the `ConfigProvider.fromMap` constructor, which takes a map of configuration data and returns a config provider that reads configuration from that map. Then we can pass that to the `Runtime.setConfigProvider`, which returns a `ZLayer` that we can use to override the default config provider for our test specs using `Spec#provideLayer` operator:

```scala mdoc:compile-only
import zio._
import zio.test._

object MyServiceTest extends ZIOSpecDefault {

  val mockConfigProvider: ZLayer[Any, Nothing, Unit] =
    Runtime.setConfigProvider(ConfigProvider.fromMap(Map("timeout" -> "5s")))

  // This service reads configuration data (host and port) inside its implementation
  def myService: ZIO[Any, Config.Error, Double] = ???

  override def spec = {
    val expected: Double = ??? // expected value
    test("test myService") {
      for {
        result <- myService
      } yield assertTrue(result == expected)
    }
  }.provideLayer(mockConfigProvider)
  
}
```
