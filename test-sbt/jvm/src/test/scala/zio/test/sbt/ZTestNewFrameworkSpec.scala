package zio.test.sbt

import sbt.testing.{Event, EventHandler, SuiteSelector, TaskDef}
import zio.{ZIO, ZLayer}
import zio.test.Assertion.equalTo
import zio.test.{TestAspect, ZIOSpecDefault, assertCompletes, testConsole}

import java.util.concurrent.atomic.AtomicInteger

object ZTestNewFrameworkSpec extends ZIOSpecDefault {
  override def spec = suite("test framework in a more ZIO-centric way")(
    test("basic happy path")(
      for {
        _       <- loadAndExecuteAll(Seq(spec1UsingSharedLayer))
        console <- testConsole
        output  <- console.output
        _       <- ZIO.debug(s"output: ${output.mkString("\n")}")
      } yield assertCompletes
    )
  )

  def loadAndExecuteAll(
    fqns: Seq[String],
    testArgs: Array[String] = Array.empty
  ) = {

    val tasks =
      fqns
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray
    val task = new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasksZ(tasks)
      .get // TODO Unsafe

    task.executeZ(dummyHandler)
  }

  val dummyHandler: EventHandler = (_: Event) => ()

  private val counter = new AtomicInteger(0)

  lazy val sharedLayer: ZLayer[Any, Nothing, Int] = {
    ZLayer.fromZIO(ZIO.succeed(counter.getAndUpdate(value => value + 1)))
  }

  val randomFailure =
    zio.test.assert(new java.util.Random().nextInt())(equalTo(2))

  def numberedTest(specIdx: Int, suiteIdx: Int, testIdx: Int) =
    zio.test.test(s"spec $specIdx suite $suiteIdx test $testIdx") {
      assertCompletes
    }

  lazy val spec1UsingSharedLayer = Spec1UsingSharedLayer.getClass.getName
  object Spec1UsingSharedLayer extends zio.test.ZIOSpec[Int] {
    override def layer = sharedLayer

    def spec =
      suite("basic suite")(
        numberedTest(specIdx = 1, suiteIdx = 1, 1),
        numberedTest(specIdx = 1, suiteIdx = 1, 2),
        numberedTest(specIdx = 1, suiteIdx = 1, 3),
        numberedTest(specIdx = 1, suiteIdx = 1, 4)
      ) @@ TestAspect.parallel
  }
}
