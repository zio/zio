package zio.test

import zio.{Chunk, ZIO}
import zio.test.environment.TestEnvironment

/**
 * Syntax for writing test like
 * {{{
 * object MySpec extends MutableRunnableSpec {
 *   suite("foo") {
 *     testM("name") {
 *     } @@ ignore
 *
 *     test("name 2") {
 *       suite("another suite") {
 *       }
 *     }
 *   }
 * }
 * }}}
 */
trait MutableRunnableSpec extends DefaultRunnableSpec { self =>

  type ZS = ZSpec[Environment, Any]

  final class SpecBuilder(_spec: ZS) {

    var spec: ZS = _spec

    /**
     * Syntax for adding aspects.
     * {{{
     * test("foo") { assert(42, equalTo(42)) } @@ ignore
     * }}}
     */
    final def @@(
      aspect: TestAspect[Environment, Environment, Failure, Failure]
    ): SpecBuilder = {
      spec = spec @@ aspect
      this
    }

  }

  var allSuites: Chunk[SpecBuilder] = Chunk.empty
  var tests: Chunk[SpecBuilder]     = Chunk.empty

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite(label: String)(specs: => SpecBuilder): SpecBuilder = {
    specs
    val suite = zio.test.suite(label)(tests.map(_.spec): _*)
    tests = Chunk.empty
    val sb = new SpecBuilder(suite)
    allSuites = allSuites :+ sb
    sb
  }

  /**
   * Builds a spec with a single pure test.
   */
  def test(label: String)(assertion: => TestResult): SpecBuilder = {
    val test = zio.test.test(label)(assertion)
    val sb = new SpecBuilder(test)
    tests = tests :+ sb
    sb
  }

  /**
   * Builds a spec with a single effectful test.
   */
  def testM(label: String)(assertion: => ZIO[Environment, Failure, TestResult]): SpecBuilder = {
    val test = zio.test.testM(label)(assertion)
    val sb = new SpecBuilder(test)
    tests = tests :+ sb
    sb
  }

  override def spec: ZSpec[Environment, Failure] =
    zio.test.suite(self.getClass.getSimpleName)(
      allSuites.map(_.spec.asInstanceOf[Spec[Any, TestFailure[Any], TestSuccess]]): _*
    )
}
