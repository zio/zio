package zio.test

import zio._
import zio.test.Assertion._
import zio.test.ShareLayersAcrossSpecs._

import java.util.concurrent.atomic.AtomicInteger

object ShareLayersAcrossSpecs {

  private val counter = new AtomicInteger(0)

  final case class BoxedInt(i: Int) {
    override def toString: String =
      s"BoxedInt($i)@${System.identityHashCode(this).toHexString}"
  }

  val sharedLayer: ULayer[Has[BoxedInt]] = {
    UIO(BoxedInt(counter.getAndIncrement())).toLayer
  }

  val assertWeHaveABoxedZeroInTheEnv: ZIO[Has[BoxedInt], Nothing, TestResult] =
    assertM(
      ZIO
        .service[BoxedInt]
        .map(_.i)
    )(equalTo(0))
}

object ShareLayersAcrossSpecsSpec1 extends CustomRunnableSpec(sharedLayer) {
  override def spec: ZSpec[Environment with SharedEnvironment, Failure] =
    suite("Shared layer across specs - 1")(
      testM("The same BoxedInt instance should be shared across all Specs")(
        assertWeHaveABoxedZeroInTheEnv
      )
    )
}

object ShareLayersAcrossSpecsSpec2 extends CustomRunnableSpec(sharedLayer) {
  override def spec: ZSpec[Environment with SharedEnvironment, Failure] =
    suite("Shared layer across specs - 2")(
      testM("The same BoxedInt instance should be shared across all Specs")(
        assertWeHaveABoxedZeroInTheEnv
      )
    )
}
