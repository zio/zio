package zio.test

import org.scalacheck.rng.Seed
import org.scalacheck.Gen.Parameters
import org.scalacheck.{Prop, Properties, Test => CheckTest}
import zio._
import zio.test.{ErrorMessage => M}

/**
 * Connects ScalaCheck to ZIO Test. Importing `zio.test.scalacheck._` adds three
 * extension methods:
 *
 *   - `toGenZIO` on a ScalaCheck `Gen`, which converts it to a ZIO Test
 *     `zio.test.Gen`
 *   - `assertZIO` on a ScalaCheck `Prop`, which checks it and returns a
 *     `zio.test.TestResult`
 *   - `assertZIO` on ScalaCheck `Properties`, which checks each property and
 *     returns a `zio.test.TestResult`
 *
 * '''Generators'''
 *
 * `toGenZIO` converts existing ScalaCheck generators to ZIO Test generators, so
 * that you can move to ZIO Test without reimplementing them. To use it, import
 * this package and call `toGenZIO` on a ScalaCheck generator. For example:
 *
 * {{{
 * import org.scalacheck.Arbitrary
 *
 * import zio._
 * import zio.test._
 * import zio.test.scalacheck._
 *
 * val anyInt: Gen[Any, Int] =
 *   Arbitrary.arbitrary[Int].toGenZIO
 * }}}
 *
 * '''Asserting ScalaCheck `Prop` and `Properties`'''
 *
 * `assertZIO` checks a ScalaCheck `Prop` or `Properties` with ScalaCheck's test
 * runner and returns the outcome as a ZIO Test `TestResult`. This helps with
 * libraries that provide their checks as ScalaCheck properties, such as
 * cats-laws.
 *
 * `Prop` example:
 *
 * {{{
 * import org.scalacheck.Prop
 * import org.scalacheck.Test.{ Parameters => ScalaCheckParameters }
 *
 * import zio._
 * import zio.test._
 * import zio.test.scalacheck._
 *
 * val prop: Prop = Prop.forAll { (n: Int, m: Int) =>
 *   n + m == m + n
 * }
 * val resultDefault: TestResult = prop.assertZIO()
 *
 * val resultWithCustomizations: TestResult =
 *   prop.assertZIO("My Prop Name", ScalaCheckParameters.default.withMaxSize(10))
 * }}}
 *
 * `Properties` example:
 *
 * {{{
 * import org.scalacheck.{ Prop, Properties }
 * import org.scalacheck.Test.{ Parameters => ScalaCheckParameters }
 *
 * import zio._
 * import zio.test._
 * import zio.test.scalacheck._
 *
 * object MyProperties extends Properties("MyProperties") {
 *   property("myProp") = Prop.forAll { (n: Int, m: Int) =>
 *     n + m == m + n
 *   }
 * }
 *
 * val resultDefault: TestResult = MyProperties.assertZIO()
 *
 * // There is no name parameter here: each failing property
 * // is reported by its own name
 * val resultWithCustomizations: TestResult =
 *   MyProperties.assertZIO(ScalaCheckParameters.default.withMaxSize(10))
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
package object scalacheck {

  /**
   * Adds `toGenZIO` to a ScalaCheck `Gen`.
   */
  implicit final class ScalaCheckGenSyntax[A](private val self: org.scalacheck.Gen[A]) extends AnyVal {

    /**
     * Converts this ScalaCheck `Gen` to a ZIO Test `Gen`. Each sample runs the
     * ScalaCheck generator with a seed taken from ZIO's `Random` service and
     * with the size from ZIO Test's `Sized` service (100 in the default test
     * environment), so the samples are the same for the same `TestRandom` seed.
     * The resulting generator does not shrink. When the ScalaCheck generator
     * yields no value, for example because a `suchThat` filter rejects every
     * candidate, the effect dies with `org.scalacheck.Gen.RetrievalError`.
     */
    def toGenZIO: Gen[Any, A] =
      Gen.fromZIO {
        for {
          long <- Random.nextLong
          size <- Sized.size
          a    <- ZIO.succeed(self.pureApply(Parameters.default.withSize(size), Seed(long)))
        } yield a
      }
  }

  /**
   * Adds `assertZIO` to a ScalaCheck `Prop`.
   */
  implicit final class ScalaCheckPropSyntax(private val self: Prop) extends AnyVal {

    /**
     * Checks this ScalaCheck `Prop` with ScalaCheck's test runner and returns a
     * ZIO Test `TestResult` that succeeds when the check passes. The check uses
     * `testParams`, not ZIO Test's `check` settings.
     *
     * @param name
     *   the name of the assertion, shown when it fails (`ScalaCheck Prop
     *   Assertion` unless given)
     * @param testParams
     *   the ScalaCheck test parameters, `Test.Parameters.default` unless given
     */
    def assertZIO(
      name: String = "ScalaCheck Prop Assertion",
      testParams: CheckTest.Parameters = CheckTest.Parameters.default
    ): TestResult =
      Assertion.assertion[Prop](name)(prop => CheckTest.check(testParams, prop).passed).run(self)
  }

  /**
   * Adds `assertZIO` to ScalaCheck `Properties`.
   */
  implicit final class ScalaCheckPropertiesSyntax(private val self: Properties) extends AnyVal {

    /**
     * Checks each property of these ScalaCheck `Properties` with ScalaCheck's
     * test runner and returns a ZIO Test `TestResult` that succeeds when all of
     * them pass. Each failing property is reported by its name. The check uses
     * `testParams`, not ZIO Test's `check` settings. When the `Properties` has
     * no properties, the test fails with a
     * `java.lang.UnsupportedOperationException`.
     *
     * @param testParams
     *   the ScalaCheck test parameters, `Test.Parameters.default` unless given
     */
    def assertZIO(
      testParams: CheckTest.Parameters = CheckTest.Parameters.default
    ): TestResult =
      Assertion(
        TestArrow
          .make[Properties, Boolean] { props =>
            CheckTest
              .checkProperties(testParams, props)
              .map { case (name, result) =>
                TestTrace.boolean(result.passed) {
                  M.text(name) + M.choice("succeeded", "failed")
                }
              }
              .reduce(_ && _)
          }
          .withCode(self.name)
      ).run(self)
  }
}
