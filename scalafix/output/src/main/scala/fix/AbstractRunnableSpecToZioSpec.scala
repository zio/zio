package fix

import zio.test._
import zio.test.ZIOSpecDefault

class AbstractRunnableSpecToZioSpec {
  object HasSpec extends ZIOSpecDefault {
    override def spec = ???
  }
}
