package zio.query

// import zio.URIO
import zio.duration._
import zio.test._
// import zio.clock.Clockimport zio.test.environment._

trait ZIOBaseSpec extends DefaultRunnableSpec {

  override def aspects =
    List(TestAspect.timeout(600.seconds))

  // override def runner: TestRunner[TestEnvironment, Any] =
  //   defaultTestRunner

  // /**
  //  * Returns an effect that executes a given spec, producing the results of the execution.
  //  */
  // private[zio] override def runSpec(
  //   spec: ZSpec[Environment, Failure]
  // ): URIO[TestLogger with Clock, ExecutedSpec[Failure]] =
  //   runner.run(aspects.foldLeft(spec)(_ @@ _))
}
