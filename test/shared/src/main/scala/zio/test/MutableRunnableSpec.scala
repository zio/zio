package zio.test

import zio.{Chunk, ZIO}

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

  private[test] final class SpecBuilder[R, E, T](_spec: Spec[R, E, T]) {

    var spec: Spec[R, E, T] = _spec

    /**
     * Syntax for adding aspects.
     * {{{
     * test("foo") { assert(42, equalTo(42)) } @@ ignore
     * }}}
     */
    final def @@[R0 <: R1, R1 <: R, E0, E1, E2 >: E0 <: E1](
      aspect: TestAspect[R0, R1, E0, E1]
    )(implicit ev1: E <:< TestFailure[E2], ev2: T <:< TestSuccess): SpecBuilder[R1, E1, T] = {
      spec = spec.@@[R0, R1, E0, E1, E2](aspect).asInstanceOf[Spec[R, E, T]]
      this.asInstanceOf[SpecBuilder[R1, E1, T]]
    }

  }

  var allSuites: Chunk[SpecBuilder[_, _, _]] = Chunk.empty
  var tests: Chunk[SpecBuilder[_, _, _]]     = Chunk.empty

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite[R, E, T](label: String)(specs: => SpecBuilder[R, E, T]): SpecBuilder[R, E, T] = {
    specs
    val suite = zio.test.suite(label)(tests.map(_.spec.asInstanceOf[Spec[R, E, T]]): _*)
    tests = Chunk.empty
    val sb = new SpecBuilder(suite)
    allSuites = allSuites :+ sb
    sb.asInstanceOf[SpecBuilder[R, E, T]]
  }

  /**
   * Builds a spec with a single pure test.
   */
  def test(label: String)(assertion: => TestResult): SpecBuilder[Any, TestFailure[Nothing], TestSuccess] = {
    val test = zio.test.test(label)(assertion)
    val sb   = new SpecBuilder(test)
    tests = tests :+ sb
    sb
  }

  /**
   * Builds a spec with a single effectful test.
   */
  def testM[R, E](label: String)(assertion: => ZIO[R, E, TestResult]): SpecBuilder[R, TestFailure[E], TestSuccess] = {
    val test = zio.test.testM(label)(assertion)
    val sb   = new SpecBuilder(test)
    tests = tests :+ sb
    sb
  }

  override def spec: ZSpec[Any, Any] =
    zio.test.suite(self.getClass.getSimpleName)(
      allSuites.map(_.spec.asInstanceOf[Spec[Any, TestFailure[Any], TestSuccess]]): _*
    )
}
