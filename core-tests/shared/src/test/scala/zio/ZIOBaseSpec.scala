package zio

import zio.duration._
import zio.test._

trait ZIOBaseSpec extends RunnableSpec {
  override def aspects = List(TestAspect.timeout(60.seconds))
}
