package zio.test.sbt

import sbt.testing.{Event, EventHandler}
import zio.{UIO, ZIO, ZLayer, durationInt}
import zio.test.{
  Annotations,
  Assertion,
  ExecutionEvent,
  Spec,
  TestAspect,
  TestFailure,
  ZIOSpecDefault,
  ZTestEventHandler,
  assertCompletes
}

import java.util.concurrent.atomic.AtomicInteger

object FrameworkSpecInstances {

  val dummyHandler: ZTestEventHandler = new ZTestEventHandler {
    override def handle(event: ExecutionEvent.Test[_]): UIO[Unit] = ZIO.unit
  }

  val counter = new AtomicInteger(0)

  lazy val sharedLayer: ZLayer[Any, Nothing, Int] = {
    ZLayer.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))
  }

  def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    zio.test.test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
    }

  object SimpleSpec extends zio.test.ZIOSpec[Int] {
    override def environmentLayer = ZLayer.succeed(1)

    def spec =
      suite("simple suite")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1)
      ) @@ TestAspect.parallel
  }

  object TimeOutSpec extends zio.test.ZIOSpecDefault {

    def spec =
      suite("simple suite")(
        test("slow test")(
          for {
            _ <- ZIO.sleep(3.seconds)
          } yield assertCompletes
        )
      ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(1.second)
  }

  object RuntimeExceptionSpec extends zio.test.ZIOSpec[Int] {
    override def environmentLayer = ZLayer.succeed(1)

    def spec =
      suite("explording suite")(
        test("boom") {
          if (true) throw new RuntimeException("Good luck ;)")
          assertCompletes
        }
      )
  }

  // TODO Restore this once
  //    https://github.com/zio/zio/pull/6614 is merged
//  object RuntimeExceptionDuringLayerConstructionSpec extends zio.test.ZIOSpec[Int] {
//    override val layer = ZLayer.fromZIO(
//      ZIO.debug("constructing faulty layer") *>
//        ZIO.attempt(throw new BindException("Other Kafka container already grabbed your port"))
//    )
//
//    def spec =
//      suite("kafka suite")(
//        test("does stuff with a live kafka cluster") {
//          assertCompletes
//        }
//      )
//  }

  object Spec1UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def environmentLayer = sharedLayer

    def spec =
      suite("suite with shared layer")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
        numberedTest(specIdx = 1, suiteIdx = 1, 2),
        numberedTest(specIdx = 1, suiteIdx = 1, 3),
        numberedTest(specIdx = 1, suiteIdx = 1, 4)
      ) @@ TestAspect.parallel
  }

  object Spec2UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def environmentLayer = sharedLayer

    def spec =
      zio.test.test("test completes with shared layer 2") {
        assertCompletes
      }
  }

  object MultiLineSharedSpec extends ZIOSpecDefault {
    def spec = test("multi-line test") {
      zio.test.assert("Hello,\nWorld!")(Assertion.equalTo("Hello, World!"))
    }
  }

  lazy val failingSpecFQN = SimpleFailingSharedSpec.getClass.getName

  object SimpleFailingSharedSpec extends ZIOSpecDefault {
    def spec: Spec[Annotations, TestFailure[Any]] = zio.test.suite("some suite")(
      test("failing test") {
        zio.test.assert(1)(Assertion.equalTo(2))
      },
      test("passing test") {
        zio.test.assert(1)(Assertion.equalTo(1))
      },
      test("ignored test") {
        zio.test.assert(1)(Assertion.equalTo(2))
      } @@ TestAspect.ignore
    )
  }

}
