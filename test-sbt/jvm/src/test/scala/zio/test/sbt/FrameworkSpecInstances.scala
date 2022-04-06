package zio.test.sbt

import sbt.testing.{Event, EventHandler}
import zio.{ZIO, ZLayer}
import zio.test.{TestAspect, assertCompletes}

import java.util.concurrent.atomic.AtomicInteger

object FrameworkSpecInstances {

  val dummyHandler: EventHandler = (_: Event) => ()

  val counter = new AtomicInteger(0)

  lazy val sharedLayer: ZLayer[Any, Nothing, Int] = {
    ZLayer.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))
  }

  def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    zio.test.test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
    }

  lazy val simpleSpec = SimpleSpec.getClass.getName
  object SimpleSpec extends zio.test.ZIOSpec[Int] {
    override def layer = ZLayer.succeed(1)

    def spec =
      suite("simple suite")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
      ) @@ TestAspect.parallel
  }

  lazy val spec1UsingSharedLayer = Spec1UsingSharedLayer.getClass.getName
  object Spec1UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def layer = sharedLayer

    def spec =
      suite("suite with shared layer")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
        numberedTest(specIdx = 1, suiteIdx = 1, 2),
        numberedTest(specIdx = 1, suiteIdx = 1, 3),
        numberedTest(specIdx = 1, suiteIdx = 1, 4)
      ) @@ TestAspect.parallel
  }

  lazy val spec2UsingSharedLayer = Spec2UsingSharedLayer.getClass.getName
  object Spec2UsingSharedLayer extends zio.test.ZIOSpec[Int] {
//    override def layer = sharedLayer
    override def layer = ZLayer.succeed(1)

    def spec =
      zio.test.test("test completes with shared layer 2") {
        assertCompletes
      }
  }

}
