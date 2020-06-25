package zio.query

import zio.duration._
import zio.test._

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects = List(TestAspect.timeout(600.seconds))
}
