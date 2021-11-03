package zio.test

import zio._

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspectAtLeastR[Has[Live]]] =
    if (TestPlatform.isJVM) List(TestAspect.timeout(60.seconds))
    else List(TestAspect.sequential, TestAspect.timeout(60.seconds))
}
