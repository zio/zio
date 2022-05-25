package zio.test

import zio._

trait ZIOBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(120.seconds), TestAspect.fibers)
    else Chunk(TestAspect.timeout(120.seconds), TestAspect.sequential, TestAspect.fibers)
}
