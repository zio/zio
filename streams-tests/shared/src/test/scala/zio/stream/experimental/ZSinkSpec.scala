package zio.stream.experimental

import zio._
import zio.test._

object ZSinkSpec extends ZIOBaseSpec {
  def spec = suite("ZSinkSpec")(
    suite("Constructors")(),
    suite("Combinators")()
  )
}
