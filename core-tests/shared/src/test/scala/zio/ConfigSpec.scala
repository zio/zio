package zio

import zio.test._
import zio.test.Assertion._

import zio.Config.Secret

object ConfigSpec extends ZIOBaseSpec {

  def secretSuite =
    suite("Secret")(
      test("Chunk constructor") {
        val secret = Secret(Chunk.fromIterable("secret".toIndexedSeq))

        assertTrue(secret == Secret("secret"))
      } +
        test("Chunk extractor") {
          val chunk  = Chunk.fromIterable("secret".toIndexedSeq)
          val secret = Secret(chunk)

          assertTrue {
            secret match {
              case Secret(chunk2) => chunk == chunk2
            }
          }
        } +
        test("String constructor") {
          Secret("abc")
          assertCompletes
        } +
        test("CharSequence constructor") {
          Secret("abc": CharSequence)
          assertCompletes
        } +
        test("toString") {
          assertTrue(Secret("secret").toString() == "Secret(<redacted>)")
        } +
        test("equals") {
          assertTrue(Secret("secret") == Secret("secret")) &&
          assertTrue(Secret("secret1") != Secret("secret2"))
        } +
        test("hashCode") {
          assertTrue(Secret("secret").hashCode == Secret("secret").hashCode) &&
          assertTrue(Secret("secret1").hashCode != Secret("secret2").hashCode)
        } +
        test("wipe") {
          val secret = Secret("secret")

          secret.unsafe.wipe(Unsafe.unsafe)

          assertTrue(secret.hashCode == Chunk.fill[Char]("secret".length)(0).hashCode)
        }
    )

  def withDefaultSuite =
    suite("withDefault")(
      test("recovers from missing data error") {
        val config         = Config.int("key").withDefault(0)
        val configProvider = ConfigProvider.fromMap(Map.empty)
        for {
          value <- configProvider.load(config)
        } yield assert(value)(equalTo(0))
      },
      test("does not recover from other errors") {
        val config         = Config.int("key").withDefault(0)
        val configProvider = ConfigProvider.fromMap(Map("key" -> "value"))
        for {
          value <- configProvider.load(config).exit
        } yield assert(value)(failsWithA[Config.Error])
      },
      test("does not recover from missing data and other error") {
        val config         = Config.int("key1").zip(Config.int("key2")).withDefault((0, 0))
        val configProvider = ConfigProvider.fromMap(Map("key2" -> "value"))
        for {
          value <- configProvider.load(config).exit
        } yield assert(value)(failsWithA[Config.Error])
      },
      test("recovers from missing data or other error") {
        val config         = Config.int("key1").orElse(Config.int("key2")).withDefault(0)
        val configProvider = ConfigProvider.fromMap(Map("key2" -> "value"))
        for {
          value <- configProvider.load(config)
        } yield assert(value)(equalTo(0))
      }
    )

  def spec =
    suite("ConfigSpec")(
      secretSuite,
      withDefaultSuite
    )
}
