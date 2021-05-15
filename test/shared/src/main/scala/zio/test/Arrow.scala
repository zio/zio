package zio.test

import scala.reflect.ClassTag
import scala.util.Try

case class Assert private (val arrow: Arrow[Any, Boolean]) {
  def &&(that: Assert): Assert = Assert(arrow && that.arrow)

  def ||(that: Assert): Assert = Assert(arrow || that.arrow)

  def unary_! : Assert = Assert(!arrow)
}

object Assert {
  def all(asserts: Assert*): Assert = asserts.reduce(_ && _)

  def any(asserts: Assert*): Assert = asserts.reduce(_ || _)
}

private[test] object Assertions {
  import zio.test.{ErrorMessage => M}

  private def className[A](a: Iterable[A]) =
    M.text(a.toString().takeWhile(_ != '('))

  def isSome[A]: Arrow[Option[A], A] =
    Arrow
      .make[Option[A], A] {
        case Some(value) => Trace.succeed(value)
        case None        => Trace.halt("Option was None")
      }

  def isEmptyIterable[A]: Arrow[Iterable[A], Boolean] =
    Arrow
      .make[Iterable[A], Boolean] { a =>
        Trace.boolean(a.isEmpty) {
          className(a) + M.was + "empty"
        }
      }

  def hasAt[A](index: Int): Arrow[Seq[A], A] =
    Arrow
      .make[Seq[A], A] { as =>
        Try(as(index)).toOption match {
          case Some(value) => Trace.succeed(value)
          case None        => Trace.halt(s"Index $index is not within ${as.length}")
        }
      }

  def greaterThan[A](that: A)(implicit numeric: Numeric[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(numeric.gt(a, that)) {
          M.value(a) + M.was + "greater than" + M.value(that)
        }
      }

  def greaterThanOrEqualTo[A](that: A)(implicit numeric: Numeric[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(numeric.gteq(a, that)) {
          M.value(a) + M.was + "greater than or equal to" + M.value(that)
        }
      }

  def lessThan[A](that: A)(implicit numeric: Numeric[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(numeric.lt(a, that)) {
          M.value(a) + M.was + "less than" + M.value(that)
        }
      }

  def lessThanOrEqualTo[A](that: A)(implicit numeric: Numeric[A]): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(numeric.lteq(a, that)) {
          M.value(a) + M.was + "less than or equal to" + M.value(that)
        }
      }

  def equalTo[A](that: A): Arrow[A, Boolean] =
    Arrow
      .make[A, Boolean] { (a: A) =>
        Trace.boolean(a == that) {
          M.value(a) + M.equals + M.value(that)
        }
      }

  val throws: Arrow[Any, Throwable] =
    Arrow.makeEither(
      Trace.succeed,
      _ => Trace.halt("Expected failure")
    )

}

sealed trait Arrow[-A, +B] { self =>
  import Arrow._

  def meta(span: Option[Span] = None, parentSpan: Option[Span] = None, code: Option[String] = None): Arrow[A, B] =
    Meta(assert = self, span = span, parentSpan = parentSpan, code = code)

  def span(span: (Int, Int)): Arrow[A, B] =
    meta(span = Some(Span(span._1, span._2)))

  def withCode(code: String): Arrow[A, B] =
    meta(code = Some(code))

  def withParentSpan(span: (Int, Int)): Arrow[A, B] =
    meta(parentSpan = Some(Span(span._1, span._2)))

  def >>>[C](that: Arrow[B, C]): Arrow[A, C] = AndThen[A, B, C](self, that)

  def &&(that: Arrow[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    And(self.asInstanceOf[Arrow[Any, Boolean]], that)

  def ||(that: Arrow[Any, Boolean])(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    Or(self.asInstanceOf[Arrow[Any, Boolean]], that)

  def unary_!(implicit ev: Any <:< A, ev2: B <:< Boolean): Arrow[Any, Boolean] =
    Not(self.asInstanceOf[Arrow[Any, Boolean]])
}

object Arrow {
  def succeed[A](value: A): Arrow[Any, A] = ArrowF(_ => Trace.succeed(value))

  def fromFunction[A, B](f: A => B): Arrow[A, B] = make(f andThen Trace.succeed)

  def make[A, B](f: A => Trace[B]): Arrow[A, B] =
    makeEither(e => Trace.fail(e).annotate(Trace.Annotation.Rethrow), f)

  def makeEither[A, B](onFail: Throwable => Trace[B], onSucceed: A => Trace[B]): Arrow[A, B] =
    ArrowF {
      case Left(error)  => onFail(error)
      case Right(value) => onSucceed(value)
    }

  private def attempt[A](f: => Trace[A]): Trace[A] =
    Try(f).fold(e => Trace.fail(e), identity)

  def run[A, B](assert: Arrow[A, B], in: Either[Throwable, A]): Trace[B] = attempt {
    assert match {
      case ArrowF(f) =>
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
        run(assert, in).withSpan(span).withCode(code).withParentSpan(parentSpan)
    }
  }

  case class Span(start: Int, end: Int) {
    def substring(str: String): String = str.substring(start, end)

  }

  case class Meta[-A, +B](assert: Arrow[A, B], span: Option[Span], parentSpan: Option[Span], code: Option[String])
      extends Arrow[A, B]
  case class ArrowF[-A, +B](f: Either[Throwable, A] => Trace[B])        extends Arrow[A, B] {}
  case class AndThen[A, B, C](f: Arrow[A, B], g: Arrow[B, C])           extends Arrow[A, C]
  case class And(left: Arrow[Any, Boolean], right: Arrow[Any, Boolean]) extends Arrow[Any, Boolean]
  case class Or(left: Arrow[Any, Boolean], right: Arrow[Any, Boolean])  extends Arrow[Any, Boolean]
  case class Not(assert: Arrow[Any, Boolean])                           extends Arrow[Any, Boolean]
}
