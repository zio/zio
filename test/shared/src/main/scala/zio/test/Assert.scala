package zio.test

import scala.util.Try

trait StandardAssertions {
  import zio.test.{ErrorMessage => M}

  def get[A]: Assert[Option[A], A] =
    Assert
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.halt("Option was None")
      }

  def greaterThan[A](that: A)(implicit numeric: Numeric[A]): Assert[A, Boolean] =
    Assert
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(numeric.gt(a, that)) {
          M.value(a) + M.was + "greater than" + M.value(that)
        }
      }

  def equalTo[A](that: A): Assert[A, Boolean] =
    Assert
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(a == that) {
          M.value(a) + M.equals + M.value(that)
        }
      }

  val throws: Assert[Any, Throwable] = Assert.makeEither(
    Trace.succeed,
    _ => Trace.halt("Expected failure")
  )

}

sealed trait Assert[-A, +B] { self =>

  import Assert._

  def meta(span: Option[Span] = None, parentSpan: Option[Span] = None, code: Option[String] = None): Assert[A, B] =
    Meta(assert = self, span = span, parentSpan = parentSpan, code = code)

  def span(span: (Int, Int)): Assert[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  def withCode(code: String): Assert[A, B] =
    meta(code = Some(code))

  def withParentSpan(span: (Int, Int)): Assert[A, B] =
    meta(parentSpan = Some(Span(span._1, span._2)))

  def >>>[C](that: Assert[B, C]): Assert[A, C] = AndThen[A, B, C](self, that)

  def &&(that: Assert[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    And(self.asInstanceOf[Assert[Any, Boolean]], that)

  def ||(that: Assert[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    Or(self.asInstanceOf[Assert[Any, Boolean]], that)

  def unary_!(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    Not(self.asInstanceOf[Assert[Any, Boolean]])
}

object Assert extends StandardAssertions {
  def succeed[A](value: A): Assert[Any, A] = Arrow(_ => Trace.succeed(value))

  def fromFunction[A, B](f: A => B): Assert[A, B] = make(f andThen Trace.succeed)

  def make[A, B](f: A => Trace[B]): Assert[A, B] =
    makeEither(e => Trace.fail(e).annotate(Trace.Annotation.Rethrow), f)

  def makeEither[A, B](onFail: Throwable => Trace[B], onSucceed: A => Trace[B]): Assert[A, B] =
    Arrow {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](f: => Trace[A]): Trace[A] = Try(f).fold(e => Trace.fail(e), identity)

  def run[A, B](assert: Assert[A, B], in: Either[Throwable, A]): Trace[B] = attempt {
    assert match {
      case Arrow(f) =>
        f(in)

      case AndThen(f, g) =>
        val t1 = run(f, in)
        t1 match {
          case Trace.Halt()         => t1.asInstanceOf[Trace[B]]
          case Trace.Fail(err)      => t1 >>> run(g, Left(err))
          case Trace.Succeed(value) => t1 >>> run(g, Right(value))
        }

      case And(lhs, rhs) =>
        run(lhs, in) && run(rhs, in)

      case Or(lhs, rhs) =>
        run(lhs, in) || run(rhs, in)

      case Not(assert) =>
        !run(assert, in)

      case Meta(assert, span, parentSpan, code) =>
        println("META")
        println(parentSpan)
        run(assert, in).withSpan(span).withCode(code).withParentSpan(parentSpan)
    }
  }

  case class Span(start: Int, end: Int) {
    def substring(str: String): String = str.substring(start, end)

  }

  case class Meta[-A, +B](assert: Assert[A, B], span: Option[Span], parentSpan: Option[Span], code: Option[String])
      extends Assert[A, B]
  case class Arrow[-A, +B](f: Either[Throwable, A] => Trace[B])           extends Assert[A, B] {}
  case class AndThen[A, B, C](f: Assert[A, B], g: Assert[B, C])           extends Assert[A, C]
  case class And(left: Assert[Any, Boolean], right: Assert[Any, Boolean]) extends Assert[Any, Boolean]
  case class Or(left: Assert[Any, Boolean], right: Assert[Any, Boolean])  extends Assert[Any, Boolean]
  case class Not(assert: Assert[Any, Boolean])                            extends Assert[Any, Boolean]
}
