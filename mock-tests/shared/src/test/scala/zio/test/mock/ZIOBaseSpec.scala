package zio.mock

import zio._
import zio.test._

trait ZIOBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(60.seconds))
    else Chunk(TestAspect.sequential, TestAspect.timeout(60.seconds))
}
