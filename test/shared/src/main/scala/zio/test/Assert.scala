package zio.test

import zio.test.Trace.Annotation

import scala.util.Try

trait StandardAssertions {
  def get[A]: Assert[Option[A], A] =
    Assert
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.halt("Option was None")
      }
      .label(".get")

  def greaterThan[A](that: A)(implicit numeric: Numeric[A]): Assert[A, Boolean] =
    Assert
      .make[A, Boolean] { (a: A) =>
        Trace.succeed(numeric.gt(a, that))
//        else Trace.halt(s"$a is not greater than $that")
      }
      .label("greaterThan")

  def equalTo[A](that: A): Assert[A, Boolean] =
    Assert
      .make[A, Boolean] { (a: A) =>
        if (a == that) Trace.succeed(true)
        else Trace.halt(s"$a is not equal to $that")
      }
      .label("equalTo")

  val throws: Assert[Any, Throwable] = Assert.makeEither(
    Trace.succeed,
    _ => Trace.halt("Expected failure")
  )

}

sealed trait Assert[-A, +B] { self =>

  import Assert._

  def label(label: String): Assert[A, B] =
    meta(label = Some(label))

  def meta(label: Option[String] = None, span: Option[Span] = None, code: Option[String] = None): Assert[A, B] =
    Meta(assert = self, label = label, span = span, code = code)

  def span(span0: (Int, Int)): Assert[A, B] =
    meta(span = Some(Span(span0._1, span0._2)))

  def withCode(code: String): Assert[A, B] =
    meta(code = Some(code))

  def >>>[C](that: Assert[B, C]): Assert[A, C] = AndThen[A, B, C](self, that)

  def zip[A1 <: A, C](that: Assert[A1, C]): Assert[A1, (B, C)] = Zip(self, that)

  def &&(that: Assert[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    (self.asInstanceOf[Assert[Any, Boolean]] zip that) >>>
      make { case (a, b) => Trace.succeed(a && b).annotate(Annotation.And) }

  def ||(that: Assert[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Assert[Any, Boolean] =
    (self.asInstanceOf[Assert[Any, Boolean]] zip that) >>>
      make { case (a, b) => Trace.succeed(a || b).annotate(Annotation.Or) }

  def debug: String = self match {
    case Meta(assert, label, _, _) => s"($label ${assert.debug})"
    case Arrow(_)                  => s"*"
    case AndThen(f, g)             => s"${f.debug} >>> ${g.debug}"
    case and: Zip[_, _, _]         => s"(${and.left}) && (${and.right})"
  }
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

      case Zip(lhs, rhs) =>
        run(lhs, in) zip run(rhs, in)

      case Meta(assert, label, span, code) =>
        run(assert, in).meta(label, span).withCode(code)

    }
  }

  case class Span(start: Int, end: Int)

  case class Meta[-A, +B](assert: Assert[A, B], label: Option[String], span: Option[Span], code: Option[String])
      extends Assert[A, B]
  case class Arrow[-A, +B](f: Either[Throwable, A] => Trace[B]) extends Assert[A, B] {}

  case class AndThen[A, B, C](f: Assert[A, B], g: Assert[B, C])    extends Assert[A, C]
  case class Zip[A, B, C](left: Assert[A, B], right: Assert[A, C]) extends Assert[A, (B, C)]
}
