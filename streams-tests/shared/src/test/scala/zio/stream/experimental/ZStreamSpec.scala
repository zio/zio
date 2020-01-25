package zio.stream.experimental

import zio._
import zio.test._

object ZStreamSpec extends ZIOBaseSpec {
  def spec = suite("ZStreamSpec")(
    suite("Combinators")(),
    suite("Constructors")()
  )
}
