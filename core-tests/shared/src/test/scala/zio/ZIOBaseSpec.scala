package zio

import zio.duration._

import zio.test._
import zio.test.mock._

abstract class ZIOBaseSpec(spec: => ZSpec[MockEnvironment, Any, String, Any])
    extends DefaultRunnableSpec(spec, List(TestAspect.timeout(60.seconds)))
