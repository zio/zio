package zio.test

import zio.duration._

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(60.seconds))
}
