package zio

import zio.test._

object ConfigDerivationSpec extends ZIOBaseSpec {

  final case class DatabaseConfig(host: String, port: Int, useSsl: Boolean) derives Config

  final case class ServiceConfig(database: DatabaseConfig, tags: List[String], retries: Option[Int]) derives Config

  def spec =
    suite("Config.derived")(
      test("derives Config for a product type") {
        for {
          value <- ConfigProvider
                     .fromMap(Map("host" -> "localhost", "port" -> "5432", "useSsl" -> "true"))
                     .load(summon[Config[DatabaseConfig]])
        } yield assertTrue(value == DatabaseConfig("localhost", 5432, useSsl = true))
      },
      test("derives Config for nested products and supported collections") {
        for {
          value <- ConfigProvider
                     .fromMap(
                       Map(
                         "database.host"   -> "localhost",
                         "database.port"   -> "5432",
                         "database.useSsl" -> "false",
                         "tags"            -> "api,worker",
                         "retries"         -> "3"
                       )
                     )
                     .load(summon[Config[ServiceConfig]])
        } yield assertTrue(
          value == ServiceConfig(DatabaseConfig("localhost", 5432, useSsl = false), List("api", "worker"), Some(3))
        )
      }
    )
}
