/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

import zio.test._

object ConfigDerivationSpec extends ZIOBaseSpec {

  // Simple product type using `derives Config` — the primary use case from issue #9268
  final case class DatabaseConfig(host: String, port: Int, useSsl: Boolean) derives Config

  // Nested product with collection and optional fields
  final case class ServiceConfig(database: DatabaseConfig, tags: List[String], retries: Option[Int]) derives Config

  def spec =
    suite("Config.derived")(
      test("derives Config for a simple product type") {
        for {
          value <- ConfigProvider
                     .fromMap(Map("host" -> "localhost", "port" -> "5432", "useSsl" -> "true"))
                     .load(summon[Config[DatabaseConfig]])
        } yield assertTrue(value == DatabaseConfig("localhost", 5432, useSsl = true))
      },
      test("derives Config for nested products, List, and Option fields") {
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
      },
      test("optional field is None when missing from config") {
        for {
          value <- ConfigProvider
                     .fromMap(
                       Map(
                         "database.host"   -> "db.example.com",
                         "database.port"   -> "3306",
                         "database.useSsl" -> "true",
                         "tags"            -> "prod"
                       )
                     )
                     .load(summon[Config[ServiceConfig]])
        } yield assertTrue(
          value == ServiceConfig(DatabaseConfig("db.example.com", 3306, useSsl = true), List("prod"), None)
        )
      }
    )
}
