package scalaz.zio.testkit

final class MatchException(e: String) extends Exception(e)

trait SimpleMatchers {
  def compare[A](message: String)(f: A => Boolean)(got: A): Result =
    if (f(got)) Succeeded else Failed(new MatchException(message))

  def equals[A](expected: A, got: A) = compare[A](s"Expected ${expected}, got ${got}")(x => x == expected)(got)

  def expect[A](expected: A)(got: A) = equals(expected, got)
}

object matchers extends SimpleMatchers
