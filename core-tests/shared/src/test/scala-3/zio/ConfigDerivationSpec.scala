package zio

import zio.test._

object ConfigDerivationSpec extends ZIOBaseSpec {

  final case class DatabaseConfig(url: String, poolSize: Int) derives Config

  final case class ServiceConfig(
    database: DatabaseConfig,
    enabled: Boolean,
    ports: List[Int],
    tags: Set[String],
    token: Option[String]
  ) derives Config

  def spec =
    suite("ConfigDerivationSpec")(
      test("derives config for product types") {
        val configProvider =
          ConfigProvider.fromMap(
            Map(
              "database.url"      -> "jdbc:postgresql://localhost:5432/zio",
              "database.poolSize" -> "16",
              "enabled"           -> "true",
              "ports"             -> "8080,8081",
              "tags"              -> "api,public"
            )
          )

        for {
          config <- configProvider.load(summon[Config[ServiceConfig]])
        } yield assertTrue(
          config == ServiceConfig(
            database = DatabaseConfig("jdbc:postgresql://localhost:5432/zio", 16),
            enabled = true,
            ports = List(8080, 8081),
            tags = Set("api", "public"),
            token = None
          )
        )
      }
    )
}
