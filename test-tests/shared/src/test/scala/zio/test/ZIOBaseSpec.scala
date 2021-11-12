package zio.test

import zio._

trait ZIOBaseSpec extends DefaultRunnableSpec {
  override def aspects: List[TestAspect.WithOut[
    Nothing,
    TestEnvironment,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ]] =
    if (TestPlatform.isJVM) List(TestAspect.timeout(60.seconds))
    else List(TestAspect.sequential, TestAspect.timeout(60.seconds))
}
