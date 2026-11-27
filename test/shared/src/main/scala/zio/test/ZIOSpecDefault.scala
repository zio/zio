package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.Tracer.{instance, newTrace}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The base class for a test suite whose tests need only the standard
 * `TestEnvironment`. Extend it with an `object` and define `spec`. The sbt test
 * framework from `zio-test-sbt` finds such objects and runs them, and because
 * the class is a `ZIOApp`, the object can also be run as a main class.
 *
 * {{{
 * import zio.ZIO
 *
 * object ArithmeticSpec extends ZIOSpecDefault {
 *   def spec =
 *     suite("ArithmeticSpec")(
 *       test("addition") {
 *         assertTrue(1 + 1 == 2)
 *       },
 *       test("an effect") {
 *         ZIO.succeed(6 * 7).map(n => assertTrue(n == 42))
 *       }
 *     )
 * }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/ ZIO Test]]
 */
abstract class ZIOSpecDefault extends ZIOSpec[TestEnvironment] {

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment

  /**
   * The spec that this object runs. Its tests can use the services of
   * `TestEnvironment` and a `Scope`.
   */
  def spec: Spec[TestEnvironment with Scope, Any]
}
