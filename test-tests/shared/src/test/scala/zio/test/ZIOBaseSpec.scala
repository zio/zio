package zio.test

import zio.duration._

trait ZIOBaseSpec extends RunnableSpec {
  override def aspects = List(TestAspect.timeout(60.seconds))
}
