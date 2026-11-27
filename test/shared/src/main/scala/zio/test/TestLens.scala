package zio.test

/**
 * CustomAssertion allows users to create their own custom assertions for use in
 * `assertTrue`. They are constructed with `CustomAssertion.make` from a
 * function from `A` to `Either[String, B]`, and used with
 * `value.is(_.custom(assertion))`: the assertion fails with the message of a
 * `Left`, and continues with the value of a `Right`.
 *
 * {{{
 * sealed trait Pet
 * final case class Dog(hasBone: Boolean)    extends Pet
 * final case class Cat(livesRemaining: Int) extends Pet
 *
 * val lives: CustomAssertion[Pet, Int] =
 *   CustomAssertion.make[Pet] {
 *     case Cat(livesRemaining) => Right(livesRemaining)
 *     case other               => Left(s"Expected \$other to be a Cat")
 *   }
 *
 * suite("custom assertions")(
 *   test("a cat has lives left") {
 *     val pet: Option[Pet] = Some(Cat(8))
 *     assertTrue(pet.is(_.some.custom(lives)) == 8)
 *   }
 * )
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/assertions/smart-assertions/ Smart Assertions]]
 */
final class CustomAssertion[A, B] private[test] (private[test] val run: A => Either[String, B])

/**
 * Constructors of [[CustomAssertion]] values.
 */
object CustomAssertion {

  /**
   * Creates a [[CustomAssertion]] from a function from `A` to `Either[String,
   * B]`. Returning `Right(b)` continues the assertion with `b`, whereas
   * `Left(message)` makes it fail with `message`.
   *
   * {{{
   * sealed trait Color
   * case object Red   extends Color
   * case object Green extends Color
   *
   * val red: CustomAssertion[Color, Red.type] =
   *   CustomAssertion.make[Color] {
   *     case Red => Right(Red)
   *     case _   => Left("Expected Red!")
   *   }
   * }}}
   */
  def make[A]: MakePartiallyApplied[A] =
    new MakePartiallyApplied[A]

  /**
   * The second step of `CustomAssertion.make`: it fixes the input type `A`, so
   * that the output type `B` is inferred from the function.
   */
  final class MakePartiallyApplied[A] {

    /**
     * Creates a [[CustomAssertion]] from `run`.
     */
    def apply[B](run: A => Either[String, B]): CustomAssertion[A, B] =
      new CustomAssertion[A, B](run)
  }
}

/**
 * The type of a value inside `value.is(...)` in `assertTrue`. Its extension
 * methods look into the value: `some` for an `Option`, `left` and `right` for
 * an `Either`, `success`, `failure`, `die`, `cause` and `interrupted` for an
 * `Exit`, `failure`, `die` and `interrupted` for a `Cause`, `success` and
 * `failure` for a `Try`, and `anything`, `subtype` and `custom` for any value.
 * The `assertTrue` macro rewrites these calls; called anywhere else, they
 * throw an exception.
 *
 * {{{
 * test("nested values") {
 *   val result: Either[String, Option[Int]] = Right(Some(42))
 *   assertTrue(result.is(_.right.some) == 42)
 * }
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/assertions/smart-assertions/ Smart Assertions]]
 */
final case class TestLens[+A]()

private case class SmartAssertionExtensionError() extends Throwable {
  override def getMessage: String =
    s"This method can only be called inside of `assertTrue`"
}
