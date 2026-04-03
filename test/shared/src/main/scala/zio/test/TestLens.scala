package zio.test

/**
 * CustomAssertion allows users to create their own custom assertions for use in
 * `assertTrue`. They are constructed with `CustomAssertion.make`.
 *
 * {{{
 * // Definition
 * sealed trait Pet
 * case class Dog(hasBone: Boolean) extends Pet
 * case class Fish(bubbles: Double) extends Pet
 * case class Cat(livesRemaining: Int) extends Color
 *
 * val lives =
 *   CustomAssertion.make[Pet] {
 *     case Cat(livesRemaining) => Right(livesRemaining)
 *     case other => Left(s"Expected $$other to be Cat")
 *   }
 *
 * // Usage
 * suite("custom assertions")(
 *   test("as even") {
 *     val pet: Option[Pet] = Some(Cat(8))
 *     assertTrue(pet.is(_.some.custom(lives)) == 8)
 *   }
 * )
 * }}}
 */
final class CustomAssertion[A, B] private[test] (private[test] val run: A => Either[String, B])

object CustomAssertion {

  /**
   * Creates a SmartAssertion from a function from A to Either[String, B].
   * Returning `Right[A]` will transform the input, whereas `Left[String]` will
   * result in an error with that string as a message.
   *
   * {{{
   *   sealed trait Color
   *   case object Red extends Color
   *   case object Green extends Color
   *   case object Blue extends Color
   *
   *   val red: SmartAssertion[Color, Red] =
   *     SmartAssertion.make[Color] {
   *       case red: Red => Right(red)
   *       case _        => Left("Expected Red!")
   *     }
   * }}}
   */
  def make[A]: MakePartiallyApplied[A] =
    new MakePartiallyApplied[A]

  final class MakePartiallyApplied[A] {
    def apply[B](run: A => Either[String, B]): CustomAssertion[A, B] =
      new CustomAssertion[A, B](run)
  }
}

final case class TestLens[+A]()

private case class SmartAssertionExtensionError() extends Throwable {
  override def getMessage: String =
    s"This method can only be called inside of `assertTrue`"
}
