package zio.stream.experimental

import zio._
import zio.test._

object ZTransducerSpec extends ZIOBaseSpec {
  def spec = suite("ZTransducerSpec")(
    suite("Combinators")(),
    suite("Constructors")()
  )
}
