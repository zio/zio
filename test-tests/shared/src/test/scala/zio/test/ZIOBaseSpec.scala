package zio.test

import zio.duration._
import zio.test.environment.Live

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspectAtLeastR[Live]] =
    if (TestPlatform.isJVM) List(TestAspect.timeout(60.seconds))
    else List(TestAspect.sequential, TestAspect.timeout(60.seconds))
}
