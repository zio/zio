package zio.test.sbt

import sbt.testing.{SuiteSelector, TaskDef}
import zio.ZIO
import zio.test.sbt.FrameworkSpecInstances.SimpleSpec
import zio.test.{ZIOSpecDefault, assertCompletes, assertTrue, testConsole}

object ZTestNewFrameworkSpec extends ZIOSpecDefault {

  override def spec = suite("test framework in a more ZIO-centric way")(
    test("basic happy path") (
      for {
        _ <- loadAndExecuteAll(Seq(SimpleSpec.getClass.getName))
        output <- testOutput
      } yield assertTrue(output.mkString.contains("1 tests passed. 0 tests failed. 0 tests ignored."))
    ),
    test("ensure shared layers are not re-initialized")(
      for {
        _ <- loadAndExecuteAll(Seq(FrameworkSpecInstances.spec1UsingSharedLayer, FrameworkSpecInstances.spec2UsingSharedLayer))
      } yield assertTrue(FrameworkSpecInstances.counter.get == 1)

    ),
    suite("warn when no tests are executed")(
      test("TODO")(
      for {
        _ <- loadAndExecuteAll(Seq())
      } yield assertCompletes
      )

    )
  )

  val testOutput =
    for {
      console <- testConsole
      output  <- console.output
    } yield output

  val dumpTestOutput =
    for {
      output  <- testOutput
      outputString = "=================\n" + output.mkString + "=================\n"
      _       <- ZIO.debug(outputString)
    } yield output

  def loadAndExecuteAll(
                                 fqns: Seq[String],
                                 testArgs: Array[String] = Array.empty
                               ) = {

    val tasks =
      fqns
        .map(fqn => new TaskDef(fqn, ZioSpecFingerprint, false, Array(new SuiteSelector)))
        .toArray
    new ZTestFramework()
      .runner(testArgs, Array(), getClass.getClassLoader)
      .tasksZ(tasks)
      .map(_.executeZ(FrameworkSpecInstances.dummyHandler))
      .getOrElse(ZIO.unit) // TODO What do we want to do here?


  }
}
