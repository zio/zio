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

  final case class StockDay(
    date: java.time.LocalDate,
    open: BigDecimal,
    close: BigDecimal,
    low: BigDecimal,
    high: BigDecimal,
    volume: BigInt
  )
  object StockDay {
    val config: Config[StockDay] =
      (Config.localDate("date") ++ Config.bigDecimal("open") ++ Config.bigDecimal("close") ++ Config.bigDecimal(
        "low"
      ) ++ Config.bigDecimal("high") ++ Config.bigInt("volume")).map { case (a, b, c, d, e, f) =>
        StockDay(a, b, c, d, e, f)
      }

    val default: StockDay = StockDay(java.time.LocalDate.of(2022, 10, 28), 98.8, 150.0, 98.0, 151.5, 100091990)
  }

  final case class SNP500(stockDays: Map[String, StockDay])
  object SNP500 {
    val config: Config[SNP500] = Config.table(StockDay.config).map(SNP500(_))

    val default: SNP500 = SNP500(Map("ZIO" -> StockDay.default))
  }

  final case class WebScrapingTargets(targets: Set[java.net.URI])
  object WebScrapingTargets {
    val config: Config[WebScrapingTargets] = (Config.setOf("targets", Config.uri)).map(WebScrapingTargets(_))

    val default: WebScrapingTargets = WebScrapingTargets(
      Set(new java.net.URI("https://zio.dev"), new java.net.URI("https://github.com/zio"))
    )
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
      test("top-level list with same number of elements per key") {
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
      test("top-level lists with multi-character sequence delimiters") {
        for {
          value <-
            ConfigProvider
              .fromMap(
                Map("hostPorts.host" -> "localhost///localhost///localhost", "hostPorts.port" -> "8080///8080///8080"),
                seqDelim = "///"
              )
              .load(HostPorts.config)
        } yield assertTrue(value.hostPorts.length == 3)
      } +
      test("top-level lists with special regex multi-character sequence delimiter") {
        for {
          value <-
            ConfigProvider
              .fromMap(
                Map("hostPorts.host" -> "localhost|||localhost|||localhost", "hostPorts.port" -> "8080|||8080|||8080"),
                seqDelim = "|||"
              )
              .load(HostPorts.config)
        } yield assertTrue(value.hostPorts.length == 3)
      } +
      test("top-level lists with special regex character sequence delimiter") {
        for {
          value <-
            ConfigProvider
              .fromMap(
                Map("hostPorts.host" -> "localhost*localhost*localhost", "hostPorts.port" -> "8080*8080*8080"),
                seqDelim = "*"
              )
              .load(HostPorts.config)
        } yield assertTrue(value.hostPorts.length == 3)
      } +
      test("top-level list with different number of elements per key fails") {
        for {
          exit <-
            provider(Map("hostPorts.host" -> "localhost", "hostPorts.port" -> "8080,8080,8080"))
              .load(HostPorts.config)
              .exit
        } yield assert(exit)(Assertion.failsWithA[Config.Error])
      } +
      test("flat atoms of different types") {
        for {
          value <- provider(
                     Map(
                       "date"   -> "2022-10-28",
                       "open"   -> "98.8",
                       "close"  -> "150.0",
                       "low"    -> "98.0",
                       "high"   -> "151.5",
                       "volume" -> "100091990"
                     )
                   ).load(StockDay.config)
        } yield assertTrue(value == StockDay.default)
      } +
      test("tables") {
        for {
          value <- provider(
                     Map(
                       "ZIO.date"   -> "2022-10-28",
                       "ZIO.open"   -> "98.8",
                       "ZIO.close"  -> "150.0",
                       "ZIO.low"    -> "98.0",
                       "ZIO.high"   -> "151.5",
                       "ZIO.volume" -> "100091990"
                     )
                   ).load(SNP500.config)
        } yield assertTrue(value == SNP500.default)
      } @@ TestAspect.failing +
      test("collection of atoms") {
        for {
          value <- provider(Map("targets" -> "https://zio.dev,https://github.com/zio")).load(WebScrapingTargets.config)
        } yield assertTrue(value == WebScrapingTargets.default)
      } +
      test("accessing a non-existent key fails") {
        for {
          exit <- provider(Map("k1.k3" -> "v")).load(Config.string("k2").nested("k1")).exit
        } yield assert(exit)(Assertion.failsWithA[Config.Error])
      }
  }
}
