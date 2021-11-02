package fix

import zio.{Has, ZIOAppArgs, ZLayer}
import zio.test._
import zio.test.ZIOSpecDefault
import zio.test.environment.TestEnvironment // Only add this when necessary


class AbstractRunnableSpecToZioSpec {
  object HasSpec extends ZIOSpecDefault {
    override def spec = ???

    override def layer: ZLayer[Has[ZIOAppArgs], Any, TestEnvironment] =
      zio.ZEnv.live >>> TestEnvironment.live
  }
}
