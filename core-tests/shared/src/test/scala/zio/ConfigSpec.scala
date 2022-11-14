package zio

import zio.test._

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

  def spec =
    suite("ConfigSpec")(
      secretSuite
    )
}
