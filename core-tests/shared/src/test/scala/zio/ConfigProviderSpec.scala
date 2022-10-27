package zio

import zio.test._

object ConfigProviderSpec extends ZIOBaseSpec {
  def provider(map: Map[String, String]): ConfigProvider = ConfigProvider.fromMap(map)

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
      }
  }
}
