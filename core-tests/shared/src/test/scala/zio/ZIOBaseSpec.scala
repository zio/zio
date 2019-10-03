package zio

import zio.duration._
import zio.test.{ DefaultRunnableSpec, TestAspect, ZSpec }
import zio.test.environment.TestEnvironment

abstract class ZIOBaseSpec(spec: => ZSpec[TestEnvironment, Any, String, Any])
    extends DefaultRunnableSpec(spec, List(TestAspect.timeout(60.seconds)))
