package zio.test

import zio._

trait ZIOBaseSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspect.WithOut[
    Nothing,
    TestEnvironment,
    Nothing,
    Any,
    ({ type OutEnv[Env] = Env })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr
  ]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(60.seconds))
    else Chunk(TestAspect.sequential, TestAspect.timeout(60.seconds))
}
