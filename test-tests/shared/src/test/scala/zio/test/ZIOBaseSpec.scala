package zio.test

import zio._

trait ZIOBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(60.seconds))
    else Chunk(TestAspect.timeout(60.seconds), TestAspect.sequential)
}
