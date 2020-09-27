package zio.test

import zio.duration._
import zio.test.environment.Live

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspectAtLeastR[Live]] = List(TestAspect.timeout(600.seconds))
}
