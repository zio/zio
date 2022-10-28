package zio

import zio.test._

object ConfigProviderSpec extends ZIOBaseSpec {
  def provider(map: Map[String, String]): ConfigProvider = ConfigProvider.fromMap(map)
  def propsProvider(props: Map[String, String]): UIO[ConfigProvider] =
    ZIO
      .foreachDiscard(props) { case (key, value) =>
        TestSystem.putProperty(key, value)
      }
      .as(ConfigProvider.propsProvider)

  final case class HostPort(host: String, port: Int)
  object HostPort {
    val config: Config[HostPort] = (Config.string("host") ++ Config.int("port")).map { case (a, b) => HostPort(a, b) }

    val default: HostPort = HostPort("localhost", 8080)
  }

  final case class ServiceConfig(hostPort: HostPort, timeout: Int)
  object ServiceConfig {
    val config: Config[ServiceConfig] =
      (HostPort.config.nested("hostPort") ++ Config.int("timeout")).map { case (a, b) => ServiceConfig(a, b) }

    val default: ServiceConfig = ServiceConfig(HostPort.default, 1000)
  }

  final case class HostPorts(hostPorts: List[HostPort])
  object HostPorts {
    val config: Config[HostPorts] = Config.listOf("hostPorts", HostPort.config).map(HostPorts(_))

    val default: HostPorts = HostPorts(List(HostPort.default))
  }

  def spec = suite("ConfigProviderSpec") {
    test("flat atoms") {
      for {
        value <- provider(Map("host" -> "localhost", "port" -> "8080")).load(HostPort.config)
      } yield assertTrue(value == HostPort.default)
    } +
      test("nested atoms") {
        for {
          value <- provider(Map("hostPort.host" -> "localhost", "hostPort.port" -> "8080", "timeout" -> "1000"))
                     .load(ServiceConfig.config)
        } yield assertTrue(value == ServiceConfig.default)
      } +
      test("top-level list") {
        for {
          value <-
            provider(Map("hostPorts.host" -> "localhost,localhost,localhost", "hostPorts.port" -> "8080,8080,8080"))
              .load(HostPorts.config)
        } yield assertTrue(value.hostPorts.length == 3)
      } +
      test("top-level missing list") {
        for {
          value <- provider(Map()).load(HostPorts.config)
        } yield assertTrue(value.hostPorts.length == 0)
      } +
      test("simple map") {
        for {
          value <- provider(Map("name" -> "Sherlock Holmes", "address" -> "221B Baker Street"))
                     .load(Config.table(Config.string))
        } yield assertTrue(value == Map("name" -> "Sherlock Holmes", "address" -> "221B Baker Street"))
      } +
      suite("props")(
        test("flat atoms") {
          for {
            provider <- propsProvider(Map("host" -> "localhost", "port" -> "8080"))
            result   <- provider.load(HostPort.config)
          } yield assertTrue(result == HostPort.default)
        },
        test("nested atoms") {
          for {
            provider <-
              propsProvider(Map("hostPort.host" -> "localhost", "hostPort.port" -> "8080", "timeout" -> "1000"))
            result <- provider.load(ServiceConfig.config)
          } yield assertTrue(result == ServiceConfig.default)
        },
        test("top-level list") {
          for {
            provider <- propsProvider(
                          Map("hostPorts.host" -> "localhost,localhost,localhost", "hostPorts.port" -> "8080,8080,8080")
                        )
            result <- provider.load(HostPorts.config)
          } yield assertTrue(result.hostPorts.length == 3)
        },
        test("top-level missing list") {
          for {
            provider <- propsProvider(Map())
            result   <- provider.load(HostPorts.config)
          } yield assertTrue(result.hostPorts.length == 0)
        },
        test("simple map") {
          for {
            provider <- propsProvider(Map("name" -> "Sherlock Holmes", "address" -> "221B Baker Street"))
            result   <- provider.load(Config.table(Config.string))
          } yield assertTrue(result == Map("name" -> "Sherlock Holmes", "address" -> "221B Baker Street"))
        },
        test("empty property name") {
          for {
            provider <- propsProvider(Map("" -> "42.24"))

            result2 <- provider.load(Config.double)
            result1 <- provider.load(Config.double(""))
          } yield assertTrue(result1 == 42.24, result2 == 42.24)
        },
        test("path delimiter property name") {
          for {
            provider <- propsProvider(Map("." -> "42", ".." -> "24"))

            result1 <- provider.load(Config.int("."))
            result2 <- provider.load(Config.int(".."))
          } yield assertTrue(result1 == 42, result2 == 24)
        },
        test("incorrect path property name") {
          for {
            provider <- propsProvider(Map(".a" -> "42"))

            result1 <- provider.load(Config.int(".a"))
            result2 <- provider.load(Config.int("a").nested(""))
          } yield assertTrue(result1 == 42, result2 == 42)
        },
        test("fail for non symmetric top-level list") {
          for {
            provider <- propsProvider(
                          Map("hostPorts.host" -> "localhost", "hostPorts.port" -> "8080,8080")
                        )
            result <- provider.load(HostPorts.config).flip.exit
          } yield assertTrue(
            result.exists(
              _ == Config.Error.MissingData(
                path = Chunk("hostPorts"),
                message = "The element at index 1 in a sequence at hostPorts was missing"
              )
            )
          )
        },
        test("fail for missing property") {
          for {
            provider <- propsProvider(Map.empty)
            result   <- provider.load(Config.string("key")).flip.exit
          } yield assertTrue(
            result.exists(
              _ == Config.Error.MissingData(
                path = Chunk("key"),
                message = "Expected key to be set in properties"
              )
            )
          )
        },
        test("fail for wrong property type") {
          for {
            provider <- propsProvider(Map("key" -> "value"))
            result   <- provider.load(Config.int("key")).flip.exit
          } yield assertTrue(
            result.exists(
              _ == Config.Error.InvalidData(
                path = Chunk("key"),
                message = "Expected an integer value, but found value"
              )
            )
          )
        }
      )
  }
}
