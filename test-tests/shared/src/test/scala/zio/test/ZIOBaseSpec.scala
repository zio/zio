package zio.test

import zio.duration._

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(
    if (TestPlatform.isJVM) TestAspect.timeout(60.seconds) else TestAspect.timeout(120.seconds),
    TestAspect.timed
  )
}
