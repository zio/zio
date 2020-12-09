package zio

import java.util.concurrent.Executors

import zio.internal.Executor
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import scala.concurrent.ExecutionContext

object CancelableFutureSpecJVM extends ZIOBaseSpec {

  import ZIOTag._

  def spec: ZSpec[ZTestEnv with Annotations with TestConfig, Any] =
    suite("CancelableFutureSpecJVM")(
      testM("fromFuture/unsafeRunToFuture doesn't deadlock") {

        val tst =
          for {
            runtime <- ZIO.runtime[Any]
            r       <- ZIO.fromFuture(_ => runtime.unsafeRunToFuture(UIO.succeedNow(0)))
          } yield assert(r)(equalTo(0))
        ZIO
          .runtime[Any]
          .map(
            _.mapPlatform(
              _.withExecutor(
                Executor.fromExecutionContext(1)(
                  ExecutionContext.fromExecutor(Executors.newSingleThreadScheduledExecutor())
                )
              )
            ).unsafeRun(tst)
          )
      } @@ nonFlaky
    ) @@ zioTag(future)
}
