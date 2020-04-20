package zio
package stm

import zio.test.Assertion._
import zio.test._

object ZSTMAlternativeSpec extends ZIOBaseSpec {
  def spec =
    suite("ZSTM.alternative") {
      testM("retries left after right retries") {
        for {
          ref     <- TRef.makeCommit(0)
          left    = ref.get.flatMap(v => STM.check(v > 500).as("left"))
          right   = STM.retry
          updater = ref.update(_ + 10).commit.forever
          res     <- (left <|> right).commit.race(updater)
        } yield assert(res)(equalTo("left"))
      }
    }
}
