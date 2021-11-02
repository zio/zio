package zio.test

import scala.language.implicitConversions

/**
 * SmartAssertion allows users to create their own custom assertions for use
 * in `assertTrue`. They are constructed with `SmartAssertion.cond` and
 * `SmartAssertion.either`.
 *
 * {{{
 * // Definitions
 * implicit final class DoubleOps(private val self: Double) extends AnyVal {
 *   def ~=(that: Double): SmartAssertion[Boolean] =
 *     SmartAssertion.cond(
 *       self.round == that.round,
 *       s"$self is not approximately equal to $that"
 *     )
 *
 *   def asEven: SmartAssertion[Double] =
 *     SmartAssertion.fromEither(
 *       Either.cond(
 *         self % 2 == 0,
 *         self,
 *         s"$self must be even."
 *       )
 *     )
 * }
 *
 * // Usage
 * suite("custom assertions")(
 *   test("approximately equal to") {
 *     val double = 12.5
 *     assertTrue(double ~= 12.8)
 *   },
 *   test("as even") {
 *     val double = 12.0
 *     assertTrue(double.asEven * 2 == 24.0)
 *   }
 * )
 * }}}
 */
final class SmartAssertion[A] private[test] (val result: () => Either[String, A], val error: String) {

  /**
   * Converts a SmartAssertion into its result inside of `assertTrue`.
   * This is to be used when the implicit `smartAssertion2Value` conversion
   * does not fire, usually due to calling `==` directly on a SmartAssertion.
   *
   * {{{
   *   val color: Color = ???
   *   assertTrue(asRed(color).run == Red(50.0))
   * }}}
   */
  def run: A =
    throw new Error("This must only be called within `assertTrue`")
}

object SmartAssertion {

  /**
   * Creates a SmartAssertion from a predicate and an error. The error will
   * be used when the predicate fails.
   *
   * {{{
   *   def isEven(int: Int): SmartAssertion[Boolean] =
   *     SmartAssertion.cond(int % 2 == 0, s"$int must be even")
   * }}}
   */
  def cond(predicate: => Boolean, error: String): SmartAssertion[Boolean] =
    new SmartAssertion(() => Right(predicate), error)

  /**
   * Creates a SmartAssertion an either. Return `Right[A]` will transform the
   * input, whereas `Left[String]` will result in an error with that string as
   * a message.
   *
   * {{{
   *   def asRed(color: Color): SmartAssertion[Color.Red] =
   *     SmartAssertion.fromEither {
   *       color match {
   *         case red: Red => Right(red)
   *         case other    => Left(s"Expected Red, got $other")
   *       }
   *     }
   * }}}
   */
  def fromEither[A](result: => Either[String, A]): SmartAssertion[A] =
    new SmartAssertion(() => result, "")

  implicit def smartAssertion2Value[A](smartAssertion: SmartAssertion[A]): A = {
    val _ = smartAssertion
    throw new Error("This must only be called within `assertTrue`")
  }
}
