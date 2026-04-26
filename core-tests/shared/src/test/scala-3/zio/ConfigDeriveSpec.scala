/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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
import zio.test.Assertion._

object ConfigDeriveSpec extends ZIOBaseSpec {

  // A simple case class that derives Config
  case class ServerConfig(host: String, port: Int) derives Config

  // Nested case class
  case class DbConfig(url: String, maxPoolSize: Int) derives Config
  case class AppConfig(server: ServerConfig, db: DbConfig) derives Config

  def spec = suite("Config.derived")(
    test("derives Config for a simple case class") {
      val provider = ConfigProvider.fromMap(
        Map("host" -> "localhost", "port" -> "8080")
      )
      for {
        cfg <- provider.load(summon[Config[ServerConfig]])
      } yield assert(cfg)(equalTo(ServerConfig("localhost", 8080)))
    },
    test("reports missing field with descriptive error") {
      val provider = ConfigProvider.fromMap(Map("host" -> "localhost"))
      for {
        result <- provider.load(summon[Config[ServerConfig]]).either
      } yield assert(result)(isLeft(anything))
    },
    test("derives Config for nested case classes") {
      val provider = ConfigProvider.fromMap(
        Map(
          "server.host"    -> "example.com",
          "server.port"    -> "443",
          "db.url"         -> "jdbc:postgresql://localhost/mydb",
          "db.maxPoolSize" -> "10"
        )
      )
      for {
        cfg <- provider.load(summon[Config[AppConfig]])
      } yield assert(cfg)(
        equalTo(
          AppConfig(
            ServerConfig("example.com", 443),
            DbConfig("jdbc:postgresql://localhost/mydb", 10)
          )
        )
      )
    }
  )
}
